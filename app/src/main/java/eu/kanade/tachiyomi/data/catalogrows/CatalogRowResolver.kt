package eu.kanade.tachiyomi.data.catalogrows

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.mikodb.MangaNovelIdBridge
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.data.mikodb.WorksBrowseApi
import eu.kanade.tachiyomi.metadata.cinemeta.CinemetaCatalogFeed
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMetaShort
import eu.kanade.tachiyomi.metadata.openlibrary.OpenLibraryTrending
import eu.kanade.tachiyomi.metadata.tmdb.TmdbDiscover
import eu.kanade.tachiyomi.ui.discovery.DiscoverySort
import java.util.Locale

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
 * Routes a persisted [CatalogRowSpec] to its feed backend and returns
 * the rendered row items (Phase 18).
 *
 * Facet matrix — only the facets a feed supports are applied; the rest
 * are inert (the builder never authors them):
 *
 * | Feed      | genreIds | keywordIds | watchProviders | genreNames | year | voteGte | sort |
 * |-----------|----------|------------|----------------|------------|------|---------|------|
 * | Tmdb      | ✓        | ✓          | ✓ + region     | —          | ✓    | ✓       | DiscoverySort |
 * | Cinemeta  | —        | —          | —              | —          | —    | —       | feed order |
 * | Canonical | —        | —          | —              | ✓ filter   | —    | —       | feed order |
 * | Extension | —        | —          | —              | ✓ source filters | — | —  | ExtKind |
 * | Library   | —        | —          | —              | ✓ filter   | —    | —       | LibrarySort |
 * | History   | —        | —          | —              | —          | —    | —       | last-consumed |
 *
 * The extension and library arms delegate to their `*CatalogFeed`
 * use-cases (the same extraction pattern [CinemetaCatalogFeed] set) so
 * they stay unit-testable against the source-manager interactor seams.
 */
