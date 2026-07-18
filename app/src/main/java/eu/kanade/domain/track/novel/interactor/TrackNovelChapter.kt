package eu.kanade.domain.track.novel.interactor

import eu.kanade.domain.track.novel.model.toDbTrack
import eu.kanade.domain.track.novel.model.toNovelTrack
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack

class TrackNovelChapter(
    private val getTracks: GetNovelTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertNovelTrack,
) {

    suspend fun await(novelId: Long, chapterNumber: Double) {
        withNonCancellableContext {
            val tracks = getTracks.await(novelId)
            if (tracks.isEmpty()) return@withNonCancellableContext

            tracks.mapNotNull { track ->
                val service = trackerManager.get(track.trackerId)
                if (service == null || !service.isLoggedIn || chapterNumber <= track.lastChapterRead) {
                    return@mapNotNull null
                }

                val mangaService = service.mangaService

                async {
                    runCatching {
                        val updatedTrack = mangaService.refresh(track.toDbTrack())
                            .toNovelTrack(idRequired = true)!!
                            .copy(lastChapterRead = chapterNumber)
                        mangaService.update(updatedTrack.toDbTrack(), true)
                        insertTrack.await(updatedTrack)
                    }
                }
            }
                .awaitAll()
                .mapNotNull { it.exceptionOrNull() }
                .forEach { logcat(LogPriority.INFO, it) }
        }
    }
}
