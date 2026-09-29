package eu.kanade.tachiyomi.metadata.tmdb

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches per-season poster URLs from TMDB using the IMDB ID extracted from
 * Cinemeta URLs. The posters are cached in-memory per IMDB ID.
 *
 * TMDB API key is the same one used by NuvioMobile.
 */
class TmdbSeasonPosters(
    private val networkHelper: NetworkHelper,
    private val json: Json,
) {
    companion object {
        private const val API_KEY = "d131017ccc6e5462a81c9304d21476de"
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val POSTER_BASE = "https://image.tmdb.org/t/p/w500"
        private const val STILL_BASE = "https://image.tmdb.org/t/p/w780"
    }

    private val client: OkHttpClient get() = networkHelper.client

    // In-memory cache: imdbId -> (seasonNumber -> posterUrl)
    private val cache = mutableMapOf<String, Map<Int, String>>()

    // In-memory cache: imdbId -> (seasonNumber -> season overview)
    private val overviewCache = mutableMapOf<String, Map<Int, String>>()

    // In-memory cache: imdbId -> series-level poster URL
    private val seriesPosterCache = mutableMapOf<String, String>()

    // In-memory cache: imdbId -> (seasonNumber -> (episodeNumber -> stillUrl)).
    // Only non-empty season results are cached — a season that failed
    // transiently is retried on the next call instead of being stuck empty.
    private val stillsCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, Map<Int, String>>>()

    @Serializable
    private data class TmdbFindResponse(
        val tv_results: List<TmdbTvResult> = emptyList(),
    )

    @Serializable
    private data class TmdbTvResult(
        val id: Long,
    )

    @Serializable
    private data class TmdbTvDetails(
        val poster_path: String? = null,
        val seasons: List<TmdbSeason> = emptyList(),
    )

    @Serializable
    private data class TmdbSeason(
        val season_number: Int,
        val poster_path: String? = null,
        val overview: String? = null,
    )

    @Serializable
    private data class TmdbSeasonDetails(
        val episodes: List<TmdbEpisode> = emptyList(),
    )

    @Serializable
    private data class TmdbEpisode(
        val episode_number: Int,
        val still_path: String? = null,
    )

    /**
     * Get a map of season number to poster URL for the given IMDB ID.
     * Returns an empty map if the show is not found or the API fails.
     */
    suspend fun getSeasonPosters(imdbId: String): Map<Int, String> {
        cache[imdbId]?.let { return it }

        return try {
            val tmdbId = findTmdbId(imdbId) ?: return emptyMap()
            val details = getTvDetails(tmdbId) ?: return emptyMap()

            val posters = details.seasons.mapNotNull { season ->
                season.poster_path?.let { path ->
                    season.season_number to "$POSTER_BASE$path"
                }
            }.toMap()

            synchronized(cache) {
                cache[imdbId] = posters
            }
            posters
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Get episode still URLs for the given IMDB ID, as
     * (seasonNumber -> (episodeNumber -> stillUrl)).
     *
     * Used as the primary thumbnail source for Cinemeta episodes — Cinemeta's
     * own thumbnail CDN (metahub) only proxies TMDB and has big coverage gaps
     * (e.g. Naruto only has season-1 stills; later seasons 404).
     */
    suspend fun getEpisodeStills(imdbId: String): Map<Int, Map<Int, String>> {
        val tmdbId = try {
            findTmdbId(imdbId)
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "TmdbSeasonPosters: find failed for $imdbId: ${e.message}" }
            null
        } ?: return emptyMap()

        val details = try {
            getTvDetails(tmdbId)
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "TmdbSeasonPosters: tv details failed for $imdbId: ${e.message}" }
            null
        } ?: return emptyMap()

        val seasonCache = stillsCache.getOrPut(imdbId) { java.util.concurrent.ConcurrentHashMap() }

        return coroutineScope {
            details.seasons.map { it.season_number }.map { seasonNumber ->
                async {
                    seasonCache[seasonNumber]?.let { return@async seasonNumber to it }
                    val stills = getSeasonStills(tmdbId, seasonNumber)
                    if (stills.isNotEmpty()) seasonCache[seasonNumber] = stills
                    seasonNumber to stills
                }
            }.awaitAll().toMap()
        }
    }

    /**
     * Get episode stills keyed by ABSOLUTE episode index (1..N across all
     * non-special seasons in order). Cinemeta and TMDB often split the same
     * show into different season counts (Naruto: Cinemeta 5 vs TMDB 4), so
     * season+episode lookup misses — absolute order matches because both
     * follow original air order.
     */
    suspend fun getEpisodeStillsByAbsoluteIndex(imdbId: String): Map<Int, String> {
        val perSeason = getEpisodeStills(imdbId)
        val flat = LinkedHashMap<Int, String>()
        var absolute = 0
        perSeason.keys.filter { it > 0 }.sorted().forEach { season ->
            perSeason[season]?.toSortedMap()?.forEach { (_, url) ->
                flat[++absolute] = url
            }
        }
        return flat
    }

    /**
     * Get a map of season number to TMDB season overview for the given
     * IMDB ID — used for per-season descriptions in the detail view.
     * Returns an empty map if the show is not found or the API fails.
     */
    suspend fun getSeasonOverviews(imdbId: String): Map<Int, String> {
        overviewCache[imdbId]?.let { return it }

        return try {
            val tmdbId = findTmdbId(imdbId) ?: return emptyMap()
            val details = getTvDetails(tmdbId) ?: return emptyMap()

            val overviews = details.seasons.mapNotNull { season ->
                season.overview?.takeIf { it.isNotBlank() }?.let {
                    season.season_number to it
                }
            }.toMap()

            synchronized(overviewCache) {
                overviewCache[imdbId] = overviews
            }
            overviews
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Get the series-level poster URL for the given IMDB ID. Used as the
     * fallback cover when the selected season has no TMDB poster (e.g.
     * Cinemeta splits a show into more seasons than TMDB tracks — Naruto's
     * "Season 5" has no TMDB counterpart).
     */
    suspend fun getSeriesPoster(imdbId: String): String? {
        seriesPosterCache[imdbId]?.let { return it }

        return try {
            val tmdbId = findTmdbId(imdbId) ?: return null
            val poster = getTvDetails(tmdbId)?.poster_path
                ?.let { "$POSTER_BASE$it" } ?: return null
            synchronized(seriesPosterCache) {
                seriesPosterCache[imdbId] = poster
            }
            poster
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun findTmdbId(imdbId: String): Long? {
        val findUrl = "$BASE_URL/find/$imdbId?api_key=$API_KEY&external_source=imdb_id"
        val findResponse = client.newCall(GET(findUrl)).awaitSuccess()
        val findResult = with(json) { findResponse.parseAs<TmdbFindResponse>() }
        return findResult.tv_results.firstOrNull()?.id
    }

    private suspend fun getTvDetails(tmdbId: Long): TmdbTvDetails? {
        val detailsUrl = "$BASE_URL/tv/$tmdbId?api_key=$API_KEY"
        val detailsResponse = client.newCall(GET(detailsUrl)).awaitSuccess()
        return with(json) { detailsResponse.parseAs<TmdbTvDetails>() }
    }

    private suspend fun getSeasonStills(tmdbId: Long, seasonNumber: Int): Map<Int, String> {
        return try {
            val seasonUrl = "$BASE_URL/tv/$tmdbId/season/$seasonNumber?api_key=$API_KEY"
            val response = client.newCall(GET(seasonUrl)).awaitSuccess()
            val details = with(json) { response.parseAs<TmdbSeasonDetails>() }
            details.episodes.mapNotNull { ep ->
                ep.still_path?.let { ep.episode_number to "$STILL_BASE$it" }
            }.toMap()
        } catch (e: Exception) {
            logcat(LogPriority.WARN) {
                "TmdbSeasonPosters: season $seasonNumber stills failed for tmdb $tmdbId: ${e.message}"
            }
            emptyMap()
        }
    }

    /**
     * Extract the IMDB ID from a Cinemeta URL like "cinemeta:series:tt0903747".
     */
    fun extractImdbId(cinemetaUrl: String): String? {
        val parts = cinemetaUrl.split(":")
        return if (parts.size >= 3) parts[2] else null
    }
}
