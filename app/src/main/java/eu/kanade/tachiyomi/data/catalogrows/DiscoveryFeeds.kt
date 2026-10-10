package eu.kanade.tachiyomi.data.catalogrows

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.data.mikodb.WorksBrowseApi
import eu.kanade.tachiyomi.metadata.openlibrary.OpenLibraryTrending
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy

/**
 * Shared fetch/dispatch for the discovery catalog feeds — the
 * screen model's pager loads pages through [fetch], and the app-start
 * warmer refreshes cached feeds through [warm] so Discovery opens with
 * today's content already on disk.
 *
 * Feed keys: `$medium|$kindsCsv` for the worker's `/v1/works/browse` —
 * every pill is MikoDB-backed so Discovery never depends on an
 * installed extension. `$medium|ol` is the legacy OpenLibrary key
 * (still fetchable for stale cached feeds); the books pill now keys
 * `books|book` and falls back to live OL trending when the seeded
 * chart isn't in works_cache yet.
 */
class DiscoveryFeeds {

    private val browseApi: WorksBrowseApi by injectLazy()
    private val openLibrary: OpenLibraryTrending by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()
    private val catalog: CanonicalCatalog by injectLazy()
    private val store: DiscoveryFeedStore by injectLazy()

    /** Feed key for (medium, pill, sort): the books pill reroutes to the
     *  worker's seeded book chart (`books|book`) — the `books|ol`
     *  OpenLibrary-trending key only survives as a stale-cache
     *  fallback. Everything else maps straight to `$medium|$kindsCsv`.
     *  A non-default sort appends a third segment (`…|rating`) so every
     *  sort gets its own cache/disk entry. */
    fun feedFor(
        medium: String,
        kindsCsv: String?,
        sort: String = WorksBrowseApi.SORT_POPULARITY,
        filter: CatalogFilter? = null,
    ): String {
        val base = if (medium == WorkIdResolver.MEDIUM_BOOKS && kindsCsv == null) {
            "$medium$FEED_SEPARATOR$FEED_BOOK"
        } else {
            feedKey(medium, kindsCsv)
        }
        // An active filter namespaces the key (`…|<sort>|f:<facets>`) —
        // a filtered feed must never share the unfiltered feed's cache.
        if (filter?.isActive == true) {
            return "$base$FEED_SEPARATOR$sort$FEED_SEPARATOR$FILTER_PREFIX${filter.toFeedKey()}"
        }
        return if (sort == WorksBrowseApi.SORT_POPULARITY) {
            base
        } else {
            "$base$FEED_SEPARATOR$sort"
        }
    }

    /** One feed page — `null` on failure. The pair is
     *  (items, hasMore). [filter] carries the browse facet params the
     *  feed key's `f:` segment was minted from. */
    suspend fun fetch(
        feed: String,
        page: Int,
        filter: CatalogFilter? = null,
    ): Pair<List<CatalogItem>, Boolean>? {
        val tag = feed.substringAfter(FEED_SEPARATOR, missingDelimiterValue = "")
        val medium = feed.substringBefore(FEED_SEPARATOR)
        // Tag may carry a sort suffix (`kindsCsv|sort`) — default keys
        // stay bare so old disk caches keep hydrating.
        val segments = tag.split(FEED_SEPARATOR)
        val kindsTag = segments[0]
        val sort = segments.getOrNull(1)
            ?.takeIf { it in VALID_SORTS }
            ?: WorksBrowseApi.SORT_POPULARITY
        return when {
            kindsTag == FEED_OPENLIBRARY -> openLibrary.trending()
                ?.map { it.toCatalogItem() }
                ?.let { it to false }
            kindsTag == FEED_BOOK -> {
                // Seeded MikoDB book chart first; live OL trending only
                // while works_cache lacks a chart's worth of book rows
                // (pre-seed deployments, worker failures).
                val page1 = browseApi.browse(
                    medium = medium,
                    kinds = listOf(KIND_BOOK),
                    sort = sort,
                    page = page,
                    includeNsfw = sourcePreferences.showNsfwSource().get(),
                    genres = filter?.includeGenres.orEmpty(),
                    excludeGenres = filter?.excludeGenres.orEmpty(),
                    yearFrom = filter?.yearFrom,
                    yearTo = filter?.yearTo,
                    minRating = filter?.ratingGte,
                )
                if (page1 != null && (page > 1 || page1.items.size >= MIN_CHART_ROWS)) {
                    page1.items.map { it.toCatalogItem() } to page1.hasMore
                } else if (page == 1 && filter?.isActive != true) {
                    // No OL fallback under an active filter — a sparse
                    // filtered chart would swap the whole view to a
                    // different, unfiltered dataset.
                    openLibrary.trending()
                        ?.map { it.toCatalogItem() }
                        ?.let { it to false }
                } else {
                    page1?.let { it.items.map { w -> w.toCatalogItem() } to it.hasMore }
                }
            }
            kindsTag.isEmpty() -> null
            else -> browseApi.browse(
                medium = medium,
                kinds = kindsTag.split(','),
                sort = sort,
                page = page,
                includeNsfw = sourcePreferences.showNsfwSource().get(),
                genres = filter?.includeGenres.orEmpty(),
                excludeGenres = filter?.excludeGenres.orEmpty(),
                yearFrom = filter?.yearFrom,
                yearTo = filter?.yearTo,
                minRating = filter?.ratingGte,
            )?.let { it.items.map { w -> w.toCatalogItem() } to it.hasMore }
        }
    }

