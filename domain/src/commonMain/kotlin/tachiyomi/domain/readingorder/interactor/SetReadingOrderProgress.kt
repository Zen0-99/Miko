package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository
import kotlin.time.Clock

class SetReadingOrderProgress(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(orderId: Long, entryId: Long, completed: Boolean) {
        val completedAt = if (completed) Clock.System.now().toEpochMilliseconds() else null
        repository.setProgress(orderId, entryId, completed, completedAt)
    }
}
