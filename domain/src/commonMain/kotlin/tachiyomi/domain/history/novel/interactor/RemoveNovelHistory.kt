package tachiyomi.domain.history.novel.interactor

import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository

class RemoveNovelHistory(
    private val repository: NovelHistoryRepository,
) {

    suspend fun awaitAll(): Boolean {
        return repository.deleteAllNovelHistory()
    }

    suspend fun await(history: NovelHistoryWithRelations) {
        repository.resetNovelHistory(history.id)
    }

    suspend fun await(novelId: Long) {
        repository.resetHistoryByNovelId(novelId)
    }
}
