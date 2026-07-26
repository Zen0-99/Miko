package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class DeleteReadingOrder(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(id: Long) {
        repository.deleteReadingOrder(id)
    }
}
