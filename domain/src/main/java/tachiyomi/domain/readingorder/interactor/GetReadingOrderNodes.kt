package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.model.ReadingOrderNode
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class GetReadingOrderNodes(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(orderId: Long): List<ReadingOrderNode> = repository.getNodes(orderId)
}
