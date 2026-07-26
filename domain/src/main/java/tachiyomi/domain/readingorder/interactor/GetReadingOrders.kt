package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class GetReadingOrders(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(): List<ReadingOrder> = repository.getAllReadingOrders()

    suspend fun await(id: Long): ReadingOrder? = repository.getReadingOrder(id)

    suspend fun awaitForManga(mangaId: Long): List<ReadingOrder> = repository.getReadingOrdersForManga(mangaId)
}
