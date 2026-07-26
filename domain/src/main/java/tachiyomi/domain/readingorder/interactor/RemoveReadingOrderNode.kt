package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class RemoveReadingOrderNode(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(orderId: Long, mangaId: Long) {
        repository.removeNode(orderId, mangaId)
    }
}
