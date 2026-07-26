package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

/**
 * Checks whether a manga is locked by any reading order.
 *
 * A manga is locked when it belongs to a reading order AND it has
 * prerequisite manga (incoming edges) that have not been marked as
 * completed yet. When all prerequisites are completed, the manga
 * unlocks automatically.
 *
 * Returns the list of reading orders that are currently locking this
 * manga (empty = unlocked).
 */
class GetLockedReadingOrders(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(mangaId: Long): List<ReadingOrder> {
        val orders = repository.getReadingOrdersForManga(mangaId)
        return orders.filter { order ->
            val prerequisites = repository.getPrerequisites(order.id, mangaId)
            if (prerequisites.isEmpty()) return@filter false
            prerequisites.any { prereqId ->
                val progress = repository.getProgress(order.id, prereqId)
                progress == null || !progress.completed
            }
        }
    }

    suspend fun isLocked(mangaId: Long): Boolean {
        return await(mangaId).isNotEmpty()
    }
}
