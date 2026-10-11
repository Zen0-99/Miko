package miko.ui.library

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import miko.ui.MikoUiGraph
import miko.ui.carrier.BrowseCarrier

/**
 * Library favorites for the active carrier — re-emits on every DB write
 * (favorite toggles, item sync, read/seen marks). A flattened
 * [LibraryEntry] keeps the grid carrier-agnostic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryScreenModel(
    graph: MikoUiGraph,
    carrierFlow: StateFlow<BrowseCarrier>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class LibraryEntry(
        val id: Long,
        val sourceId: Long,
        val url: String,
        val title: String,
        val coverUrl: String?,
        val unreadCount: Long,
    )

    data class State(
        val initialized: Boolean = false,
        val entries: List<LibraryEntry> = emptyList(),
        /** Android's display_library_title — covers show title overlays. */
        val showTitle: Boolean = true,
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            carrierFlow.flatMapLatest { carrier ->
                when (carrier) {
                    BrowseCarrier.SHOWS -> graph.getLibraryAnime.subscribe().map { list ->
                        list.map {
                            LibraryEntry(
                                id = it.id,
                                sourceId = it.anime.source,
                                url = it.anime.url,
                                title = it.anime.title,
                                coverUrl = it.anime.thumbnailUrl,
                                unreadCount = it.unseenCount,
                            )
                        }
                    }
                    BrowseCarrier.MANGA -> graph.getLibraryManga.subscribe().map { list ->
                        list.map {
                            LibraryEntry(
                                id = it.id,
                                sourceId = it.manga.source,
                                url = it.manga.url,
                                title = it.manga.title,
                                coverUrl = it.manga.thumbnailUrl,
                                unreadCount = it.unreadCount,
                            )
                        }
                    }
                    BrowseCarrier.BOOKS -> graph.getLibraryNovels.subscribe().map { list ->
                        list.map {
                            LibraryEntry(
                                id = it.id,
                                sourceId = it.novel.source,
                                url = it.novel.url,
                                title = it.novel.title,
                                coverUrl = it.novel.thumbnailUrl,
                                unreadCount = it.unreadCount,
                            )
                        }
                    }
                }
            }.collect { entries ->
                _state.value = _state.value.copy(initialized = true, entries = entries)
            }
        }
        scope.launch {
            graph.preferenceStore
                .getBoolean("display_library_title", true)
                .changes()
                .collect { show -> _state.value = _state.value.copy(showTitle = show) }
        }
    }

    fun dispose() = scope.cancel()
}
