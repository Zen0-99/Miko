package tachiyomi.domain.history.novel.interactor

import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.service.getNovelChapterSort
import kotlin.math.max

class GetNextNovelChapters(
    private val getNovelChaptersByNovelId: GetNovelChaptersByNovelId,
    private val getNovel: GetNovel,
    private val historyRepository: NovelHistoryRepository,
) {

    suspend fun await(onlyUnread: Boolean = true): List<NovelChapter> {
        val history = historyRepository.getLastNovelHistory() ?: return emptyList()
        return await(history.novelId, history.chapterId, onlyUnread)
    }

    suspend fun await(novelId: Long, onlyUnread: Boolean = true): List<NovelChapter> {
        val novel = getNovel.await(novelId) ?: return emptyList()
        val chapters = getNovelChaptersByNovelId.await(novelId)
            .sortedWith(getNovelChapterSort(novel, sortDescending = false))

        return if (onlyUnread) {
            chapters.filterNot { it.read }
        } else {
            chapters
        }
    }

    suspend fun await(novelId: Long, fromChapterId: Long, onlyUnread: Boolean = true): List<NovelChapter> {
        val chapters = await(novelId, onlyUnread)
        val currChapterIndex = chapters.indexOfFirst { it.id == fromChapterId }
        val nextChapters = chapters.subList(max(0, currChapterIndex), chapters.size)

        if (onlyUnread) {
            return nextChapters
        }

        val fromChapter = chapters.getOrNull(currChapterIndex)
        return if (fromChapter != null && !fromChapter.read) {
            nextChapters
        } else {
            nextChapters.drop(1)
        }
    }
}
