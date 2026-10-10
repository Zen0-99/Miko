package tachiyomi.domain.items.chapter.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapterUpdate

interface NovelChapterRepository {

    suspend fun getNovelChapterById(id: Long): NovelChapter?

    suspend fun getNovelChaptersByNovelId(novelId: Long): List<NovelChapter>

    fun getNovelChaptersByNovelIdAsFlow(novelId: Long): Flow<List<NovelChapter>>

    suspend fun addAllNovelChapters(chapters: List<NovelChapter>): List<NovelChapter>

    suspend fun updateNovelChapter(update: NovelChapterUpdate): Boolean

    suspend fun updateAllNovelChapters(updates: List<NovelChapterUpdate>): Boolean

    suspend fun removeChaptersWithIds(chapterIds: List<Long>)

    suspend fun getTotalReadCharCount(): Long

    /**
     * Get novel IDs that have read chapters with missing char counts
     * (total_char_count = 0). Used for stats backfill.
     */
    suspend fun getNovelIdsWithMissingReadCharCount(): List<Long>

    /**
     * Get the average char count of read chapters that have a non-zero char
     * count, for a given novel. Used to estimate char counts for chapters
     * that were marked as read before char count tracking was implemented.
     */
    suspend fun getAverageReadCharCountForNovel(novelId: Long): Long

    /**
     * Set the char count for all read chapters with total_char_count = 0
     * in a given novel. Used for stats backfill.
     */
    suspend fun backfillReadCharCountForNovel(novelId: Long, charCount: Long): Int
}
