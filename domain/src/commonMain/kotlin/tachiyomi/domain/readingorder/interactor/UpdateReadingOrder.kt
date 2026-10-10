package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class UpdateReadingOrder(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(id: Long, name: String, description: String?) {
        repository.updateReadingOrder(id, name, description)
    }
}
