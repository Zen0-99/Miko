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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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
 * Performance optimizations modeled on Miko-Yokai-Old's MigrationListController:
 * - Two-level semaphore parallelization (novel-level + source-level)
 * - In-memory result cache (1 hour TTL) to skip re-searching on re-entry
 * - Shorter timeouts (search: 15s, chapter count: 5s)
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
            // Load all novels, restoring any results cached from a previous visit
            // to this screen so re-entering doesn't restart every search.
            val novels = novelIds.mapNotNull { getNovel.await(it) }
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
                )
            }.toImmutableList()
            mutableState.update {
                it.copy(
                    items = items,
                    total = novels.size,
                    processed = items.count { item -> item.status != MigrationStatus.Searching },
                )
            }
            // Search for matches concurrently with novel-level semaphore
            // to avoid overwhelming sources with too many parallel requests.
            coroutineScope {
                val novelSemaphore = Semaphore(MAX_CONCURRENT_NOVELS)
                val jobs: List<kotlinx.coroutines.Deferred<Unit>> = novels.map { novel ->
                    async(Dispatchers.IO) {
                        novelSemaphore.withPermit {
                            if (!isActive) return@withPermit
                            // Already resolved on a previous visit — nothing to redo.
                            if (getCachedResult(novel.id) != null) return@withPermit
                            refreshOldChapterCount(novel)
                            searchForMatches(novel)
                        }
                    }
                }
                jobs.awaitAll()
            }
            mutableState.update { it.copy(allDone = true) }
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
        val remoteCount = try {
            withTimeoutOrNull(CHAPTER_COUNT_TIMEOUT_MS) {
                source.getChapterList(oldNovel.toSNovel()).size
            } ?: 0
        } catch (_: Exception) {
            0
        }
        if (remoteCount > 0) {
            oldChapterCountCache[oldNovel.id] = remoteCount
            updateItem(oldNovel.id) { it.copy(oldChapterCount = remoteCount) }
        }
    }

    private suspend fun searchForMatches(oldNovel: Novel) {
        updateItem(oldNovel.id) { it.copy(status = MigrationStatus.Searching) }

        val sources = getEnabledSources().filter { it.id != oldNovel.source }
        if (sources.isEmpty()) {
            cacheResult(oldNovel.id, MigrationStatus.NotFound, null, 0)
            updateItem(oldNovel.id) { it.copy(status = MigrationStatus.NotFound) }
            return
        }

        val normalizedTitle = normalizeTitle(oldNovel.title)
        val matches = Collections.synchronizedList(mutableListOf<MatchCandidate>())
        coroutineScope {
            val sourceSemaphore = Semaphore(MAX_CONCURRENT_SOURCES_PER_NOVEL)
            val sourceJobs: List<kotlinx.coroutines.Deferred<Unit>> = sources.map { source ->
                async(Dispatchers.IO) {
                    sourceSemaphore.withPermit {
                        if (!isActive) return@async
                        try {
                            // Shorter timeout — 15s instead of 60s
                            val results = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                                source.getSearchNovels(1, normalizedTitle, source.getFilterList())
                            } ?: return@async
                            // Pick the closest title match from the first page of results
                            val best = results.novels
                                .minByOrNull { result ->
                                    titleDistance(result.title, oldNovel.title)
                                } ?: return@async
                            val localNovel = networkToLocalNovel.await(best.toDomainNovel(source.id))
                            // Fetch chapter count with shorter timeout — 5s instead of 30s
                            val chapterCount = try {
                                withTimeoutOrNull(CHAPTER_COUNT_TIMEOUT_MS) {
                                    source.getChapterList(localNovel.toSNovel()).size
                                } ?: 0
                            } catch (_: Exception) {
                                0
                            }
                            if (chapterCount > 0) {
                                matches.add(MatchCandidate(localNovel, source, chapterCount))
                            }
                        } catch (_: Exception) {
                            // Skip failing sources
                        }
                    }
                }
            }
            sourceJobs.awaitAll()
        }

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
            )
        }
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
        updateItem(novelId) { it.copy(status = MigrationStatus.Skipped) }
    }

    fun markMigrated(novelId: Long) {
        cacheStatus(novelId, MigrationStatus.Migrated)
        updateItem(novelId) { it.copy(status = MigrationStatus.Migrated) }
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
        private const val MAX_CONCURRENT_SOURCES_PER_NOVEL = 3
        private const val SEARCH_TIMEOUT_MS = 15_000L
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
    }
}
