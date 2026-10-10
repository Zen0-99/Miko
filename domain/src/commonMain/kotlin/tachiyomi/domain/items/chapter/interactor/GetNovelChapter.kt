package tachiyomi.domain.items.chapter.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository

class GetNovelChapter(
    private val novelChapterRepository: NovelChapterRepository,
) {

    suspend fun await(id: Long): NovelChapter? {
        return try {
            novelChapterRepository.getNovelChapterById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }
}
