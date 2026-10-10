package tachiyomi.domain.savedsearches.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.savedsearches.model.SavedSearch
import tachiyomi.domain.savedsearches.novel.repository.NovelSavedSearchRepository

class GetNovelSavedSearches(
    private val repository: NovelSavedSearchRepository,
) {
    fun subscribe(sourceId: Long): Flow<List<SavedSearch>> {
        return repository.getSavedSearchesBySourceId(sourceId)
    }
}
