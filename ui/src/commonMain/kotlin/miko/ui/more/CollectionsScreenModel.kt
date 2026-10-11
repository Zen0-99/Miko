package miko.ui.more

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import miko.ui.MikoUiGraph
import miko.ui.carrier.BrowseCarrier
import tachiyomi.domain.collection.model.Collection

/**
 * Android's CollectionsTab model — the visible collections for the active
 * carrier with create/rename/delete writes routed through the binding.
 */
class CollectionsScreenModel(
    private val graph: MikoUiGraph,
    private val carrierFlow: StateFlow<BrowseCarrier>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class State(
        val collections: List<Collection> = emptyList(),
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            carrierFlow.flatMapLatest { carrier ->
                graph.binding(carrier).collections()
            }.collect { collections ->
                _state.value = _state.value.copy(collections = collections)
            }
        }
    }

    fun createCollection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            graph.binding(carrierFlow.value).createCollection(trimmed)
        }
    }

    fun renameCollection(collection: Collection, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == collection.name) return
        scope.launch {
            graph.binding(carrierFlow.value).renameCollection(collection.id, trimmed)
        }
    }

    fun deleteCollection(collection: Collection) {
        scope.launch {
            graph.binding(carrierFlow.value).deleteCollection(collection.id)
        }
    }

    fun dispose() = scope.cancel()
}
