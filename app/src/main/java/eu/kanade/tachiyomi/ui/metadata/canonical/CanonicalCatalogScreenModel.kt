package eu.kanade.tachiyomi.ui.metadata.canonical

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
import eu.kanade.tachiyomi.data.mikodb.MangaNovelIdBridge.WorkRow
import eu.kanade.tachiyomi.data.mikodb.SeenReporter
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.data.mikodb.WorksBrowseApi
import eu.kanade.tachiyomi.metadata.openlibrary.OpenLibraryTrending
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
 * One row of the idle catalog grid — [work] materializes on tap;
 * [coverUrl] renders the poster. [mintCandidate] is non-null only for
 * non-canonical rows (OpenLibrary trending): browsing resolves them via
 * the Worker (which mints the `mk:ol:` row server-side) before
 * reporting `markSeen`. Canonical browse rows were minted by the Worker
 * already — seeded rows skip reporting entirely (no coverage signal).
 */
@Immutable
data class CatalogItem(
    val work: WorkRow,
    val coverUrl: String? = null,
    val mintCandidate: WorkIdResolver.Candidate? = null,
)

/**
 * Discovery surface for the manga/books carriers (NCM-007 / Phase 12-01,
 * extended by 15-07).
 *
 * Idle view: a ranked catalog feed — the Worker's `/v1/works/browse`
 * for the manga/novels pills (kind-filtered) and OpenLibrary trending
 * for the books "Books" pill. Feeds are keyed `$medium|$kindsCsv` (the
 * OpenLibrary feed carries the [FEED_OPENLIBRARY] tag) and cached for
 * the day like the Cinemeta catalog.
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
    private val browseApi: WorksBrowseApi = Injekt.get(),
    private val openLibrary: OpenLibraryTrending = Injekt.get(),
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
        val boundEntryIds: Map<String, Long> = emptyMap(),
    )

    private val resolver: WorkIdResolver by injectLazy()
    private val seenReporter: SeenReporter by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()

    /** Minted/reported ids this session — gates the resolve path; the
     *  persistent `seen_reported` table dedupes the write itself. */
    private val reportedWorkIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mintPermits = Semaphore(MINT_CONCURRENCY)

    /** Cached feed data keyed by feed key, valid for [CACHE_MS]. */
    private data class FeedCache(
        val items: List<CatalogItem>,
        val page: Int,
        val hasMore: Boolean,
        val timestamp: Long,
    )

    private val feedCache = mutableMapOf<String, FeedCache>()

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
        val feed = feedKey(medium, kindsCsv)
        if (state.value.browseFeed == feed && state.value.browseError == null) return
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
                // Binding set is recomputed when the page lands —
                // a cached page's ids can sit stale until then.
                boundEntryIds = emptyMap(),
            )
        }
        if (cached == null) {
            screenModelScope.launch { loadBrowsePage(feed, kindsCsv, page = 1, append = false) }
        } else {
            screenModelScope.launch {
                val bound = catalog.localLibraryEntryIds(
                    cached.items.map { it.work }, feed.substringBefore(FEED_SEPARATOR),
                )
                mutableState.update { s ->
                    if (s.browseFeed == feed) s.copy(boundEntryIds = bound) else s
                }
            }
        }
    }

    /**
     * Snapshot of a feed's cached items without making it live — pager
     * pages preview their own pill's feed during a swipe (the settled
     * page owns [State.browseItems]). Empty when nothing is cached.
     */
    fun peekCachedFeed(medium: String, kindsCsv: String?): List<CatalogItem> =
        feedCache[feedKey(medium, kindsCsv)]
            ?.takeIf { System.currentTimeMillis() - it.timestamp < CACHE_MS }
            ?.items
            .orEmpty()

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

    /** Pull-to-refresh — drops the cached feed and reloads page 1. */
    fun refreshBrowse() {
        val s = state.value
        val feed = s.browseFeed ?: return
        feedCache.remove(feed)
        mutableState.update {
            it.copy(
                browseItems = emptyList(),
                browsePage = 0,
                browseHasMore = true,
                browseLoading = true,
                browseError = null,
            )
        }
        screenModelScope.launch {
            loadBrowsePage(feed, s.browseKindsCsv, page = 1, append = false)
        }
    }

    fun retryBrowse() = refreshBrowse()

    private suspend fun loadBrowsePage(feed: String, kindsCsv: String?, page: Int, append: Boolean) {
        mutableState.update { s -> if (s.browseFeed == feed) s.copy(browseLoading = true, browseError = null) else s }
        if (kindsCsv == null) {
            val items = openLibrary.trending()?.map { it.toCatalogItem() }
            if (items == null) {
                mutableState.update { s ->
                    if (s.browseFeed == feed) s.copy(browseLoading = false, browseError = FEED_ERROR) else s
                }
                return
            }
            feedCache[feed] = FeedCache(items, page = 1, hasMore = false, timestamp = System.currentTimeMillis())
            val medium = feed.substringBefore(FEED_SEPARATOR)
            val bound = catalog.localLibraryEntryIds(items.map { it.work }, medium)
            mutableState.update { s ->
                if (s.browseFeed != feed) {
                    s
                } else {
                    s.copy(
                        browseItems = items,
                        browsePage = 1,
                        browseHasMore = false,
                        browseLoading = false,
                        boundEntryIds = bound,
                    )
                }
            }
            return
        }

        val medium = feed.substringBefore(FEED_SEPARATOR)
        val result = browseApi.browse(
            medium = medium,
            kinds = kindsCsv.split(','),
            sort = WorksBrowseApi.SORT_POPULARITY,
            page = page,
            includeNsfw = sourcePreferences.showNsfwSource().get(),
        )
        if (result == null) {
            mutableState.update { s ->
                if (s.browseFeed == feed) s.copy(browseLoading = false, browseError = FEED_ERROR) else s
            }
            return
        }
        // Merge against the cache — a pill switch mid-request leaves the
        // state pointing at another feed; the page still lands in its
        // own feed's cache entry.
        val merged = if (append) {
            val prev = feedCache[feed]?.items.orEmpty()
            val seen = prev.mapTo(HashSet()) { it.work.mikoWorkId }
            prev + result.items.map { it.toCatalogItem() }.filter { it.work.mikoWorkId !in seen }
        } else {
            result.items.map { it.toCatalogItem() }
        }
        feedCache[feed] = FeedCache(merged, result.page, result.hasMore, System.currentTimeMillis())
        val bound = catalog.localLibraryEntryIds(merged.map { it.work }, medium)
        mutableState.update { s ->
            if (s.browseFeed != feed) {
                s
            } else {
                s.copy(
                    browseItems = merged,
                    browsePage = result.page,
                    browseHasMore = result.hasMore,
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
                        // Identity-correct reclassification: a trending
                        // "book" that resolve proves is a MangaBaka-known
                        // work (light novel/manga kind) belongs under the
                        // Novels pill, not Books — pull it from the feed.
                        if (resolved.value.startsWith(MK_MB_PREFIX) ||
                            (resolved.kind != null && resolved.kind != KIND_BOOK)
                        ) {
                            dropFeedItem(item)
                        }
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG) { "discovery mint-on-browse failed for '${item.work.title}': ${e.message}" }
            }
        }
    }

    /**
     * Materializes the canonical entry row for [work] and returns the
     * database id to navigate to (MangaScreen / NovelScreen). The
     * explicit [medium] keeps the pick correct even if the row was
     * tapped while a carrier switch is still settling.
     *
     * When a favorited extension row already covers the work the tap
     * opens THAT row instead — the library and discovery surfaces point
     * at one entry, and read/tracking/download state is untouched. A
     * canonical row here would fork the user's progress.
     */
    suspend fun materializeEntryId(work: WorkRow, medium: String): Long {
        state.value.boundEntryIds[work.mikoWorkId]?.let { return it }
        catalog.localLibraryEntryId(work, medium)?.let { return it }
        return if (medium == WorkIdResolver.MEDIUM_BOOKS) {
            catalog.materializeNovelId(work)
        } else {
            catalog.materializeMangaId(work)
        }
    }

    /**
     * Materializes the canonical entry row for [work] and returns the
     * database id to navigate to (MangaScreen / NovelScreen).
     */
    suspend fun getOrCreateEntryId(work: WorkRow): Long =
        materializeEntryId(work, state.value.medium)

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

    private fun WorksBrowseApi.WorkDto.toCatalogItem() = CatalogItem(
        work = WorkRow(
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
        coverUrl = cover_url,
    )

    private fun OpenLibraryTrending.Item.toCatalogItem(): CatalogItem {
        val workUrl = "$OPENLIBRARY_WORK_URL$workKey"
        return CatalogItem(
            work = WorkRow(
                // The Worker mints `mk:ol:{workKey}` for OL works — the
                // row materializes deterministically under that id.
                mikoWorkId = "$MK_OL_PREFIX$workKey",
                type = "book",
                title = title,
                author = author,
                year = year?.toLong(),
                resolutionSource = RESOLUTION_OPENLIBRARY,
                externalIds = mapOf("openlibrary" to workKey),
            ),
            coverUrl = coverUrl,
            mintCandidate = WorkIdResolver.Candidate(
                medium = WorkIdResolver.MEDIUM_BOOKS,
                title = title,
                url = workUrl,
                author = author,
                isbn = isbn,
                isBook = true,
                kindHint = "book",
            ),
        )
    }

    companion object {
        /** Feed-key tag for the OpenLibrary trending feed. */
        private const val FEED_OPENLIBRARY = "ol"
        private const val FEED_SEPARATOR = "|"
        private const val RESOLUTION_BROWSE = "works_browse"
        private const val RESOLUTION_OPENLIBRARY = "openlibrary"
        private const val MK_OL_PREFIX = "mk:ol:"
        private const val MK_MB_PREFIX = "mk:mb:"
        private const val KIND_BOOK = "book"
        private const val OPENLIBRARY_WORK_URL = "https://openlibrary.org/works/"
        private const val FEED_ERROR = "Couldn't load the catalog"

        /** Mint-on-browse caps concurrent resolve calls. */
        private const val MINT_CONCURRENCY = 2

        /** Feed cache freshness — matches CinemetaBrowseScreenModel's 24h. */
        private const val CACHE_MS = 24 * 60 * 60 * 1000L

        internal fun feedKey(medium: String, kindsCsv: String?): String =
            "$medium$FEED_SEPARATOR${kindsCsv ?: FEED_OPENLIBRARY}"
    }
}
