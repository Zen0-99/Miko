package tachiyomi.domain.savedsearches.novel.interactor

import tachiyomi.domain.savedsearches.novel.repository.NovelSavedSearchRepository

class DeleteNovelSavedSearch(
    private val repository: NovelSavedSearchRepository,
) {
    suspend fun await(id: Long) {
        repository.deleteSavedSearch(id)
    }
}
