package tachiyomi.data.items.chapter

import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapterUpdate
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository

class NovelChapterRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelChapterRepository {

    override suspend fun getNovelChapterById(id: Long): NovelChapter? {
        return handler.awaitOneOrNull {
            novelchaptersQueries.getChapterById(id, ::mapNovelChapter)
        }
    }

    override suspend fun getNovelChaptersByNovelId(novelId: Long): List<NovelChapter> {
        return handler.awaitList {
            novelchaptersQueries.getChaptersByNovelId(novelId, ::mapNovelChapter)
        }
    }

    override fun getNovelChaptersByNovelIdAsFlow(novelId: Long): kotlinx.coroutines.flow.Flow<List<NovelChapter>> {
        return handler.subscribeToList {
            novelchaptersQueries.getChaptersByNovelId(novelId, ::mapNovelChapter)
        }
    }

    override suspend fun addAllNovelChapters(chapters: List<NovelChapter>): List<NovelChapter> {
        return try {
            handler.await(inTransaction = true) {
                chapters.map { chapter ->
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
                    val lastInsertId = novelchaptersQueries.selectLastInsertedRowId().executeAsOne()
                    chapter.copy(id = lastInsertId)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun updateNovelChapter(update: NovelChapterUpdate): Boolean {
        return try {
            handler.await {
                novelchaptersQueries.update(
                    novelId = update.novelId,
                    url = update.url,
                    name = update.name,
                    scanlator = update.scanlator,
                    read = update.read,
                    bookmark = update.bookmark,
                    lastCharRead = update.lastCharRead,
                    totalCharCount = update.totalCharCount,
                    chapterNumber = update.chapterNumber,
                    sourceOrder = update.sourceOrder,
                    dateFetch = update.dateFetch,
                    dateUpload = update.dateUpload,
                    version = update.version,
                    isSyncing = null,
                    chapterId = update.id,
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun updateAllNovelChapters(updates: List<NovelChapterUpdate>): Boolean {
        return try {
            handler.await(inTransaction = true) {
                updates.forEach { update ->
                    novelchaptersQueries.update(
                        novelId = update.novelId,
                        url = update.url,
                        name = update.name,
                        scanlator = update.scanlator,
                        read = update.read,
                        bookmark = update.bookmark,
                        lastCharRead = update.lastCharRead,
                        totalCharCount = update.totalCharCount,
                        chapterNumber = update.chapterNumber,
                        sourceOrder = update.sourceOrder,
                        dateFetch = update.dateFetch,
                        dateUpload = update.dateUpload,
                        version = update.version,
                        isSyncing = null,
                        chapterId = update.id,
                    )
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun mapNovelChapter(
        id: Long,
        novelId: Long,
        url: String,
        name: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastCharRead: Long,
        totalCharCount: Long,
        chapterNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
    ): NovelChapter = NovelChapter(
        id = id,
        novelId = novelId,
        read = read,
        bookmark = bookmark,
        lastCharRead = lastCharRead,
        totalCharCount = totalCharCount,
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        chapterNumber = chapterNumber,
        scanlator = scanlator,
        lastModifiedAt = lastModifiedAt,
        version = version,
    )
}
