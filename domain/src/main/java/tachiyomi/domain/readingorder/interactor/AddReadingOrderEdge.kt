package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class AddReadingOrderEdge(
    private val repository: ReadingOrderRepository,
    private val checkCycle: CheckReadingOrderCycle,
) {
    suspend fun await(orderId: Long, fromEntryId: Long, toEntryId: Long): Boolean {
        if (checkCycle.await(orderId, fromEntryId, toEntryId)) {
            return false
        }
        repository.addEdge(orderId, fromEntryId, toEntryId)
        return true
    }
}
