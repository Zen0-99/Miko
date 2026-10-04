package eu.kanade.tachiyomi.metadata.tmdb

import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * TMDB `discover/movie` + `discover/tv` client backing the Discovery
 * surface's facet filters (Phase 15-06). Enrichment only — never in the
 * minting path; results route to shows entries through the IMDB-keyed
 * Cinemeta path after an `external_ids` lookup.
 *
 * Auth follows [TmdbSeasonPosters] conventions (`api_key=` query param)
 * with the key resolved by [TmdbKeyProvider] — BYOK pref → BuildConfig →
 * bundled fallback. When no key resolves, [discover] returns null and
 * the caller falls back to the unfiltered catalog (stub fallback).
 */
class TmdbDiscover(
    private val networkHelper: NetworkHelper,
    private val json: Json,
    private val keyProvider: TmdbKeyProvider,
) {
    /**
     * Resolved TMDB facet query — the wire shape, already translated
     * from the UI filter model by the caller.
     */
    data class DiscoverQuery(
        val withGenres: List<Int> = emptyList(),
        val withoutGenres: List<Int> = emptyList(),
        val withKeywords: List<Int> = emptyList(),
        val withoutKeywords: List<Int> = emptyList(),
        /** Streaming-service facet (Phase 18) — Netflix/Crunchyroll ids
         *  from [watchProviders]; `watch_region` rides along. */
        val withWatchProviders: List<Int> = emptyList(),
        val watchRegion: String = DEFAULT_WATCH_REGION,
        val yearGte: Int? = null,
        val yearLte: Int? = null,
        val voteAverageGte: Double? = null,
        val voteCountGte: Int? = null,
        val sortBy: String = "popularity.desc",
    )

    data class Item(
        val id: Long,
        val title: String,
        val posterUrl: String?,
        val year: Int?,
        val voteAverage: Double?,
        val mediaType: MediaType,
    )

    data class Page(
        val items: List<Item>,
        val page: Int,
        val hasMore: Boolean,
    )

    @Serializable
    private data class DiscoverResponse(
        val page: Int = 1,
        val total_pages: Int = 1,
        val results: List<DiscoverResult> = emptyList(),
    )

    @Serializable
    private data class DiscoverResult(
        val id: Long,
        val title: String? = null,
        val name: String? = null,
        val poster_path: String? = null,
        val release_date: String? = null,
        val first_air_date: String? = null,
        val vote_average: Double? = null,
    )

    @Serializable
    private data class ExternalIds(
        val imdb_id: String? = null,
    )

    /** One streaming-service entry from `watch/providers/{type}` —
     *  Netflix, Crunchyroll etc. (Phase 18 catalog builder). */
    @Serializable
    data class WatchProvider(
        val provider_id: Int,
        val provider_name: String,
        val logo_path: String? = null,
    )

    @Serializable
    private data class WatchProvidersResponse(
        val results: List<WatchProvider> = emptyList(),
    )

    private val client: OkHttpClient get() = networkHelper.client

    /** Whether any TMDB key resolves (BYOK → BuildConfig → bundled). */
    fun hasKey(): Boolean = keyProvider.apiKey().isNotBlank()

    /**
     * One discover page, or null when no TMDB key is available / the
     * request fails (the discovery surface treats null as "filters
     * unavailable" and falls back to the catalog list).
     */
    suspend fun discover(type: MediaType, query: DiscoverQuery, page: Int): Page? {
        val apiKey = keyProvider.apiKey().takeIf { it.isNotBlank() } ?: return null
        return try {
            val url = discoverUrl(type, query, page, apiKey)
            val response = client.newCall(GET(url)).awaitSuccess()
            val parsed = with(json) { response.parseAs<DiscoverResponse>() }
            Page(
                items = parsed.results.map { it.toItem(type) },
                page = parsed.page,
                hasMore = parsed.page < parsed.total_pages,
            )
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "TmdbDiscover: discover failed: ${e.message}" }
            null
        }
    }

    /**
     * TMDB → IMDB link for routing a discover result into the
     * Cinemeta-keyed shows pipeline. Null when the entry has no IMDB id
     * (those stay non-navigable — minimal viable behavior).
     */
    suspend fun imdbId(type: MediaType, tmdbId: Long): String? {
        val apiKey = keyProvider.apiKey().takeIf { it.isNotBlank() } ?: return null
        return try {
            val url = "$BASE_URL/${type.path}/$tmdbId/external_ids?api_key=$apiKey"
            val response = client.newCall(GET(url)).awaitSuccess()
            with(json) { response.parseAs<ExternalIds>() }.imdb_id
                ?.takeIf { it.startsWith("tt") }
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "TmdbDiscover: external_ids failed for $tmdbId: ${e.message}" }
            null
        }
    }

    /** In-memory per-type cache — the provider list is slow-moving. */
    private val watchProvidersCache = java.util.concurrent.ConcurrentHashMap<MediaType, List<WatchProvider>>()

    /**
     * Streaming-service catalog for [type] in the default region — feeds
     * the catalog builder's provider picker and `displayTitle` lookups.
     * Empty when no key resolves or the fetch fails.
     */
    suspend fun watchProviders(type: MediaType): List<WatchProvider> {
        watchProvidersCache[type]?.let { return it }
        val apiKey = keyProvider.apiKey().takeIf { it.isNotBlank() } ?: return emptyList()
        return try {
            val url = "$BASE_URL/watch/providers/${type.path}?api_key=$apiKey&watch_region=$DEFAULT_WATCH_REGION"
            val response = client.newCall(GET(url)).awaitSuccess()
            val providers = with(json) { response.parseAs<WatchProvidersResponse>() }.results
            watchProvidersCache[type] = providers
            providers
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "TmdbDiscover: watch providers failed: ${e.message}" }
            emptyList()
        }
    }

    private fun DiscoverResult.toItem(type: MediaType): Item {
        val dateField = if (type == MediaType.MOVIE) release_date else first_air_date
        return Item(
            id = id,
            title = title ?: name ?: "",
            posterUrl = poster_path?.let { "$POSTER_BASE$it" },
            year = dateField?.take(4)?.toIntOrNull(),
            voteAverage = vote_average,
            mediaType = type,
        )
    }

    companion object {
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val POSTER_BASE = "https://image.tmdb.org/t/p/w342"

        /** Streaming-service facet region — fixed to US like Flexio
         *  (a per-row region field can land later without a spec bump). */
        const val DEFAULT_WATCH_REGION = "US"

        private val MediaType.path: String
            get() = when (this) {
                MediaType.MOVIE -> "movie"
                MediaType.TV -> "tv"
            }

        /** Cinemeta catalog type for a TMDB media type — series vs movie. */
        fun cinemetaType(type: MediaType): String = when (type) {
            MediaType.MOVIE -> "movie"
            MediaType.TV -> "series"
        }

        /**
         * Facet → TMDB query params (sans `api_key`). Tri-state genres
         * split into with_/without_genres; year bounds map to
         * date-window params (movie: primary_release_date.gte/lte, tv:
         * first_air_date.gte/lte — TMDB has no year-range fields, the
         * single-year params can't express a span); the vote-count floor
         * guards rating filters and Top-rated sort against
         * few-votes noise.
         */
        internal fun queryParams(type: MediaType, query: DiscoverQuery): Map<String, String> {
            val params = linkedMapOf<String, String>()
            if (query.withGenres.isNotEmpty()) {
                params["with_genres"] = query.withGenres.joinToString(",")
            }
            if (query.withoutGenres.isNotEmpty()) {
                params["without_genres"] = query.withoutGenres.joinToString(",")
            }
            if (query.withKeywords.isNotEmpty()) {
                params["with_keywords"] = query.withKeywords.joinToString(",")
            }
            if (query.withoutKeywords.isNotEmpty()) {
                params["without_keywords"] = query.withoutKeywords.joinToString(",")
            }
            if (query.withWatchProviders.isNotEmpty()) {
                params["with_watch_providers"] = query.withWatchProviders.joinToString(",")
                // watch_region is meaningless without providers — emit
                // them together so a provider-less query stays region-
                // neutral.
                params["watch_region"] = query.watchRegion
            }
            val dateGte = if (type == MediaType.MOVIE) "primary_release_date.gte" else "first_air_date.gte"
            val dateLte = if (type == MediaType.MOVIE) "primary_release_date.lte" else "first_air_date.lte"
            query.yearGte?.let { params[dateGte] = "$it-01-01" }
            query.yearLte?.let { params[dateLte] = "$it-12-31" }
            query.voteAverageGte?.let { params["vote_average.gte"] = it.toString() }
            // Vote floor rides the rating facet AND rating sort so a
            // 10.0-from-3-votes can't top the list. Top-rated sorts need
            // the heavier floor — at 100 votes TMDB still surfaces
            // obscure festival films (9.9 avg / ~143 votes).
            val voteFloor = query.voteCountGte
                ?: TOP_RATED_VOTE_COUNT_GTE.takeIf { query.sortBy.startsWith("vote_average") }
                ?: (query.voteAverageGte?.let { DEFAULT_VOTE_COUNT_GTE })
            voteFloor?.let { params["vote_count.gte"] = it.toString() }
            params["sort_by"] = query.sortBy
            return params
        }

        private fun discoverUrl(type: MediaType, query: DiscoverQuery, page: Int, apiKey: String): String =
            "$BASE_URL/discover/${type.path}".toHttpUrl().newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("page", page.toString())
                .apply {
                    queryParams(type, query).forEach { (k, v) -> addQueryParameter(k, v) }
                }
                .build()
                .toString()

        /** Default vote-count floor when a rating filter/sort is active. */
        const val DEFAULT_VOTE_COUNT_GTE = 100

        /** Vote-count floor for top-rated (`vote_average.*`) sorts — the
         *  100 default still lets obscure few-votes titles top the list. */
        const val TOP_RATED_VOTE_COUNT_GTE = 500
    }
}
