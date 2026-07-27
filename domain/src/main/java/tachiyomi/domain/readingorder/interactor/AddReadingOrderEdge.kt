package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class AddReadingOrderEdge(
    private val repository: ReadingOrderRepository,
    private val checkCycle: CheckReadingOrderCycle,
) {
    /**
     * Adds a prerequisite edge: `fromMangaId` must be read before `toMangaId`.
     *
     * @return true if the edge was added, false if it would create a cycle
     */
    suspend fun await(orderId: Long, fromMangaId: Long, toMangaId: Long): Boolean {
        if (checkCycle.await(orderId, fromMangaId, toMangaId)) {
            return false
        }
        repository.addEdge(orderId, fromMangaId, toMangaId)
        return true
    }
}
