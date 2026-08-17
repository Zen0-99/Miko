package eu.kanade.tachiyomi.ui.readingorder

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.readingorder.interactor.AddReadingOrderNode
import tachiyomi.domain.readingorder.interactor.GetReadingOrderEdges
import tachiyomi.domain.readingorder.interactor.GetReadingOrderNodes
import tachiyomi.domain.readingorder.interactor.GetReadingOrderProgress
import tachiyomi.domain.readingorder.interactor.GetReadingOrders
import tachiyomi.domain.readingorder.interactor.RemoveReadingOrderNode
import tachiyomi.domain.readingorder.interactor.SetReadingOrderProgress
import tachiyomi.domain.readingorder.model.ReadingOrder
import tachiyomi.domain.readingorder.model.ReadingOrderEdge
import tachiyomi.domain.readingorder.model.ReadingOrderNode
import tachiyomi.domain.readingorder.model.ReadingOrderProgress
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReadingOrderEditorScreenModel(
    private val orderId: Long,
    private val getReadingOrders: GetReadingOrders = Injekt.get(),
    private val getReadingOrderNodes: GetReadingOrderNodes = Injekt.get(),
    private val getReadingOrderEdges: GetReadingOrderEdges = Injekt.get(),
    private val getReadingOrderProgress: GetReadingOrderProgress = Injekt.get(),
    private val addReadingOrderNode: AddReadingOrderNode = Injekt.get(),
    private val removeReadingOrderNode: RemoveReadingOrderNode = Injekt.get(),
    private val setReadingOrderProgress: SetReadingOrderProgress = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
) : StateScreenModel<ReadingOrderEditorScreenModel.State>(State()) {

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val order: ReadingOrder? = null,
        val nodes: List<ReadingOrderNode> = emptyList(),
        val edges: List<ReadingOrderEdge> = emptyList(),
        val progress: Map<Long, ReadingOrderProgress> = emptyMap(),
        val mangaMap: Map<Long, Manga> = emptyMap(),
        val libraryManga: List<Manga> = emptyList(),
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object AddManga : Dialog
    }

    init {
        load()
    }

    private fun load() {
        screenModelScope.launchIO {
            val order = getReadingOrders.await(orderId)
            val nodes = getReadingOrderNodes.await(orderId)
            val edges = getReadingOrderEdges.await(orderId)
            val progressList = getReadingOrderProgress.awaitAll(orderId)
            val progressMap = progressList.associateBy { it.entryId }

            val mangaIds = nodes.map { it.entryId }
            val mangaMap = mutableMapOf<Long, Manga>()
            for (mangaId in mangaIds) {
                getManga.await(mangaId)?.let { mangaMap[it.id] = it }
            }

            val libraryManga = getLibraryManga.await().map { it.manga }

            mutableState.update {
                it.copy(
                    isLoading = false,
                    order = order,
                    nodes = nodes,
                    edges = edges,
                    progress = progressMap,
                    mangaMap = mangaMap,
                    libraryManga = libraryManga,
                )
            }
        }
    }

    fun showAddMangaDialog() {
        mutableState.update { it.copy(dialog = Dialog.AddManga) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun addManga(mangaId: Long) {
        screenModelScope.launchIO {
            addReadingOrderNode.await(orderId, mangaId)
            refresh()
            closeDialog()
        }
    }

    fun removeManga(mangaId: Long) {
        screenModelScope.launchIO {
            removeReadingOrderNode.await(orderId, mangaId)
            refresh()
        }
    }

    fun toggleCompleted(mangaId: Long) {
        screenModelScope.launchIO {
            val current = state.value.progress[mangaId]
            val newCompleted = !(current?.completed ?: false)
            setReadingOrderProgress.await(orderId, mangaId, newCompleted)
            refresh()
        }
    }

    private suspend fun refresh() {
        val nodes = getReadingOrderNodes.await(orderId)
        val edges = getReadingOrderEdges.await(orderId)
        val progressList = getReadingOrderProgress.awaitAll(orderId)
        val progressMap = progressList.associateBy { it.entryId }

        val mangaIds = nodes.map { it.entryId }
        val mangaMap = mutableMapOf<Long, Manga>()
        for (mangaId in mangaIds) {
            getManga.await(mangaId)?.let { mangaMap[it.id] = it }
        }

        mutableState.update {
            it.copy(
                nodes = nodes,
                edges = edges,
                progress = progressMap,
                mangaMap = mangaMap,
            )
        }
    }
}
