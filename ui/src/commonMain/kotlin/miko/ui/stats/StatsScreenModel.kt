package miko.ui.stats

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import miko.ui.MikoUiGraph
import miko.ui.carrier.BrowseCarrier
import miko.ui.download.DownloadManager
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED

/**
 * Android's per-carrier stats models (Manga/Shows/NovelStatsScreenModel),
 * folded into one model: overview (library/completed/duration), titles
 * (global-update/started/local), items (total/read/downloaded), trackers
 * (always logged-out zeros on iOS — tracker services arrive with Phase 32).
 */
class StatsScreenModel(
    private val graph: MikoUiGraph,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class CarrierStats(
        val carrier: BrowseCarrier,
        val libraryCount: Int,
        val completedCount: Int,
        /** Milliseconds spent reading/watching. */
        val totalDurationMs: Long,
        val globalUpdateCount: Int,
        val startedCount: Int,
        /** Entries on the device-local source (source id 0). */
        val localCount: Int,
        val totalItemCount: Int,
        val readItemCount: Int,
        val downloadCount: Int,
    )

    data class State(
        val initialized: Boolean = false,
        val carriers: List<CarrierStats> = emptyList(),
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            // Android's NovelStatsScreenModel backfills char counts for
            // chapters read before tracking existed — same call, same order.
            runCatching { graph.backfillNovelCharCounts.await() }
            val stats = BrowseCarrier.entries
                .map { carrier -> async { statsFor(carrier) } }
                .map { it.await() }
            _state.value = State(initialized = true, carriers = stats)
        }
    }

    private suspend fun statsFor(carrier: BrowseCarrier): CarrierStats = when (carrier) {
        BrowseCarrier.SHOWS -> showsStats()
        BrowseCarrier.MANGA -> mangaStats()
        BrowseCarrier.BOOKS -> novelStats()
    }

    private suspend fun showsStats(): CarrierStats {
        val library = graph.getLibraryAnime.subscribe().first()
        val distinct = library.distinctBy { it.id }
        var watchTime = 0L
        for (entry in distinct) {
            for (episode in graph.getEpisodesByAnimeId.await(entry.anime.id)) {
                watchTime += if (episode.seen) episode.totalSeconds else episode.lastSecondSeen
            }
        }
        val prefs = graph.libraryPreferences
        val includedCollections = prefs.showsUpdateCollections().get().map { it.toLong() }
        val included = if (includedCollections.isNotEmpty()) {
            library.filter { it.collection in includedCollections }
        } else {
            library
        }
        val excludedCollections = prefs.showsUpdateCollectionsExclude().get().map { it.toLong() }
        val excludedIds = if (excludedCollections.isNotEmpty()) {
            library.mapNotNull { entry -> entry.id.takeIf { entry.collection in excludedCollections } }
        } else {
            emptyList()
        }
        return CarrierStats(
            carrier = BrowseCarrier.SHOWS,
            libraryCount = distinct.size,
            completedCount = distinct.count {
                it.anime.status.toInt() == SAnime.COMPLETED && it.unseenCount == 0L
            },
            totalDurationMs = watchTime,
            globalUpdateCount = globalUpdateCount(
                included = included,
                excludedIds = excludedIds,
                id = { it.anime.id },
                isCompleted = { it.anime.status.toInt() == SAnime.COMPLETED },
                hasUnviewed = { it.unseenCount != 0L },
                notStarted = { it.totalCount > 0 && !it.hasStarted },
            ),
            startedCount = distinct.count { it.hasStarted },
            localCount = distinct.count { it.anime.isLocal() },
            totalItemCount = distinct.sumOf { it.totalCount }.toInt(),
            readItemCount = distinct.sumOf { it.seenCount }.toInt(),
            downloadCount = DownloadManager.downloadCount(BrowseCarrier.SHOWS),
        )
    }

    private suspend fun mangaStats(): CarrierStats {
        val library = graph.getLibraryManga.subscribe().first()
        val distinct = library.distinctBy { it.id }
        val prefs = graph.libraryPreferences
        val includedCollections = prefs.mangaUpdateCollections().get().map { it.toLong() }
        val included = if (includedCollections.isNotEmpty()) {
            library.filter { it.collection in includedCollections }
        } else {
            library
        }
        val excludedCollections = prefs.mangaUpdateCollectionsExclude().get().map { it.toLong() }
        val excludedIds = if (excludedCollections.isNotEmpty()) {
            library.mapNotNull { entry -> entry.id.takeIf { entry.collection in excludedCollections } }
        } else {
            emptyList()
        }
        return CarrierStats(
            carrier = BrowseCarrier.MANGA,
            libraryCount = distinct.size,
            completedCount = distinct.count {
                it.manga.status.toInt() == SManga.COMPLETED && it.unreadCount == 0L
            },
            totalDurationMs = graph.getTotalMangaReadDuration.await(),
            globalUpdateCount = globalUpdateCount(
                included = included,
                excludedIds = excludedIds,
                id = { it.manga.id },
                isCompleted = { it.manga.status.toInt() == SManga.COMPLETED },
                hasUnviewed = { it.unreadCount != 0L },
                notStarted = { it.totalChapters > 0 && !it.hasStarted },
            ),
            startedCount = distinct.count { it.hasStarted },
            localCount = distinct.count { it.manga.isLocal() },
            totalItemCount = distinct.sumOf { it.totalChapters }.toInt(),
            readItemCount = distinct.sumOf { it.readCount }.toInt(),
            downloadCount = DownloadManager.downloadCount(BrowseCarrier.MANGA),
        )
    }

    private suspend fun novelStats(): CarrierStats {
        val library = graph.getLibraryNovels.subscribe().first()
        val distinct = library.distinctBy { it.id }

        // Android's NovelStatsScreenModel: tracked session duration is often
        // far too low (chapters marked read outside the reader), so it falls
        // back to a ~250 wpm estimate when tracked time is under 30s per
        // read chapter.
        val trackedDuration = graph.getTotalNovelReadDuration.await()
        val totalReadCharCount = graph.getTotalReadNovelCharCount.await()
        val readChapterCount = distinct.sumOf { it.readCount }.toInt()
        val duration = if (readChapterCount > 0 &&
            totalReadCharCount > 0 &&
            trackedDuration < readChapterCount * 30_000L
        ) {
            (totalReadCharCount / 1250.0 * 60_000.0).toLong()
        } else {
            trackedDuration
        }
        return CarrierStats(
            carrier = BrowseCarrier.BOOKS,
            libraryCount = distinct.size,
            completedCount = distinct.count {
                it.novel.status.toInt() == SNovel.COMPLETED && it.unreadCount == 0L
            },
            totalDurationMs = duration,
            // Android's novel stats uses the plain library size here.
            globalUpdateCount = distinct.size,
            startedCount = distinct.count { it.hasStarted },
            localCount = 0,
            totalItemCount = distinct.sumOf { it.totalChapters }.toInt(),
            readItemCount = distinct.sumOf { it.readCount }.toInt(),
            downloadCount = DownloadManager.downloadCount(BrowseCarrier.BOOKS),
        )
    }

    /** Android's `getGlobalUpdateItemCount` — collection include/exclude
     *  plus the auto-update restriction set. */
    private fun <T> globalUpdateCount(
        included: List<T>,
        excludedIds: List<Long>,
        id: (T) -> Long,
        isCompleted: (T) -> Boolean,
        hasUnviewed: (T) -> Boolean,
        notStarted: (T) -> Boolean,
    ): Int {
        val restrictions = graph.libraryPreferences.autoUpdateItemRestrictions().get()
        return included
            .filterNot { id(it) in excludedIds }
            .distinctBy(id)
            .count {
                !(
                    (ENTRY_NON_COMPLETED in restrictions && isCompleted(it)) ||
                        (ENTRY_HAS_UNVIEWED in restrictions && hasUnviewed(it)) ||
                        (ENTRY_NON_VIEWED in restrictions && notStarted(it))
                    )
            }
    }

    /** Local source id (0) — `LocalMangaSource.ID`/`LocalAnimeSource.ID` are
     *  Android-only constants; the numeric id is the same contract here. */
    private fun Manga.isLocal(): Boolean = source == 0L
    private fun Anime.isLocal(): Boolean = source == 0L
    private fun Novel.isLocal(): Boolean = source == 0L

    fun dispose() = scope.cancel()
}
