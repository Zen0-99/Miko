package tachiyomi.domain.items.chapter.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository

class GetNovelChaptersByNovelId(
    private val novelChapterRepository: NovelChapterRepository,
) {

    suspend fun await(novelId: Long): List<NovelChapter> {
        return try {
            novelChapterRepository.getNovelChaptersByNovelId(novelId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
