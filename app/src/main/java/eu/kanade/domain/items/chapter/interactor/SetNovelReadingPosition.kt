package eu.kanade.domain.items.chapter.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapterUpdate
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository

/**
 * Persist character-level reading progress to `novelchapters.last_char_read`.
 */
class SetNovelReadingPosition(
    private val novelChapterRepository: NovelChapterRepository,
) {

    suspend fun await(chapter: NovelChapter, charPosition: Long): Result = withNonCancellableContext {
        try {
            novelChapterRepository.updateNovelChapter(
                NovelChapterUpdate(
                    id = chapter.id,
                    lastCharRead = charPosition,
                ),
            )
            Result.Success
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    suspend fun await(chapterId: Long, charPosition: Long): Result {
        val chapter = novelChapterRepository.getNovelChapterById(chapterId)
            ?: return Result.NotFound
        return await(chapter, charPosition)
    }

    sealed interface Result {
        data object Success : Result
        data object NotFound : Result
        data class InternalError(val error: Throwable) : Result
    }
}
