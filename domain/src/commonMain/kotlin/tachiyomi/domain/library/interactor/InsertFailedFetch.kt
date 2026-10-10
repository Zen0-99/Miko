package tachiyomi.domain.library.interactor

import tachiyomi.domain.library.model.FailedFetch
import tachiyomi.domain.library.repository.FailedFetchRepository

class InsertFailedFetch(
    private val repository: FailedFetchRepository,
) {
    suspend fun await(failedFetch: FailedFetch): Long = repository.insert(failedFetch)
    suspend fun awaitAll(failedFetches: List<FailedFetch>): List<Long> = repository.insertAll(failedFetches)
}
