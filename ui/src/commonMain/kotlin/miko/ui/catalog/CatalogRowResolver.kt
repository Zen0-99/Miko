package miko.ui.catalog

import eu.kanade.tachiyomi.metadata.miko.dto.MikoMetaShort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import miko.ui.MikoUiGraph
import miko.ui.carrier.BrowseCarrier
import miko.ui.carrier.CarrierSource
import miko.ui.carrier.FeedFilter

/** No TMDB key resolves — the row renders the BYOK notice. */
class TmdbKeyMissingException : Exception()

/** The spec's feed can't be served (not implemented / endpoint missing). */
class FeedNotImplementedException(feed: Feed) : Exception(
    "feed not implemented: ${feed::class.simpleName}",
)

/** The row's extension is uninstalled or was reinstalled under a
 *  different package since the spec was authored. */
class ExtensionUnavailableException(sourceId: Long) : Exception(
    "extension source $sourceId unavailable",
)

class CatalogFetchException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Android's `CatalogRowResolver` — routes a persisted [CatalogRowSpec] to
 * its feed backend and returns rendered row items.
 *
 * The medium routes to the matching [BrowseCarrier] binding, so a spec's
 * `Extension`/`Library` arm works identically on iOS and Android — the
 * carrier abstraction is exactly what Android's per-medium
 * source-manager triplication maps onto. Facet application is one
 * uniform pass over [FeedFilter] (the wrapper already unifies the three
 * source filter hierarchies, write-through included).
 *
 * Facet matrix (inert facets are never errors):
 *
 * | Feed      | genreNames/excluded | kindsCsv | sort        |
 * |-----------|---------------------|----------|-------------|
 * | Tmdb      | —                   | —        | Phase 30-02 |
 * | Cinemeta  | —                   | —        | Phase 30-02 |
 * | Canonical | filter              | ✓        | mikoDB-deferred |
 * | Extension | source filters      | ✓        | ExtKind     |
 * | Library   | filter              | —        | LibrarySort |
 * | History   | —                   | —        | last-consumed |
 */
