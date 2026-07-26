package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.model.ReadingOrderEdge
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class GetReadingOrderEdges(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(orderId: Long): List<ReadingOrderEdge> = repository.getEdges(orderId)
}
