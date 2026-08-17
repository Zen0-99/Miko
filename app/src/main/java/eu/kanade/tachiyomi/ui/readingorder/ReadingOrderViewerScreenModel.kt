package eu.kanade.tachiyomi.ui.readingorder

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.EntryCover
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.readingorder.interactor.AddReadingOrderEdge
import tachiyomi.domain.readingorder.interactor.DeleteReadingOrder
import tachiyomi.domain.readingorder.interactor.GetLockedReadingOrders
import tachiyomi.domain.readingorder.interactor.GetReadingOrderEdges
import tachiyomi.domain.readingorder.interactor.GetReadingOrderNodes
import tachiyomi.domain.readingorder.interactor.GetReadingOrderProgress
import tachiyomi.domain.readingorder.interactor.GetReadingOrders
import tachiyomi.domain.readingorder.interactor.RemoveReadingOrderNode
import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.domain.readingorder.model.ReadingOrderEdge
import tachiyomi.domain.readingorder.model.ReadingOrderNode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReadingOrderViewerScreenModel(
    private val orderId: Long,
    private val getReadingOrders: GetReadingOrders = Injekt.get(),
    private val getReadingOrderNodes: GetReadingOrderNodes = Injekt.get(),
    private val getReadingOrderEdges: GetReadingOrderEdges = Injekt.get(),
    private val removeReadingOrderNode: RemoveReadingOrderNode = Injekt.get(),
    private val addReadingOrderEdge: AddReadingOrderEdge = Injekt.get(),
    private val deleteReadingOrder: DeleteReadingOrder = Injekt.get(),
    private val getReadingOrderProgress: GetReadingOrderProgress = Injekt.get(),
    private val getLockedReadingOrders: GetLockedReadingOrders = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
) : StateScreenModel<ReadingOrderViewerScreenModel.State>(State()) {

    @Immutable
    data class EntryInfo(
        val id: Long,
        val title: String,
        val cover: EntryCover?,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val order: ReadingOrder? = null,
        val layers: List<List<EntryInfo>> = emptyList(),
        val lockedEntryIds: Set<Long> = emptySet(),
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data class RemoveConfirm(val entry: EntryInfo) : Dialog
    }

    init {
        load()
    }

    private fun load() {
        screenModelScope.launchIO {
            val order = getReadingOrders.await(orderId)
            val nodes = getReadingOrderNodes.await(orderId)
            val edges = getReadingOrderEdges.await(orderId)
            val entryKind = order?.entryKind ?: "manga"
            val layers = computeLayers(nodes, edges, entryKind)
            val progressList = getReadingOrderProgress.awaitAll(orderId)
            val completedIds = progressList.filter { it.completed }.map { it.entryId }.toSet()
            val entryToLayer = computeEntryToLayerMap(nodes, edges)
            val maxLayer = entryToLayer.values.maxOrNull() ?: 0
            val lockedIds = mutableSetOf<Long>()
            for (currentLayer in 2..maxLayer) {
                val hasIncompletePrereq = (1 until currentLayer).any { prereqLayer ->
                    entryToLayer.entries.filter { it.value == prereqLayer }.any { it.key !in completedIds }
                }
                if (hasIncompletePrereq) {
                    entryToLayer.entries.filter { it.value >= currentLayer }.forEach {
                        lockedIds.add(it.key)
                    }
                }
            }
            mutableState.update {
                it.copy(isLoading = false, order = order, layers = layers, lockedEntryIds = lockedIds)
            }
        }
    }

    private suspend fun computeLayers(
        nodes: List<ReadingOrderNode>,
        edges: List<ReadingOrderEdge>,
        entryKind: String,
    ): List<List<EntryInfo>> {
        val entryToLayer = computeEntryToLayerMap(nodes, edges)
        val maxLayer = entryToLayer.values.maxOrNull() ?: 0
        val result = mutableListOf<List<EntryInfo>>()
        for (i in 0..maxLayer) {
            val layerEntryIds = entryToLayer.entries.filter { it.value == i }.map { it.key }
            val entries = layerEntryIds.mapNotNull { id ->
                loadEntryInfo(id, entryKind)
            }
            result.add(entries)
        }
        return result
    }

    private fun computeLayerCount(
        nodes: List<ReadingOrderNode>,
        edges: List<ReadingOrderEdge>,
    ): Int {
        val entryToLayer = computeEntryToLayerMap(nodes, edges)
        val maxLayer = entryToLayer.values.maxOrNull() ?: 0
        return maxLayer + 1
    }

    private fun computeEntryToLayerMap(
        nodes: List<ReadingOrderNode>,
        edges: List<ReadingOrderEdge>,
    ): Map<Long, Int> {
        val entryIds = nodes.map { it.entryId }.toSet()
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
        return entryToLayer
    }

    private suspend fun loadEntryInfo(id: Long, entryKind: String): EntryInfo? {
        return when (entryKind) {
            "manga" -> {
                val manga = getManga.await(id) ?: return null
                EntryInfo(
                    id = manga.id,
                    title = manga.title,
                    cover = MangaCover(
                        mangaId = manga.id,
                        sourceId = manga.source,
                        isMangaFavorite = manga.favorite,
                        url = manga.thumbnailUrl,
                        lastModified = manga.coverLastModified,
                    ),
                )
            }
            "anime" -> {
                val anime = getAnime.await(id) ?: return null
                EntryInfo(
                    id = anime.id,
                    title = anime.title,
                    cover = AnimeCover(
                        animeId = anime.id,
                        sourceId = anime.source,
                        isAnimeFavorite = anime.favorite,
                        url = anime.thumbnailUrl,
                        lastModified = anime.coverLastModified,
                    ),
                )
            }
            "novel" -> {
                val novel = getNovel.await(id) ?: return null
                EntryInfo(
                    id = novel.id,
                    title = novel.title,
                    cover = NovelCover(
                        novelId = novel.id,
                        sourceId = novel.source,
                        isNovelFavorite = novel.favorite,
                        url = novel.thumbnailUrl,
                        lastModified = novel.coverLastModified,
                    ),
                )
            }
            else -> null
        }
    }

    fun showRemoveConfirm(entry: EntryInfo) {
        mutableState.update { it.copy(dialog = Dialog.RemoveConfirm(entry)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun removeEntry(entry: EntryInfo) {
        screenModelScope.launchIO {
            val edges = getReadingOrderEdges.await(orderId)
            val prerequisites = edges.filter { it.toEntryId == entry.id }.map { it.fromEntryId }
            val dependents = edges.filter { it.fromEntryId == entry.id }.map { it.toEntryId }
            for (from in prerequisites) {
                for (to in dependents) {
                    if (from != to) {
                        addReadingOrderEdge.await(orderId, from, to)
                    }
                }
            }
            removeReadingOrderNode.await(orderId, entry.id)
            val remainingNodes = getReadingOrderNodes.await(orderId)
            if (remainingNodes.isNotEmpty()) {
                val remainingEdges = getReadingOrderEdges.await(orderId)
                val computedLayers = computeLayerCount(remainingNodes, remainingEdges)
                if (computedLayers < 2) {
                    deleteReadingOrder.await(orderId)
                    mutableState.update { it.copy(layers = emptyList(), order = null, dialog = null) }
                    return@launchIO
                }
            }
            closeDialog()
            load()
        }
    }
}
