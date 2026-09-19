package eu.kanade.tachiyomi.metadata.tmdb

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

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
    }

    private val client: OkHttpClient get() = networkHelper.client

    // In-memory cache: imdbId -> (seasonNumber -> posterUrl)
    private val cache = mutableMapOf<String, Map<Int, String>>()

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
        val seasons: List<TmdbSeason> = emptyList(),
    )

    @Serializable
    private data class TmdbSeason(
        val season_number: Int,
        val poster_path: String? = null,
    )

    /**
     * Get a map of season number to poster URL for the given IMDB ID.
     * Returns an empty map if the show is not found or the API fails.
     */
    suspend fun getSeasonPosters(imdbId: String): Map<Int, String> {
        cache[imdbId]?.let { return it }

        return try {
            // Find TMDB TV ID from IMDB ID
            val findUrl = "$BASE_URL/find/$imdbId?api_key=$API_KEY&external_source=imdb_id"
            val findResponse = client.newCall(GET(findUrl)).awaitSuccess()
            val findResult = with(json) { findResponse.parseAs<TmdbFindResponse>() }
            val tmdbId = findResult.tv_results.firstOrNull()?.id
                ?: return emptyMap()

            // Get TV details with seasons
            val detailsUrl = "$BASE_URL/tv/$tmdbId?api_key=$API_KEY"
            val detailsResponse = client.newCall(GET(detailsUrl)).awaitSuccess()
            val details = with(json) { detailsResponse.parseAs<TmdbTvDetails>() }

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
     * Extract the IMDB ID from a Cinemeta URL like "cinemeta:series:tt0903747".
     */
    fun extractImdbId(cinemetaUrl: String): String? {
        val parts = cinemetaUrl.split(":")
        return if (parts.size >= 3) parts[2] else null
    }
}
