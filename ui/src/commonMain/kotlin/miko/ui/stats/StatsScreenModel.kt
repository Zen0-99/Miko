package miko.ui.stats

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import miko.ui.MikoUiGraph
import miko.ui.carrier.BrowseCarrier

/**
 * Android's StatsScreen, scoped: per-carrier counts taken from the library
 * + history feeds — entries in library, unread/unseen items, history
 * sessions, and accumulated read time (shows have no duration column).
 */
class StatsScreenModel(
    private val graph: MikoUiGraph,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class CarrierStats(
        val carrier: BrowseCarrier,
        val libraryEntries: Int,
        val unreadItems: Long,
        val historySessions: Int,
        /** Milliseconds spent reading; null for the shows carrier. */
        val readDurationMs: Long?,
    )

    data class State(
        val initialized: Boolean = false,
        val carriers: List<CarrierStats> = emptyList(),
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            val stats = BrowseCarrier.entries
                .map { carrier -> async { statsFor(carrier) } }
                .map { it.await() }
            _state.value = State(initialized = true, carriers = stats)
        }
    }

    private suspend fun statsFor(carrier: BrowseCarrier): CarrierStats = when (carrier) {
        BrowseCarrier.SHOWS -> {
            val library = graph.getLibraryAnime.subscribe().first()
            val history = graph.getAnimeHistory.subscribe("").first()
            CarrierStats(
                carrier = carrier,
                libraryEntries = library.size,
                unreadItems = library.sumOf { it.unseenCount },
                historySessions = history.size,
                readDurationMs = null,
            )
        }
        BrowseCarrier.MANGA -> {
            val library = graph.getLibraryManga.subscribe().first()
            val history = graph.getMangaHistory.subscribe("").first()
            CarrierStats(
                carrier = carrier,
                libraryEntries = library.size,
                unreadItems = library.sumOf { it.unreadCount },
                historySessions = history.size,
                readDurationMs = history.sumOf { it.readDuration },
            )
        }
        BrowseCarrier.BOOKS -> {
            val library = graph.getLibraryNovels.subscribe().first()
            val history = graph.getNovelHistory.subscribe("").first()
            CarrierStats(
                carrier = carrier,
                libraryEntries = library.size,
                unreadItems = library.sumOf { it.unreadCount },
                historySessions = history.size,
                readDurationMs = history.sumOf { it.readDuration },
            )
        }
    }

    fun dispose() = scope.cancel()
}
