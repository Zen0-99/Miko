package eu.kanade.tachiyomi.data.track

import eu.kanade.domain.track.novel.model.toNovelTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * Interface for novel-specific trackers. Extends [MangaTracker] but persists
 * via [InsertNovelTrack] instead of InsertMangaTrack.
 *
 * The default [setRemoteMangaStatus], [setRemoteLastChapterRead], etc. in
 * [MangaTracker] call `insertTrack.await()` which writes to the manga track
 * table. Novel trackers must override these to use [InsertNovelTrack] instead.
 */
interface NovelTracker : MangaTracker {

    val novelTrackerId: Long

    /**
     * Called after [update] succeeds to persist the track to the novel_sync table.
     * Default implementation converts to NovelTrack and inserts via [InsertNovelTrack].
     */
    suspend fun persistNovelTrack(track: MangaTrack) {
        track.toNovelTrack(idRequired = false)?.let {
            insertNovelTrack.await(it)
        }
    }

    companion object {
        private val insertNovelTrack: InsertNovelTrack by injectLazy()
    }
}
