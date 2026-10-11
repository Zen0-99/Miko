package miko.ui.updates

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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import miko.ui.MikoUiGraph
import miko.ui.carrier.BrowseCarrier
import kotlin.time.Instant

/**
 * Updates + History — Android's UpdatesTab with the History sub-tab. The
 * [carrierFlow] switches which carrier's updates/history feeds the list;
 * [modeFlow] switches between the two sub-tabs. Update rows carry entry +
 * item ids (the *UpdatesWithRelations shape) — navigation targets resolve
 * through the carrier binding at click time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesScreenModel(
    private val graph: MikoUiGraph,
    private val carrierFlow: StateFlow<BrowseCarrier>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    enum class Mode { Updates, History }

    sealed interface Row {
        data class Header(val key: String, val label: String) : Row

        data class Update(
            val entryId: Long,
            val itemId: Long,
            val sourceId: Long,
            val entryTitle: String,
            val itemName: String,
            val done: Boolean,
            val dateFetch: Long,
            val coverUrl: String?,
        ) : Row

        data class History(
            val id: Long,
            val entryId: Long,
            val itemId: Long,
            val entryTitle: String,
            val itemName: String,
            val readAt: Long?,
            val coverUrl: String?,
        ) : Row
    }

    data class State(
        val mode: Mode = Mode.Updates,
        val initialized: Boolean = false,
        val rows: List<Row> = emptyList(),
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val modeFlow = MutableStateFlow(Mode.Updates)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            carrierFlow.flatMapLatest { carrier ->
                modeFlow.flatMapLatest { mode ->
                    rowsFor(carrier, mode).map { rows ->
                        State(mode = mode, initialized = true, rows = rows)
                    }
                }
            }.collect { _state.value = it }
        }
    }

    fun setMode(mode: Mode) {
        modeFlow.value = mode
    }

    private fun rowsFor(carrier: BrowseCarrier, mode: Mode) = when (mode) {
        Mode.Updates -> updatesRows(carrier)
        Mode.History -> historyRows(carrier)
    }

    private fun updatesRows(carrier: BrowseCarrier) = when (carrier) {
        BrowseCarrier.SHOWS -> graph.getAnimeUpdates.subscribe(Instant.DISTANT_PAST).map { list ->
            list.map {
                Row.Update(
                    entryId = it.animeId,
                    itemId = it.episodeId,
                    sourceId = it.sourceId,
                    entryTitle = it.animeTitle,
                    itemName = it.episodeName,
                    done = it.seen,
                    dateFetch = it.dateFetch,
                    coverUrl = it.coverData.url,
                )
            }.withDateHeaders { it.dateFetch }
        }
        BrowseCarrier.MANGA -> graph.getMangaUpdates.subscribe(Instant.DISTANT_PAST).map { list ->
            list.map {
                Row.Update(
                    entryId = it.mangaId,
                    itemId = it.chapterId,
                    sourceId = it.sourceId,
                    entryTitle = it.mangaTitle,
                    itemName = it.chapterName,
                    done = it.read,
                    dateFetch = it.dateFetch,
                    coverUrl = it.coverData.url,
                )
            }.withDateHeaders { it.dateFetch }
        }
        BrowseCarrier.BOOKS -> graph.getNovelUpdates.subscribe(Instant.DISTANT_PAST).map { list ->
            list.map {
                Row.Update(
                    entryId = it.novelId,
                    itemId = it.chapterId,
                    sourceId = it.sourceId,
                    entryTitle = it.novelTitle,
                    itemName = it.chapterName,
                    done = it.read,
                    dateFetch = it.dateFetch,
                    coverUrl = it.coverData.url,
                )
            }.withDateHeaders { it.dateFetch }
        }
    }

    private fun historyRows(carrier: BrowseCarrier) = when (carrier) {
        BrowseCarrier.SHOWS -> graph.getAnimeHistory.subscribe("").map { list ->
            list.map {
                Row.History(
                    id = it.id,
                    entryId = it.animeId,
                    itemId = it.episodeId,
                    entryTitle = it.title,
                    itemName = "Ep. ${formatNumber(it.episodeNumber)}",
                    readAt = it.seenAt?.toEpochMilliseconds(),
                    coverUrl = it.coverData.url,
                )
            }
        }
        BrowseCarrier.MANGA -> graph.getMangaHistory.subscribe("").map { list ->
            list.map {
                Row.History(
                    id = it.id,
                    entryId = it.mangaId,
                    itemId = it.chapterId,
                    entryTitle = it.title,
                    itemName = "Ch. ${formatNumber(it.chapterNumber)}",
                    readAt = it.readAt?.toEpochMilliseconds(),
                    coverUrl = it.coverData.url,
                )
            }
        }
        BrowseCarrier.BOOKS -> graph.getNovelHistory.subscribe("").map { list ->
            list.map {
                Row.History(
                    id = it.id,
                    entryId = it.novelId,
                    itemId = it.chapterId,
                    entryTitle = it.title,
                    itemName = it.chapterName.orEmpty(),
                    readAt = it.readAt?.toEpochMilliseconds(),
                    coverUrl = it.coverData.url,
                )
            }
        }
    }

    /** Resolved navigation target for a row — ids mapped to urls + source. */
    data class NavTarget(val sourceId: Long, val entryUrl: String, val itemUrl: String)

    /**
     * Resolves an update/history row to urls for navigation — the feed rows
     * carry ids, and details/reader destinations are url-keyed. The source
     * id comes from the local entry (history rows don't carry it).
     */
    suspend fun resolveTarget(entryId: Long, itemId: Long): NavTarget? {
        val binding = graph.binding(carrierFlow.value)
        val local = binding.localEntryById(entryId) ?: return null
        val item = binding.getLocalItems(local.id)
            .firstOrNull { it.id == itemId }
            ?: return null
        return NavTarget(local.sourceId, local.url, item.url)
    }

    fun dispose() = scope.cancel()
}

/** Android's insertSeparators: a header row whenever the LocalDate changes. */
private fun List<UpdatesScreenModel.Row.Update>.withDateHeaders(
    dateOf: (UpdatesScreenModel.Row.Update) -> Long,
): List<UpdatesScreenModel.Row> {
    val rows = mutableListOf<UpdatesScreenModel.Row>()
    var lastDate: LocalDate? = null
    for (row in this) {
        val date = Instant.fromEpochMilliseconds(dateOf(row))
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        if (date != lastDate) {
            lastDate = date
            rows += UpdatesScreenModel.Row.Header(
                key = "d$date",
                label = dateLabel(date),
            )
        }
        rows += row
    }
    return rows
}

private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** "1" for whole numbers, "2.5" otherwise — chapter/episode display. */
private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/** Today shows as "Today" (relative_time_today); other days as "March 5, 2024". */
internal fun dateLabel(date: LocalDate): String {
    val today = Instant.fromEpochMilliseconds(kotlin.time.Clock.System.now().toEpochMilliseconds())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    if (date == today) return "Today"
    return "${monthNames[date.month.ordinal]} ${date.day}, ${date.year}"
}
