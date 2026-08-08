package eu.kanade.tachiyomi.ui.browse.novel.migration.all

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Collections

/**
 * Mass-migration ScreenModel. For each favorite novel from the source extension being
 * migrated away from, searches all other enabled catalogue sources for a title match,
 * fetches the chapter count for each match, and picks the source with the most chapters
 * as the recommended migration target.
 *
 * Performance optimizations:
 * - Hard timeout via CompletableDeferred race — blocking I/O can't overrun the timeout
 *   because we race `await()` against `withTimeoutOrNull`, not the I/O itself
 * - In-memory result cache (1 hour TTL) to skip re-searching on re-entry
 * - Search phase tracking for UI feedback ("Searching 15 sources...", "Fetching chapters...")
 * - Title normalization for better cross-source matching
 */
class MigrateNovelAllScreenModel(
    private val novelIds: List<Long>,
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val getNovelWithChapters: GetNovelWithChapters = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<MigrateNovelAllScreenModel.State>(State()) {

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledNovelSources().get()

    init {
        screenModelScope.launch {
            val initStart = System.currentTimeMillis()
            android.util.Log.i("MigrateNovel", "=== init START === novelIds=${novelIds.size}")

            // Load all novels, restoring any results cached from a previous visit
            // to this screen so re-entering doesn't restart every search.
            val loadStart = System.currentTimeMillis()
            val novels = novelIds.mapNotNull { getNovel.await(it) }
            android.util.Log.i("MigrateNovel", "getNovel.await took ${System.currentTimeMillis() - loadStart}ms for ${novels.size} novels")

            val items = novels.map { novel ->
                val cached = getCachedResult(novel.id)
                val localCount = try {
                    getNovelWithChapters.awaitChapters(novel.id).size
                } catch (_: Exception) { 0 }
                MigrationItem(
                    oldNovel = novel,
                    oldChapterCount = oldChapterCountCache[novel.id] ?: localCount,
                    status = cached?.status ?: MigrationStatus.Searching,
                    recommendedNovel = cached?.novel,
                    recommendedChapterCount = cached?.chapterCount ?: 0,
                    searchPhase = if (cached != null) "" else "Starting...",
                )
            }.toImmutableList()
            mutableState.update {
                it.copy(
                    items = items,
                    total = novels.size,
                    processed = items.count { item -> item.status != MigrationStatus.Searching },
                )
            }
            android.util.Log.i("MigrateNovel", "State initialized in ${System.currentTimeMillis() - loadStart}ms")

            // Search for matches concurrently with novel-level semaphore
            // to avoid overwhelming sources with too many parallel requests.
            val searchAllStart = System.currentTimeMillis()
            coroutineScope {
                val novelSemaphore = Semaphore(MAX_CONCURRENT_NOVELS)
                val jobs: List<kotlinx.coroutines.Deferred<Unit>> = novels.map { novel ->
                    async(Dispatchers.IO) {
                        novelSemaphore.withPermit {
                            if (!isActive) return@withPermit
                            // Already resolved on a previous visit — nothing to redo.
                            if (getCachedResult(novel.id) != null) {
                                android.util.Log.i("MigrateNovel", "Novel ${novel.id} '${novel.title}' — CACHED, skipping")
                                return@withPermit
                            }
                            // Skip the old-chapter-count refresh for single-entry
                            // migration — it's an extra network call that only
                            // matters when comparing multiple candidates.
                            if (novelIds.size > 1) {
                                refreshOldChapterCount(novel)
                            }
                            searchForMatches(novel)
                        }
                    }
                }
                jobs.awaitAll()
            }
            android.util.Log.i("MigrateNovel", "=== All searches done in ${System.currentTimeMillis() - searchAllStart}ms ===")
            mutableState.update { it.copy(allDone = true) }
            android.util.Log.i("MigrateNovel", "=== init DONE total=${System.currentTimeMillis() - initStart}ms ===")
        }
    }

    /**
     * Fetch the old novel's *total* chapter count from its own source so it is
     * directly comparable to the candidates' counts. The locally-stored count
     * only covers chapters that have been synced, which understates the total.
     * Falls back to keeping the local count when the source can't be reached.
     */
    private suspend fun refreshOldChapterCount(oldNovel: Novel) {
        val source = sourceManager.get(oldNovel.source) as? NovelCatalogueSource ?: return
        val remoteCount = hardTimeout(CHAPTER_COUNT_TIMEOUT_MS) {
            source.getChapterList(oldNovel.toSNovel()).size
        } ?: 0
        if (remoteCount > 0) {
            oldChapterCountCache[oldNovel.id] = remoteCount
            updateItem(oldNovel.id) { it.copy(oldChapterCount = remoteCount) }
        }
    }

    private suspend fun searchForMatches(oldNovel: Novel) {
        val searchStart = System.currentTimeMillis()
        updateItem(oldNovel.id) { it.copy(status = MigrationStatus.Searching, searchPhase = "Finding sources...") }

        val sources = getEnabledSources().filter { it.id != oldNovel.source }
        android.util.Log.i("MigrateNovel", "searchForMatches('${oldNovel.title}') — ${sources.size} sources to search")
        if (sources.isEmpty()) {
            cacheResult(oldNovel.id, MigrationStatus.NotFound, null, 0)
            updateItem(oldNovel.id) { it.copy(status = MigrationStatus.NotFound, searchPhase = "") }
            android.util.Log.i("MigrateNovel", "searchForMatches — no sources, done in ${System.currentTimeMillis() - searchStart}ms")
            return
        }

        val normalizedTitle = normalizeTitle(oldNovel.title)

        // Phase 1: Search all sources in parallel
        updateItem(oldNovel.id) { it.copy(searchPhase = "Searching ${sources.size} sources...") }

        val isSingleEntry = novelIds.size <= 1
        val matches = Collections.synchronizedList(mutableListOf<MatchCandidate>())
        val searchedCount = java.util.concurrent.atomic.AtomicInteger(0)
        coroutineScope {
            val sourceSemaphore = if (isSingleEntry) null else Semaphore(MAX_CONCURRENT_SOURCES_PER_NOVEL)
            val sourceJobs: List<kotlinx.coroutines.Deferred<Unit>> = sources.map { source ->
                async(Dispatchers.IO) {
                    if (sourceSemaphore != null) sourceSemaphore.acquire()
                    try {
                        if (!isActive) return@async
                        // Hard timeout — uses CompletableDeferred race so blocking
                        // I/O can't overrun the timeout.
                        val searchReqStart = System.currentTimeMillis()
                        val results = hardTimeout(SEARCH_TIMEOUT_MS) {
                            source.getSearchNovels(1, normalizedTitle, source.getFilterList())
                        }
                        val searchMs = System.currentTimeMillis() - searchReqStart
                        val done = searchedCount.incrementAndGet()
                        updateItem(oldNovel.id) { it.copy(searchPhase = "Searching sources... ($done/${sources.size})") }

                        if (results == null) {
                            android.util.Log.w("MigrateNovel", "  [${source.name}] search TIMEOUT (${searchMs}ms)")
                            return@async
                        }
                        android.util.Log.i("MigrateNovel", "  [${source.name}] search=${searchMs}ms, ${results.novels.size} results")
                        val best = results.novels
                            .minByOrNull { result ->
                                titleDistance(result.title, oldNovel.title)
                            }
                        if (best == null) {
                            android.util.Log.i("MigrateNovel", "  [${source.name}] no best match from ${results.novels.size} results")
                            return@async
                        }
                        val dist = titleDistance(best.title, oldNovel.title)
                        android.util.Log.i("MigrateNovel", "  [${source.name}] best='${best.title}' dist=$dist")
                        val localNovel = networkToLocalNovel.await(best.toDomainNovel(source.id))
                        // Phase 2: Fetch chapter count — needed to pick the best source
                        val chapStart = System.currentTimeMillis()
                        val chapterCount = hardTimeout(CHAPTER_COUNT_TIMEOUT_MS) {
                            source.getChapterList(localNovel.toSNovel()).size
                        } ?: 0
                        val chapMs = System.currentTimeMillis() - chapStart
                        android.util.Log.i("MigrateNovel", "  [${source.name}] chapters=${chapterCount} (${chapMs}ms)")
                        if (chapterCount > 0) {
                            matches.add(MatchCandidate(localNovel, source, chapterCount))
                        }
                    } catch (_: Exception) {
                        android.util.Log.w("MigrateNovel", "  [${source.name}] EXCEPTION")
                    } finally {
                        sourceSemaphore?.release()
                    }
                }
            }
            sourceJobs.awaitAll()
        }

        // Phase 3: Sorting by chapter count
        updateItem(oldNovel.id) { it.copy(searchPhase = "Sorting by chapter count...") }

        // Recommend the source with the most chapters
        val sorted = matches.sortedByDescending { it.chapterCount }
        val recommended = sorted.firstOrNull()
        val alternatives = sorted.drop(1).map { it.novel }.toImmutableList()

        val status = if (recommended != null) MigrationStatus.Found else MigrationStatus.NotFound
        cacheResult(oldNovel.id, status, recommended?.novel, recommended?.chapterCount ?: 0)

        updateItem(oldNovel.id) {
            it.copy(
                status = status,
                recommendedNovel = recommended?.novel,
                recommendedChapterCount = recommended?.chapterCount ?: 0,
                alternatives = alternatives,
                searchPhase = "",
            )
        }
        android.util.Log.i("MigrateNovel", "searchForMatches('${oldNovel.title}') DONE in ${System.currentTimeMillis() - searchStart}ms — ${matches.size} matches, recommended=${recommended?.source?.name} (${recommended?.chapterCount} ch)")
    }

    private fun getEnabledSources(): List<NovelCatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
    }

    private fun updateItem(novelId: Long, transform: (MigrationItem) -> MigrationItem) {
        mutableState.update { state ->
            val newItems = state.items.map {
                if (it.oldNovel.id == novelId) transform(it) else it
            }.toImmutableList()
            state.copy(
                items = newItems,
                processed = newItems.count { it.status != MigrationStatus.Searching },
            )
        }
    }

    fun skip(novelId: Long) {
        cacheStatus(novelId, MigrationStatus.Skipped)
        updateItem(novelId) { it.copy(status = MigrationStatus.Skipped, searchPhase = "") }
    }

    fun markMigrated(novelId: Long) {
        cacheStatus(novelId, MigrationStatus.Migrated)
        updateItem(novelId) { it.copy(status = MigrationStatus.Migrated, searchPhase = "") }
    }

    /**
     * Normalize a title for cross-source searching. Removes common suffixes/prefixes
     * that differ between sources (e.g. "(LN)", "(Web Novel)", "—") and collapses
     * whitespace so titles from LibRead match FreeWebNovel's format.
     */
    private fun normalizeTitle(title: String): String {
        return title
            .replace(Regex("\\s*\\((LN|Web Novel|WN|Novel)\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[(LN|Web Novel|WN|Novel)\\]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*—\\s*.*$"), "") // em-dash subtitle
            .replace(Regex("\\s*:\\s*.*$"), "") // colon subtitle
            .replace(Regex("[^\\w\\s]"), " ") // remove special chars
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() } ?: title
    }

    private fun titleDistance(a: String, b: String): Int {
        val aNorm = a.lowercase().trim()
        val bNorm = b.lowercase().trim()
        return if (aNorm == bNorm) {
            0
        } else if (aNorm.contains(bNorm) || bNorm.contains(aNorm)) {
            kotlin.math.abs(aNorm.length - bNorm.length)
        } else {
            levenshtein(aNorm, bNorm)
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[a.length][b.length]
    }

    @Immutable
    data class MigrationItem(
        val oldNovel: Novel,
        val oldChapterCount: Int = 0,
        val status: MigrationStatus = MigrationStatus.Searching,
        val recommendedNovel: Novel? = null,
        val recommendedChapterCount: Int = 0,
        val alternatives: ImmutableList<Novel> = persistentListOf(),
        /** Human-readable search phase for UI display while searching. */
        val searchPhase: String = "",
    )

    enum class MigrationStatus {
        Searching,
        Found,
        NotFound,
        Skipped,
        Migrated,
    }

    @Immutable
    data class State(
        val items: ImmutableList<MigrationItem> = persistentListOf(),
        val total: Int = 0,
        val processed: Int = 0,
        val allDone: Boolean = false,
    )

    private data class MatchCandidate(
        val novel: Novel,
        val source: NovelCatalogueSource,
        val chapterCount: Int,
    )

    private data class CacheEntry(
        val status: MigrationStatus,
        val novel: Novel?,
        val chapterCount: Int,
        val timestamp: Long,
    )

    companion object {
        private const val MAX_CONCURRENT_NOVELS = 3
        private const val MAX_CONCURRENT_SOURCES_PER_NOVEL = 5
        // 5-second timeout for source search — all sources are searched in
        // parallel, and the one with the most chapters is recommended.
        private const val SEARCH_TIMEOUT_MS = 5_000L
        private const val CHAPTER_COUNT_TIMEOUT_MS = 5_000L
        private const val CACHE_TTL_MS = 60L * 60L * 1000L // 1 hour

        /**
         * Search results, keyed by novel id. Lives on the companion so results
         * survive leaving and re-entering the screen — the screen model is
         * recreated on every navigation, which would otherwise discard every
         * match and restart the whole search.
         */
        private val resultCache = Collections.synchronizedMap(mutableMapOf<Long, CacheEntry>())

        /** Source-reported chapter counts for the novels being migrated away from. */
        private val oldChapterCountCache = Collections.synchronizedMap(mutableMapOf<Long, Int>())

        private fun getCachedResult(novelId: Long): CacheEntry? {
            val entry = resultCache[novelId] ?: return null
            if (System.currentTimeMillis() - entry.timestamp > CACHE_TTL_MS) {
                resultCache.remove(novelId)
                return null
            }
            return entry
        }

        private fun cacheResult(novelId: Long, status: MigrationStatus, novel: Novel?, chapterCount: Int) {
            resultCache[novelId] = CacheEntry(status, novel, chapterCount, System.currentTimeMillis())
        }

        /** Update just the status of a cached entry, keeping the matched novel. */
        private fun cacheStatus(novelId: Long, status: MigrationStatus) {
            val existing = getCachedResult(novelId)
            resultCache[novelId] = CacheEntry(
                status = status,
                novel = existing?.novel,
                chapterCount = existing?.chapterCount ?: 0,
                timestamp = System.currentTimeMillis(),
            )
        }

        /**
         * Hard timeout that works even with blocking I/O.
         *
         * The blocking I/O runs in a fire-and-forget coroutine on Dispatchers.IO.
         * We race `result.await()` against `withTimeoutOrNull`. When the timeout
         * fires, `await()` is cancelled immediately (it's a proper suspension
         * point), and we return null. The background I/O continues running but
         * its result is discarded — it will complete eventually and be GC'd.
         *
         * This is necessary because `withTimeoutOrNull { blockingCall() }` does
         * NOT work for blocking I/O — coroutine cancellation is cooperative and
         * only takes effect at suspension points. A blocking OkHttp call has no
         * suspension points, so the timeout can't interrupt it.
         */
        private suspend fun <T> hardTimeout(timeoutMs: Long, block: suspend () -> T): T? {
            val result = CompletableDeferred<T?>()
            // Fire-and-forget — this scope is intentionally not cancelled on timeout.
            // The background job will complete eventually; we just don't wait for it.
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    result.complete(block())
                } catch (_: Exception) {
                    result.complete(null)
                }
            }
            return withTimeoutOrNull(timeoutMs) {
                result.await()
            }
        }
    }
}