class CatalogRowResolver(
    private val graph: MikoUiGraph,
) {
    suspend fun resolve(spec: CatalogRowSpec): List<RowItem> = when (val feed = spec.feed) {
        is Feed.Extension -> resolveExtension(spec, feed)
        is Feed.Library -> resolveLibrary(spec)
        is Feed.Tmdb -> resolveTmdb(spec, feed)
        is Feed.Cinemeta -> resolveCinemeta(feed)
        // Worker `/v1/works/browse` — needs the mikoDB work-materialize
        // tap path to open entries; honest "unsupported" until it lands.
        is Feed.Canonical -> throw FeedNotImplementedException(feed)
        // Served live from screen-model state — matches Android: an
        // escaped spec resolves empty and the row hides itself.
        Feed.History -> emptyList()
    }

    /**
     * TMDB `discover` — the facet-complete backend: genres, sub-genre
     * keywords, streaming services, year window, rating floor, sort.
     * `spec.sort` defaults to `"default"`, which is NOT a
     * [DiscoverySort] member — unparseable values fall back to
     * [DiscoverySort.POPULARITY] instead of erroring the row.
     *
     * Row items resolve fully (BYOK); the `CanonicalShows` tap path —
     * `external_ids` → IMDB → Cinemeta meta → local entry — is part of
     * the deferred canonical-shows infra, so taps on these cards are
     * inert until that lands.
     */
    private suspend fun resolveTmdb(spec: CatalogRowSpec, feed: Feed.Tmdb): List<RowItem> {
        val tmdbDiscover = graph.tmdbDiscover
        if (!tmdbDiscover.hasKey()) throw TmdbKeyMissingException()
        val type = feed.tmdbMediaType()
        val sort = runCatching { DiscoverySort.valueOf(spec.sort) }
            .getOrDefault(DiscoverySort.POPULARITY)
        val query = TmdbDiscover.DiscoverQuery(
            withGenres = spec.genreIds,
            withoutGenres = spec.excludedGenreIds,
            withKeywords = spec.keywordIds,
            withoutKeywords = spec.excludedKeywordIds,
            withWatchProviders = spec.watchProviderIds,
            yearGte = spec.yearFrom,
            yearLte = spec.yearTo,
            voteAverageGte = spec.voteAverageGte,
            voteCountGte = spec.voteCountGte,
            sortBy = sort.param(type),
        )
        val page = tmdbDiscover.discover(type, query, page = 1)
            ?: throw CatalogFetchException("tmdb discover returned null")
        return page.items.map { it.toRowItem() }
    }

    private fun TmdbDiscover.Item.toRowItem(): RowItem = RowItem(
        title = title,
        coverUrl = posterUrl,
        subtitle = listOfNotNull(
            year?.toString(),
            voteAverage?.let { "★ ${formatOneDecimal(it)}" },
        ).joinToString(" · ").takeIf { it.isNotEmpty() },
        target = RowTarget.CanonicalShows(tmdbId = id, mediaType = mediaType),
        releaseInfo = year?.toString(),
        imdbRating = voteAverage?.toString(),
        mintKey = "tmdb:$mediaType:$id",
        key = "tmdb:${mediaType.name}:$id",
    )

    /**
     * Cinemeta catalog — the catalog pick IS the facet surface; items
     * resolve to metas whose materialization (Cinemeta-entry → local
     * anime) rides the deferred canonical-shows infra.
     */
    private suspend fun resolveCinemeta(feed: Feed.Cinemeta): List<RowItem> {
        val cinemetaCatalogFeed = graph.cinemetaCatalogFeed
        val result = runCatching {
            cinemetaCatalogFeed.fetchCatalog(feed.cinemetaType, feed.catalogId)
        }.getOrElse { throw CatalogFetchException("cinemeta fetch failed", it) }
        return cinemetaCatalogFeed.dedupeMetas(result.metas).map { it.toRowItem() }
    }

    private fun MikoMetaShort.toRowItem(): RowItem = RowItem(
        title = name,
        coverUrl = poster,
        subtitle = listOfNotNull(
            releaseInfo,
            imdbRating?.let { "★ $it" },
        ).joinToString(" · ").takeIf { it.isNotEmpty() },
        target = RowTarget.CinemetaEntry(this),
        releaseInfo = releaseInfo,
        imdbRating = imdbRating,
        key = "cinemeta:$type:$id",
    )

    // --- Extension feed ---

    private fun carrierFor(medium: String): BrowseCarrier = when (medium) {
        CatalogRowSpec.MEDIUM_SHOWS -> BrowseCarrier.SHOWS
        CatalogRowSpec.MEDIUM_MANGA -> BrowseCarrier.MANGA
        else -> BrowseCarrier.BOOKS
    }

    private suspend fun resolveExtension(spec: CatalogRowSpec, feed: Feed.Extension): List<RowItem> {
        val binding = graph.binding(carrierFor(spec.medium))
        // A row can compose while extension classloading is mid-flight —
        // bounded wait before the honest unavailable state (Android's
        // INIT_TIMEOUT_MS pattern).
        if (!binding.isInitialized.value) {
            withTimeoutOrNull(INIT_TIMEOUT_MS) { binding.isInitialized.first { it } }
        }
        val source = binding.source(feed.sourceId)
            ?: throw ExtensionUnavailableException(feed.sourceId)
        if (feed.kind == ExtKind.LATEST && !source.supportsLatest) {
            throw FeedNotImplementedException(feed)
        }
        val keywords = feed.kindKeywords()
        val constraints = keywords != null ||
            spec.genreNames.isNotEmpty() ||
            spec.excludedGenreNames.isNotEmpty()
        val filters = if (constraints) source.filters() else emptyList()
        val kindApplied = applyKindFilter(filters, keywords)
        if (!kindApplied && keywords != null && feed.kindStrict) {
            throw FeedNotImplementedException(feed)
        }
        val genresApplied = applyGenreFilters(filters, spec.genreNames, spec.excludedGenreNames)
        val page = if (kindApplied || genresApplied) {
            source.search(1, "")
        } else if (feed.kind == ExtKind.LATEST) {
            source.latest(1)
        } else {
            source.popular(1)
        }
        return page.entries
            .filter { genresApplied || matchesGenreNames(spec.genreNames, it.genre) }
            .take(ROW_ITEM_LIMIT)
            .map { entry ->
                RowItem(
                    title = entry.title,
                    coverUrl = entry.coverUrl,
                    target = RowTarget.ExtensionEntry(feed.sourceId, entry.url, spec.medium),
                    key = "ext:${feed.sourceId}:${entry.url}",
                )
            }
    }

    /** The source's own genre vocabulary for the builder's facet pills —
     *  names lifted from its filter groups/selects so a picked pill maps
     *  back onto the filter entry the fetch applies. */
    fun genreOptions(medium: String, sourceId: Long): List<String> {
        val source = graph.binding(carrierFor(medium)).source(sourceId) ?: return emptyList()
        return genreOptionsOf(source)
    }

    internal fun genreOptionsOf(source: CarrierSource): List<String> = source.filters()
        .flatMap { f ->
            when {
                f.kind == FeedFilter.Kind.Group && GENRE_FILTER_RE.containsMatchIn(f.name) ->
                    f.children.map { it.name }
                f.kind == FeedFilter.Kind.Select && GENRE_FILTER_RE.containsMatchIn(f.name) ->
                    f.options
                else -> emptyList()
            }
        }.distinct().sortedBy { it.lowercase() }

    // --- Library feed ---

    private suspend fun resolveLibrary(spec: CatalogRowSpec): List<RowItem> {
        val sort = runCatching { LibrarySort.valueOf(spec.sort) }
            .getOrDefault(LibrarySort.RECENTLY_ADDED)
        val genreHit: (List<String>?) -> Boolean = { genres ->
            spec.genreNames.isEmpty() ||
                genres?.any { g -> spec.genreNames.any { g.contains(it, ignoreCase = true) } } == true
        }

        return when (spec.medium) {
            CatalogRowSpec.MEDIUM_MANGA -> graph.getLibraryManga.await()
                .filter { genreHit(it.manga.genre) }
                .let { list ->
                    when (sort) {
                        LibrarySort.TITLE -> list.sortedBy { it.manga.title }
                        LibrarySort.RECENTLY_ADDED -> list.sortedByDescending { it.manga.dateAdded }
                        LibrarySort.LAST_READ -> list.sortedByDescending { it.lastRead }
                    }
                }
                .take(ROW_ITEM_LIMIT)
                .map { lib ->
                    RowItem(
                        title = lib.manga.title,
                        coverUrl = lib.manga.thumbnailUrl,
                        subtitle = "${lib.totalChapters} ch",
                        target = RowTarget.LibraryEntry(lib.manga.id, spec.medium),
                        key = "lib:${spec.medium}:${lib.manga.id}",
                    )
                }
            CatalogRowSpec.MEDIUM_SHOWS -> graph.getLibraryAnime.await()
                .filter { genreHit(it.anime.genre) }
                .let { list ->
                    when (sort) {
                        LibrarySort.TITLE -> list.sortedBy { it.anime.title }
                        LibrarySort.RECENTLY_ADDED -> list.sortedByDescending { it.anime.dateAdded }
                        LibrarySort.LAST_READ -> list.sortedByDescending { it.lastSeen }
                    }
                }
                .take(ROW_ITEM_LIMIT)
                .map { lib ->
                    RowItem(
                        title = lib.anime.title,
                        coverUrl = lib.anime.thumbnailUrl,
                        subtitle = "${lib.totalCount} ep",
                        target = RowTarget.LibraryEntry(lib.anime.id, spec.medium),
                        key = "lib:${spec.medium}:${lib.anime.id}",
                    )
                }
            else -> graph.getLibraryNovels.await()
                .filter { genreHit(it.novel.genre) }
                .let { list ->
                    when (sort) {
                        LibrarySort.TITLE -> list.sortedBy { it.novel.title }
                        LibrarySort.RECENTLY_ADDED -> list.sortedByDescending { it.novel.dateAdded }
                        LibrarySort.LAST_READ -> list.sortedByDescending { it.lastRead }
                    }
                }
                .take(ROW_ITEM_LIMIT)
                .map { lib ->
                    RowItem(
                        title = lib.novel.title,
                        coverUrl = lib.novel.thumbnailUrl,
                        subtitle = "${lib.totalChapters} ch",
                        target = RowTarget.LibraryEntry(lib.novel.id, spec.medium),
                        key = "lib:${spec.medium}:${lib.novel.id}",
                    )
                }
        }
    }

    /** The builder's facet-pill vocabulary for a library row — genres
     *  actually present on the medium's entries. */
    suspend fun libraryGenreOptions(medium: String): List<String> = when (medium) {
        CatalogRowSpec.MEDIUM_MANGA -> graph.getLibraryManga.await().flatMap { it.manga.genre.orEmpty() }
        CatalogRowSpec.MEDIUM_SHOWS -> graph.getLibraryAnime.await().flatMap { it.anime.genre.orEmpty() }
        else -> graph.getLibraryNovels.await().flatMap { it.novel.genre.orEmpty() }
    }.map { it.trim() }.filter { it.isNotEmpty() }
        .distinct().sortedBy { it.lowercase() }

    companion object {
        /** Page-1 rows render at most this many cards (Android's
         *  `LibraryCatalogFeed.ROW_ITEM_LIMIT`). */
        const val ROW_ITEM_LIMIT = 20

        /** Upper bound on waiting for the medium's source manager before
         *  declaring a source missing (Android's INIT_TIMEOUT_MS). */
        private const val INIT_TIMEOUT_MS = 20_000L
    }
}

