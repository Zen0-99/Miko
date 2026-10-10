package tachiyomi.domain.library.interactor

import tachiyomi.domain.library.model.FailedFetch
import tachiyomi.domain.library.repository.FailedFetchRepository

class GetFailedFetches(
    private val repository: FailedFetchRepository,
) {
    suspend fun await(): List<FailedFetch> = repository.getAll()
    fun subscribe() = repository.subscribeAll()
}
