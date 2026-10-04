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

    /** Feed key for (medium, pill): the books pill reroutes to the
     *  worker's seeded book chart (`books|book`) — the `books|ol`
     *  OpenLibrary-trending key only survives as a stale-cache
     *  fallback. Everything else maps straight to `$medium|$kindsCsv`. */
    fun feedFor(medium: String, kindsCsv: String?): String =
        if (medium == WorkIdResolver.MEDIUM_BOOKS && kindsCsv == null) {
            "$medium$FEED_SEPARATOR$FEED_BOOK"
        } else {
            feedKey(medium, kindsCsv)
        }

    /** One feed page — `null` on failure. The pair is
     *  (items, hasMore). */
    suspend fun fetch(feed: String, page: Int): Pair<List<CatalogItem>, Boolean>? {
        val tag = feed.substringAfter(FEED_SEPARATOR, missingDelimiterValue = "")
        val medium = feed.substringBefore(FEED_SEPARATOR)
        return when {
            tag == FEED_OPENLIBRARY -> openLibrary.trending()
                ?.map { it.toCatalogItem() }
                ?.let { it to false }
            tag == FEED_BOOK -> {
                // Seeded MikoDB book chart first; live OL trending only
                // while works_cache lacks a chart's worth of book rows
                // (pre-seed deployments, worker failures).
                val page1 = browseApi.browse(
                    medium = medium,
                    kinds = listOf(KIND_BOOK),
                    sort = WorksBrowseApi.SORT_POPULARITY,
                    page = page,
                    includeNsfw = sourcePreferences.showNsfwSource().get(),
                )
                if (page1 != null && (page > 1 || page1.items.size >= MIN_CHART_ROWS)) {
                    page1.items.map { it.toCatalogItem() } to page1.hasMore
                } else if (page == 1) {
                    openLibrary.trending()
                        ?.map { it.toCatalogItem() }
                        ?.let { it to false }
                } else {
                    page1?.let { it.items.map { w -> w.toCatalogItem() } to it.hasMore }
                }
            }
            tag.isEmpty() -> null
            else -> browseApi.browse(
                medium = medium,
                kinds = tag.split(','),
                sort = WorksBrowseApi.SORT_POPULARITY,
                page = page,
                includeNsfw = sourcePreferences.showNsfwSource().get(),
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
            // fetch for a feed no pill reads anymore.
            feeds.filterNot { it.substringAfter(FEED_SEPARATOR).startsWith(WEBTOON_FEED_TAG) }
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
        val bound = catalog.localLibraryEntryIds(merged.map { it.work }, medium)
        val feedHasMore = hasMore || backlog.any { it.work.mikoWorkId !in freshIds }
        store.save(
            feed,
            merged,
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
