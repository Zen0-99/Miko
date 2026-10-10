package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class GetReadingOrders(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(entryKind: String): List<ReadingOrder> = repository.getReadingOrdersByKind(entryKind)

    suspend fun await(id: Long): ReadingOrder? = repository.getReadingOrder(id)

    suspend fun awaitForEntry(entryId: Long): List<ReadingOrder> = repository.getReadingOrdersForEntry(entryId)
}
