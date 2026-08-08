package eu.kanade.tachiyomi.ui.browse.manga.migration.all

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
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
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Collections

/**
 * Mass-migration ScreenModel for manga. For each favorite manga from the source extension
 * being migrated away from, searches all other enabled catalogue sources for a title match,
 * fetches the chapter count for each match, and picks the source with the most chapters
 * as the recommended migration target.
 *
 * Mirrors MigrateNovelAllScreenModel — see that class for design notes.
 */
class MigrateMangaAllScreenModel(
    private val mangaIds: List<Long>,
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getMangaWithChapters: GetMangaWithChapters = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<MigrateMangaAllScreenModel.State>(State()) {

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledMangaSources().get()

    init {
        screenModelScope.launch {
            val initStart = System.currentTimeMillis()
            android.util.Log.i("MigrateManga", "=== init START === mangaIds=${mangaIds.size}")

            val loadStart = System.currentTimeMillis()
            val mangas = mangaIds.mapNotNull { getManga.await(it) }
            android.util.Log.i("MigrateManga", "getManga.await took ${System.currentTimeMillis() - loadStart}ms for ${mangas.size} mangas")

            val items = mangas.map { manga ->
                val cached = getCachedResult(manga.id)
                val localCount = try {
                    getMangaWithChapters.awaitChapters(manga.id).size
                } catch (_: Exception) { 0 }
                MigrationItem(
                    oldManga = manga,
                    oldChapterCount = oldChapterCountCache[manga.id] ?: localCount,
                    status = cached?.status ?: MigrationStatus.Searching,
                    recommendedManga = cached?.manga,
                    recommendedChapterCount = cached?.chapterCount ?: 0,
                    searchPhase = if (cached != null) "" else "Starting...",
                )
            }.toImmutableList()
            mutableState.update {
                it.copy(
                    items = items,
                    total = mangas.size,
                    processed = items.count { item -> item.status != MigrationStatus.Searching },
                )
            }
            android.util.Log.i("MigrateManga", "State initialized in ${System.currentTimeMillis() - loadStart}ms")

            val searchAllStart = System.currentTimeMillis()
            coroutineScope {
                val mangaSemaphore = Semaphore(MAX_CONCURRENT_MANGA)
                val jobs: List<kotlinx.coroutines.Deferred<Unit>> = mangas.map { manga ->
                    async(Dispatchers.IO) {
                        mangaSemaphore.withPermit {
                            if (!isActive) return@withPermit
                            if (getCachedResult(manga.id) != null) {
                                android.util.Log.i("MigrateManga", "Manga ${manga.id} '${manga.title}' — CACHED, skipping")
                                return@withPermit
                            }
                            if (mangaIds.size > 1) {
                                refreshOldChapterCount(manga)
                            }
                            searchForMatches(manga)
                        }
                    }
                }
                jobs.awaitAll()
            }
            android.util.Log.i("MigrateManga", "=== All searches done in ${System.currentTimeMillis() - searchAllStart}ms ===")
            mutableState.update { it.copy(allDone = true) }
            android.util.Log.i("MigrateManga", "=== init DONE total=${System.currentTimeMillis() - initStart}ms ===")
        }
    }

    private suspend fun refreshOldChapterCount(oldManga: Manga) {
        val source = sourceManager.get(oldManga.source) as? CatalogueSource ?: return
        val remoteCount = hardTimeout(CHAPTER_COUNT_TIMEOUT_MS) {
            source.getChapterList(oldManga.toSManga()).size
        } ?: 0
        if (remoteCount > 0) {
            oldChapterCountCache[oldManga.id] = remoteCount
            updateItem(oldManga.id) { it.copy(oldChapterCount = remoteCount) }
        }
    }

    private suspend fun searchForMatches(oldManga: Manga) {
        val searchStart = System.currentTimeMillis()
        updateItem(oldManga.id) { it.copy(status = MigrationStatus.Searching, searchPhase = "Finding sources...") }

        val sources = getEnabledSources().filter { it.id != oldManga.source }
        android.util.Log.i("MigrateManga", "searchForMatches('${oldManga.title}') — ${sources.size} sources to search")
        if (sources.isEmpty()) {
            cacheResult(oldManga.id, MigrationStatus.NotFound, null, 0)
            updateItem(oldManga.id) { it.copy(status = MigrationStatus.NotFound, searchPhase = "") }
            android.util.Log.i("MigrateManga", "searchForMatches — no sources, done in ${System.currentTimeMillis() - searchStart}ms")
            return
        }

        val normalizedTitle = normalizeTitle(oldManga.title)

        updateItem(oldManga.id) { it.copy(searchPhase = "Searching ${sources.size} sources...") }

        val isSingleEntry = mangaIds.size <= 1
        val matches = Collections.synchronizedList(mutableListOf<MatchCandidate>())
        val searchedCount = java.util.concurrent.atomic.AtomicInteger(0)
        coroutineScope {
            val sourceSemaphore = if (isSingleEntry) null else Semaphore(MAX_CONCURRENT_SOURCES_PER_MANGA)
            val sourceJobs: List<kotlinx.coroutines.Deferred<Unit>> = sources.map { source ->
                async(Dispatchers.IO) {
                    if (sourceSemaphore != null) sourceSemaphore.acquire()
                    try {
                        if (!isActive) return@async
                        val searchReqStart = System.currentTimeMillis()
                        val results = hardTimeout(SEARCH_TIMEOUT_MS) {
                            source.getSearchManga(1, normalizedTitle, source.getFilterList())
                        }
                        val searchMs = System.currentTimeMillis() - searchReqStart
                        val done = searchedCount.incrementAndGet()
                        updateItem(oldManga.id) { it.copy(searchPhase = "Searching sources... ($done/${sources.size})") }

                        if (results == null) {
                            android.util.Log.w("MigrateManga", "  [${source.name}] search TIMEOUT (${searchMs}ms)")
                            return@async
                        }
                        android.util.Log.i("MigrateManga", "  [${source.name}] search=${searchMs}ms, ${results.mangas.size} results")
                        val best = results.mangas
                            .minByOrNull { result ->
                                titleDistance(result.title, oldManga.title)
                            }
                        if (best == null) {
                            android.util.Log.i("MigrateManga", "  [${source.name}] no best match from ${results.mangas.size} results")
                            return@async
                        }
                        val dist = titleDistance(best.title, oldManga.title)
                        android.util.Log.i("MigrateManga", "  [${source.name}] best='${best.title}' dist=$dist")
                        val localManga = networkToLocalManga.await(best.toDomainManga(source.id))
                        val chapStart = System.currentTimeMillis()
                        val chapterCount = hardTimeout(CHAPTER_COUNT_TIMEOUT_MS) {
                            source.getChapterList(localManga.toSManga()).size
                        } ?: 0
                        val chapMs = System.currentTimeMillis() - chapStart
                        android.util.Log.i("MigrateManga", "  [${source.name}] chapters=${chapterCount} (${chapMs}ms)")
                        if (chapterCount > 0) {
                            matches.add(MatchCandidate(localManga, source, chapterCount))
                        }
                    } catch (_: Exception) {
                        android.util.Log.w("MigrateManga", "  [${source.name}] EXCEPTION")
                    } finally {
                        sourceSemaphore?.release()
                    }
                }
            }
            sourceJobs.awaitAll()
        }

        updateItem(oldManga.id) { it.copy(searchPhase = "Sorting by chapter count...") }

        val sorted = matches.sortedByDescending { it.chapterCount }
        val recommended = sorted.firstOrNull()
        val alternatives = sorted.drop(1).map { it.manga }.toImmutableList()

        val status = if (recommended != null) MigrationStatus.Found else MigrationStatus.NotFound
        cacheResult(oldManga.id, status, recommended?.manga, recommended?.chapterCount ?: 0)

        updateItem(oldManga.id) {
            it.copy(
                status = status,
                recommendedManga = recommended?.manga,
                recommendedChapterCount = recommended?.chapterCount ?: 0,
                alternatives = alternatives,
                searchPhase = "",
            )
        }
        android.util.Log.i("MigrateManga", "searchForMatches('${oldManga.title}') DONE in ${System.currentTimeMillis() - searchStart}ms — ${matches.size} matches, recommended=${recommended?.source?.name} (${recommended?.chapterCount} ch)")
    }

    private fun getEnabledSources(): List<CatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
    }

    private fun updateItem(mangaId: Long, transform: (MigrationItem) -> MigrationItem) {
        mutableState.update { state ->
            val newItems = state.items.map {
                if (it.oldManga.id == mangaId) transform(it) else it
            }.toImmutableList()
            state.copy(
                items = newItems,
                processed = newItems.count { it.status != MigrationStatus.Searching },
            )
        }
    }

    fun skip(mangaId: Long) {
        cacheStatus(mangaId, MigrationStatus.Skipped)
        updateItem(mangaId) { it.copy(status = MigrationStatus.Skipped, searchPhase = "") }
    }

    fun markMigrated(mangaId: Long) {
        cacheStatus(mangaId, MigrationStatus.Migrated)
        updateItem(mangaId) { it.copy(status = MigrationStatus.Migrated, searchPhase = "") }
    }

    private fun normalizeTitle(title: String): String {
        return title
            .replace(Regex("\\s*\\((LN|Web Novel|WN|Novel|Manga|Manhwa|Manhua)\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[(LN|Web Novel|WN|Novel|Manga|Manhwa|Manhua)\\]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*—\\s*.*$"), "")
            .replace(Regex("\\s*:\\s*.*$"), "")
            .replace(Regex("[^\\w\\s]"), " ")
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
        val oldManga: Manga,
        val oldChapterCount: Int = 0,
        val status: MigrationStatus = MigrationStatus.Searching,
        val recommendedManga: Manga? = null,
        val recommendedChapterCount: Int = 0,
        val alternatives: ImmutableList<Manga> = persistentListOf(),
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
        val manga: Manga,
        val source: CatalogueSource,
        val chapterCount: Int,
    )

    private data class CacheEntry(
        val status: MigrationStatus,
        val manga: Manga?,
        val chapterCount: Int,
        val timestamp: Long,
    )

    companion object {
        private const val MAX_CONCURRENT_MANGA = 3
        private const val MAX_CONCURRENT_SOURCES_PER_MANGA = 5
        private const val SEARCH_TIMEOUT_MS = 5_000L
        private const val CHAPTER_COUNT_TIMEOUT_MS = 5_000L
        private const val CACHE_TTL_MS = 60L * 60L * 1000L // 1 hour

        private val resultCache = Collections.synchronizedMap(mutableMapOf<Long, CacheEntry>())
        private val oldChapterCountCache = Collections.synchronizedMap(mutableMapOf<Long, Int>())

        private fun getCachedResult(mangaId: Long): CacheEntry? {
            val entry = resultCache[mangaId] ?: return null
            if (System.currentTimeMillis() - entry.timestamp > CACHE_TTL_MS) {
                resultCache.remove(mangaId)
                return null
            }
            return entry
        }

        private fun cacheResult(mangaId: Long, status: MigrationStatus, manga: Manga?, chapterCount: Int) {
            resultCache[mangaId] = CacheEntry(status, manga, chapterCount, System.currentTimeMillis())
        }

        private fun cacheStatus(mangaId: Long, status: MigrationStatus) {
            val existing = getCachedResult(mangaId)
            resultCache[mangaId] = CacheEntry(
                status = status,
                manga = existing?.manga,
                chapterCount = existing?.chapterCount ?: 0,
                timestamp = System.currentTimeMillis(),
            )
        }

        private suspend fun <T> hardTimeout(timeoutMs: Long, block: suspend () -> T): T? {
            val result = CompletableDeferred<T?>()
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