/**
 * One rendered card inside a catalog row — the feed-agnostic shape the
 * home UI binds directly.
 *
 * [releaseInfo]/[imdbRating] carry the TMDB fields needed to rebuild a
 * meta at tap time (the discovery tap-path precedent). [mintKey] dedupes
 * the mint-on-browse resolve per session.
 */
data class RowItem(
    val title: String,
    val coverUrl: String?,
    val subtitle: String? = null,
    val target: RowTarget,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val mintKey: String? = null,
    /** Stable lazy-row key — a bare title isn't unique (the same work can
     *  appear in several rows, feeds repeat). Defaults to title+index. */
    val key: String? = null,
)

/**
 * Where tapping a [RowItem] lands.
 *
 * - [CanonicalShows] — a TMDB-discovered show/movie; Android's tap does
 *   the `external_ids` → IMDB → Cinemeta meta → local-entry hop
 *   (mikoDB-deferred on iOS — resolves to null until it lands).
 * - [ExtensionEntry] — source listing entry; materializes to a local
 *   entry at tap (Android's `networkToLocal*` hop — our details screen
 *   does the same on open).
 * - [LibraryEntry] — a library entry id in its medium's table.
 * - [HistoryEntry] — a continue-row item; resumes via the binding's
 *   nav-target resolution (entryId+itemId → urls).
 */
