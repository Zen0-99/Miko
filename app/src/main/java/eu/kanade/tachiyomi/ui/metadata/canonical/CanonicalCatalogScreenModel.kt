package eu.kanade.tachiyomi.ui.metadata.canonical

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.tachiyomi.data.catalogrows.CatalogItem
import eu.kanade.tachiyomi.data.catalogrows.EXT_ID_PREFIX
import eu.kanade.tachiyomi.data.catalogrows.EXT_SOURCE_KEY
import eu.kanade.tachiyomi.data.catalogrows.EXT_URL_KEY
import eu.kanade.tachiyomi.data.catalogrows.WEBTOON_KINDS_CSV
import eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
import eu.kanade.tachiyomi.data.mikodb.MangaNovelIdBridge.WorkRow
import eu.kanade.tachiyomi.data.mikodb.SeenReporter
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

/**
 * Discovery surface for the manga/books carriers (NCM-007 / Phase 12-01,
 * extended by 15-07).
 *
 * Idle view: a ranked catalog feed — the Worker's `/v1/works/browse`
 * for every pill (kind-filtered; the books pill uses the seeded
 * `book` chart and falls back to live OpenLibrary trending until the
 * chart lands in works_cache). Feeds are keyed `$medium|$kindsCsv`
 * (`books|book` for the book chart), disk-persisted, and warmed on
 * app start. No feed depends on an installed extension.
 *
 * Search stays map-backed — one row per canonical work, never
 * per-extension duplicates. Tapping either a feed row or a search hit
 * materializes the canonical entry row and opens its detail screen.
 *
 * Mint-on-browse: OpenLibrary rows resolve → report once per install
 * (the Worker mints the `mk:ol:` row server-side, then `markSeen`
 * records the coverage signal). Seeded rows (`mk:mb:`/`mk:hs:`) are
 * already cataloged — they are never reported. Dedupe persists in the
 * `seen_reported` table via [SeenReporter], so restarts don't
 * re-report. Capped at [MINT_CONCURRENCY] in-flight, fire-and-forget.
 */
