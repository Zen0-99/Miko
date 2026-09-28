package tachiyomi.domain.savedsearches.shows.interactor

import tachiyomi.domain.savedsearches.shows.repository.AnimeSavedSearchRepository
import tachiyomi.domain.savedsearches.model.SavedSearch

class InsertAnimeSavedSearch(
    private val repository: AnimeSavedSearchRepository,
) {
    suspend fun await(savedSearch: SavedSearch) {
        repository.insertSavedSearch(savedSearch)
    }
}
