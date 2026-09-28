package tachiyomi.domain.savedsearches.shows.interactor

import tachiyomi.domain.savedsearches.shows.repository.AnimeSavedSearchRepository

class DeleteAnimeSavedSearch(
    private val repository: AnimeSavedSearchRepository,
) {
    suspend fun await(id: Long) {
        repository.deleteSavedSearch(id)
    }
}
