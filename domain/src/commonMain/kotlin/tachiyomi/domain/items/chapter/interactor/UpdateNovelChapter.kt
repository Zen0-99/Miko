package tachiyomi.domain.items.chapter.interactor

import tachiyomi.domain.items.chapter.model.NovelChapterUpdate
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository

class UpdateNovelChapter(
    private val novelChapterRepository: NovelChapterRepository,
) {

    suspend fun await(update: NovelChapterUpdate): Boolean {
        return novelChapterRepository.updateNovelChapter(update)
    }

    suspend fun awaitAll(updates: List<NovelChapterUpdate>): Boolean {
        return novelChapterRepository.updateAllNovelChapters(updates)
    }
}
