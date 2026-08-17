package tachiyomi.domain.readingorder.repository

import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.domain.readingorder.model.ReadingOrderEdge
import tachiyomi.domain.readingorder.model.ReadingOrderNode
import tachiyomi.domain.readingorder.model.ReadingOrderProgress

interface ReadingOrderRepository {

    suspend fun getReadingOrder(id: Long): ReadingOrder?

    suspend fun getAllReadingOrders(): List<ReadingOrder>

    suspend fun getReadingOrdersByKind(entryKind: String): List<ReadingOrder>

    suspend fun insertReadingOrder(name: String, description: String?, entryKind: String): Long

    suspend fun updateReadingOrder(id: Long, name: String, description: String?)

    suspend fun deleteReadingOrder(id: Long)

    suspend fun getNodes(orderId: Long): List<ReadingOrderNode>

    suspend fun addNode(orderId: Long, entryId: Long): Long

    suspend fun removeNode(orderId: Long, entryId: Long)

    suspend fun getEdges(orderId: Long): List<ReadingOrderEdge>

    suspend fun addEdge(orderId: Long, fromEntryId: Long, toEntryId: Long)

    suspend fun removeEdge(orderId: Long, fromEntryId: Long, toEntryId: Long)

    suspend fun getPrerequisites(orderId: Long, entryId: Long): List<Long>

    suspend fun getProgress(orderId: Long, entryId: Long): ReadingOrderProgress?

    suspend fun getAllProgress(orderId: Long): List<ReadingOrderProgress>

    suspend fun setProgress(orderId: Long, entryId: Long, completed: Boolean, completedAt: Long?)

    suspend fun getReadingOrdersForEntry(entryId: Long): List<ReadingOrder>
}
