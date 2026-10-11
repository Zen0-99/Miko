package miko.ui.home

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
import miko.ui.carrier.NavTarget

/**
 * Home tab — scoped port of Android's HomeHub: greeting + hero "continue"
 * card + a recents row, all driven by the carrier's history feed (the
 * recommendation/discovery feed engine is deferred with the Discovery tab).
 * Recents dedupe by entry so each series appears once, newest activity first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenModel(
    private val graph: MikoUiGraph,
    private val carrierFlow: StateFlow<BrowseCarrier>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class RecentItem(
        val entryId: Long,
        val itemId: Long,
        val entryTitle: String,
        val itemName: String,
        val coverUrl: String?,
        val readAt: Long?,
    )

    enum class Greeting { Morning, Afternoon, Evening, Night }

    data class State(
        val initialized: Boolean = false,
        val recents: List<RecentItem> = emptyList(),
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            carrierFlow.flatMapLatest { carrier ->
                recentsFor(carrier).map { recents ->
                    State(initialized = true, recents = recents)
                }
            }.collect { _state.value = it }
        }
    }

    /** Time-of-day greeting, Android's home_greeting_* split. */
    fun greeting(hour: Int): Greeting = when (hour) {
        in 5..11 -> Greeting.Morning
        in 12..16 -> Greeting.Afternoon
        in 17..22 -> Greeting.Evening
        else -> Greeting.Night
    }

    private fun recentsFor(carrier: BrowseCarrier) = when (carrier) {
        BrowseCarrier.SHOWS -> graph.getAnimeHistory.subscribe("").map { list ->
            list.map {
                RecentItem(
                    entryId = it.animeId,
                    itemId = it.episodeId,
                    entryTitle = it.title,
                    itemName = "Ep. ${formatNumber(it.episodeNumber)}",
                    coverUrl = it.coverData.url,
                    readAt = it.seenAt?.toEpochMilliseconds(),
                )
            }
        }
        BrowseCarrier.MANGA -> graph.getMangaHistory.subscribe("").map { list ->
            list.map {
                RecentItem(
                    entryId = it.mangaId,
                    itemId = it.chapterId,
                    entryTitle = it.title,
                    itemName = "Ch. ${formatNumber(it.chapterNumber)}",
                    coverUrl = it.coverData.url,
                    readAt = it.readAt?.toEpochMilliseconds(),
                )
            }
        }
        BrowseCarrier.BOOKS -> graph.getNovelHistory.subscribe("").map { list ->
            list.map {
                RecentItem(
                    entryId = it.novelId,
                    itemId = it.chapterId,
                    entryTitle = it.title,
                    itemName = it.chapterName
                        ?: "Ch. ${formatNumber(it.chapterNumber)}",
                    coverUrl = it.coverData.url,
                    readAt = it.readAt?.toEpochMilliseconds(),
                )
            }
        }
    }.map { rows ->
        rows.distinctBy { it.entryId }.take(RECENT_LIMIT)
    }

    suspend fun resolveTarget(entryId: Long, itemId: Long): NavTarget? =
        graph.binding(carrierFlow.value).resolveNavTarget(entryId, itemId)

    fun dispose() = scope.cancel()

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private companion object {
        const val RECENT_LIMIT = 12
    }
}
