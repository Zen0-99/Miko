package miko.ui.updates

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import miko.ui.MikoUiGraph
import miko.ui.carrier.BrowseCarrier
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.LocalItem
import miko.ui.carrier.NavTarget
import miko.ui.download.DownloadManager
import miko.ui.download.DownloadState
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

    enum class Mode { Updates, History, Upcoming }

    sealed interface Row {
        data class Header(val key: String, val label: String) : Row

        data class Update(
            val entryId: Long,
            val itemId: Long,
            val sourceId: Long,
            val entryTitle: String,
            val itemName: String,
            val itemUrl: String,
            val sourceName: String,
            val done: Boolean,
            val bookmarked: Boolean,
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

        /** Android's Upcoming feed — favorites with a stamped next_update. */
        data class Upcoming(
            val entryId: Long,
            val sourceId: Long,
            val entryUrl: String,
            val entryTitle: String,
            val nextUpdate: Long,
            val coverUrl: String?,
        ) : Row
    }

    data class State(
        val mode: Mode = Mode.Updates,
        val initialized: Boolean = false,
        val rows: List<Row> = emptyList(),
        /** Android's Upcoming calendar — date → entry count per day. */
        val events: Map<LocalDate, Int> = emptyMap(),
        /** Month the calendar renders (day-1 of the month), today by default. */
        val calendarMonthStart: LocalDate = currentMonthStart(),
        /** Android's updates selection mode — itemIds currently selected. */
        val selected: Set<Long> = emptySet(),
    ) {
        val selectionMode: Boolean get() = selected.isNotEmpty()
    }

    private val scope = CoroutineScope(Job() + dispatcher)

    private val modeFlow = MutableStateFlow(Mode.Updates)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Item-dir paths whose `.done` marker exists — update-row downloaded state. */
    private val _downloadedDirs = MutableStateFlow<Set<String>>(emptySet())
    val downloadedDirs: StateFlow<Set<String>> = _downloadedDirs.asStateFlow()

    // History-mode toolbar query — Android's HistoriesTab subscribe(query).
    private val historyQuery = MutableStateFlow("")

    /**
     * The (carrier, mode) feed the current rows came from — Android exits
     * action mode when the tab changes, so the selection resets on feed
     * swaps while surviving same-feed re-emissions (pruned to live ids).
     */
    private var lastFeed: Pair<BrowseCarrier, Mode>? = null

    init {
        scope.launch {
            carrierFlow.flatMapLatest { carrier ->
                modeFlow.flatMapLatest { mode ->
                    historyQuery.flatMapLatest { query ->
                        rowsFor(carrier, mode, query).map { (rows, events) ->
                            val feedChanged = lastFeed != (carrier to mode)
                            lastFeed = carrier to mode
                            val updateIds = rows.filterIsInstance<Row.Update>()
                                .map { it.itemId }.toSet()
                            _state.value.copy(
                                mode = mode,
                                initialized = true,
                                rows = rows,
                                events = events,
                                selected = if (feedChanged) {
                                    emptySet()
                                } else {
                                    _state.value.selected intersect updateIds
                                },
                            )
                        }
                    }
                }
            }.collect {
                _state.value = it
                rescanDownloads()
            }
        }
        scope.launch {
            // A download completing/removal flips row state without a DB write.
            DownloadManager.changes.collect { rescanDownloads() }
        }
    }

    private fun rescanDownloads() {
        val carrier = carrierFlow.value
        _downloadedDirs.value = _state.value.rows
            .filterIsInstance<Row.Update>()
            .filter { it.itemUrl.isNotBlank() && it.sourceName.isNotBlank() }
            .mapNotNull { row ->
                DownloadManager
                    .itemDir(carrier, row.sourceName, row.entryTitle, row.itemName)
                    .takeIf { DownloadManager.isDownloaded(it) }
                    ?.toString()
            }
            .toSet()
    }

    /** The row's item-dir key into [downloadedDirs]. */
    fun itemDirPath(row: Row.Update): String =
        DownloadManager
            .itemDir(carrierFlow.value, row.sourceName, row.entryTitle, row.itemName)
            .toString()

    /**
     * Android's update-row download tap — the indicator cycles
     * enqueue → cancel → delete (a downloaded item's tap removes it).
     */
    fun onDownloadClick(row: Row.Update) {
        if (row.itemUrl.isBlank() || row.sourceName.isBlank()) return
        scope.launch {
            val carrier = carrierFlow.value
            val dir = DownloadManager
                .itemDir(carrier, row.sourceName, row.entryTitle, row.itemName)
            when (DownloadManager.states.value[row.itemUrl]) {
                DownloadState.QUEUED,
                DownloadState.DOWNLOADING,
                -> DownloadManager.cancel(row.itemUrl)
                DownloadState.DOWNLOADED,
                null,
                -> if (DownloadManager.isDownloaded(dir)) {
                    DownloadManager
                        .delete(carrier, row.sourceName, row.entryTitle, row.itemName)
                } else {
                    val binding = graph.binding(carrier)
                    DownloadManager.enqueue(
                        carrier,
                        row.sourceName,
                        row.entryTitle,
                        row.itemName,
                        row.itemUrl,
                    ) { binding.itemPayload(row.sourceId, row.itemUrl) }
                }
                DownloadState.ERROR ->
                    DownloadManager.enqueue(
                        carrier,
                        row.sourceName,
                        row.entryTitle,
                        row.itemName,
                        row.itemUrl,
                    ) {
                        graph.binding(carrier).itemPayload(row.sourceId, row.itemUrl)
                    }
            }
        }
    }

    /**
     * Android's updates selection — long-press toggles an item into the
     * selection set; the mode exits when the set empties.
     */
    fun toggleSelection(row: Row.Update) {
        val selected = _state.value.selected.let {
            if (row.itemId in it) it - row.itemId else it + row.itemId
        }
        _state.value = _state.value.copy(selected = selected)
    }

    /** Android's select-all in the updates action-mode toolbar. */
    fun selectAll() {
        _state.value = _state.value.copy(
            selected = _state.value.rows.filterIsInstance<Row.Update>()
                .map { it.itemId }.toSet(),
        )
    }

    /** Android's invert-selection in the updates action-mode toolbar. */
    fun invertSelection() {
        val all = _state.value.rows.filterIsInstance<Row.Update>()
            .map { it.itemId }.toSet()
        _state.value = _state.value.copy(selected = all - _state.value.selected)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = emptySet())
    }

    private fun selectedRows(): List<Row.Update> =
        _state.value.rows.filterIsInstance<Row.Update>()
            .filter { it.itemId in _state.value.selected }

    /** Android's action-mode bookmark — flags every selected item. */
    fun bookmarkSelected(bookmark: Boolean) {
        val rows = selectedRows()
        scope.launch {
            val binding = graph.binding(carrierFlow.value)
            rows.forEach { runCatching { binding.setItemBookmark(it.itemId, bookmark) } }
        }
        clearSelection()
    }

    /** Android's action-mode mark read/unread. */
    fun markSelectedDone(done: Boolean) {
        val rows = selectedRows()
        scope.launch {
            val binding = graph.binding(carrierFlow.value)
            rows.forEach { runCatching { binding.setItemDone(it.itemId, done) } }
        }
        clearSelection()
    }

    /** Android's action-mode download — enqueues every selected item. */
    fun downloadSelected() {
        val rows = selectedRows()
        scope.launch {
            val carrier = carrierFlow.value
            val binding = graph.binding(carrier)
            rows
                .filter { it.itemUrl.isNotBlank() && it.sourceName.isNotBlank() }
                .forEach { row ->
                    runCatching {
                        DownloadManager.enqueue(
                            carrier,
                            row.sourceName,
                            row.entryTitle,
                            row.itemName,
                            row.itemUrl,
                        ) { binding.itemPayload(row.sourceId, row.itemUrl) }
                    }
                }
        }
        clearSelection()
    }

    /** Android's action-mode delete downloads. */
    fun deleteSelectedDownloads() {
        val rows = selectedRows()
        scope.launch {
            val carrier = carrierFlow.value
            rows.forEach { row ->
                runCatching {
                    DownloadManager.delete(
                        carrier,
                        row.sourceName,
                        row.entryTitle,
                        row.itemName,
                    )
                }
            }
        }
        clearSelection()
    }

    fun setMode(mode: Mode) {
        modeFlow.value = mode
    }

    fun setHistoryQuery(query: String) {
        historyQuery.value = query
    }

    /**
     * Manual library update — Android's toolbar refresh. Refetches every
     * favorite's item list; new chapters/episodes land in the updates view
     * (their date_fetch is stamped on insert). Rows re-emit through the DB
     * subscription; no explicit state write needed here.
     */
    fun refresh() {
        if (_refreshing.value) return
        scope.launch {
            _refreshing.value = true
            try {
                graph.binding(carrierFlow.value).refreshFavorites()
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun rowsFor(carrier: BrowseCarrier, mode: Mode, query: String) = when (mode) {
        Mode.Updates -> updatesRows(carrier).map { it to emptyMap() }
        Mode.History -> historyRows(carrier, query).map { it to emptyMap() }
        Mode.Upcoming -> upcomingRows(carrier, query)
    }

    private fun upcomingRows(
        carrier: BrowseCarrier,
        query: String,
    ): Flow<Pair<List<Row>, Map<LocalDate, Int>>> {
        // subscribe() is suspend on these interactors — emit the flow inline.
        val upcoming = when (carrier) {
            BrowseCarrier.SHOWS -> flow {
                emitAll(graph.getUpcomingAnime.subscribe())
            }.map { list ->
                list.map {
                    Row.Upcoming(
                        entryId = it.id,
                        sourceId = it.source,
                        entryUrl = it.url,
                        entryTitle = it.title,
                        nextUpdate = it.nextUpdate,
                        coverUrl = it.thumbnailUrl,
                    )
                }
            }
            BrowseCarrier.MANGA -> flow {
                emitAll(graph.getUpcomingManga.subscribe())
            }.map { list ->
                list.map {
                    Row.Upcoming(
                        entryId = it.id,
                        sourceId = it.source,
                        entryUrl = it.url,
                        entryTitle = it.title,
                        nextUpdate = it.nextUpdate,
                        coverUrl = it.thumbnailUrl,
                    )
                }
            }
            BrowseCarrier.BOOKS -> flow {
                emitAll(graph.getUpcomingNovel.subscribe())
            }.map { list ->
                list.map {
                    Row.Upcoming(
                        entryId = it.id,
                        sourceId = it.source,
                        entryUrl = it.url,
                        entryTitle = it.title,
                        nextUpdate = it.nextUpdate,
                        coverUrl = it.thumbnailUrl,
                    )
                }
            }
        }
        return upcoming.map { list ->
            val filtered = list
                .filter {
                    query.isBlank() || it.entryTitle.contains(query, ignoreCase = true)
                }
                .sortedBy { it.nextUpdate }
            val events = filtered.groupingBy {
                Instant.fromEpochMilliseconds(it.nextUpdate)
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
            }.eachCount()
            filtered.withUpcomingHeaders() to events
        }
    }

    /** Android's calendar month arrows — [monthStart] stays at day-1. */
    fun setCalendarMonth(monthStart: LocalDate) {
        _state.value = _state.value.copy(calendarMonthStart = monthStart)
    }

    private fun updatesRows(carrier: BrowseCarrier): Flow<List<Row>> {
        val binding = graph.binding(carrier)
        return when (carrier) {
            BrowseCarrier.SHOWS -> graph.getAnimeUpdates.subscribe(Instant.DISTANT_PAST).map { list ->
                val itemsByEntry = localItemsByEntry(binding, list.map { it.animeId })
                list.map {
                    val localItem = itemsByEntry[it.animeId]
                        ?.firstOrNull { item -> item.id == it.episodeId }
                    Row.Update(
                        entryId = it.animeId,
                        itemId = it.episodeId,
                        sourceId = it.sourceId,
                        entryTitle = it.animeTitle,
                        itemName = it.episodeName,
                        itemUrl = localItem?.url ?: "",
                        sourceName = binding.source(it.sourceId)?.name ?: "",
                        done = it.seen,
                        bookmarked = localItem?.bookmarked ?: false,
                        dateFetch = it.dateFetch,
                        coverUrl = it.coverData.url,
                    )
                }.withDateHeaders { it.dateFetch }
            }
            BrowseCarrier.MANGA -> graph.getMangaUpdates.subscribe(Instant.DISTANT_PAST).map { list ->
                val itemsByEntry = localItemsByEntry(binding, list.map { it.mangaId })
                list.map {
                    val localItem = itemsByEntry[it.mangaId]
                        ?.firstOrNull { item -> item.id == it.chapterId }
                    Row.Update(
                        entryId = it.mangaId,
                        itemId = it.chapterId,
                        sourceId = it.sourceId,
                        entryTitle = it.mangaTitle,
                        itemName = it.chapterName,
                        itemUrl = localItem?.url ?: "",
                        sourceName = binding.source(it.sourceId)?.name ?: "",
                        done = it.read,
                        bookmarked = localItem?.bookmarked ?: false,
                        dateFetch = it.dateFetch,
                        coverUrl = it.coverData.url,
                    )
                }.withDateHeaders { it.dateFetch }
            }
            BrowseCarrier.BOOKS -> graph.getNovelUpdates.subscribe(Instant.DISTANT_PAST).map { list ->
                val itemsByEntry = localItemsByEntry(binding, list.map { it.novelId })
                list.map {
                    val localItem = itemsByEntry[it.novelId]
                        ?.firstOrNull { item -> item.id == it.chapterId }
                    Row.Update(
                        entryId = it.novelId,
                        itemId = it.chapterId,
                        sourceId = it.sourceId,
                        entryTitle = it.novelTitle,
                        itemName = it.chapterName,
                        itemUrl = localItem?.url ?: "",
                        sourceName = binding.source(it.sourceId)?.name ?: "",
                        done = it.read,
                        bookmarked = localItem?.bookmarked ?: false,
                        dateFetch = it.dateFetch,
                        coverUrl = it.coverData.url,
                    )
                }.withDateHeaders { it.dateFetch }
            }
        }
    }

    /** One `getLocalItems` call per distinct entry — the row url lookup. */
    private suspend fun localItemsByEntry(
        binding: CarrierBinding,
        entryIds: List<Long>,
    ): Map<Long, List<LocalItem>> {
        val result = HashMap<Long, List<LocalItem>>()
        for (id in entryIds.toSet()) {
            result[id] = runCatching { binding.getLocalItems(id) }.getOrDefault(emptyList())
        }
        return result
    }

    private fun historyRows(carrier: BrowseCarrier, query: String) = when (carrier) {
        BrowseCarrier.SHOWS -> graph.getAnimeHistory.subscribe(query).map { list ->
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
        BrowseCarrier.MANGA -> graph.getMangaHistory.subscribe(query).map { list ->
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
        BrowseCarrier.BOOKS -> graph.getNovelHistory.subscribe(query).map { list ->
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

    /** Delegates to [CarrierBinding.resolveNavTarget] for the active carrier. */
    suspend fun resolveTarget(entryId: Long, itemId: Long): NavTarget? =
        graph.binding(carrierFlow.value).resolveNavTarget(entryId, itemId)

    /** Android's checkmark action — toggles read/seen on the chapter/episode. */
    fun markItemDone(itemId: Long, done: Boolean) {
        scope.launch {
            runCatching {
                graph.binding(carrierFlow.value).setItemDone(itemId, done)
            }
        }
    }

    /**
     * Android's HistoryDeleteDialog — [all] resets every history row for the
     * entry ("Reset all chapters for this entry"), else just the one row.
     */
    fun removeHistory(row: Row.History, all: Boolean) {
        scope.launch {
            runCatching {
                val binding = graph.binding(carrierFlow.value)
                if (all) {
                    binding.resetEntryHistory(row.entryId)
                } else {
                    binding.resetHistoryRow(row.id)
                }
            }
        }
    }

    /** Android's HistoryDeleteAllDialog — the History-mode toolbar wipe. */
    fun clearHistory() {
        scope.launch {
            runCatching {
                graph.binding(carrierFlow.value).clearHistory()
            }
        }
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

/** Same insertSeparators idiom for upcoming rows, bucketing on next_update. */
private fun List<UpdatesScreenModel.Row.Upcoming>.withUpcomingHeaders(): List<UpdatesScreenModel.Row> {
    val rows = mutableListOf<UpdatesScreenModel.Row>()
    var lastDate: LocalDate? = null
    for (row in this) {
        val date = Instant.fromEpochMilliseconds(row.nextUpdate)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        if (date != lastDate) {
            lastDate = date
            rows += UpdatesScreenModel.Row.Header(key = "u$date", label = dateLabel(date))
        }
        rows += row
    }
    return rows
}

private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** Day-1 of the current month — the calendar's initial page. */
internal fun currentMonthStart(): LocalDate {
    val today = Instant.fromEpochMilliseconds(kotlin.time.Clock.System.now().toEpochMilliseconds())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return LocalDate(today.year, today.month, 1)
}

/** "March 2026" — the calendar header's month label. */
internal fun monthLabel(monthStart: LocalDate): String =
    "${monthNames[monthStart.month.ordinal]} ${monthStart.year}"

/** Month shift for the ‹ › header arrows. */
internal fun LocalDate.shiftMonths(delta: Int): LocalDate {
    var year = this.year
    var month = this.monthNumber + delta
    while (month > 12) {
        month -= 12
        year++
    }
    while (month < 1) {
        month += 12
        year--
    }
    return LocalDate(year, month, 1)
}

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