sealed interface RowTarget {
    data class CanonicalShows(val tmdbId: Long, val mediaType: TmdbGenres.MediaType) : RowTarget
    data class CinemetaEntry(val meta: MikoMetaShort) : RowTarget
    data class ExtensionEntry(val sourceId: Long, val url: String, val medium: String) : RowTarget
    data class LibraryEntry(val entryId: Long, val medium: String) : RowTarget
    data class HistoryEntry(val entryId: Long, val itemId: Long, val medium: String) : RowTarget
}

/** Filter names that describe what KIND of work an item is. */
private val TYPE_FILTER_RE =
    Regex("""type|format|kind|demographic|country of origin|content type""", RegexOption.IGNORE_CASE)

/** Filter names that hold the source's genre/tag vocabulary. */
private val GENRE_FILTER_RE =
    Regex("""genre|tag|categor""", RegexOption.IGNORE_CASE)

/** Option/child label ↔ vocab keyword match. */
private fun matchesKeyword(label: String, keywords: List<String>): Boolean =
    keywords.any { label.contains(it, ignoreCase = true) }

private fun matchesName(label: String, wanted: List<String>): Boolean =
    wanted.any { label.equals(it, ignoreCase = true) || label.contains(it, ignoreCase = true) }

/** `7.5` — commonMain has no `String.format` (Android's `%.1f`). */
private fun formatOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10).toInt()
    return "${rounded / 10}.${kotlin.math.abs(rounded % 10)}"
}

