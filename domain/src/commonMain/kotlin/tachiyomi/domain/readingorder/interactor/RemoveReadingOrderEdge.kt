package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class RemoveReadingOrderEdge(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(orderId: Long, fromEntryId: Long, toEntryId: Long) {
        repository.removeEdge(orderId, fromEntryId, toEntryId)
    }
}
