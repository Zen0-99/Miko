package tachiyomi.domain.items.chapter.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository

class GetTotalReadNovelCharCount(
    private val novelChapterRepository: NovelChapterRepository,
) {

    suspend fun await(): Long {
        return try {
            novelChapterRepository.getTotalReadCharCount()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            0L
        }
    }
}