class CanonicalCatalogScreenModel(
    private val catalog: CanonicalCatalog = Injekt.get(),
) : StateScreenModel<CanonicalCatalogScreenModel.State>(State()) {

    @Immutable
    data class State(
        val medium: String = WorkIdResolver.MEDIUM_MANGA,
        val results: List<WorkRow> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val searchQuery: String? = null,
        /**
         * Idle-catalog feed — `null` until [setCatalogFeed] selects one.
         * The key is `$medium|$kindsCsv` (`$medium|ol` for OpenLibrary).
         */
        val browseFeed: String? = null,
        /** null = the feed is the OpenLibrary trending list. */
        val browseKindsCsv: String? = null,
        val browseItems: List<CatalogItem> = emptyList(),
        val browsePage: Int = 0,
        val browseHasMore: Boolean = false,
        val browseLoading: Boolean = false,
        val browseError: String? = null,
        /**
         * `mikoWorkId` → local library entry id for works a favorited
         * extension row already covers (normalized-title match). Cards
         * show the in-library badge and taps open the local entry — the
         * canonical row would fork library state.
         */
        val boundEntryIds: Map<String, CanonicalCatalog.BoundEntry> = emptyMap(),
    )

    private val resolver: WorkIdResolver by injectLazy()
    private val seenReporter: SeenReporter by injectLazy()

    /** Minted/reported ids this session — gates the resolve path; the
     *  persistent `seen_reported` table dedupes the write itself. */
    private val reportedWorkIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mintPermits = Semaphore(MINT_CONCURRENCY)

    /** Cached feed data keyed by feed key, valid for [CACHE_MS].
     *  [bound] carries the feed's last-known library bindings so a pill
     *  switch restores the in-library badge in the same frame the items
     *  land — no "badges pop in late" flash. */
    private data class FeedCache(
        val items: List<CatalogItem>,
        val page: Int,
        val hasMore: Boolean,
        val timestamp: Long,
        val bound: Map<String, CanonicalCatalog.BoundEntry> = emptyMap(),
    )

    private val feedCache = mutableMapOf<String, FeedCache>()
    private val feeds: eu.kanade.tachiyomi.data.catalogrows.DiscoveryFeeds by injectLazy()
    private val feedStore: eu.kanade.tachiyomi.data.catalogrows.DiscoveryFeedStore by injectLazy()

    private val networkToLocalManga: tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga by injectLazy()
    private val networkToLocalNovel: tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel by injectLazy()

    init {
        // Disk-hydrate the feed cache — the app-start warmer already wrote
        // today's pages, so Discovery renders instantly on first visit.
        screenModelScope.launchIO {
            feedStore.load().forEach { (feed, stored) ->
                feedCache.putIfAbsent(
                    feed,
                    FeedCache(
                        stored.items,
                        stored.page,
                        stored.hasMore,
                        stored.savedAt,
                        stored.bound.mapValues { (workId, id) ->
                            CanonicalCatalog.BoundEntry(id, stored.boundCovers[workId])
                        },
                    ),
                )
            }
        }
    }

    /** Called when the content mode switches — re-runs an active search. */
    fun setMedium(medium: String) {
        if (state.value.medium == medium) return
        mutableState.update {
            it.copy(
                medium = medium,
                results = emptyList(),
                boundEntryIds = emptyMap(),
                // The feed is carrier-keyed — drop it; the composable
                // selects the new carrier's feed right after.
                browseFeed = null,
                browseKindsCsv = null,
                browseItems = emptyList(),
                browsePage = 0,
                browseHasMore = false,
                browseLoading = false,
                browseError = null,
            )
        }
        state.value.searchQuery?.let { search(it) }
    }

    fun search(query: String) {
        screenModelScope.launch {
            mutableState.update {
                it.copy(results = emptyList(), searchQuery = query, loading = true, error = null)
            }
            try {
                val results = catalog.search(query, state.value.medium)
                val bound = catalog.localLibraryEntryIds(results, state.value.medium)
                mutableState.update {
                    it.copy(loading = false, results = results, boundEntryIds = bound)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "canonical catalog search failed" }
                mutableState.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Unknown error (${e::class.simpleName})",
                    )
                }
            }
        }
    }

    fun clearSearch() {
        mutableState.update {
            it.copy(
                results = emptyList(),
                searchQuery = null,
                loading = false,
                error = null,
                boundEntryIds = emptyMap(),
            )
        }
    }

    fun retry() {
        state.value.searchQuery?.let { search(it) }
    }

    // ---------- idle-catalog feed ----------

    /**
     * Point the idle grid at a feed. [kindsCsv] null selects the
     * OpenLibrary trending feed (the BOOKS carrier's "Books" pill);
     * otherwise it is the csv `kind` filter for `/v1/works/browse`
     * under [medium]. No-op while the feed is already live, but an
     * errored feed retries on re-selection.
     */
    fun setCatalogFeed(medium: String, kindsCsv: String?) {
        screenModelScope.launch {
            val feed = feedFor(medium, kindsCsv)
            if (state.value.browseFeed == feed && state.value.browseError == null) return@launch
            val cached = feedCache[feed]?.takeIf {
                System.currentTimeMillis() - it.timestamp < CACHE_MS
            }
            mutableState.update {
                it.copy(
                    browseFeed = feed,
                    browseKindsCsv = kindsCsv,
                    browseItems = cached?.items.orEmpty(),
                    browsePage = cached?.page ?: 0,
                    browseHasMore = cached?.hasMore ?: false,
                    browseLoading = cached == null,
                    browseError = null,
                    // Restore the feed's last-known bindings instantly —
                    // clearing them on every pill switch is what made the
                    // badge pop in a beat late. The async recompute below
                    // (or the page landing) still freshens them.
                    boundEntryIds = cached?.bound.orEmpty(),
                )
            }
            if (cached == null) {
                loadBrowsePage(feed, kindsCsv, page = 1, append = false)
            } else {
                val bound = catalog.localLibraryEntryIds(
                    cached.items.map { it.work }, feed.substringBefore(FEED_SEPARATOR),
                )
                mutableState.update { s ->
                    if (s.browseFeed == feed) s.copy(boundEntryIds = bound) else s
                }
                feedCache[feed]?.let { feedCache[feed] = it.copy(bound = bound) }
                // Past [REFRESH_AFTER_MS] the cached rows render instantly
                // AND page 1 silently re-fetches — the app-start warmer
                // owns the disk copy but hydration can predate its write.
                if (System.currentTimeMillis() - cached.timestamp > REFRESH_AFTER_MS) {
                    loadBrowsePage(feed, kindsCsv, page = 1, append = false, mergeRefresh = true)
                }
            }
        }
    }

    /** Feed key for (medium, pill) — delegates the books reroute to
     *  [DiscoveryFeeds.feedFor]. */
    private fun feedFor(medium: String, kindsCsv: String?): String =
        feeds.feedFor(medium, kindsCsv)

    /**
     * Snapshot of a feed's cached items without making it live — pager
     * pages preview their own pill's feed during a swipe (the settled
     * page owns [State.browseItems]). Empty when nothing is cached.
     */
    fun peekCachedFeed(medium: String, kindsCsv: String?): List<CatalogItem> =
        feedCache[peekFeedKey(medium, kindsCsv)]
            ?.takeIf { System.currentTimeMillis() - it.timestamp < CACHE_MS }
            ?.items
            .orEmpty()

    /** Last-known bound set for a non-live feed — preview pages render
     *  the in-library badge from their own cache entry instead of going
     *  bare until their feed goes live again. */
    fun peekCachedBound(medium: String, kindsCsv: String?): Map<String, CanonicalCatalog.BoundEntry> =
        feedCache[peekFeedKey(medium, kindsCsv)]
            ?.takeIf { System.currentTimeMillis() - it.timestamp < CACHE_MS }
            ?.bound
            .orEmpty()

    /** Sync mirror of [feedFor] for preview lookups. */
    private fun peekFeedKey(medium: String, kindsCsv: String?): String =
        feeds.feedFor(medium, kindsCsv)

    /** Next page for the live feed — no-op while a load runs or the
     *  feed is single-page (OpenLibrary trending has no offset). */
    fun loadNextBrowsePage() {
        val s = state.value
        if (s.searchQuery != null || s.browseLoading || !s.browseHasMore) return
        val feed = s.browseFeed ?: return
        screenModelScope.launch {
            loadBrowsePage(feed, s.browseKindsCsv, s.browsePage + 1, append = true)
        }
    }

    /** Pull-to-refresh — re-fetches page 1 and merges it ahead of the
     *  current rows. The grid keeps its content while the indicator
     *  spins; new chart entries land on top. */
    fun refreshBrowse() {
        val s = state.value
        val feed = s.browseFeed ?: return
        mutableState.update {
            it.copy(browseLoading = true, browseError = null)
        }
        screenModelScope.launch {
            loadBrowsePage(feed, s.browseKindsCsv, page = 1, append = false, mergeRefresh = true)
        }
    }

    fun retryBrowse() = refreshBrowse()

    private suspend fun loadBrowsePage(
        feed: String,
        kindsCsv: String?,
        page: Int,
        append: Boolean,
        mergeRefresh: Boolean = false,
    ) {
        mutableState.update { s -> if (s.browseFeed == feed) s.copy(browseLoading = true, browseError = null) else s }
        val isWebtoon = feed.substringBefore(FEED_SEPARATOR) == WorkIdResolver.MEDIUM_MANGA &&
            kindsCsv == WEBTOON_KINDS_CSV
        val result = feeds.fetch(feed, page)
        if (result == null) {
            mutableState.update { s ->
                if (s.browseFeed == feed) s.copy(browseLoading = false, browseError = FEED_ERROR) else s
            }
            return
        }
        commitBrowsePage(
            feed,
            result.first,
            page,
            hasMore = result.second,
            append = append,
            mergeRefresh = mergeRefresh,
            cap = if (isWebtoon) WEBTOON_TOP_CAP else null,
        )
    }

    /** Merge a fetched page into the feed cache + live state, recompute
     *  the bound set, and write the page through to the on-disk store.
     *  [cap] bounds the accumulated feed size (the webtoon Top-500);
     *  hitting it ends pagination. */
    private suspend fun commitBrowsePage(
        feed: String,
        pageItems: List<CatalogItem>,
        page: Int,
        hasMore: Boolean,
        append: Boolean,
        mergeRefresh: Boolean = false,
        cap: Int? = null,
    ) {
        val medium = feed.substringBefore(FEED_SEPARATOR)
        // Merge against the cache — a pill switch mid-request leaves the
        // state pointing at another feed; the page still lands in its
        // own feed's cache entry. `mergeRefresh` (refresh/stale pick)
        // keeps the backlog: the fresh page 1 lands in chart order and
        // previously cached rows that didn't return stay appended.
        val merged = if (append) {
            val prev = feedCache[feed]?.items.orEmpty()
            val seen = prev.mapTo(HashSet()) { it.work.mikoWorkId }
            prev + pageItems.filter { it.work.mikoWorkId !in seen }
        } else if (mergeRefresh) {
            val freshIds = pageItems.mapTo(HashSet()) { it.work.mikoWorkId }
            val backlog = feedCache[feed]?.items.orEmpty()
            pageItems + backlog.filterNot { it.work.mikoWorkId in freshIds }
        } else {
            pageItems
        }.let { m -> cap?.let { m.take(it) } ?: m }
        val effectiveHasMore = cap?.let { hasMore && merged.size < it } ?: hasMore
        val bound = catalog.localLibraryEntryIds(merged.map { it.work }, medium)
        feedCache[feed] = FeedCache(merged, page, effectiveHasMore, System.currentTimeMillis(), bound)
        screenModelScope.launchIO {
            feedStore.save(
                feed,
                merged,
                page,
                effectiveHasMore,
                bound.mapValues { it.value.entryId },
                bound.mapNotNull { (k, v) -> v.coverUrl?.let { k to it } }.toMap(),
            )
        }
        mutableState.update { s ->
            if (s.browseFeed != feed) {
                s
            } else {
                s.copy(
                    browseItems = merged,
                    browsePage = page,
                    browseHasMore = effectiveHasMore,
                    browseLoading = false,
                    browseError = null,
                    boundEntryIds = bound,
                )
            }
        }
    }

    // ---------- mint-on-browse ----------

    /**
     * Called when a catalog row enters composition. Canonical browse
     * rows were minted by the Worker — report `markSeen` only.
     * OpenLibrary rows resolve first (the Worker mints the `mk:ol:` row
     * via the `openlibrary` external id or the OL title/author search),
     * then report. Each work id is handled once per session and the
     * work is semaphore-capped — scrolling a full page mints at most
     * [MINT_CONCURRENCY] works at a time, never on the UI path.
     */
    fun onCatalogItemRendered(item: CatalogItem) {
        val workId = item.work.mikoWorkId
        // Seeded rows (mk:mb:/mk:hs:) have no coverage signal to report —
        // nothing to do unless the row also needs a resolve.
        if (item.mintCandidate == null && !seenReporter.isReportable(workId)) return
        if (!reportedWorkIds.add(workId)) return
        screenModelScope.launchIO {
            try {
                val candidate = item.mintCandidate
                if (candidate == null) {
                    seenReporter.report(workId, state.value.medium)
                } else {
                    mintPermits.withPermit {
                        // OL feed ids ARE the resolved id (mk:ol: is
                        // deterministic) — a prior install's report means
                        // resolve already ran; skip it too.
                        if (seenReporter.wasReported(workId)) return@withPermit
                        val resolved = resolver.resolve(candidate) ?: return@withPermit
                        seenReporter.report(resolved.value, resolved.medium)
                        when {
                            // Identity-correct reclassification: a trending
                            // "book" that resolve proves is a MangaBaka-known
                            // work (light novel/manga kind) belongs under the
                            // Novels pill, not Books — pull it from the feed.
                            workId.startsWith(MK_OL_PREFIX) &&
                                (
                                    resolved.value.startsWith(MK_MB_PREFIX) ||
                                        (resolved.kind != null && resolved.kind != KIND_BOOK)
                                    ) -> dropFeedItem(item)
                            // Provisional webtoon-feed rows (ext:{pkg}:{url})
                            // promote to their canonical id in place — same
                            // card, real work identity for badges + taps.
                            workId.startsWith(EXT_ID_PREFIX) && resolved.value != workId ->
                                promoteFeedItem(item, resolved)
                        }
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG) { "discovery mint-on-browse failed for '${item.work.title}': ${e.message}" }
            }
        }
    }

    /** Rewrite a provisional `ext:` row to its resolved canonical id —
     *  in-place in the feed cache and live state, carrying the bound
     *  badge across the id swap. */
    private fun promoteFeedItem(item: CatalogItem, resolved: WorkIdResolver.CanonicalWorkId) {
        val oldId = item.work.mikoWorkId
        val promoted = item.copy(
            work = item.work.copy(
                mikoWorkId = resolved.value,
                type = resolved.kind ?: item.work.type,
                nsfw = resolved.nsfw,
            ),
        )
        feedCache.entries.firstOrNull { e ->
            e.value.items.any { it.work.mikoWorkId == oldId }
        }?.let { e ->
            feedCache[e.key] = e.value.copy(
                items = e.value.items.map { if (it.work.mikoWorkId == oldId) promoted else it },
            )
        }
        mutableState.update { s ->
            val carried = s.boundEntryIds[oldId]
            s.copy(
                browseItems = s.browseItems.map {
                    if (it.work.mikoWorkId == oldId) promoted else it
                },
                boundEntryIds = if (carried != null) {
                    s.boundEntryIds - oldId + (resolved.value to carried)
                } else {
                    s.boundEntryIds
                },
            )
        }
    }

    /**
     * Materializes the canonical entry row for [work] and returns the
     * database id to navigate to (MangaScreen / NovelScreen). The
     * explicit [medium] keeps the pick correct even if the row was
     * tapped while a carrier switch is still settling.
     *
     * The tap always materializes the canonical row — discovery is the
     * work's identity, and opening it is what fires the canonical
     * pipeline (enrich → canonize → chapter sync). Bound library rows
     * are surfaced via [UiState.boundEntryIds] as a badge only.
     */
    suspend fun materializeEntryId(work: WorkRow, medium: String): Long {
        return if (medium == WorkIdResolver.MEDIUM_BOOKS) {
            catalog.materializeNovelId(work)
        } else {
            catalog.materializeMangaId(work)
        }
    }

    /**
     * Tap-time entry resolution for catalog cards.
     *
     * 1. A bound library row wins — discovery badge and tap agree, and
     *    the user's favorited entry opens instead of a canonical fork.
     * 2. Provisional webtoon rows (`ext:` ids) resolve to their canonical
     *    work id first, then materialize — unresolvable rows fall back
     *    to the carrying extension's own entry.
     * 3. Everything else materializes the canonical row as before.
     */
    suspend fun entryIdFor(item: CatalogItem, medium: String): Long? {
        // Bound row wins — check the live feed's set, then every cached
        // feed's (a tap on a preview page mid-swipe finds its binding in
        // that feed's cache entry, not the live state).
        val boundEntryId = state.value.boundEntryIds[item.work.mikoWorkId]?.entryId
            ?: feedCache.values.firstNotNullOfOrNull { it.bound[item.work.mikoWorkId]?.entryId }
        if (boundEntryId != null) return boundEntryId
        val work = item.work
        if (!work.mikoWorkId.startsWith(EXT_ID_PREFIX)) {
            return materializeEntryId(work, medium)
        }
        val resolved = item.mintCandidate
            ?.let { runCatching { resolver.resolve(it) }.getOrNull() }
        if (resolved != null) {
            promoteFeedItem(item, resolved)
            return materializeEntryId(
                work.copy(
                    mikoWorkId = resolved.value,
                    type = resolved.kind ?: work.type,
                    nsfw = resolved.nsfw,
                ),
                medium,
            )
        }
        // Unresolvable provisional row — open the carrying extension's
        // entry directly rather than minting a useless `miko:` stub.
        val sourceId = work.externalIds[EXT_SOURCE_KEY]?.toLongOrNull() ?: return null
        val url = work.externalIds[EXT_URL_KEY] ?: return null
        return if (medium == WorkIdResolver.MEDIUM_BOOKS) {
            networkToLocalNovel.await(
                SNovel.create().apply {
                    this.url = url
                    title = work.title
                    thumbnail_url = item.coverUrl
                }.toDomainNovel(sourceId),
            ).id
        } else {
            networkToLocalManga.await(
                SManga.create().apply {
                    this.url = url
                    title = work.title
                    thumbnail_url = item.coverUrl
                }.toDomainManga(sourceId),
            ).id
        }
    }

    /**
     * Materializes the canonical entry row for [work] and returns the
     * database id to navigate to (MangaScreen / NovelScreen). A bound
     * library row wins — a search hit that's already in the library
     * opens the library entry, not a canonical fork.
     */
    suspend fun getOrCreateEntryId(work: WorkRow): Long {
        state.value.boundEntryIds[work.mikoWorkId]?.entryId?.let { return it }
        return materializeEntryId(work, state.value.medium)
    }

    /**
     * Remove a rendered catalog row from the live feed + its cache
     * entry — used when resolve proves an OpenLibrary "book" is really
     * a MangaBaka light novel/manga (it belongs to the Novels feed).
     */
    private fun dropFeedItem(item: CatalogItem) {
        val droppedId = item.work.mikoWorkId
        feedCache[state.value.browseFeed]?.let { cache ->
            feedCache[state.value.browseFeed!!] =
                cache.copy(items = cache.items.filterNot { it.work.mikoWorkId == droppedId })
        }
        mutableState.update { s ->
            s.copy(browseItems = s.browseItems.filterNot { it.work.mikoWorkId == droppedId })
        }
    }

    companion object {
        private const val FEED_SEPARATOR = "|"
        private const val MK_OL_PREFIX = "mk:ol:"
        private const val MK_MB_PREFIX = "mk:mb:"
        private const val KIND_BOOK = "book"
        private const val FEED_ERROR = "Couldn't load the catalog"

        /** Mint-on-browse caps concurrent resolve calls. */
        private const val MINT_CONCURRENCY = 2

        /** Feed cache freshness — matches CinemetaBrowseScreenModel's 24h. */
        private const val CACHE_MS = 24 * 60 * 60 * 1000L

        /** Cached feeds past this age render instantly AND silently
         *  merge-refresh page 1 in the background. */
        private const val REFRESH_AFTER_MS = 30 * 60 * 1000L

        /** Webtoon chart depth — the pill is a Top-500, not an
         *  infinite scroll. */
        private const val WEBTOON_TOP_CAP = 500

        internal fun feedKey(medium: String, kindsCsv: String?): String =
            eu.kanade.tachiyomi.data.catalogrows.DiscoveryFeeds.feedKey(medium, kindsCsv)
    }
}


