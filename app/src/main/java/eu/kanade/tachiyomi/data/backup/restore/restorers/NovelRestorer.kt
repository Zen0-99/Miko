package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.tachiyomi.data.backup.models.BackupCollection
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelChapter
import eu.kanade.tachiyomi.data.backup.models.BackupNovelHistory
import eu.kanade.tachiyomi.data.backup.models.BackupNovelTracking
import tachiyomi.data.NovelUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.entries.novel.interactor.GetNovelByUrlAndSourceId
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import tachiyomi.domain.track.novel.model.NovelTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import kotlin.math.max

class NovelRestorer(
    private val handler: NovelDatabaseHandler = Injekt.get(),
    private val getCollections: GetNovelCollections = Injekt.get(),
    private val getNovelByUrlAndSourceId: GetNovelByUrlAndSourceId = Injekt.get(),
    private val getChaptersByNovelId: GetNovelChaptersByNovelId = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val getTracks: GetNovelTracks = Injekt.get(),
    private val insertTrack: InsertNovelTrack = Injekt.get(),
) {

    /**
     * Mapping from backup source IDs to installed source IDs.
     * Set by [BackupRestorer] before restore begins.
     * Used to remap source IDs so entries link to the correct installed extensions.
     */
    var sourceIdMap: Map<Long, Long> = emptyMap()

    /**
     * Remap a backup source ID to the installed source ID, if a mapping exists.
     */
    private fun remapSourceId(sourceId: Long): Long = sourceIdMap[sourceId] ?: sourceId

    suspend fun sortByNew(backupNovels: List<BackupNovel>): List<BackupNovel> {
        val urlsBySource = handler.awaitList { novelsQueries.getAllNovelSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupNovels
            .sortedWith(
                compareBy<BackupNovel> { it.url in urlsBySource[remapSourceId(it.source)].orEmpty() }
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    suspend fun restore(
        backupNovel: BackupNovel,
        backupCollections: List<BackupCollection>,
    ) {
        handler.await(inTransaction = true) {
            val dbNovel = findExistingNovel(backupNovel)
            val novel = backupNovel.getNovelImpl().copy(source = remapSourceId(backupNovel.source))
            val restoredNovel = if (dbNovel == null) {
                restoreNewNovel(novel)
            } else {
                restoreExistingNovel(novel, dbNovel)
            }

            restoreNovelDetails(
                novel = restoredNovel,
                chapters = backupNovel.chapters,
                collections = backupNovel.collections,
                backupCollections = backupCollections,
                history = backupNovel.history,
                tracks = backupNovel.tracking,
            )
        }
    }

    private suspend fun findExistingNovel(backupNovel: BackupNovel): Novel? {
        val effectiveSourceId = remapSourceId(backupNovel.source)
        return getNovelByUrlAndSourceId.await(backupNovel.url, effectiveSourceId)
    }

    private suspend fun restoreExistingNovel(novel: Novel, dbNovel: Novel): Novel {
        return if (novel.version > dbNovel.version) {
            updateNovel(dbNovel.copyFrom(novel).copy(id = dbNovel.id))
        } else {
            updateNovel(novel.copyFrom(dbNovel).copy(id = dbNovel.id))
        }
    }

    private fun Novel.copyFrom(newer: Novel): Novel {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            author = newer.author,
            artist = newer.artist,
            description = newer.description,
            genre = newer.genre,
            thumbnailUrl = newer.thumbnailUrl,
            status = newer.status,
            initialized = this.initialized || newer.initialized,
            coverLastModified = newer.coverLastModified,
            version = newer.version,
        )
    }

    private suspend fun updateNovel(novel: Novel): Novel {
        handler.await(true) {
            novelsQueries.update(
                source = novel.source,
                url = novel.url,
                artist = novel.artist,
                author = novel.author,
                description = novel.description,
                genre = novel.genre?.let(StringListColumnAdapter::encode),
                title = novel.title,
                status = novel.status,
                thumbnailUrl = novel.thumbnailUrl,
                favorite = novel.favorite,
                lastUpdate = novel.lastUpdate,
                nextUpdate = null,
                initialized = novel.initialized,
                viewer = novel.viewerFlags,
                chapterFlags = novel.chapterFlags,
                coverLastModified = novel.coverLastModified,
                dateAdded = novel.dateAdded,
                updateStrategy = novel.updateStrategy.let(NovelUpdateStrategyColumnAdapter::encode),
                calculateInterval = null,
                version = novel.version,
                isSyncing = 1,
                novelId = novel.id,
            )
        }
        return novel
    }

    private suspend fun restoreNewNovel(
        novel: Novel,
    ): Novel {
        return novel.copy(
            initialized = novel.description != null,
            id = insertNovel(novel),
            version = novel.version,
        )
    }

    private suspend fun insertNovel(novel: Novel): Long {
        return handler.awaitOneExecutable(true) {
            novelsQueries.insert(
                source = novel.source,
                url = novel.url,
                artist = novel.artist,
                author = novel.author,
                description = novel.description,
                genre = novel.genre,
                title = novel.title,
                status = novel.status,
                thumbnailUrl = novel.thumbnailUrl,
                favorite = novel.favorite,
                lastUpdate = novel.lastUpdate,
                nextUpdate = 0L,
                initialized = novel.initialized,
                viewerFlags = novel.viewerFlags,
                chapterFlags = novel.chapterFlags,
                coverLastModified = novel.coverLastModified,
                dateAdded = novel.dateAdded,
                updateStrategy = novel.updateStrategy,
                calculateInterval = 0L,
                version = novel.version,
            )
            novelsQueries.selectLastInsertedRowId()
        }
    }

    private suspend fun restoreNovelDetails(
        novel: Novel,
        chapters: List<BackupNovelChapter>,
        collections: List<Long>,
        backupCollections: List<BackupCollection>,
        history: List<BackupNovelHistory>,
        tracks: List<BackupNovelTracking>,
    ): Novel {
        restoreCollections(novel, collections, backupCollections)
        restoreChapters(novel, chapters)
        restoreTracking(novel, tracks)
        restoreHistory(history)
        return novel
    }

    private suspend fun restoreCollections(
        novel: Novel,
        collections: List<Long>,
        backupCollections: List<BackupCollection>,
    ) {
        val dbCollections = getCollections.await()
        val dbCollectionsByName = dbCollections.associateBy { it.name }

        val backupCollectionsByOrder = backupCollections.associateBy { it.order }

        val novelCollectionsToUpdate = collections.mapNotNull { backupCollectionOrder ->
            backupCollectionsByOrder[backupCollectionOrder]?.let { backupCollection ->
                dbCollectionsByName[backupCollection.name]?.let { dbCollection ->
                    Pair(novel.id, dbCollection.id)
                }
            }
        }

        if (novelCollectionsToUpdate.isNotEmpty()) {
            handler.await(true) {
                novels_categoriesQueries.deleteNovelCategoryByNovelId(novel.id)
                novelCollectionsToUpdate.forEach { (novelId, collectionId) ->
                    novels_categoriesQueries.insert(novelId, collectionId)
                }
            }
        }
    }

    private suspend fun restoreChapters(novel: Novel, backupChapters: List<BackupNovelChapter>) {
        val dbChaptersByUrl = getChaptersByNovelId.await(novel.id)
            .associateBy { it.url }

        val (existingChapters, newChapters) = backupChapters
            .mapNotNull {
                val chapter = it.toNovelChapterImpl().copy(novelId = novel.id)

                val dbChapter = dbChaptersByUrl[chapter.url]
                    ?: return@mapNotNull chapter

                if (chapter.forComparison() == dbChapter.forComparison()) {
                    return@mapNotNull null
                }

                var updatedChapter = chapter
                    .copyFrom(dbChapter)
                    .copy(
                        id = dbChapter.id,
                        bookmark = chapter.bookmark || dbChapter.bookmark,
                    )
                if (dbChapter.read && !updatedChapter.read) {
                    updatedChapter = updatedChapter.copy(
                        read = true,
                        lastCharRead = dbChapter.lastCharRead,
                    )
                } else if (updatedChapter.lastCharRead == 0L && dbChapter.lastCharRead != 0L) {
                    updatedChapter = updatedChapter.copy(
                        lastCharRead = dbChapter.lastCharRead,
                    )
                }
                updatedChapter
            }
            .partition { it.id > 0 }

        insertNewChapters(newChapters)
        updateExistingChapters(existingChapters)
    }

    private fun NovelChapter.forComparison() =
        this.copy(id = 0L, novelId = 0L, dateFetch = 0L, dateUpload = 0L, lastModifiedAt = 0L, version = 0L)

    private fun NovelTrack.forComparison() = this.copy(id = 0L, novelId = 0L)

    private suspend fun insertNewChapters(chapters: List<NovelChapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                novelchaptersQueries.insert(
                    chapter.novelId,
                    chapter.url,
                    chapter.name,
                    chapter.scanlator,
                    chapter.read,
                    chapter.bookmark,
                    chapter.lastCharRead,
                    chapter.totalCharCount,
                    chapter.chapterNumber,
                    chapter.sourceOrder,
                    chapter.dateFetch,
                    chapter.dateUpload,
                    chapter.version,
                )
            }
        }
    }

    private suspend fun updateExistingChapters(chapters: List<NovelChapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                novelchaptersQueries.update(
                    novelId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastCharRead = chapter.lastCharRead,
                    totalCharCount = chapter.totalCharCount,
                    chapterNumber = null,
                    sourceOrder = null,
                    dateFetch = null,
                    dateUpload = null,
                    version = chapter.version,
                    isSyncing = null,
                    chapterId = chapter.id,
                )
            }
        }
    }

    private suspend fun restoreHistory(backupHistory: List<BackupNovelHistory>) {
        val toUpdate = backupHistory.mapNotNull { history ->
            val dbHistory = handler.awaitOneOrNull { novelhistoryQueries.getHistoryByChapterUrl(history.url) }
            val item = history.getHistoryImpl()

            if (dbHistory == null) {
                val chapter = handler.awaitOneOrNull { novelchaptersQueries.getChapterByUrl(history.url) }
                return@mapNotNull if (chapter == null) {
                    null
                } else {
                    item.copy(chapterId = chapter._id)
                }
            }

            item.copy(
                id = dbHistory._id,
                chapterId = dbHistory.chapter_id,
                readAt = max(item.readAt?.time ?: 0L, dbHistory.last_read?.time ?: 0L)
                    .takeIf { it > 0L }
                    ?.let { Date(it) },
                readDuration = max(item.readDuration, dbHistory.time_read) - dbHistory.time_read,
            )
        }

        if (toUpdate.isNotEmpty()) {
            handler.await(true) {
                toUpdate.forEach {
                    novelhistoryQueries.upsert(
                        it.chapterId,
                        it.readAt,
                        it.readDuration,
                    )
                }
            }
        }
    }

    private suspend fun restoreTracking(novel: Novel, backupTracks: List<BackupNovelTracking>) {
        val dbTrackByTrackerId = getTracks.await(novel.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = backupTracks
            .mapNotNull {
                val track = it.getTrackImpl()
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: // New track
                    return@mapNotNull track.copy(
                        id = 0, // Let DB assign new ID
                        novelId = novel.id,
                    )

                if (track.forComparison() == dbTrack.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                // Update to an existing track
                dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    lastChapterRead = max(dbTrack.lastChapterRead, track.lastChapterRead),
                )
            }
            .partition { it.id > 0 }

        if (newTracks.isNotEmpty()) {
            insertTrack.awaitAll(newTracks)
        }
        if (existingTracks.isNotEmpty()) {
            handler.await(true) {
                existingTracks.forEach { track ->
                    novelsyncQueries.update(
                        track.novelId,
                        track.trackerId,
                        track.remoteId,
                        track.libraryId,
                        track.title,
                        track.lastChapterRead,
                        track.totalChapters,
                        track.status,
                        track.score,
                        track.remoteUrl,
                        track.startDate,
                        track.finishDate,
                        track.private,
                        track.id,
                    )
                }
            }
        }
    }
}
