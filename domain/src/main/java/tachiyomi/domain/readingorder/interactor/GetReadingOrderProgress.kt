package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.model.ReadingOrderProgress
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class GetReadingOrderProgress(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(orderId: Long, entryId: Long): ReadingOrderProgress? {
        return repository.getProgress(orderId, entryId)
    }

    suspend fun awaitAll(orderId: Long): List<ReadingOrderProgress> {
        return repository.getAllProgress(orderId)
    }
}
