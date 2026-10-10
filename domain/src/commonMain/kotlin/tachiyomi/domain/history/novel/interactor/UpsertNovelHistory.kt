package tachiyomi.domain.history.novel.interactor

import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository

class UpsertNovelHistory(
    private val historyRepository: NovelHistoryRepository,
) {

    suspend fun await(historyUpdate: NovelHistoryUpdate) {
        historyRepository.upsertNovelHistory(historyUpdate)
    }
}
