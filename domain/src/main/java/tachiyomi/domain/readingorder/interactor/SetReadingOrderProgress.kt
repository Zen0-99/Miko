package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class SetReadingOrderProgress(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(orderId: Long, mangaId: Long, completed: Boolean) {
        val completedAt = if (completed) System.currentTimeMillis() else null
        repository.setProgress(orderId, mangaId, completed, completedAt)
    }
}
