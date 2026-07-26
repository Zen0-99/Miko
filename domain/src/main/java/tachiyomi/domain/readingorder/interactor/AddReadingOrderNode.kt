package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class AddReadingOrderNode(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(orderId: Long, mangaId: Long): Long {
        return repository.addNode(orderId, mangaId)
    }
}
