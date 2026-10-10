package tachiyomi.domain.savedsearches.shows.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.savedsearches.model.SavedSearch
import tachiyomi.domain.savedsearches.shows.repository.AnimeSavedSearchRepository

class GetAnimeSavedSearches(
    private val repository: AnimeSavedSearchRepository,
) {
    fun subscribe(sourceId: Long): Flow<List<SavedSearch>> {
        return repository.getSavedSearchesBySourceId(sourceId)
    }
}