/** `genreNames` contains-match on an entry's genre CSV — legacy fallback
 *  for items whose source has no genre filter to apply. */
internal fun matchesGenreNames(wanted: List<String>, genreCsv: String?): Boolean =
    wanted.isEmpty() ||
        (genreCsv ?: "").split(',').map { it.trim() }
            .any { g -> wanted.any { g.contains(it, ignoreCase = true) } }

/**
 * Kind/type facet — type/format-named filters first; a format modelled
 * as a genre tag ("Webtoon" inside "Genres") falls back to the genre
 * group. One uniform [FeedFilter] pass covering all three carriers.
 */
private fun applyKindFilter(filters: List<FeedFilter>, keywords: List<String>?): Boolean {
    if (keywords == null) return false
    var applied = false
    for (nameRe in listOf(TYPE_FILTER_RE, GENRE_FILTER_RE)) {
        if (applied) break
        filters.forEach { filter ->
            when (filter.kind) {
                FeedFilter.Kind.Select -> if (nameRe.containsMatchIn(filter.name)) {
                    filter.options.indexOfFirst { matchesKeyword(it, keywords) }
                        .takeIf { it >= 0 }
                        ?.let {
                            filter.selectedIndex = it
                            applied = true
                        }
                }
                FeedFilter.Kind.Group -> if (nameRe.containsMatchIn(filter.name)) {
                    filter.children.filter { matchesKeyword(it.name, keywords) }
                        .forEach { child ->
                            when (child.kind) {
                                FeedFilter.Kind.CheckBox -> child.checked = true
                                FeedFilter.Kind.TriState -> child.triState = FeedFilter.STATE_INCLUDE
                                else -> return@forEach
                            }
                            applied = true
                        }
                }
                else -> Unit
            }
        }
    }
    return applied
}

/** Genre include/exclude facet over the source's genre groups/selects. */
private fun applyGenreFilters(
    filters: List<FeedFilter>,
    include: List<String>,
    exclude: List<String>,
): Boolean {
    var applied = false
    filters.forEach { filter ->
        when (filter.kind) {
            FeedFilter.Kind.Group -> if (GENRE_FILTER_RE.containsMatchIn(filter.name)) {
                filter.children.forEach { child ->
                    when (child.kind) {
                        FeedFilter.Kind.CheckBox -> if (matchesName(child.name, include)) {
                            child.checked = true
                            applied = true
                        }
                        FeedFilter.Kind.TriState -> when {
                            matchesName(child.name, include) -> {
                                child.triState = FeedFilter.STATE_INCLUDE
                                applied = true
                            }
                            matchesName(child.name, exclude) -> {
                                child.triState = FeedFilter.STATE_EXCLUDE
                                applied = true
                            }
                        }
                        else -> Unit
                    }
                }
            }
            FeedFilter.Kind.Select -> if (GENRE_FILTER_RE.containsMatchIn(filter.name)) {
                filter.options.indexOfFirst { matchesName(it, include) }
                    .takeIf { it >= 0 }
                    ?.let {
                        filter.selectedIndex = it
                        applied = true
                    }
            }
            else -> Unit
        }
    }
    return applied
}
