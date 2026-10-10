package tachiyomi.domain.library.interactor

import tachiyomi.domain.library.repository.FailedFetchRepository

class ClearFailedFetches(
    private val repository: FailedFetchRepository,
) {
    suspend fun await() = repository.clearAll()
}
