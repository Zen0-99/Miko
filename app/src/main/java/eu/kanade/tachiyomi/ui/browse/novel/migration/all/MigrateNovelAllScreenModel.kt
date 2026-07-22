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
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Mass-migration ScreenModel. For each favorite novel from the source extension being
 * migrated away from, searches all other enabled catalogue sources for a title match,
 * fetches the chapter count for each match, and picks the source with the most chapters
 * as the recommended migration target.
 *
 * Modeled on Miko-Yokai-Old's MigrationListController "useSourceWithMost" logic.
 */
class MigrateNovelAllScreenModel(
    private val novelIds: List<Long>,
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<MigrateNovelAllScreenModel.State>(State()) {

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledNovelSources().get()

    init {
        screenModelScope.launch {
            // Load all novels first
            val novels = novelIds.mapNotNull { getNovel.await(it) }
            mutableState.update {
                it.copy(
                    items = novels.map { MigrationItem(oldNovel = it) }.toImmutableList(),
                    total = novels.size,
                )
            }
            // Search for matches concurrently (semaphore limits source pressure)
            coroutineScope {
                val jobs: List<kotlinx.coroutines.Deferred<Unit>> = novels.map { novel ->
                    async(Dispatchers.IO) {
                        if (!isActive) return@async
                        searchForMatches(novel)
                    }
                }
                jobs.awaitAll()
            }
            mutableState.update { it.copy(allDone = true) }
        }
    }

    private suspend fun searchForMatches(oldNovel: Novel) {
        updateItem(oldNovel.id) { it.copy(status = MigrationStatus.Searching) }

        val sources = getEnabledSources().filter { it.id != oldNovel.source }
        if (sources.isEmpty()) {
            updateItem(oldNovel.id) { it.copy(status = MigrationStatus.NotFound) }
            return
        }

        val matches = mutableListOf<MatchCandidate>()
        coroutineScope {
            val sourceSemaphore = Semaphore(3)
            val sourceJobs: List<kotlinx.coroutines.Deferred<Unit>> = sources.map { source ->
                async(Dispatchers.IO) {
                    sourceSemaphore.withPermit {
                        if (!isActive) return@async
                        try {
                            val results = withTimeoutOrNull(60_000L) {
                                source.getSearchNovels(1, oldNovel.title, source.getFilterList())
                            } ?: return@async
                            // Pick the closest title match from the first page of results
                            val best = results.novels
                                .minByOrNull { result ->
                                    titleDistance(result.title, oldNovel.title)
                                } ?: return@async
                            val localNovel = networkToLocalNovel.await(best.toDomainNovel(source.id))
                            // Fetch chapter count to rank candidates
                            val chapterCount = try {
                                withTimeoutOrNull(30_000L) {
                                    source.getChapterList(localNovel.toSNovel()).size
                                } ?: 0
                            } catch (_: Exception) {
                                0
                            }
                            if (chapterCount > 0) {
                                synchronized(matches) {
                                    matches.add(MatchCandidate(localNovel, source, chapterCount))
                                }
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

        updateItem(oldNovel.id) {
            it.copy(
                status = if (recommended != null) MigrationStatus.Found else MigrationStatus.NotFound,
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
        updateItem(novelId) { it.copy(status = MigrationStatus.Skipped) }
    }

    fun markMigrated(novelId: Long) {
        updateItem(novelId) { it.copy(status = MigrationStatus.Migrated) }
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
    data class MatchCandidate(
        val novel: Novel,
        val source: NovelCatalogueSource,
        val chapterCount: Int,
    )

    @Immutable
    data class State(
        val items: ImmutableList<MigrationItem> = persistentListOf(),
        val total: Int = 0,
        val processed: Int = 0,
        val allDone: Boolean = false,
    )
}