class CatalogRowResolver(
    private val tmdbDiscover: TmdbDiscover,
    private val cinemetaCatalogFeed: CinemetaCatalogFeed,
    private val worksBrowseApi: WorksBrowseApi,
    private val openLibraryTrending: OpenLibraryTrending,
    private val sourcePreferences: SourcePreferences,
    private val extensionCatalogFeed: ExtensionCatalogFeed,
    private val libraryCatalogFeed: LibraryCatalogFeed,
) {

    suspend fun resolve(spec: CatalogRowSpec): List<RowItem> = when (val feed = spec.feed) {
        is Feed.Tmdb -> resolveTmdb(spec, feed)
        is Feed.Cinemeta -> resolveCinemeta(feed)
        is Feed.Canonical -> resolveCanonical(spec, feed)
        is Feed.Extension -> extensionCatalogFeed.fetch(spec, feed)
        is Feed.Library -> libraryCatalogFeed.fetch(spec)
        // Served live from screen-model state (the recents flows already
        // run there); the resolver path only sees it if a spec escapes
        // the short-circuit — an empty row hides itself.
        Feed.History -> emptyList()
    }

    /**
     * TMDB `discover` — the facet-complete backend: genres, sub-genre
     * keywords, streaming services, year window, rating floor, sort.
     * `spec.sort` defaults to `"default"`, which is NOT a
     * [DiscoverySort] member — unparseable values fall back to
     * [DiscoverySort.POPULARITY] instead of erroring the row.
     */
    private suspend fun resolveTmdb(spec: CatalogRowSpec, feed: Feed.Tmdb): List<RowItem> {
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
            voteAverage?.let { "★ %.1f".format(Locale.US, it) },
        ).joinToString(" · ").takeIf { it.isNotEmpty() },
        target = RowTarget.CanonicalShows(tmdbId = id, mediaType = mediaType),
        releaseInfo = year?.toString(),
        imdbRating = voteAverage?.toString(),
        mintKey = "tmdb:$mediaType:$id",
    )

    /**
     * Cinemeta catalog — the catalog pick IS the facet surface; items
     * materialize through the shared feed's meta→entry path at tap.
     */
    private suspend fun resolveCinemeta(feed: Feed.Cinemeta): List<RowItem> {
        val result = cinemetaCatalogFeed.fetchCatalog(feed.cinemetaType, feed.catalogId)
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
        // No mintKey — Cinemeta ids aren't work ids; there's no resolve
        // or report to run for these cards.
    )

    /**
     * Canonical MikoDB catalog — `kindsCsv == "ol"` serves the
     * OpenLibrary trending feed (books only); anything else hits the
     * worker's `/v1/works/browse`. `genreNames` filters client-side —
     * the worker has no genre facet.
     */
    private suspend fun resolveCanonical(spec: CatalogRowSpec, feed: Feed.Canonical): List<RowItem> {
        val kinds = feed.kindsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (kinds == listOf(FEED_OPENLIBRARY) && spec.medium == WorkIdResolver.MEDIUM_BOOKS) {
            val items = openLibraryTrending.trending()
                ?: throw CatalogFetchException("openlibrary trending returned null")
            return items.map { it.toRowItem() }
        }
        val result = worksBrowseApi.browse(
            medium = spec.medium,
            kinds = kinds,
            sort = WorksBrowseApi.SORT_POPULARITY,
            page = 1,
            includeNsfw = sourcePreferences.showNsfwSource().get(),
        ) ?: throw CatalogFetchException("works browse returned null")
        return result.items
            .filter { dto ->
                spec.genreNames.isEmpty() ||
                    dto.genres.any { g -> spec.genreNames.any { g.contains(it, ignoreCase = true) } }
            }
            .map { it.toRowItem(spec.medium) }
    }

    private fun WorksBrowseApi.WorkDto.toRowItem(medium: String): RowItem = RowItem(
        title = title,
        coverUrl = cover_url,
        subtitle = author,
        target = RowTarget.CanonicalWork(
            work = MangaNovelIdBridge.WorkRow(
                mikoWorkId = miko_work_id,
                type = kind ?: "",
                title = title,
                author = author,
                year = year?.toLong(),
                nsfw = nsfw,
                resolutionSource = RESOLUTION_BROWSE,
                genres = genres.takeIf { it.isNotEmpty() },
                coverUrl = cover_url,
            ),
            medium = medium,
        ),
        mintKey = miko_work_id,
    )

    private fun OpenLibraryTrending.Item.toRowItem(): RowItem {
        val workId = "$MK_OL_PREFIX$workKey"
        return RowItem(
            title = title,
            coverUrl = coverUrl,
            subtitle = author,
            target = RowTarget.CanonicalWork(
                work = MangaNovelIdBridge.WorkRow(
                    mikoWorkId = workId,
                    type = "book",
                    title = title,
                    author = author,
                    year = year?.toLong(),
                    resolutionSource = RESOLUTION_OPENLIBRARY,
                    coverUrl = coverUrl,
                    externalIds = mapOf("openlibrary" to workKey),
                ),
                medium = WorkIdResolver.MEDIUM_BOOKS,
            ),
            mintCandidate = WorkIdResolver.Candidate(
                medium = WorkIdResolver.MEDIUM_BOOKS,
                title = title,
                url = "$OPENLIBRARY_WORK_URL$workKey",
                author = author,
                isbn = isbn,
                isBook = true,
                kindHint = "book",
            ),
            mintKey = workId,
        )
    }

    companion object {
        /** Feed tag for the OpenLibrary trending feed — matches the
         *  canonical catalog's feed-key convention. */
        private const val FEED_OPENLIBRARY = "ol"
        private const val MK_OL_PREFIX = "mk:ol:"
        private const val OPENLIBRARY_WORK_URL = "https://openlibrary.org/works/"
        private const val RESOLUTION_BROWSE = "works_browse"
        private const val RESOLUTION_OPENLIBRARY = "openlibrary"
    }
}
