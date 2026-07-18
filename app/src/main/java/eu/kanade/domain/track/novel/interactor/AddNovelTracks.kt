package eu.kanade.domain.track.novel.interactor

import eu.kanade.domain.track.novel.model.toDbTrack
import eu.kanade.domain.track.novel.model.toNovelTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.NovelTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack

class AddNovelTracks(
    private val insertTrack: InsertNovelTrack,
    private val syncNovelChapterProgressWithTrack: SyncNovelChapterProgressWithTrack,
    private val getNovelChaptersByNovelId: GetNovelChaptersByNovelId,
    private val trackerManager: TrackerManager,
) {

    suspend fun bind(tracker: MangaTracker, item: MangaTrack, novelId: Long) = withNonCancellableContext {
        withIOContext {
            val allChapters = getNovelChaptersByNovelId.await(novelId)
            val hasReadChapters = allChapters.any { it.read }
            tracker.bind(item, hasReadChapters)

            val track = item.toNovelTrack(idRequired = false) ?: return@withIOContext

            insertTrack.await(track)

            // Update chapter progress if newer chapters marked read locally
            if (hasReadChapters) {
                val latestLocalReadChapterNumber = allChapters
                    .filter { it.isRecognizedNumber }
                    .sortedBy { it.chapterNumber }
                    .takeWhile { it.read }
                    .lastOrNull()
                    ?.chapterNumber ?: -1.0

                if (latestLocalReadChapterNumber > track.lastChapterRead) {
                    val updatedTrack = track.copy(lastChapterRead = latestLocalReadChapterNumber)
                    tracker.setRemoteLastChapterRead(updatedTrack.toDbTrack(), latestLocalReadChapterNumber.toInt())
                }
            }

            syncNovelChapterProgressWithTrack.await(novelId, track, tracker)
        }
    }
}