    /**
     * App-start warm: refresh every cached feed's page 1 in parallel,
     * merging the fresh slice ahead of the retained backlog — new chart
     * entries surface immediately, deeper pages aren't blown away. Feeds
     * the user has never opened stay untouched (no wasted requests), but
     * a fresh install warms the four default pills so first-visit
     * Discovery is instant.
     */
    suspend fun warm() {
        val stored = store.load()
        val feeds = if (stored.isEmpty()) {
            DEFAULT_PILLS.mapTo(HashSet()) { (medium, kinds) -> feedFor(medium, kinds) }
        } else {
            stored.keys
        }
        coroutineScope {
            // Stale `webtoon:{pkg}` keys come from the retired extension
            // chart path — refreshing them would burn an extension
            // fetch for a feed no pill reads anymore. Filtered keys
            // (`|f:` segment) are skipped too: the facet object isn't
            // recoverable from the key, and an unfiltered refresh would
            // write foreign rows into the filtered cache.
            feeds.filterNot { it.substringAfter(FEED_SEPARATOR).startsWith(WEBTOON_FEED_TAG) }
                .filterNot { it.contains("$FEED_SEPARATOR$FILTER_PREFIX") }
                .map { feed ->
                    async {
                        runCatching { refreshFeed(feed, stored[feed]) }
                            .onFailure {
                                logcat(LogPriority.WARN) { "DiscoveryFeeds: warm failed for $feed: ${it.message}" }
                            }
                    }
                }.awaitAll()
        }
    }

    /** Refetch page 1 of [feed], merge ahead of the cached backlog,
     *  recompute bindings, persist. */
    private suspend fun refreshFeed(feed: String, cached: DiscoveryFeedStore.Stored?) {
        val (fresh, hasMore) = fetch(feed, 1) ?: return
        val backlog = cached?.items.orEmpty()
        val freshIds = fresh.mapTo(HashSet()) { it.work.mikoWorkId }
        val merged = fresh + backlog.filterNot { it.work.mikoWorkId in freshIds }
        val medium = feed.substringBefore(FEED_SEPARATOR)
        // Worker seed covers are low-res imgproxy art — overlay covers a
        // detail visit healed onto the canonical entry row, or a cold
        // start repaints the seed art then swaps it in view.
        val covers = catalog.canonicalEntryCovers(
            merged.map { it.work.mikoWorkId },
            medium,
        )
        val healed = merged.map { item ->
            // The canonical row's thumbnail is authoritative here —
            // pinned picks are written into it, so user art survives
            // refresh without a preferCover dance.
            covers[item.work.mikoWorkId]?.url?.takeIf { it != item.coverUrl }
                ?.let { item.copy(coverUrl = it, work = item.work.copy(coverUrl = it)) }
                ?: item
        }
        val bound = catalog.localLibraryEntryIds(healed.map { it.work }, medium)
        val feedHasMore = hasMore || backlog.any { it.work.mikoWorkId !in freshIds }
        store.save(
            feed,
            healed,
            page = maxOf(1, cached?.page ?: 1),
            hasMore = feedHasMore,
            bound = bound.mapValues { it.value.entryId },
            boundCovers = bound.mapNotNull { (k, v) -> v.coverUrl?.let { k to it } }.toMap(),
        )
    }

    companion object {
        private const val FEED_SEPARATOR = "|"
        private const val FEED_OPENLIBRARY = "ol"
        private const val FEED_BOOK = "book"
        private const val KIND_BOOK = "book"

        /** Below this many page-1 book rows the seeded chart counts as
         *  absent and the pill falls back to live OL trending. */
        private const val MIN_CHART_ROWS = 20

        /** Retired extension-chart key prefix — kept for skipping stale
         *  cached feeds in [warm]. */
        private const val WEBTOON_FEED_TAG = "webtoon:"

        /** Feed-key segment prefix marking a filtered feed
         *  (`medium|kinds|sort|f:<facets>`). [warm] skips them — the
         *  filter object isn't recoverable from the key alone, and a
         *  filtered view re-fetches when the user revisits it anyway. */
        private const val FILTER_PREFIX = "f:"

        /** Sort vocabulary the worker accepts for `/v1/works/browse` —
         *  anything else in a feed key's third segment falls back to
         *  the default. */
        private val VALID_SORTS = setOf(
            WorksBrowseApi.SORT_POPULARITY,
            WorksBrowseApi.SORT_RATING,
            WorksBrowseApi.SORT_NEWEST,
        )

        /** The discovery pills — a fresh install warms all four so the
         *  first Discovery visit renders immediately. */
        private val DEFAULT_PILLS: List<Pair<String, String?>> = listOf(
            WorkIdResolver.MEDIUM_MANGA to "manga,comic",
            WorkIdResolver.MEDIUM_MANGA to WEBTOON_KINDS_CSV,
            WorkIdResolver.MEDIUM_BOOKS to "novel",
            WorkIdResolver.MEDIUM_BOOKS to null,
        )

        fun feedKey(medium: String, kindsCsv: String?): String =
            "$medium$FEED_SEPARATOR${kindsCsv ?: FEED_OPENLIBRARY}"
    }
}
