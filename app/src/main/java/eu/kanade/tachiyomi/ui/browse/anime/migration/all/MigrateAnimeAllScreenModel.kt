package eu.kanade.tachiyomi.ui.browse.anime.migration.all

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
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
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Collections

/**
 * Mass-migration ScreenModel for anime. For each favorite anime from the source extension
 * being migrated away from, searches all other enabled catalogue sources for a title match,
 * fetches the episode count for each match, and picks the source with the most episodes
 * as the recommended migration target.
 *
 * Mirrors MigrateNovelAllScreenModel — see that class for design notes.
 */
class MigrateAnimeAllScreenModel(
    private val animeIds: List<Long>,
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getAnimeWithEpisodes: GetAnimeWithEpisodesAndSeasons = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<MigrateAnimeAllScreenModel.State>(State()) {

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledAnimeSources().get()

    init {
        screenModelScope.launch {
            val initStart = System.currentTimeMillis()
            android.util.Log.i("MigrateAnime", "=== init START === animeIds=${animeIds.size}")

            val loadStart = System.currentTimeMillis()
            val animes = animeIds.mapNotNull { getAnime.await(it) }
            android.util.Log.i("MigrateAnime", "getAnime.await took ${System.currentTimeMillis() - loadStart}ms for ${animes.size} animes")

            val items = animes.map { anime ->
                val cached = getCachedResult(anime.id)
                val localCount = try {
                    getAnimeWithEpisodes.awaitEpisodes(anime.id).size
                } catch (_: Exception) { 0 }
                MigrationItem(
                    oldAnime = anime,
                    oldEpisodeCount = oldEpisodeCountCache[anime.id] ?: localCount,
                    status = cached?.status ?: MigrationStatus.Searching,
                    recommendedAnime = cached?.anime,
                    recommendedEpisodeCount = cached?.episodeCount ?: 0,
                    searchPhase = if (cached != null) "" else "Starting...",
                )
            }.toImmutableList()
            mutableState.update {
                it.copy(
                    items = items,
                    total = animes.size,
                    processed = items.count { item -> item.status != MigrationStatus.Searching },
                )
            }
            android.util.Log.i("MigrateAnime", "State initialized in ${System.currentTimeMillis() - loadStart}ms")

            val searchAllStart = System.currentTimeMillis()
            coroutineScope {
                val animeSemaphore = Semaphore(MAX_CONCURRENT_ANIME)
                val jobs: List<kotlinx.coroutines.Deferred<Unit>> = animes.map { anime ->
                    async(Dispatchers.IO) {
                        animeSemaphore.withPermit {
                            if (!isActive) return@withPermit
                            if (getCachedResult(anime.id) != null) {
                                android.util.Log.i("MigrateAnime", "Anime ${anime.id} '${anime.title}' — CACHED, skipping")
                                return@withPermit
                            }
                            if (animeIds.size > 1) {
                                refreshOldEpisodeCount(anime)
                            }
                            searchForMatches(anime)
                        }
                    }
                }
                jobs.awaitAll()
            }
            android.util.Log.i("MigrateAnime", "=== All searches done in ${System.currentTimeMillis() - searchAllStart}ms ===")
            mutableState.update { it.copy(allDone = true) }
            android.util.Log.i("MigrateAnime", "=== init DONE total=${System.currentTimeMillis() - initStart}ms ===")
        }
    }

    private suspend fun refreshOldEpisodeCount(oldAnime: Anime) {
        val source = sourceManager.get(oldAnime.source) as? AnimeCatalogueSource ?: return
        val remoteCount = hardTimeout(EPISODE_COUNT_TIMEOUT_MS) {
            source.getEpisodeList(oldAnime.toSAnime()).size
        } ?: 0
        if (remoteCount > 0) {
            oldEpisodeCountCache[oldAnime.id] = remoteCount
            updateItem(oldAnime.id) { it.copy(oldEpisodeCount = remoteCount) }
        }
    }

    private suspend fun searchForMatches(oldAnime: Anime) {
        val searchStart = System.currentTimeMillis()
        updateItem(oldAnime.id) { it.copy(status = MigrationStatus.Searching, searchPhase = "Finding sources...") }

        val sources = getEnabledSources().filter { it.id != oldAnime.source }
        android.util.Log.i("MigrateAnime", "searchForMatches('${oldAnime.title}') — ${sources.size} sources to search")
        if (sources.isEmpty()) {
            cacheResult(oldAnime.id, MigrationStatus.NotFound, null, 0)
            updateItem(oldAnime.id) { it.copy(status = MigrationStatus.NotFound, searchPhase = "") }
            android.util.Log.i("MigrateAnime", "searchForMatches — no sources, done in ${System.currentTimeMillis() - searchStart}ms")
            return
        }

        val normalizedTitle = normalizeTitle(oldAnime.title)

        updateItem(oldAnime.id) { it.copy(searchPhase = "Searching ${sources.size} sources...") }

        val isSingleEntry = animeIds.size <= 1
        val matches = Collections.synchronizedList(mutableListOf<MatchCandidate>())
        val searchedCount = java.util.concurrent.atomic.AtomicInteger(0)
        coroutineScope {
            val sourceSemaphore = if (isSingleEntry) null else Semaphore(MAX_CONCURRENT_SOURCES_PER_ANIME)
            val sourceJobs: List<kotlinx.coroutines.Deferred<Unit>> = sources.map { source ->
                async(Dispatchers.IO) {
                    if (sourceSemaphore != null) sourceSemaphore.acquire()
                    try {
                        if (!isActive) return@async
                        val searchReqStart = System.currentTimeMillis()
                        val results = hardTimeout(SEARCH_TIMEOUT_MS) {
                            source.getSearchAnime(1, normalizedTitle, source.getFilterList())
                        }
                        val searchMs = System.currentTimeMillis() - searchReqStart
                        val done = searchedCount.incrementAndGet()
                        updateItem(oldAnime.id) { it.copy(searchPhase = "Searching sources... ($done/${sources.size})") }

                        if (results == null) {
                            android.util.Log.w("MigrateAnime", "  [${source.name}] search TIMEOUT (${searchMs}ms)")
                            return@async
                        }
                        android.util.Log.i("MigrateAnime", "  [${source.name}] search=${searchMs}ms, ${results.animes.size} results")
                        val best = results.animes
                            .minByOrNull { result ->
                                titleDistance(result.title, oldAnime.title)
                            }
                        if (best == null) {
                            android.util.Log.i("MigrateAnime", "  [${source.name}] no best match from ${results.animes.size} results")
                            return@async
                        }
                        val dist = titleDistance(best.title, oldAnime.title)
                        android.util.Log.i("MigrateAnime", "  [${source.name}] best='${best.title}' dist=$dist")
                        val localAnime = networkToLocalAnime.await(best.toDomainAnime(source.id))
                        val epStart = System.currentTimeMillis()
                        val episodeCount = hardTimeout(EPISODE_COUNT_TIMEOUT_MS) {
                            source.getEpisodeList(localAnime.toSAnime()).size
                        } ?: 0
                        val epMs = System.currentTimeMillis() - epStart
                        android.util.Log.i("MigrateAnime", "  [${source.name}] episodes=${episodeCount} (${epMs}ms)")
                        if (episodeCount > 0) {
                            matches.add(MatchCandidate(localAnime, source, episodeCount))
                        }
                    } catch (_: Exception) {
                        android.util.Log.w("MigrateAnime", "  [${source.name}] EXCEPTION")
                    } finally {
                        sourceSemaphore?.release()
                    }
                }
            }
            sourceJobs.awaitAll()
        }

        updateItem(oldAnime.id) { it.copy(searchPhase = "Sorting by episode count...") }

        val sorted = matches.sortedByDescending { it.episodeCount }
        val recommended = sorted.firstOrNull()
        val alternatives = sorted.drop(1).map { it.anime }.toImmutableList()

        val status = if (recommended != null) MigrationStatus.Found else MigrationStatus.NotFound
        cacheResult(oldAnime.id, status, recommended?.anime, recommended?.episodeCount ?: 0)

        updateItem(oldAnime.id) {
            it.copy(
                status = status,
                recommendedAnime = recommended?.anime,
                recommendedEpisodeCount = recommended?.episodeCount ?: 0,
                alternatives = alternatives,
                searchPhase = "",
            )
        }
        android.util.Log.i("MigrateAnime", "searchForMatches('${oldAnime.title}') DONE in ${System.currentTimeMillis() - searchStart}ms — ${matches.size} matches, recommended=${recommended?.source?.name} (${recommended?.episodeCount} ep)")
    }

    private fun getEnabledSources(): List<AnimeCatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
    }

    private fun updateItem(animeId: Long, transform: (MigrationItem) -> MigrationItem) {
        mutableState.update { state ->
            val newItems = state.items.map {
                if (it.oldAnime.id == animeId) transform(it) else it
            }.toImmutableList()
            state.copy(
                items = newItems,
                processed = newItems.count { it.status != MigrationStatus.Searching },
            )
        }
    }

    fun skip(animeId: Long) {
        cacheStatus(animeId, MigrationStatus.Skipped)
        updateItem(animeId) { it.copy(status = MigrationStatus.Skipped, searchPhase = "") }
    }

    fun markMigrated(animeId: Long) {
        cacheStatus(animeId, MigrationStatus.Migrated)
        updateItem(animeId) { it.copy(status = MigrationStatus.Migrated, searchPhase = "") }
    }

    private fun normalizeTitle(title: String): String {
        return title
            .replace(Regex("\\s*\\((TV|OVA|ONA|Movie|Web|LN|Novel)\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[(TV|OVA|ONA|Movie|Web|LN|Novel)\\]\\s*", RegexOption.IGNORE_CASE), "")
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
        val oldAnime: Anime,
        val oldEpisodeCount: Int = 0,
        val status: MigrationStatus = MigrationStatus.Searching,
        val recommendedAnime: Anime? = null,
        val recommendedEpisodeCount: Int = 0,
        val alternatives: ImmutableList<Anime> = persistentListOf(),
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
        val anime: Anime,
        val source: AnimeCatalogueSource,
        val episodeCount: Int,
    )

    private data class CacheEntry(
        val status: MigrationStatus,
        val anime: Anime?,
        val episodeCount: Int,
        val timestamp: Long,
    )

    companion object {
        private const val MAX_CONCURRENT_ANIME = 3
        private const val MAX_CONCURRENT_SOURCES_PER_ANIME = 5
        private const val SEARCH_TIMEOUT_MS = 5_000L
        private const val EPISODE_COUNT_TIMEOUT_MS = 5_000L
        private const val CACHE_TTL_MS = 60L * 60L * 1000L // 1 hour

        private val resultCache = Collections.synchronizedMap(mutableMapOf<Long, CacheEntry>())
        private val oldEpisodeCountCache = Collections.synchronizedMap(mutableMapOf<Long, Int>())

        private fun getCachedResult(animeId: Long): CacheEntry? {
            val entry = resultCache[animeId] ?: return null
            if (System.currentTimeMillis() - entry.timestamp > CACHE_TTL_MS) {
                resultCache.remove(animeId)
                return null
            }
            return entry
        }

        private fun cacheResult(animeId: Long, status: MigrationStatus, anime: Anime?, episodeCount: Int) {
            resultCache[animeId] = CacheEntry(status, anime, episodeCount, System.currentTimeMillis())
        }

        private fun cacheStatus(animeId: Long, status: MigrationStatus) {
            val existing = getCachedResult(animeId)
            resultCache[animeId] = CacheEntry(
                status = status,
                anime = existing?.anime,
                episodeCount = existing?.episodeCount ?: 0,
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
