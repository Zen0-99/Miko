package eu.kanade.tachiyomi.ui.stats.novel

import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.presentation.more.stats.data.StatsData
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.track.NovelTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.history.novel.interactor.GetTotalNovelReadDuration
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelStatsScreenModel(
    private val downloadManager: NovelDownloadManager = Injekt.get(),
    private val getLibraryNovels: GetLibraryNovels = Injekt.get(),
    private val getTotalReadDuration: GetTotalNovelReadDuration = Injekt.get(),
    private val getTracks: GetNovelTracks = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<StatsScreenState>(StatsScreenState.Loading) {

    private val loggedInTrackers by lazy { trackerManager.loggedInTrackers().filter { it is NovelTracker } }

    init {
        screenModelScope.launchIO {
            val libraryNovels = getLibraryNovels.await()

            val distinctLibraryNovels = libraryNovels.fastDistinctBy { it.id }

            val novelTrackMap = getNovelTrackMap(distinctLibraryNovels)
            val scoredNovelTrackerMap = getScoredNovelTrackMap(novelTrackMap)

            val meanScore = getTrackMeanScore(scoredNovelTrackerMap)

            val overviewStatData = StatsData.MangaOverview(
                libraryMangaCount = distinctLibraryNovels.size,
                completedMangaCount = distinctLibraryNovels.count {
                    it.novel.status.toInt() == SNovel.COMPLETED && it.unreadCount == 0L
                },
                totalReadDuration = getTotalReadDuration.await(),
            )

            val titlesStatData = StatsData.MangaTitles(
                globalUpdateItemCount = distinctLibraryNovels.size,
                startedMangaCount = distinctLibraryNovels.count { it.hasStarted },
                localMangaCount = 0,
            )

            val chaptersStatData = StatsData.Chapters(
                totalChapterCount = distinctLibraryNovels.sumOf { it.totalChapters }.toInt(),
                readChapterCount = distinctLibraryNovels.sumOf { it.readCount }.toInt(),
                downloadCount = downloadManager.getDownloadCount(),
            )

            val trackersStatData = StatsData.Trackers(
                trackedTitleCount = novelTrackMap.count { it.value.isNotEmpty() },
                meanScore = meanScore,
                trackerCount = loggedInTrackers.size,
            )

            mutableState.update {
                StatsScreenState.SuccessManga(
                    overview = overviewStatData,
                    titles = titlesStatData,
                    chapters = chaptersStatData,
                    trackers = trackersStatData,
                )
            }
        }
    }

    private suspend fun getNovelTrackMap(libraryNovels: List<LibraryNovel>): Map<Long, List<tachiyomi.domain.track.novel.model.NovelTrack>> {
        val loggedInTrackerIds = loggedInTrackers.map { it.id }.toHashSet()
        return libraryNovels.associate { novel ->
            val tracks = getTracks.await(novel.id)
                .fastFilter { it.trackerId in loggedInTrackerIds }

            novel.id to tracks
        }
    }

    private fun getScoredNovelTrackMap(novelTrackMap: Map<Long, List<tachiyomi.domain.track.novel.model.NovelTrack>>): Map<Long, List<tachiyomi.domain.track.novel.model.NovelTrack>> {
        return novelTrackMap.mapNotNull { (novelId, tracks) ->
            val trackList = tracks.mapNotNull { track ->
                track.takeIf { it.score > 0.0 }
            }
            if (trackList.isEmpty()) return@mapNotNull null
            novelId to trackList
        }.toMap()
    }

    private fun getTrackMeanScore(scoredNovelTrackMap: Map<Long, List<tachiyomi.domain.track.novel.model.NovelTrack>>): Double {
        return scoredNovelTrackMap
            .map { (_, tracks) ->
                tracks.map { it.score }.average()
            }
            .fastFilter { !it.isNaN() }
            .average()
    }
}
