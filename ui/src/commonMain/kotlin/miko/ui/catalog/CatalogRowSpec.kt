package miko.ui.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Android's `CatalogRowSpec` — one user-built home catalog row: a stable
 * id, a feed choice carrying its own required keys, a nullable facet bag
 * consumed only where the feed supports it, ordering, visibility, and an
 * optional custom title. Serial names match Android's so rows round-trip
 * through backup/restore.
 *
 * `medium` uses the mikoDB/`Carrier` vocabulary ("shows"/"manga"/"books").
 * `sort` serializes a per-feed enum name ("default" when untouched).
 */
@Serializable
data class CatalogRowSpec(
    val id: String,
    val title: String? = null,
    val medium: String,
    val feed: Feed,
    // --- TMDB facets ---
    val genreIds: List<Int> = emptyList(),
    val excludedGenreIds: List<Int> = emptyList(),
    val keywordIds: List<Int> = emptyList(),
    val excludedKeywordIds: List<Int> = emptyList(),
    val watchProviderIds: List<Int> = emptyList(),
    // --- Non-TMDB feeds (name-matched / mapped to source filters) ---
    val genreNames: List<String> = emptyList(),
    val excludedGenreNames: List<String> = emptyList(),
    // --- Shared facets ---
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val voteAverageGte: Double? = null,
    val voteCountGte: Int? = null,
    val sort: String = "default",
    val order: Int,
    val visible: Boolean = true,
) {
    /**
     * Header title — the user's custom title when set, else auto-composed
     * as `{feed label} · {facet names} — {medium label}`. Dynamic names
     * (watch providers, extension sources) resolve through [lookup]; TMDB
     * facet name tables land with the TMDB client (Phase 30-02).
     */
    fun displayTitle(lookup: CatalogTitleLookup = CatalogTitleLookup()): String {
        title?.takeIf { it.isNotBlank() }?.let { return it }
        val feedLabel = when (val f = feed) {
            is Feed.Tmdb -> "Discover"
            is Feed.Cinemeta -> f.catalogId.replaceFirstChar { c -> c.uppercase() }
            is Feed.Canonical -> "Catalog"
            is Feed.Extension -> lookup.sourceName ?: "Extension"
            is Feed.Library -> "Library"
            Feed.History -> return when (medium) {
                MEDIUM_SHOWS -> "Continue Watching"
                else -> "Continue Reading"
            }
        }
        val facets = buildList {
            addAll(genreNames)
            keywordIds.mapTo(this) { lookup.keywordNames[it] ?: "#$it" }
            watchProviderIds.mapTo(this) { lookup.providerNames[it] ?: "#$it" }
            if (yearFrom != null || yearTo != null) {
                add("${yearFrom ?: "…"}–${yearTo ?: "…"}")
            }
        }
        val mediumLabel = when (medium) {
            MEDIUM_SHOWS -> "Series"
            MEDIUM_MANGA -> "Manga"
            else -> "Books"
        }
        return buildString {
            append(feedLabel)
            if (facets.isNotEmpty()) append(" · ").append(facets.joinToString(" · "))
            append(" — ").append(mediumLabel)
        }
    }

    companion object {
        const val MEDIUM_SHOWS = "shows"
        const val MEDIUM_MANGA = "manga"
        const val MEDIUM_BOOKS = "books"
    }
}

/**
 * Dynamic display-name lookups for [CatalogRowSpec.displayTitle] —
 * watch-provider names come from the TMDB providers endpoint and
 * extension names from the source managers.
 */
class CatalogTitleLookup(
    val providerNames: Map<Int, String> = emptyMap(),
    val keywordNames: Map<Int, String> = emptyMap(),
    val sourceName: String? = null,
)

/**
 * Feed selection for a [CatalogRowSpec] — each variant carries the keys
 * its backend requires so a spec can't point at options it can't serve.
 *
 * - [Tmdb] — TMDB `discover/{movie|tv}`; every facet plus the
 *   streaming-service dimension (BYOK — Phase 30-02).
 * - [Cinemeta] — a Cinemeta catalog (`cinemetaType`/`catalogId`).
 * - [Canonical] — the worker `/v1/works/browse` feed for manga/books;
 *   `"ol"` kindsCsv = OpenLibrary trending.
 * - [Extension] — an installed source's popular/latest feed.
 * - [Library] — rows derived from the user's own library.
 * - [History] — continue-reading/watching, served live from recents.
 */
@Serializable
sealed interface Feed {

    @Serializable
    @SerialName("tmdb")
    data class Tmdb(
        /** `"MOVIE"` | `"TV"`. */
        val mediaType: String,
    ) : Feed

    @Serializable
    @SerialName("cinemeta")
    data class Cinemeta(
        /** `"movie"` | `"series"`. */
        val cinemetaType: String,
        val catalogId: String,
    ) : Feed

    @Serializable
    @SerialName("canonical")
    data class Canonical(
        /** Comma-joined kind filter (`"ol"` = OpenLibrary trending). */
        val kindsCsv: String,
    ) : Feed

    @Serializable
    @SerialName("extension")
    data class Extension(
        val pkgName: String,
        val sourceId: Long,
        val kind: ExtKind,
        /**
         * Comma-joined sub-type keywords matched against the source's
         * own `getFilterList()` type/format options at fetch.
         * Null = no type constraint.
         */
        val kindsCsv: String? = null,
        /**
         * Whether an inexpressible [kindsCsv] fails the fetch
         * (`FeedNotImplementedException` → the "unsupported" row) or
         * degrades to the unfiltered listing. Narrowing sub-types are
         * strict; baseline kinds degrade (plenty of single-kind sources
         * have no type vocabulary at all).
         */
        val kindStrict: Boolean = true,
    ) : Feed {
        fun kindKeywords(): List<String>? = kindsCsv
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
    }

    @Serializable
    @SerialName("library")
    data object Library : Feed

    @Serializable
    @SerialName("history")
    data object History : Feed
}

enum class ExtKind { POPULAR, LATEST }

/** Sort vocabulary for `Feed.Library` rows. */
enum class LibrarySort { TITLE, RECENTLY_ADDED, LAST_READ }
