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
    private val graph: MikoUiGraph,
    private val carrierFlow: StateFlow<BrowseCarrier>,
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
        /** Toolbar search filter — title substring, case-insensitive. */
        val query: String = "",
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var rawEntries: List<LibraryEntry> = emptyList()

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
                rawEntries = entries
                _state.value = _state.value.copy(
                    initialized = true,
                    entries = applyQuery(entries, _state.value.query),
                )
            }
        }
        scope.launch {
            graph.preferenceStore
                .getBoolean("display_library_title", true)
                .changes()
                .collect { show -> _state.value = _state.value.copy(showTitle = show) }
        }
    }

    /** Android's library toolbar search — filters by title substring. */
    fun setQuery(query: String) {
        _state.value = _state.value.copy(
            query = query,
            entries = applyQuery(rawEntries, query),
        )
    }

    /** Android's selection action — unfavorites the entry. */
    fun removeFromLibrary(entry: LibraryEntry) {
        scope.launch {
            graph.binding(carrierFlow.value).setFavorite(entry.id, false)
        }
    }

    /** Android's selection action — marks every item read/seen. */
    fun markAllDone(entry: LibraryEntry) {
        scope.launch {
            graph.binding(carrierFlow.value).setAllItemsDone(entry.id, true)
        }
    }

    private fun applyQuery(
        entries: List<LibraryEntry>,
        query: String,
    ): List<LibraryEntry> {
        val needle = query.trim()
        if (needle.isEmpty()) return entries
        return entries.filter { it.title.contains(needle, ignoreCase = true) }
    }

    fun dispose() = scope.cancel()
}
