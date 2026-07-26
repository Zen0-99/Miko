package tachiyomi.domain.readingorder.repository

import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.domain.readingorder.model.ReadingOrderEdge
import tachiyomi.domain.readingorder.model.ReadingOrderNode
import tachiyomi.domain.readingorder.model.ReadingOrderProgress

interface ReadingOrderRepository {

    suspend fun getReadingOrder(id: Long): ReadingOrder?

    suspend fun getAllReadingOrders(): List<ReadingOrder>

    suspend fun insertReadingOrder(name: String, description: String?): Long

    suspend fun updateReadingOrder(id: Long, name: String, description: String?)

    suspend fun deleteReadingOrder(id: Long)

    suspend fun getNodes(orderId: Long): List<ReadingOrderNode>

    suspend fun addNode(orderId: Long, mangaId: Long): Long

    suspend fun removeNode(orderId: Long, mangaId: Long)

    suspend fun getEdges(orderId: Long): List<ReadingOrderEdge>

    suspend fun addEdge(orderId: Long, fromMangaId: Long, toMangaId: Long)

    suspend fun removeEdge(orderId: Long, fromMangaId: Long, toMangaId: Long)

    suspend fun getPrerequisites(orderId: Long, mangaId: Long): List<Long>

    suspend fun getProgress(orderId: Long, mangaId: Long): ReadingOrderProgress?

    suspend fun getAllProgress(orderId: Long): List<ReadingOrderProgress>

    suspend fun setProgress(orderId: Long, mangaId: Long, completed: Boolean, completedAt: Long?)

    suspend fun getReadingOrdersForManga(mangaId: Long): List<ReadingOrder>
}
