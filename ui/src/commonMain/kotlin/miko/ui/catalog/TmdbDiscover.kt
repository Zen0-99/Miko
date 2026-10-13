package miko.ui.catalog

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.NetworkPreferences
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import miko.ui.catalog.TmdbGenres.MediaType
import tachiyomi.i18n.aniyomi.AYMR

/**
 * Android's `TmdbKeyProvider` — BYOK pref (`pref_tmdb_api_key`) wins when
 * set. iOS has no BuildConfig fallback — a blank key degrades every TMDB
 * call to its stub path (BYOK-only, keys never in code).
 */
class TmdbKeyProvider(
    private val networkPreferences: NetworkPreferences,
) {
    fun apiKey(): String =
        networkPreferences.tmdbApiKey().get().trim()
}

/**
 * Android's `TmdbDiscover` — TMDB `discover/movie` + `discover/tv` client
 * backing catalog-row facet queries. Portable REST + static facet vocab
 * ([TmdbGenres]/[TmdbSubGenreKeywords]); results route to shows entries
 * through the IMDB-keyed canonical path (mikoDB-deferred on iOS — the
 * resolver arm still serves Android-synced specs).
 */
class TmdbDiscover(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val keyProvider: TmdbKeyProvider,
) {
    data class DiscoverQuery(
        val withGenres: List<Int> = emptyList(),
        val withoutGenres: List<Int> = emptyList(),
        val withKeywords: List<Int> = emptyList(),
        val withoutKeywords: List<Int> = emptyList(),
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

    private val client by lazy { HttpClient() }

    /** Whether any TMDB key resolves (BYOK). */
    fun hasKey(): Boolean = keyProvider.apiKey().isNotBlank()

    /** One discover page, or null when no key resolves / the request
     *  fails (callers treat null as "filters unavailable"). */
    suspend fun discover(type: MediaType, query: DiscoverQuery, page: Int): Page? {
        val apiKey = keyProvider.apiKey().takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val params = queryParams(type, query) +
                mapOf("api_key" to apiKey, "page" to page.toString())
            val url = "$BASE_URL/discover/${type.path}?" +
                params.entries.joinToString("&") { (k, v) ->
                    "$k=${encodeQueryValue(v)}"
                }
            val parsed = json.decodeFromString<DiscoverResponse>(client.get(url).bodyAsText())
            Page(
                items = parsed.results.map { it.toItem(type) },
                page = parsed.page,
                hasMore = parsed.page < parsed.total_pages,
            )
        }.getOrNull()
    }

    /** TMDB → IMDB link for routing a discover result into the
     *  canonical shows pipeline. Null when no IMDB id exists. */
    suspend fun imdbId(type: MediaType, tmdbId: Long): String? {
        val apiKey = keyProvider.apiKey().takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val url = "$BASE_URL/${type.path}/$tmdbId/external_ids?api_key=$apiKey"
            json.decodeFromString<ExternalIds>(client.get(url).bodyAsText()).imdb_id
                ?.takeIf { it.startsWith("tt") }
        }.getOrNull()
    }

    private val watchProvidersCache = mutableMapOf<MediaType, List<WatchProvider>>()

    /** Streaming-service catalog for [type] in the default region —
     *  feeds the catalog builder's provider picker + displayTitle
     *  lookups. Empty when no key resolves. */
    suspend fun watchProviders(type: MediaType): List<WatchProvider> {
        watchProvidersCache[type]?.let { return it }
        val apiKey = keyProvider.apiKey().takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            val url = "$BASE_URL/watch/providers/${type.path}?api_key=$apiKey&watch_region=$DEFAULT_WATCH_REGION"
            val providers =
                json.decodeFromString<WatchProvidersResponse>(client.get(url).bodyAsText()).results
            watchProvidersCache[type] = providers
            providers
        }.getOrDefault(emptyList())
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

    private fun encodeQueryValue(value: String): String =
        buildString {
            for (c in value) {
                when {
                    c.isLetterOrDigit() || c in "-._~," -> append(c)
                    else -> c.toString().encodeToByteArray()
                        .forEach { b -> append('%').append(b.toHexString().uppercase()) }
                }
            }
        }

    private fun Byte.toHexString(): String {
        val v = toInt() and 0xFF
        return v.toString(16).padStart(2, '0')
    }

    companion object {
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val POSTER_BASE = "https://image.tmdb.org/t/p/w342"

        /** Streaming-service facet region — fixed to US (a per-row
         *  region field can land later without a spec bump). */
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

        /** Facet → TMDB query params (sans `api_key`). Date-window params
         *  differ by type (movie: primary_release, tv: first_air_date);
         *  the vote-count floor guards rating facets and Top-rated sorts
         *  against few-votes noise. */
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
                params["watch_region"] = query.watchRegion
            }
            val dateGte = if (type == MediaType.MOVIE) "primary_release_date.gte" else "first_air_date.gte"
            val dateLte = if (type == MediaType.MOVIE) "primary_release_date.lte" else "first_air_date.lte"
            query.yearGte?.let { params[dateGte] = "$it-01-01" }
            query.yearLte?.let { params[dateLte] = "$it-12-31" }
            query.voteAverageGte?.let { params["vote_average.gte"] = it.toString() }
            val voteFloor = query.voteCountGte
                ?: TOP_RATED_VOTE_COUNT_GTE.takeIf { query.sortBy.startsWith("vote_average") }
                ?: (query.voteAverageGte?.let { DEFAULT_VOTE_COUNT_GTE })
            voteFloor?.let { params["vote_count.gte"] = it.toString() }
            params["sort_by"] = query.sortBy
            return params
        }

        /** Default vote-count floor when a rating filter/sort is active. */
        const val DEFAULT_VOTE_COUNT_GTE = 100

        /** Vote-count floor for top-rated (`vote_average.*`) sorts. */
        const val TOP_RATED_VOTE_COUNT_GTE = 500
    }
}

/**
 * Android's `DiscoverySort` — the enum carries the TMDB `sort_by` string
 * per media type.
 */
enum class DiscoverySort(
    val labelRes: StringResource,
    private val movieParam: String,
    private val tvParam: String,
) {
    POPULARITY(AYMR.strings.discovery_sort_popularity, "popularity.desc", "popularity.desc"),
    RATING(AYMR.strings.discovery_sort_rating, "vote_average.desc", "vote_average.desc"),
    NEWEST(AYMR.strings.discovery_sort_newest, "primary_release_date.desc", "first_air_date.desc"),
    ;

    fun param(type: MediaType): String = when (type) {
        MediaType.MOVIE -> movieParam
        MediaType.TV -> tvParam
    }
}
