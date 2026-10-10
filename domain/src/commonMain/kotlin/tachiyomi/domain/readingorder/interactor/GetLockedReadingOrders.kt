package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class GetLockedReadingOrders(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(entryId: Long): List<ReadingOrder> {
        val orders = repository.getReadingOrdersForEntry(entryId)
        return orders.filter { order ->
            val prerequisites = repository.getPrerequisites(order.id, entryId)
            if (prerequisites.isEmpty()) return@filter false
            prerequisites.any { prereqId ->
                val progress = repository.getProgress(order.id, prereqId)
                progress == null || !progress.completed
            }
        }
    }

    suspend fun isLocked(entryId: Long): Boolean {
        return await(entryId).isNotEmpty()
    }
}
