package tachiyomi.data.readingorder

import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.domain.readingorder.model.ReadingOrderEdge
import tachiyomi.domain.readingorder.model.ReadingOrderNode
import tachiyomi.domain.readingorder.model.ReadingOrderProgress
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class ReadingOrderRepositoryImpl(
    private val handler: MangaDatabaseHandler,
) : ReadingOrderRepository {

    override suspend fun getReadingOrder(id: Long): ReadingOrder? {
        return handler.awaitOneOrNull {
            reading_orderQueries.getReadingOrderById(id, ::mapReadingOrder)
        }
    }

    override suspend fun getAllReadingOrders(): List<ReadingOrder> {
        return handler.awaitList {
            reading_orderQueries.getAllReadingOrders(::mapReadingOrder)
        }
    }

    override suspend fun insertReadingOrder(name: String, description: String?): Long {
        val now = System.currentTimeMillis()
        return handler.awaitOneExecutable(inTransaction = true) {
            reading_orderQueries.insertReadingOrder(name, description, now, now)
            reading_orderQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun updateReadingOrder(id: Long, name: String, description: String?) {
        handler.await {
            reading_orderQueries.updateReadingOrder(
                name = name,
                description = description,
                updatedAt = System.currentTimeMillis(),
                id = id,
            )
        }
    }

    override suspend fun deleteReadingOrder(id: Long) {
        handler.await {
            reading_orderQueries.deleteReadingOrder(id)
        }
    }

    override suspend fun getNodes(orderId: Long): List<ReadingOrderNode> {
        return handler.awaitList {
            reading_order_nodeQueries.getNodesByOrderId(orderId, ::mapNode)
        }
    }

    override suspend fun addNode(orderId: Long, mangaId: Long): Long {
        val maxPos = handler.awaitOneOrNull<Long> {
            reading_order_nodeQueries.getMaxPosition(orderId) { maxPos: Long? -> maxPos ?: -1L }
        } ?: -1L
        return handler.awaitOneExecutable(inTransaction = true) {
            reading_order_nodeQueries.insertNode(orderId, mangaId, maxPos + 1L)
            reading_order_nodeQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun removeNode(orderId: Long, mangaId: Long) {
        handler.await {
            reading_order_edgeQueries.deleteEdgesByMangaId(orderId, mangaId)
            reading_order_progressQueries.deleteProgressByMangaId(orderId, mangaId)
            reading_order_nodeQueries.deleteNode(orderId, mangaId)
        }
    }

    override suspend fun getEdges(orderId: Long): List<ReadingOrderEdge> {
        return handler.awaitList {
            reading_order_edgeQueries.getEdgesByOrderId(orderId, ::mapEdge)
        }
    }

    override suspend fun addEdge(orderId: Long, fromMangaId: Long, toMangaId: Long) {
        handler.await {
            reading_order_edgeQueries.insertEdge(orderId, fromMangaId, toMangaId)
        }
    }

    override suspend fun removeEdge(orderId: Long, fromMangaId: Long, toMangaId: Long) {
        handler.await {
            reading_order_edgeQueries.deleteEdge(orderId, fromMangaId, toMangaId)
        }
    }

    override suspend fun getPrerequisites(orderId: Long, mangaId: Long): List<Long> {
        return handler.awaitList {
            reading_order_edgeQueries.getPrerequisites(orderId, mangaId)
        }
    }

    override suspend fun getProgress(orderId: Long, mangaId: Long): ReadingOrderProgress? {
        return handler.awaitOneOrNull {
            reading_order_progressQueries.getProgress(
                orderId,
                mangaId,
            ) { completed, completedAt ->
                ReadingOrderProgress(orderId, mangaId, completed == 1L, completedAt)
            }
        }
    }

    override suspend fun getAllProgress(orderId: Long): List<ReadingOrderProgress> {
        return handler.awaitList {
            reading_order_progressQueries.getAllProgressByOrderId(orderId) { mangaId, completed, completedAt ->
                ReadingOrderProgress(orderId, mangaId, completed == 1L, completedAt)
            }
        }
    }

    override suspend fun setProgress(orderId: Long, mangaId: Long, completed: Boolean, completedAt: Long?) {
        handler.await {
            reading_order_progressQueries.upsertProgress(
                orderId = orderId,
                mangaId = mangaId,
                completed = if (completed) 1L else 0L,
                completedAt = completedAt,
            )
        }
    }

    override suspend fun getReadingOrdersForManga(mangaId: Long): List<ReadingOrder> {
        val allOrders = handler.awaitList {
            reading_orderQueries.getAllReadingOrders(::mapReadingOrder)
        }
        return allOrders.filter { order ->
            handler.awaitOneOrNull {
                reading_order_nodeQueries.getNodeByMangaId(order.id, mangaId) { id, _, _, _ -> id }
            } != null
        }
    }

    private fun mapReadingOrder(
        id: Long,
        name: String,
        description: String?,
        createdAt: Long,
        updatedAt: Long,
    ): ReadingOrder = ReadingOrder(id, name, description, createdAt, updatedAt)

    private fun mapNode(
        id: Long,
        orderId: Long,
        mangaId: Long,
        position: Long,
    ): ReadingOrderNode = ReadingOrderNode(id, orderId, mangaId, position)

    private fun mapEdge(
        id: Long,
        orderId: Long,
        fromMangaId: Long,
        toMangaId: Long,
    ): ReadingOrderEdge = ReadingOrderEdge(id, orderId, fromMangaId, toMangaId)
}
