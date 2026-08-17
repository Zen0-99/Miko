package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository
import tachiyomi.domain.readingorder.model.ReadingOrderEdge

class AutoRemoveCompletedReadingOrderEntries(
    private val repository: ReadingOrderRepository,
    private val getReadingOrders: GetReadingOrders,
    private val getReadingOrderNodes: GetReadingOrderNodes,
    private val getReadingOrderEdges: GetReadingOrderEdges,
    private val removeReadingOrderNode: RemoveReadingOrderNode,
    private val addReadingOrderEdge: AddReadingOrderEdge,
    private val deleteReadingOrder: DeleteReadingOrder,
    private val getManga: GetManga,
    private val getAnime: GetAnime,
    private val getNovel: GetNovel,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val getChaptersByNovelId: GetNovelChaptersByNovelId,
) {

    suspend fun await(): Boolean {
        var anyRemoved = false
        val allOrders = mutableListOf<Long>()
        val mangaOrders = getReadingOrders.await("manga")
        val animeOrders = getReadingOrders.await("anime")
        val novelOrders = getReadingOrders.await("novel")
        allOrders.addAll(mangaOrders.map { it.id })
        allOrders.addAll(animeOrders.map { it.id })
        allOrders.addAll(novelOrders.map { it.id })

        for (orderId in allOrders) {
            val order = getReadingOrders.await(orderId) ?: continue
            val nodes = getReadingOrderNodes.await(orderId)
            val edges = getReadingOrderEdges.await(orderId)
            val completedEntryIds = mutableSetOf<Long>()

            for (node in nodes) {
                if (isEntryCompleted(node.entryId, order.entryKind)) {
                    completedEntryIds.add(node.entryId)
                }
            }

            for (entryId in completedEntryIds) {
                val prerequisites = edges.filter { it.toEntryId == entryId }.map { it.fromEntryId }
                val dependents = edges.filter { it.fromEntryId == entryId }.map { it.toEntryId }
                for (from in prerequisites) {
                    for (to in dependents) {
                        if (from != to) {
                            addReadingOrderEdge.await(orderId, from, to)
                        }
                    }
                }
                removeReadingOrderNode.await(orderId, entryId)
                anyRemoved = true
            }

            if (anyRemoved) {
                val remainingNodes = getReadingOrderNodes.await(orderId)
                if (remainingNodes.isNotEmpty()) {
                    val remainingEdges = getReadingOrderEdges.await(orderId)
                    val layerCount = computeLayerCount(remainingNodes.map { it.entryId }.toSet(), remainingEdges)
                    if (layerCount < 2) {
                        deleteReadingOrder.await(orderId)
                    }
                } else {
                    deleteReadingOrder.await(orderId)
                }
            }
        }
        return anyRemoved
    }

    private suspend fun isEntryCompleted(entryId: Long, entryKind: String): Boolean {
        return when (entryKind) {
            "manga" -> {
                val manga = getManga.await(entryId) ?: return false
                val chapters = getChaptersByMangaId.await(entryId)
                chapters.isNotEmpty() && chapters.all { it.read }
            }
            "anime" -> {
                val anime = getAnime.await(entryId) ?: return false
                val episodes = getEpisodesByAnimeId.await(entryId)
                episodes.isNotEmpty() && episodes.all { it.seen }
            }
            "novel" -> {
                val novel = getNovel.await(entryId) ?: return false
                val chapters = getChaptersByNovelId.await(entryId)
                chapters.isNotEmpty() && chapters.all { it.read }
            }
            else -> false
        }
    }

    private fun computeLayerCount(entryIds: Set<Long>, edges: List<ReadingOrderEdge>): Int {
        val entryToLayer = mutableMapOf<Long, Int>()
        val remaining = entryIds.toMutableSet()
        var layer = 0
        while (remaining.isNotEmpty()) {
            val layerItems = remaining.filter { id ->
                edges.none { it.toEntryId == id && it.fromEntryId in remaining }
            }
            if (layerItems.isEmpty()) {
                remaining.forEach { entryToLayer[it] = layer }
                break
            }
            layerItems.forEach { id ->
                entryToLayer[id] = layer
                remaining.remove(id)
            }
            layer++
        }
        val maxLayer = entryToLayer.values.maxOrNull() ?: 0
        return maxLayer + 1
    }
}
