package tachiyomi.domain.history.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.novel.model.NovelHistory
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository

class GetNovelHistory(
    private val repository: NovelHistoryRepository,
) {

    suspend fun await(novelId: Long): List<NovelHistory> {
        return repository.getHistoryByNovelId(novelId)
    }

    fun subscribe(query: String): Flow<List<NovelHistoryWithRelations>> {
        return repository.getNovelHistory(query)
    }
}
