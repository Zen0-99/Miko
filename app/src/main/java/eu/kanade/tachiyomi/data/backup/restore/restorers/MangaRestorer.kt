package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.backup.models.BackupCollection
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.model.MangaTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.max

class MangaRestorer(
    private val handler: MangaDatabaseHandler = Injekt.get(),
    private val getCollections: GetMangaCollections = Injekt.get(),
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getTracks: GetMangaTracks = Injekt.get(),
    private val insertTrack: InsertMangaTrack = Injekt.get(),
    fetchInterval: MangaFetchInterval = Injekt.get(),
) {

    /**
     * Mapping from backup source IDs to installed source IDs.
     * Set by [BackupRestorer] before restore begins.
     */
    var sourceIdMap: Map<Long, Long> = emptyMap()

    private fun remapSourceId(sourceId: Long): Long = sourceIdMap[sourceId] ?: sourceId

    private var now = ZonedDateTime.now()
    private var currentFetchWindow = fetchInterval.getWindow(now)

    init {
        now = ZonedDateTime.now()
        currentFetchWindow = fetchInterval.getWindow(now)
    }

    suspend fun sortByNew(backupMangas: List<BackupManga>): List<BackupManga> {
        val urlsBySource = handler.awaitList { mangasQueries.getAllMangaSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupMangas
            .sortedWith(
                compareBy<BackupManga> { it.url in urlsBySource[remapSourceId(it.source)].orEmpty() }
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    suspend fun restore(
        backupManga: BackupManga,
        backupCollections: List<BackupCollection>,
    ) {
        handler.await(inTransaction = true) {
            val dbManga = findExistingManga(backupManga)
            val manga = backupManga.getMangaImpl().copy(source = remapSourceId(backupManga.source))
            val restoredManga = if (dbManga == null) {
                restoreNewManga(manga)
            } else {
                restoreExistingManga(manga, dbManga)
            }

            restoreMangaDetails(
                manga = restoredManga,
                chapters = backupManga.chapters,
                collections = backupManga.collections,
                backupCollections = backupCollections,
                history = backupManga.history,
                tracks = backupManga.tracking,
                excludedScanlators = backupManga.excludedScanlators,
            )
        }
    }

    private suspend fun findExistingManga(backupManga: BackupManga): Manga? {
        val effectiveSourceId = remapSourceId(backupManga.source)
        return getMangaByUrlAndSourceId.await(backupManga.url, effectiveSourceId)
    }

    private suspend fun restoreExistingManga(manga: Manga, dbManga: Manga): Manga {
        return if (manga.version > dbManga.version) {
            updateManga(dbManga.copyFrom(manga).copy(id = dbManga.id))
        } else {
            updateManga(manga.copyFrom(dbManga).copy(id = dbManga.id))
        }
    }

    private fun Manga.copyFrom(newer: Manga): Manga {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            author = newer.author,
            artist = newer.artist,
            description = newer.description,
            genre = newer.genre,
            thumbnailUrl = newer.thumbnailUrl,
            status = newer.status,
            initialized = this.initialized || newer.initialized,
            version = newer.version,
        )
    }

    private suspend fun updateManga(manga: Manga): Manga {
        handler.await(true) {
            mangasQueries.update(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre?.joinToString(separator = ", "),
                title = manga.title,
                status = manga.status,
                thumbnailUrl = manga.thumbnailUrl,
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = null,
                calculateInterval = null,
                initialized = manga.initialized,
                viewer = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                mangaId = manga.id,
                updateStrategy = manga.updateStrategy.let(MangaUpdateStrategyColumnAdapter::encode),
                version = manga.version,
                isSyncing = 1,
            )
        }
        return manga
    }

    private suspend fun restoreNewManga(
        manga: Manga,
    ): Manga {
        return manga.copy(
            initialized = manga.description != null,
            id = insertManga(manga),
            version = manga.version,
        )
    }

    private suspend fun restoreChapters(manga: Manga, backupChapters: List<BackupChapter>) {
        val dbChaptersByUrl = getChaptersByMangaId.await(manga.id)
            .associateBy { it.url }

        val (existingChapters, newChapters) = backupChapters
            .mapNotNull {
                val chapter = it.toChapterImpl().copy(mangaId = manga.id)

                val dbChapter = dbChaptersByUrl[chapter.url]
                    ?: // New chapter
                    return@mapNotNull chapter

                if (chapter.forComparison() == dbChapter.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                // Update to an existing chapter
                var updatedChapter = chapter
                    .copyFrom(dbChapter)
                    .copy(
                        id = dbChapter.id,
                        bookmark = chapter.bookmark || dbChapter.bookmark,
                    )
                if (dbChapter.read && !updatedChapter.read) {
                    updatedChapter = updatedChapter.copy(
                        read = true,
                        lastPageRead = dbChapter.lastPageRead,
                    )
                } else if (updatedChapter.lastPageRead == 0L && dbChapter.lastPageRead != 0L) {
                    updatedChapter = updatedChapter.copy(
                        lastPageRead = dbChapter.lastPageRead,
                    )
                }
                updatedChapter
            }
            .partition { it.id > 0 }

        insertNewChapters(newChapters)
        updateExistingChapters(existingChapters)
    }

    private fun Chapter.forComparison() =
        this.copy(id = 0L, mangaId = 0L, dateFetch = 0L, dateUpload = 0L, lastModifiedAt = 0L, version = 0L)

    private suspend fun insertNewChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.insert(
                    chapter.mangaId,
                    chapter.url,
                    chapter.name,
                    chapter.scanlator,
                    chapter.read,
                    chapter.bookmark,
                    chapter.lastPageRead,
                    chapter.chapterNumber,
                    chapter.sourceOrder,
                    chapter.dateFetch,
                    chapter.dateUpload,
                    chapter.version,
                )
            }
        }
    }

    private suspend fun updateExistingChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.update(
                    mangaId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead,
                    chapterNumber = null,
                    sourceOrder = null,
                    dateFetch = null,
                    dateUpload = null,
                    chapterId = chapter.id,
                    version = chapter.version,
                    isSyncing = 0,
                )
            }
        }
    }

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    private suspend fun insertManga(manga: Manga): Long {
        return handler.awaitOneExecutable(true) {
            mangasQueries.insert(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                title = manga.title,
                status = manga.status,
                thumbnailUrl = manga.thumbnailUrl,
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = 0L,
                calculateInterval = 0L,
                initialized = manga.initialized,
                viewerFlags = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                updateStrategy = manga.updateStrategy,
                version = manga.version,
                uuid = manga.uuid,
            )
            mangasQueries.selectLastInsertedRowId()
        }
    }

    private suspend fun restoreMangaDetails(
        manga: Manga,
        chapters: List<BackupChapter>,
        collections: List<Long>,
        backupCollections: List<BackupCollection>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
        excludedScanlators: List<String>,
    ): Manga {
        restoreCollections(manga, collections, backupCollections)
        restoreChapters(manga, chapters)
        restoreTracking(manga, tracks)
        restoreHistory(history)
        restoreExcludedScanlators(manga, excludedScanlators)
        updateManga.awaitUpdateFetchInterval(manga, now, currentFetchWindow)
        return manga
    }

    /**
     * Restores the collections a manga is in.
     *
     * @param manga the manga whose collections have to be restored.
     * @param collections the collections to restore.
     */
    private suspend fun restoreCollections(
        manga: Manga,
        collections: List<Long>,
        backupCollections: List<BackupCollection>,
    ) {
        val dbCollections = getCollections.await()
        val dbCollectionsByName = dbCollections.associateBy { it.name }

        val backupCollectionsByOrder = backupCollections.associateBy { it.order }

        val mangaCollectionsToUpdate = collections.mapNotNull { backupCollectionOrder ->
            backupCollectionsByOrder[backupCollectionOrder]?.let { backupCollection ->
                dbCollectionsByName[backupCollection.name]?.let { dbCollection ->
                    Pair(manga.id, dbCollection.id)
                }
            }
        }

        if (mangaCollectionsToUpdate.isNotEmpty()) {
            handler.await(true) {
                mangas_categoriesQueries.deleteMangaCategoryByMangaId(manga.id)
                mangaCollectionsToUpdate.forEach { (mangaId, collectionId) ->
                    mangas_categoriesQueries.insert(mangaId, collectionId)
                }
            }
        }
    }

    private suspend fun restoreHistory(backupHistory: List<BackupHistory>) {
        val toUpdate = backupHistory.mapNotNull { history ->
            val dbHistory = handler.awaitOneOrNull { historyQueries.getHistoryByChapterUrl(history.url) }
            val item = history.getHistoryImpl()

            if (dbHistory == null) {
                val chapter = handler.awaitOneOrNull { chaptersQueries.getChapterByUrl(history.url) }
                return@mapNotNull if (chapter == null) {
                    // Chapter doesn't exist; skip
                    null
                } else {
                    // New history entry
                    item.copy(chapterId = chapter._id)
                }
            }

            // Update history entry
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
                    historyQueries.upsert(
                        it.chapterId,
                        it.readAt,
                        it.readDuration,
                    )
                }
            }
        }
    }

    private suspend fun restoreTracking(manga: Manga, backupTracks: List<BackupTracking>) {
        val dbTrackByTrackerId = getTracks.await(manga.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = backupTracks
            .mapNotNull {
                val track = it.getTrackImpl()
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: // New track
                    return@mapNotNull track.copy(
                        id = 0, // Let DB assign new ID
                        mangaId = manga.id,
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
                    manga_syncQueries.update(
                        track.mangaId,
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

    private fun MangaTrack.forComparison() = this.copy(id = 0L, mangaId = 0L)

    /**
     * Restores the excluded scanlators for the manga.
     *
     * @param manga the manga whose excluded scanlators have to be restored.
     * @param excludedScanlators the excluded scanlators to restore.
     */
    private suspend fun restoreExcludedScanlators(manga: Manga, excludedScanlators: List<String>) {
        if (excludedScanlators.isEmpty()) return
        val existingExcludedScanlators = handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(manga.id)
        }
        val toInsert = excludedScanlators.filter { it !in existingExcludedScanlators }
        if (toInsert.isNotEmpty()) {
            handler.await {
                toInsert.forEach {
                    excluded_scanlatorsQueries.insert(manga.id, it)
                }
            }
        }
    }
}
