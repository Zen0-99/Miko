package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class CreateReadingOrder(
    private val repository: ReadingOrderRepository,
) {
    suspend fun await(name: String, description: String? = null): Long {
        return repository.insertReadingOrder(name, description)
    }
}
