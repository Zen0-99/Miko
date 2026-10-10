package tachiyomi.domain.savedsearches.manga.interactor

import tachiyomi.domain.savedsearches.manga.repository.MangaSavedSearchRepository
import tachiyomi.domain.savedsearches.model.SavedSearch

class InsertMangaSavedSearch(
    private val repository: MangaSavedSearchRepository,
) {
    suspend fun await(savedSearch: SavedSearch) {
        repository.insertSavedSearch(savedSearch)
    }
}
