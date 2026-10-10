package tachiyomi.domain.savedsearches.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.savedsearches.manga.repository.MangaSavedSearchRepository
import tachiyomi.domain.savedsearches.model.SavedSearch

class GetMangaSavedSearches(
    private val repository: MangaSavedSearchRepository,
) {
    fun subscribe(sourceId: Long): Flow<List<SavedSearch>> {
        return repository.getSavedSearchesBySourceId(sourceId)
    }
}
