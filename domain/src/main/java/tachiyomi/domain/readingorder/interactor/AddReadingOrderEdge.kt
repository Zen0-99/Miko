package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class AddReadingOrderEdge(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(orderId: Long, fromMangaId: Long, toMangaId: Long) {
        repository.addEdge(orderId, fromMangaId, toMangaId)
    }
}
