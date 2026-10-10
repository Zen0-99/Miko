package eu.kanade.tachiyomi.data.catalogrows

import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import eu.kanade.tachiyomi.metadata.tmdb.TmdbSubGenreKeywords
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One user-built home catalog row (Phase 18) — the persisted spec a row
 * is rendered from. Mirrors Flexio's `CatalogSectionConfig`: a stable id,
 * a feed choice carrying its own required keys, a nullable facet bag that
 * is only consumed where the feed supports it, ordering, visibility, and
 * an optional custom title.
 *
 * `medium` uses the [WorkIdResolver] vocabulary (`"shows"`/`"manga"`/
 * `"books"` — owned by `Carrier.medium`) so rows render under exactly one
 * content mode; spec facets the feed can't serve are inert, never errors.
 *
 * `sort` serializes a per-feed enum name ("default" when untouched —
 * resolvers map unknown names to their feed's own default).
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
     * (watch providers, extension sources) resolve through [lookup]; the
     * static facet names come from the TMDB vocab tables.
     */
    fun displayTitle(lookup: CatalogTitleLookup = CatalogTitleLookup()): String {
        title?.takeIf { it.isNotBlank() }?.let { return it }
        val feedLabel = when (val f = feed) {
            is Feed.Tmdb -> "Discover"
            is Feed.Cinemeta -> f.catalogId.replaceFirstChar { c -> c.uppercase() }
            is Feed.Canonical -> "Catalog"
            is Feed.Extension -> lookup.sourceName ?: "Extension"
            is Feed.Library -> "Library"
            // English literals match this function's convention — the
            // row header itself localizes in the composable.
            Feed.History -> return when (medium) {
                WorkIdResolver.MEDIUM_SHOWS -> "Continue Watching"
                else -> "Continue Reading"
            }
        }
        val facets = buildList {
            genreIds.mapNotNullTo(this) { TmdbGenres.nameForId(it) }
            addAll(genreNames)
            keywordIds.mapNotNullTo(this) { TmdbSubGenreKeywords.nameForKeyword(it) }
            watchProviderIds.mapTo(this) { lookup.providerNames[it] ?: "#$it" }
            if (yearFrom != null || yearTo != null) {
                add("${yearFrom ?: "…"}–${yearTo ?: "…"}")
            }
        }
        val mediumLabel = when {
            feed is Feed.Tmdb && feed.tmdbMediaType() == MediaType.MOVIE -> "Movies"
            medium == WorkIdResolver.MEDIUM_SHOWS -> "Series"
            medium == WorkIdResolver.MEDIUM_MANGA -> "Manga"
            else -> "Books"
        }
        return buildString {
            append(feedLabel)
            if (facets.isNotEmpty()) append(" · ").append(facets.joinToString(" · "))
            append(" — ").append(mediumLabel)
        }
    }
}

/**
 * Dynamic display-name lookups for [CatalogRowSpec.displayTitle] —
 * watch-provider names come from the TMDB providers endpoint and
 * extension names from the source managers; neither is static data.
 */
class CatalogTitleLookup(
    val providerNames: Map<Int, String> = emptyMap(),
    val sourceName: String? = null,
)

/**
 * Feed selection for a [CatalogRowSpec] — each variant carries the keys
 * its backend requires so a spec can't point at options it can't serve.
 *
 * - [Tmdb] — TMDB `discover/{movie|tv}`; serves every facet plus the
 *   streaming-service (`with_watch_providers`) dimension.
 * - [Cinemeta] — a Cinemeta catalog (`cinemetaType`/`catalogId`, e.g.
 *   `series`/`top`); catalog choice is the whole facet surface.
 * - [Canonical] — the worker `/v1/works/browse` feed for manga/books;
 *   `kindsCsv` is a comma-joined kind list (`"ol"` = OpenLibrary
 *   trending, matching the canonical catalog's feed tag).
 * - [Extension] — an installed extension source's popular/latest feed.
 * - [Library] — rows derived from the user's own library.
 */
@Serializable
sealed interface Feed {

    @Serializable
    @SerialName("tmdb")
    data class Tmdb(
        /** [TmdbGenres.MediaType] name — `"MOVIE"` | `"TV"`. */
        val mediaType: String,
    ) : Feed {
        fun tmdbMediaType(): MediaType =
            runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.TV)
    }

    @Serializable
    @SerialName("cinemeta")
    data class Cinemeta(
        /** `"movie"` | `"series"` — named `cinemetaType`, not `type`: the
         *  sealed-class JSON discriminator already occupies `type`. */
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
         * own `getFilterList()` type/format options at fetch
         * (`"manhwa,manhua,webtoon,oel"` for a Webtoons row, `"movie,film"`
         * for Movies, `"manga,comic"` for a Manga row). Null = no type
         * constraint.
         */
        val kindsCsv: String? = null,
        /**
         * Whether an inexpressible [kindsCsv] fails the fetch
         * (`FeedNotImplementedException` → the "unsupported" row) or
         * degrades to the unfiltered listing. Narrowing sub-types
         * (Webtoons, Movies) are strict — a "Webtoons" row must never
         * silently mix manga back in. Baseline kinds (Manga, Series,
         * Novels) are soft: plenty of single-kind sources simply have
         * no type filter to set, and their listing is already right.
         */
        val kindStrict: Boolean = true,
    ) : Feed

    @Serializable
    @SerialName("library")
    data object Library : Feed

    /**
     * The continue-reading/watching feed — history-derived, served live
     * from screen-model state rather than the resolver (the recents
     * flows already run there; re-deriving per-row would double the
     * DB subscriptions). Seeded as the default rows by
     * `CatalogRowStore`.
     */
    @Serializable
    @SerialName("history")
    data object History : Feed
}

enum class ExtKind { POPULAR, LATEST }
