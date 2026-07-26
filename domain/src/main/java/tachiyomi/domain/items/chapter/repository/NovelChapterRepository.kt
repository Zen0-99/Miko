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

    suspend fun getTotalReadCharCount(): Long
}
