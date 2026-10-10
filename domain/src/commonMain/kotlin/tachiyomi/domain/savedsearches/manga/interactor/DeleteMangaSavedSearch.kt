package tachiyomi.domain.savedsearches.manga.interactor

import tachiyomi.domain.savedsearches.manga.repository.MangaSavedSearchRepository

class DeleteMangaSavedSearch(
    private val repository: MangaSavedSearchRepository,
) {
    suspend fun await(id: Long) {
        repository.deleteSavedSearch(id)
    }
}
