package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelChapter
import eu.kanade.tachiyomi.data.backup.models.BackupNovelHistory
import eu.kanade.tachiyomi.data.backup.models.BackupNovelTracking
import eu.kanade.tachiyomi.data.backup.models.backupNovelChapterMapper
import eu.kanade.tachiyomi.data.backup.models.backupNovelTrackMapper
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.history.novel.interactor.GetNovelHistory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelBackupCreator(
    private val handler: NovelDatabaseHandler = Injekt.get(),
    private val getCollections: GetNovelCollections = Injekt.get(),
    private val getHistory: GetNovelHistory = Injekt.get(),
) {

    suspend operator fun invoke(novels: List<Novel>, options: BackupOptions): List<BackupNovel> {
        return novels.map {
            backupNovel(it, options)
        }
    }

    private suspend fun backupNovel(novel: Novel, options: BackupOptions): BackupNovel {
        val novelObject = novel.toBackupNovel()

        if (options.chapters) {
            handler.awaitList {
                novelchaptersQueries.getChaptersByNovelId(
                    novelId = novel.id,
                    mapper = backupNovelChapterMapper,
                )
            }
                .takeUnless(List<BackupNovelChapter>::isEmpty)
                ?.let { novelObject.chapters = it }
        }

        if (options.collections) {
            val collectionsForNovel = getCollections.await(novel.id)
            if (collectionsForNovel.isNotEmpty()) {
                novelObject.collections = collectionsForNovel.map { it.order }
            }
        }

        if (options.history) {
            val historyByNovelId = getHistory.await(novel.id)
            if (historyByNovelId.isNotEmpty()) {
                val history = historyByNovelId.map { history ->
                    val chapter = handler.awaitOne { novelchaptersQueries.getChapterById(history.chapterId) }
                    BackupNovelHistory(chapter.url, history.readAt?.time ?: 0L, history.readDuration)
                }
                if (history.isNotEmpty()) {
                    novelObject.history = history
                }
            }
        }

        if (options.tracking) {
            val tracks = handler.awaitList { novelsyncQueries.getTracksByNovelId(novel.id, backupNovelTrackMapper) }
            if (tracks.isNotEmpty()) {
                novelObject.tracking = tracks
            }
        }

        return novelObject
    }
}

private fun Novel.toBackupNovel() =
    BackupNovel(
        url = this.url,
        title = this.title,
        artist = this.artist,
        author = this.author,
        description = this.description,
        genre = this.genre.orEmpty(),
        status = this.status.toInt(),
        thumbnailUrl = this.thumbnailUrl,
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewerFlags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        coverLastModified = this.coverLastModified,
        initialized = this.initialized,
    )
