package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class RemoveReadingOrderNode(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(orderId: Long, entryId: Long) {
        repository.removeNode(orderId, entryId)
    }
}
