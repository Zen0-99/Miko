package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class RemoveReadingOrderEdge(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(orderId: Long, fromMangaId: Long, toMangaId: Long) {
        repository.removeEdge(orderId, fromMangaId, toMangaId)
    }
}
