package eu.kanade.domain.track.novel.interactor

import eu.kanade.domain.track.novel.model.toDbTrack
import eu.kanade.domain.track.novel.model.toNovelTrack
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import tachiyomi.domain.track.novel.model.NovelTrack

class SyncNovelChapterProgressWithTrack(
    private val getTracks: GetNovelTracks,
    private val insertTrack: InsertNovelTrack,
    private val getNovelChaptersByNovelId: GetNovelChaptersByNovelId,
    private val trackerManager: TrackerManager,
) {

    suspend fun await(novelId: Long, track: NovelTrack, tracker: MangaTracker) = withNonCancellableContext {
        withIOContext {
            val dbTrack = track.toDbTrack()

            val sortedChapters = getNovelChaptersByNovelId.await(novelId)
                .filter { it.isRecognizedNumber }
                .sortedBy { it.chapterNumber }

            val latestLocalReadChapterNumber = sortedChapters
                .takeWhile { it.read }
                .lastOrNull()
                ?.chapterNumber ?: -1.0

            if (latestLocalReadChapterNumber > track.lastChapterRead) {
                tracker.setRemoteLastChapterRead(dbTrack, latestLocalReadChapterNumber.toInt())
            } else if (track.totalChapters > 0 && sortedChapters.isNotEmpty()) {
                val latestChapterNumber = sortedChapters.last().chapterNumber
                if (latestChapterNumber > track.totalChapters) {
                    val updatedTrack = track.copy(totalChapters = latestChapterNumber.toLong())
                    insertTrack.await(updatedTrack)
                }
            }
        }
    }
}
