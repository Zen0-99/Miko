package miko.ui.details

import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import miko.domain.shows.SeasonDisplayMode
import miko.ui.carrier.BrowseCarrier
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.CarrierSource
import miko.ui.carrier.ChapterSortMode
import miko.ui.carrier.ItemSwipeAction
import miko.ui.carrier.LocalEntry
import miko.ui.carrier.LocalSeason
import miko.ui.carrier.RemoteDetails
import miko.ui.carrier.RemoteItem
import miko.ui.carrier.SeasonFlagOp
import miko.ui.carrier.ordered
import miko.ui.carrier.triStateMatch
import miko.ui.download.DownloadManager
import miko.ui.download.DownloadState
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.entries.shows.model.Anime

/**
 * Entry details — remote details + item list (chapters/episodes), local
 * favorite state, local done state, and the continue/mark-all affordances
 * Android's `EntryContinueRow` drives off of. Carrier-agnostic via
 * [CarrierBinding]; the same model serves books, manga, and shows.
 */
class EntryDetailsScreenModel(
    private val binding: CarrierBinding,
    private val sourceId: Long,
    private val entryUrl: String,
    private val carrier: BrowseCarrier = BrowseCarrier.BOOKS,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class State(
        val sourceName: String = "",
        val details: RemoteDetails? = null,
        val local: LocalEntry? = null,
        val items: List<RemoteItem> = emptyList(),
        val doneUrls: Set<String> = emptySet(),
        /** Bookmarked item urls — Android's chapter bookmark badges. */
        val bookmarkedUrls: Set<String> = emptySet(),
        /** Partial read positions — manga "Page: N" pill / novel "% Read" pill. */
        val progressPages: Map<String, Int> = emptyMap(),
        val progressPercents: Map<String, Int> = emptyMap(),
        /** Partial watch positions (shows) — pill + bottom-edge progress bar. */
        val progressSeconds: Map<String, Pair<Long, Long>> = emptyMap(),
        val favorite: Boolean = false,
        val detailsLoading: Boolean = true,
        val itemsLoading: Boolean = true,
        val error: String? = null,
        /** Entry chapterFlags semantics — sort + filters + display mode. */
        val sortDescending: Boolean = false,
        val sortMode: ChapterSortMode = ChapterSortMode.NUMBER,
        val unreadFilter: TriState = TriState.DISABLED,
        val bookmarkedFilter: TriState = TriState.DISABLED,
        val fillermarkedFilter: TriState = TriState.DISABLED,
        val displayByNumber: Boolean = false,
        val showPreviews: Boolean = true,
        val showSummaries: Boolean = true,
        /** Filler episode urls (shows) — decorates the filler filter. */
        val fillermarkedUrls: Set<String> = emptySet(),
        /** Android's action-mode selection — urls of the ticked rows. */
        val selectedUrls: Set<String> = emptySet(),
        /** Android's row swipe prefs — (start-edge, end-edge) actions. */
        val swipeStart: ItemSwipeAction = ItemSwipeAction.Disabled,
        val swipeEnd: ItemSwipeAction = ItemSwipeAction.Disabled,
        /** Sanitized item names whose download dir holds the .done marker. */
        val downloadedNames: Set<String> = emptySet(),
        /** Live queue states keyed by item url — Android's DownloadState. */
        val downloadStates: Map<String, DownloadState> = emptyMap(),
        val downloadProgress: Map<String, Float> = emptyMap(),
        /** ChangeCollectionDialog — visible collections + this entry's ids. */
        val collectionSheetOpen: Boolean = false,
        val collections: List<Collection> = emptyList(),
        val entryCollectionIds: Set<Long> = emptySet(),
        /** Migrate — target-source picker + in-flight flag. */
        val migrateSheetOpen: Boolean = false,
        val migrateSources: List<CarrierSource> = emptyList(),
        val migrating: Boolean = false,
        /** Scanlator filter — Android's ScanlatorFilterDialog state. */
        val scanlatorSheetOpen: Boolean = false,
        val scanlatorNames: List<String> = emptyList(),
        val excludedScanlators: Set<String> = emptySet(),
        /**
         * Shows — season rows + aggregates (Android's `processedSeasons`
         * source). Non-empty only on structured (FetchType.Seasons) entries.
         */
        val seasons: List<LocalSeason> = emptyList(),
        /** Android's SeasonSettingsSheet dialog flag. */
        val seasonSettingsOpen: Boolean = false,
    ) {
        /** First undone item — Android's `nextContinueChapter`. */
        val nextItem: RemoteItem?
            get() = displayItems.firstOrNull { it.url !in doneUrls }

        /** Items after the entry's chapterFlags — what the list renders. */
        val displayItems: List<RemoteItem>
            get() {
                var filtered = items
                filtered = when (unreadFilter) {
                    TriState.ENABLED_IS -> filtered.filter { it.url !in doneUrls }
                    TriState.ENABLED_NOT -> filtered.filter { it.url in doneUrls }
                    TriState.DISABLED -> filtered
                }
                filtered = when (bookmarkedFilter) {
                    TriState.ENABLED_IS -> filtered.filter { it.url in bookmarkedUrls }
                    TriState.ENABLED_NOT -> filtered.filter { it.url !in bookmarkedUrls }
                    TriState.DISABLED -> filtered
                }
                filtered = when (fillermarkedFilter) {
                    TriState.ENABLED_IS -> filtered.filter { it.url in fillermarkedUrls }
                    TriState.ENABLED_NOT -> filtered.filter { it.url !in fillermarkedUrls }
                    TriState.DISABLED -> filtered
                }
                if (excludedScanlators.isNotEmpty()) {
                    filtered = filtered.filter { it.scanlator !in excludedScanlators }
                }
                return sortMode.ordered(filtered, sortDescending)
            }

        val allItemsDone: Boolean
            get() = items.isNotEmpty() && items.all { it.url in doneUrls }

        val hasStarted: Boolean
            get() = doneUrls.isNotEmpty()

        /**
         * Seasons grouped from the remote item list — Android surfaces
         * structured entries pre-favorite too (its open-time materialize
         * writes season rows); unfavorited entries fall back to this so
         * the season surface still renders. Aggregates derive from the
         * entry's local url sets when absent.
         */
        private val remoteSeasons: List<LocalSeason>
            get() = items
                .mapIndexedNotNull { index, item ->
                    item.seasonUrl?.let { Triple(index, it, item) }
                }
                .groupBy({ it.second }, { it.third to it.first })
                .map { (url, eps) ->
                    val first = eps.first().first
                    LocalSeason(
                        // No local row — drill-in is url-keyed; mark ops
                        // guard on a real id.
                        id = -1L,
                        url = url,
                        title = first.seasonTitle ?: url,
                        seasonNumber = first.seasonNumber,
                        sourceOrder = eps.minOf { it.second }.toLong(),
                        thumbnailUrl = null,
                        status = 0,
                        seen = eps.isNotEmpty() && eps.all { it.first.url in doneUrls },
                        unseenCount = eps.count { it.first.url !in doneUrls }.toLong(),
                        hasStarted = eps.any { it.first.url in doneUrls },
                        hasBookmarks = eps.any { it.first.url in bookmarkedUrls },
                        hasFillermarks = eps.any { it.first.url in fillermarkedUrls },
                        latestUpload = eps.maxOf { it.first.dateUpload },
                        fetchedAt = 0L,
                        lastSeen = 0L,
                    )
                }

        /** Local season rows when materialized; remote-derived otherwise. */
        private val seasonSurface: List<LocalSeason>
            get() = seasons.ifEmpty { remoteSeasons }

        /**
         * Android's `processedSeasons` — `applySeasonFilters` + the
         * seasonFlags comparator (descending reverses the mode sort,
         * then an always-ascending alphabetical tiebreak).
         */
        val displaySeasons: List<LocalSeason>
            get() {
                val local = local ?: return seasonSurface
                val filtered = seasonSurface
                    .filter { triStateMatch(local.seasonUnseenFilter, !it.seen) }
                    .filter { triStateMatch(local.seasonStartedFilter, it.hasStarted) }
                    .filter {
                        triStateMatch(
                            local.seasonCompletedFilter,
                            it.status == SAnime.COMPLETED,
                        )
                    }
                    .filter { triStateMatch(local.seasonBookmarkedFilter, it.hasBookmarks) }
                    .filter {
                        triStateMatch(local.seasonFillermarkedFilter, it.hasFillermarks)
                    }
                    .filter {
                        // Shows have no local/downloaded rows — the filter
                        // still cycles; IS matches nothing, NOT matches all.
                        triStateMatch(local.seasonDownloadedFilter, false)
                    }
                val comparator = Comparator<LocalSeason> { s1, s2 ->
                    when (local.seasonSorting) {
                        Anime.SEASON_SORT_SEASON ->
                            s1.seasonNumber.compareTo(s2.seasonNumber)
                        Anime.SEASON_SORT_UPLOAD ->
                            s1.latestUpload.compareTo(s2.latestUpload)
                        Anime.SEASON_SORT_ALPHABET ->
                            s1.title.compareTo(s2.title, ignoreCase = true)
                        Anime.SEASON_SORT_COUNT ->
                            s1.unseenCount.compareTo(s2.unseenCount)
                        Anime.SEASON_SORT_LAST_SEEN ->
                            s1.lastSeen.compareTo(s2.lastSeen)
                        Anime.SEASON_SORT_FETCHED ->
                            s1.fetchedAt.compareTo(s2.fetchedAt)
                        else -> s1.sourceOrder.compareTo(s2.sourceOrder)
                    }
                }
                // Android: mode comparator (descending reverses it), then
                // an always-ascending alphabetical tiebreak.
                val mode = if (local.seasonSortDescending) {
                    comparator.reversed()
                } else {
                    comparator
                }
                return filtered.sortedWith(mode.thenBy { it.title.lowercase() })
            }

        /** Structured shows render the season surface instead of items. */
        val isSeasonEntry: Boolean
            get() = seasonSurface.isNotEmpty()
    }

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var itemsJob: Job? = null

    init {
        scope.launch {
            // Favorite/library state streams from the local row — the toggle
            // materializes one via materialize() when absent.
            binding.localEntry(entryUrl, sourceId).collect { local ->
                _state.value = _state.value.copy(
                    local = local,
                    favorite = local?.favorite == true,
                    sortDescending = local?.sortDescending == true,
                    sortMode = local?.sortMode ?: ChapterSortMode.NUMBER,
                    unreadFilter = local?.unreadFilter ?: TriState.DISABLED,
                    bookmarkedFilter = local?.bookmarkedFilter ?: TriState.DISABLED,
                    fillermarkedFilter = local?.fillermarkedFilter ?: TriState.DISABLED,
                    displayByNumber = local?.displayByNumber == true,
                    showPreviews = local?.showPreviews != false,
                    showSummaries = local?.showSummaries != false,
                )
                if (local != null) subscribeLocalItems(local.id)
                rescanDownloads()
            }
        }
        scope.launch {
            binding.collections().collect { collections ->
                _state.value = _state.value.copy(collections = collections)
            }
        }
        // Android's DownloadCache semantics — downloaded names rescan on any
        // queue change touching this entry's dir; live queue states tracked
        // for the row indicators.
        scope.launch {
            DownloadManager.states.collect { s ->
                _state.value = _state.value.copy(downloadStates = s)
            }
        }
        scope.launch {
            DownloadManager.progress.collect { p ->
                _state.value = _state.value.copy(downloadProgress = p)
            }
        }
        scope.launch {
            DownloadManager.changes.collect { dir ->
                if (dir == downloadEntryPath()) rescanDownloads()
            }
        }
        scope.launch {
            val (start, end) = binding.swipeActions()
            _state.value = _state.value.copy(swipeStart = start, swipeEnd = end)
        }
        refresh()
    }

    private fun subscribeLocalItems(localId: Long) {
        if (itemsJob != null) return
        itemsJob = scope.launch {
            binding.localItems(localId).collect { items ->
                _state.value = _state.value.copy(
                    doneUrls = items.filter { it.done }
                        .mapTo(HashSet()) { it.url },
                    bookmarkedUrls = items.filter { it.bookmarked }
                        .mapTo(HashSet()) { it.url },
                    fillermarkedUrls = items.filter { it.fillermarked }
                        .mapTo(HashSet()) { it.url },
                    progressPages = items
                        .filter { !it.done && it.lastPageRead > 0 && it.totalChars == 0L }
                        .associate { it.url to it.lastPageRead },
                    progressPercents = items
                        .filter { !it.done && it.lastPageRead > 0 && it.totalChars > 0 }
                        .associate {
                            it.url to
                                (it.lastPageRead * 100 / it.totalChars).toInt().coerceIn(1, 99)
                        },
                    progressSeconds = items
                        .filter { !it.done && it.lastSecondSeen > 0 }
                        .associate { it.url to (it.lastSecondSeen to it.totalSeconds) },
                )
            }
        }
        // Android's scanlator exclusion — a view filter over the entry's
        // chapters; the flow is live so dialog edits re-filter instantly.
        scope.launch {
            binding.excludedScanlators(localId).collect { excluded ->
                _state.value = _state.value.copy(excludedScanlators = excluded)
            }
        }
        // Shows — Android's getAnimeSeasonsByIdAsFlow; empty unless the
        // source is season-structured. Flag defaults seed once per entry
        // (Android's `seasonFlags == 0L` load-time guard).
        if (carrier == BrowseCarrier.SHOWS) {
            scope.launch {
                binding.ensureSeasonDefaults(localId)
                binding.localSeasons(localId).collect { seasons ->
                    _state.value = _state.value.copy(seasons = seasons)
                }
            }
        }
    }

    fun refresh() {
        scope.launch { loadDetails() }
        scope.launch { loadItems() }
    }

    private suspend fun loadDetails() {
        val src = binding.source(sourceId) ?: run {
            _state.value = _state.value.copy(
                detailsLoading = false,
                error = "Source unavailable",
            )
            return
        }
        _state.value = _state.value.copy(sourceName = src.name)
        val result = runCatching {
            src.details(entryUrl, _state.value.local?.title ?: entryUrl)
        }
        result.fold(
            onSuccess = { details ->
                _state.value = _state.value.copy(details = details, detailsLoading = false)
                rescanDownloads()
            },
            onFailure = {
                _state.value = _state.value.copy(
                    detailsLoading = false,
                    error = it.message ?: "Failed to load details",
                )
            },
        )
    }

    private suspend fun loadItems() {
        val src = binding.source(sourceId) ?: run {
            _state.value = _state.value.copy(itemsLoading = false)
            return
        }
        val result = runCatching { src.items(entryUrl) }
        result.fold(
            onSuccess = { items ->
                _state.value = _state.value.copy(
                    items = items,
                    itemsLoading = false,
                )
                // Keep local rows in step with the remote list — the
                // favorite-time seed misses items added afterwards.
                _state.value.local?.let { local ->
                    if (local.favorite) syncLocalItems(local.id, items)
                }
            },
            onFailure = {
                _state.value = _state.value.copy(
                    itemsLoading = false,
                    error = _state.value.error ?: (it.message ?: "Failed to load items"),
                )
            },
        )
    }

    private suspend fun syncLocalItems(localId: Long, remote: List<RemoteItem>) {
        binding.addMissingItems(localId, remote)
    }

    fun toggleFavorite() {
        scope.launch {
            val current = _state.value
            val details = current.details ?: return@launch
            // Materialize (or refresh) the local row, then flip the flag.
            val local = binding.materialize(details, sourceId)
            val target = !current.favorite
            binding.setFavorite(local.id, target)
            if (target) {
                syncLocalItems(local.id, current.items)
            }
        }
    }

    /** Chapter-header sort toggle — writes the entry's chapterFlags bit. */
    fun toggleSortDirection() {
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.setChapterSortDescending(local.id, !_state.value.sortDescending)
        }
    }

    /** Chapter-header unread filter — writes the entry's chapterFlags bits. */
    fun cycleUnreadFilter() {
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.setChapterUnreadFilter(local.id, _state.value.unreadFilter.next())
        }
    }

    /** Filter-sheet sort mode — writes the entry's chapterFlags sorting bits. */
    fun setChapterSortMode(mode: ChapterSortMode) {
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.setChapterSortMode(local.id, mode)
        }
    }

    /** Filter-sheet bookmarked filter — chapterFlags bookmarked bits. */
    fun cycleBookmarkedFilter() {
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.setChapterBookmarkedFilter(local.id, _state.value.bookmarkedFilter.next())
        }
    }

    /** Filter-sheet fillermarked filter (shows) — episodeFlags filler bits. */
    fun cycleFillermarkedFilter() {
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.setChapterFillermarkedFilter(
                local.id,
                _state.value.fillermarkedFilter.next(),
            )
        }
    }

    /** Display tab (shows) — episode preview thumbnails. */
    fun setShowPreviews(show: Boolean) {
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.setChapterShowPreviews(local.id, show)
        }
    }

    /** Display tab (shows) — episode summary blurbs. */
    fun setShowSummaries(show: Boolean) {
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.setChapterShowSummaries(local.id, show)
        }
    }

    /** Display tab — "Title" vs "Chapter number" row labels. */
    fun setDisplayByNumber(byNumber: Boolean) {
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.setChapterDisplayByNumber(local.id, byNumber)
        }
    }

    /**
     * Android's mark-all toggle — flips every item to done, or back when
     * [State.allItemsDone]. Only meaningful once the entry is in the library.
     */
    fun toggleAllItemsDone() {
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.setAllItemsDone(local.id, !_state.value.allItemsDone)
        }
    }

    /** Mark a single item done/undone — the reader/player calls this on open. */
    fun markItem(itemUrl: String, done: Boolean) {
        scope.launch {
            val local = _state.value.local ?: return@launch
            val item = binding.getLocalItems(local.id)
                .firstOrNull { it.url == itemUrl }
                ?: return@launch
            binding.setItemDone(item.id, done)
        }
    }

    /**
     * Android's "mark previous as read" — every item before the selected
     * one in reading order (the current sort mode, ascending).
     */
    fun markPreviousDone(itemUrl: String) {
        scope.launch {
            val local = _state.value.local ?: return@launch
            val state = _state.value
            val readingOrder = state.sortMode.ordered(state.items, descending = false)
            val index = readingOrder.indexOfFirst { it.url == itemUrl }
            if (index <= 0) return@launch
            val previousUrls = readingOrder.take(index).mapTo(HashSet()) { it.url }
            binding.getLocalItems(local.id)
                .filter { it.url in previousUrls && !it.done }
                .forEach { binding.setItemDone(it.id, true) }
            clearSelection()
        }
    }

    /** Android's chapter bookmark toggle — row long-press action. */
    fun toggleItemBookmark(itemUrl: String) {
        scope.launch {
            val local = _state.value.local ?: return@launch
            val item = binding.getLocalItems(local.id)
                .firstOrNull { it.url == itemUrl }
                ?: return@launch
            binding.setItemBookmark(item.id, !item.bookmarked)
        }
    }

    /** Android's episode fillermark toggle (shows) — single-item variant. */
    fun toggleItemFillermark(itemUrl: String) {
        scope.launch {
            val local = _state.value.local ?: return@launch
            val item = binding.getLocalItems(local.id)
                .firstOrNull { it.url == itemUrl }
                ?: return@launch
            binding.setItemFillermark(item.id, !item.fillermarked)
        }
    }

    /**
     * Android's chapterSwipe/episodeSwipe dispatch — the configured swipe
     * action applied to one row.
     */
    fun performItemSwipe(itemUrl: String, action: ItemSwipeAction) {
        when (action) {
            ItemSwipeAction.ToggleDone -> markItem(itemUrl, itemUrl !in _state.value.doneUrls)
            ItemSwipeAction.ToggleBookmark -> toggleItemBookmark(itemUrl)
            ItemSwipeAction.ToggleFillermark -> toggleItemFillermark(itemUrl)
            ItemSwipeAction.Download -> downloadItem(itemUrl)
            ItemSwipeAction.Disabled -> Unit
        }
    }

    /** Android's action mode — long-press selects, taps toggle membership. */
    fun toggleSelection(itemUrl: String) {
        if (_state.value.local == null) return
        val selected = _state.value.selectedUrls
        _state.value = _state.value.copy(
            selectedUrls = if (itemUrl in selected) selected - itemUrl else selected + itemUrl,
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedUrls = emptySet())
    }

    /** Android's toolbar select-all — every item in the displayed list. */
    fun selectAll() {
        if (_state.value.local == null) return
        _state.value = _state.value.copy(
            selectedUrls = _state.value.displayItems.mapTo(HashSet()) { it.url },
        )
    }

    /** Android's toolbar invert — selection becomes its complement. */
    fun invertSelection() {
        if (_state.value.local == null) return
        val shown = _state.value.displayItems.mapTo(HashSet()) { it.url }
        _state.value = _state.value.copy(
            selectedUrls = shown - _state.value.selectedUrls,
        )
    }

    /**
     * Bulk mark done/undone — Android's bottom-menu action; the selection
     * clears after the op like the upstream action mode.
     */
    fun markSelected(done: Boolean) {
        val urls = _state.value.selectedUrls
        if (urls.isEmpty()) return
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.getLocalItems(local.id)
                .filter { it.url in urls && it.done != done }
                .forEach { binding.setItemDone(it.id, done) }
            clearSelection()
        }
    }

    /** Bulk bookmark/unbookmark — Android's bottom-menu action. */
    fun bookmarkSelected(bookmarked: Boolean) {
        val urls = _state.value.selectedUrls
        if (urls.isEmpty()) return
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.getLocalItems(local.id)
                .filter { it.url in urls && it.bookmarked != bookmarked }
                .forEach { binding.setItemBookmark(it.id, bookmarked) }
            clearSelection()
        }
    }

    /** Bulk fillermark (shows) — Android's bottom-menu action. */
    fun fillermarkSelected(fillermarked: Boolean) {
        val urls = _state.value.selectedUrls
        if (urls.isEmpty()) return
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.getLocalItems(local.id)
                .filter { it.url in urls && it.fillermarked != fillermarked }
                .forEach { binding.setItemFillermark(it.id, fillermarked) }
            clearSelection()
        }
    }

    // ---- Downloads (manga/novel; shows return null payloads) -----------------

    private fun downloadEntryPath(): String? {
        val s = _state.value
        val title = s.details?.title ?: s.local?.title ?: return null
        if (s.sourceName.isBlank()) return null
        return DownloadManager.entryDir(carrier, sourceName(), title).toString()
    }

    private fun sourceName(): String = _state.value.sourceName

    /** Rescan the entry's download dirs into [State.downloadedNames]. */
    private fun rescanDownloads() {
        val s = _state.value
        val title = s.details?.title ?: s.local?.title ?: return
        if (s.sourceName.isBlank()) return
        _state.value = _state.value.copy(
            downloadedNames = DownloadManager.downloadedItemNames(
                carrier,
                s.sourceName,
                title,
            ),
        )
    }

    /** Android's per-row download button — queues one item. */
    fun downloadItem(itemUrl: String) {
        scope.launch {
            val s = _state.value
            val title = s.details?.title ?: s.local?.title ?: return@launch
            val item = s.items.firstOrNull { it.url == itemUrl } ?: return@launch
            val dir = DownloadManager.itemDir(carrier, s.sourceName, title, item.name)
            if (DownloadManager.isDownloaded(dir)) return@launch
            DownloadManager.enqueue(
                carrier,
                s.sourceName,
                title,
                item.name,
                item.url,
            ) { binding.itemPayload(sourceId, itemUrl) }
        }
    }

    /**
     * Android's download dropdown — NEXT_n takes the first n unread items in
     * reading order; UNVIEWED takes all unread. Downloaded/queued items are
     * skipped by the manager's state check.
     */
    fun downloadNext(count: Int?) {
        scope.launch {
            val s = _state.value
            val unread = s.sortMode
                .ordered(s.items, descending = false)
                .filter { it.url !in s.doneUrls }
            val targets = if (count == null) unread else unread.take(count)
            targets.forEach { item ->
                DownloadManager.enqueue(
                    carrier,
                    s.sourceName,
                    s.details?.title ?: s.local?.title ?: entryUrl,
                    item.name,
                    item.url,
                ) { binding.itemPayload(sourceId, item.url) }
            }
        }
    }

    /** Android's delete-download — removes the item's download dir. */
    fun deleteDownload(itemUrl: String) {
        val s = _state.value
        val title = s.details?.title ?: s.local?.title ?: return
        val item = s.items.firstOrNull { it.url == itemUrl } ?: return
        DownloadManager.delete(carrier, s.sourceName, title, item.name)
    }

    /** Cancel a queued/running download — Android's queue cancel. */
    fun cancelDownload(itemUrl: String) = DownloadManager.cancel(itemUrl)

    /**
     * Android's selection-menu Delete — drops download dirs for the
     * downloaded subset of the selection, then clears it.
     */
    fun deleteSelectedDownloads() {
        val s = _state.value
        val title = s.details?.title ?: s.local?.title ?: return
        val selected = s.items.filter { it.url in s.selectedUrls }
        selected.forEach { item ->
            if (s.downloadedNames.contains(
                    DownloadManager.sanitizeName(item.name),
                )
            ) {
                DownloadManager.delete(carrier, s.sourceName, title, item.name)
            }
            DownloadManager.cancel(item.url)
        }
        clearSelection()
    }

    /** Android's overflow "Remove downloads → All" — the whole entry dir. */
    fun removeAllDownloads() {
        val s = _state.value
        val title = s.details?.title ?: s.local?.title ?: return
        DownloadManager.deleteEntry(carrier, s.sourceName, title)
    }

    /**
     * Android's "All but bookmarked chapters" — drops downloads for items
     * whose url isn't in the bookmarked set.
     */
    fun removeNonBookmarkedDownloads() {
        removeDownloadsWhere { item, s -> item.url !in s.bookmarkedUrls }
    }

    /** Android's "All read chapters" — drops downloads for done items. */
    fun removeReadDownloads() {
        removeDownloadsWhere { item, s -> item.url in s.doneUrls }
    }

    private fun removeDownloadsWhere(predicate: (RemoteItem, State) -> Boolean) {
        val s = _state.value
        val title = s.details?.title ?: s.local?.title ?: return
        s.items
            .filter {
                predicate(it, s) &&
                    DownloadManager.sanitizeName(it.name) in s.downloadedNames
            }
            .forEach { DownloadManager.delete(carrier, s.sourceName, title, it.name) }
    }

    /**
     * Android's ScanlatorFilterDialog — opens with the distinct scanlator
     * names on the entry's items (local rows ∪ the remote list, so a not-
     * yet-synced favorite still offers its groups). Local-only, like the
     * rest of the per-entry exclusion state.
     */
    fun showScanlatorSheet() {
        val local = _state.value.local ?: return
        scope.launch {
            val names = binding.getItemScanlators(local.id) +
                _state.value.items.mapNotNull { it.scanlator }
            _state.value = _state.value.copy(
                scanlatorNames = names.distinct()
                    .sortedWith(String.CASE_INSENSITIVE_ORDER),
                scanlatorSheetOpen = true,
            )
        }
    }

    fun dismissScanlatorSheet() {
        _state.value = _state.value.copy(scanlatorSheetOpen = false)
    }

    fun confirmExcludedScanlators(excluded: Set<String>) {
        val local = _state.value.local ?: return
        _state.value = _state.value.copy(scanlatorSheetOpen = false)
        scope.launch { binding.setExcludedScanlators(local.id, excluded) }
    }

    /** Android's setCurrentSettingsAsDefault + optional applyToExisting. */
    fun setFlagsAsDefault(applyToExisting: Boolean) {
        val local = _state.value.local ?: return
        scope.launch {
            binding.setEntryFlagsAsDefault(local.id)
            if (applyToExisting) binding.applyDefaultFlagsToAll()
        }
    }

    /** Android's resetToDefaultSettings — entry flags ← default prefs. */
    fun resetFlagsToDefault() {
        val local = _state.value.local ?: return
        scope.launch { binding.resetEntryFlagsToDefault(local.id) }
    }

    // ---- Season surface (shows) — Android's SeasonSettingsSheet + flags ----

    fun openSeasonSettings() {
        _state.value = _state.value.copy(seasonSettingsOpen = true)
    }

    fun dismissSeasonSettings() {
        _state.value = _state.value.copy(seasonSettingsOpen = false)
    }

    private fun applySeasonFlag(op: SeasonFlagOp) {
        scope.launch {
            // Android materializes the entry row on open — flag writes
            // work pre-favorite. Materialize on first write to match.
            val local = _state.value.local
                ?: _state.value.details?.let { binding.materialize(it, sourceId) }
                ?: return@launch
            binding.applySeasonFlag(local.id, op)
        }
    }

    private fun seasonFlag(state: TriState, isFlag: Long, notFlag: Long) = when (state) {
        TriState.DISABLED -> Anime.SHOW_ALL
        TriState.ENABLED_IS -> isFlag
        TriState.ENABLED_NOT -> notFlag
    }

    fun setSeasonDownloadedFilter(state: TriState) =
        applySeasonFlag(
            SeasonFlagOp.DownloadedFilter(
                seasonFlag(state, Anime.SEASON_SHOW_DOWNLOADED, Anime.SEASON_SHOW_NOT_DOWNLOADED),
            ),
        )

    fun setSeasonUnseenFilter(state: TriState) =
        applySeasonFlag(
            SeasonFlagOp.UnseenFilter(
                seasonFlag(state, Anime.SEASON_SHOW_UNSEEN, Anime.SEASON_SHOW_SEEN),
            ),
        )

    fun setSeasonStartedFilter(state: TriState) =
        applySeasonFlag(
            SeasonFlagOp.StartedFilter(
                seasonFlag(state, Anime.SEASON_SHOW_STARTED, Anime.SEASON_SHOW_NOT_STARTED),
            ),
        )

    fun setSeasonCompletedFilter(state: TriState) =
        applySeasonFlag(
            SeasonFlagOp.CompletedFilter(
                seasonFlag(state, Anime.SEASON_SHOW_COMPLETED, Anime.SEASON_SHOW_NOT_COMPLETED),
            ),
        )

    fun setSeasonBookmarkedFilter(state: TriState) =
        applySeasonFlag(
            SeasonFlagOp.BookmarkedFilter(
                seasonFlag(state, Anime.SEASON_SHOW_BOOKMARKED, Anime.SEASON_SHOW_NOT_BOOKMARKED),
            ),
        )

    fun setSeasonFillermarkedFilter(state: TriState) =
        applySeasonFlag(
            SeasonFlagOp.FillermarkedFilter(
                seasonFlag(
                    state,
                    Anime.SEASON_SHOW_FILLERMARKED,
                    Anime.SEASON_SHOW_NOT_FILLERMARKED,
                ),
            ),
        )

    /** Android's onSeasonSortModeChanged — same-mode re-pick flips direction. */
    fun setSeasonSortMode(mode: Long) =
        applySeasonFlag(SeasonFlagOp.SortModeOrFlip(mode))

    fun setSeasonGridMode(mode: SeasonDisplayMode) =
        applySeasonFlag(SeasonFlagOp.GridMode(mode))

    fun setSeasonGridSize(size: Int) =
        applySeasonFlag(SeasonFlagOp.GridSize(size))

    fun setSeasonDownloadedOverlay(on: Boolean) =
        applySeasonFlag(SeasonFlagOp.DownloadedOverlay(on))

    fun setSeasonUnseenOverlay(on: Boolean) =
        applySeasonFlag(SeasonFlagOp.UnseenOverlay(on))

    fun setSeasonLocalOverlay(on: Boolean) =
        applySeasonFlag(SeasonFlagOp.LocalOverlay(on))

    fun setSeasonLangOverlay(on: Boolean) =
        applySeasonFlag(SeasonFlagOp.LangOverlay(on))

    fun setSeasonContinueOverlay(on: Boolean) =
        applySeasonFlag(SeasonFlagOp.ContinueOverlay(on))

    fun setSeasonDisplayMode(mode: Long) =
        applySeasonFlag(SeasonFlagOp.DisplayMode(mode))

    /**
     * Android's `awaitSetSkipIntroLength` — the entry's intro seconds;
     * 0 also writes the disable bit (hides the player's skip control).
     */
    fun setSkipIntroLength(seconds: Int) {
        val local = _state.value.local ?: return
        scope.launch { binding.setSkipIntroLength(local.id, seconds) }
    }

    /** Android's setSeasonCurrentSettingsAsDefault + optional applyToExisting. */
    fun setSeasonSettingsAsDefault(applyToExisting: Boolean) {
        scope.launch {
            val local = _state.value.local
                ?: _state.value.details?.let { binding.materialize(it, sourceId) }
                ?: return@launch
            binding.setSeasonSettingsAsDefault(local.id, applyToExisting)
        }
    }

    /**
     * Android's onMultiMarkAsSeen for a season — seen unless every
     * episode already is. The binding owns the child row's item writes;
     * remote-derived rows (id -1, unfavorited) have nothing to write.
     */
    fun markSeasonSeen(season: LocalSeason) {
        if (season.id < 0) return
        scope.launch { binding.setAllItemsDone(season.id, !season.seen) }
    }

    /**
     * Android's onContinueWatching(seasonAnime) — the season's next
     * unseen episode as a player nav target; null when none remains or
     * the row isn't materialized yet.
     */
    suspend fun seasonContinueTarget(season: LocalSeason) =
        if (season.id >= 0) binding.nextUndoneItem(season.id) else null

    /**
     * Android's showChangeCollectionDialog — snapshots the entry's current
     * memberships so the checkbox list opens pre-selected.
     */
    fun showCollectionSheet() {
        val local = _state.value.local ?: return
        scope.launch {
            val entry = binding.entryCollections(local.id).first()
            _state.value = _state.value.copy(
                entryCollectionIds = entry.mapTo(HashSet()) { it.id },
                collectionSheetOpen = true,
            )
        }
    }

    fun dismissCollectionSheet() {
        _state.value = _state.value.copy(collectionSheetOpen = false)
    }

    /**
     * Android's MigrateSourceScreen — catalogue sources for this carrier
     * minus the current one.
     */
    fun showMigrateSheet() {
        val local = _state.value.local ?: return
        if (!local.favorite) return
        scope.launch {
            val sources = binding.catalogueSources.first()
                .filter { it.id != sourceId }
            _state.value = _state.value.copy(
                migrateSources = sources,
                migrateSheetOpen = true,
            )
        }
    }

    fun dismissMigrateSheet() {
        _state.value = _state.value.copy(migrateSheetOpen = false)
    }

    /**
     * Bounded Android migrate: searches [targetSourceId] for this entry's
     * title, favorites the first match, copies its item list, transfers
     * done/bookmark flags by item-number match, then unfavorites the
     * original. [onDone] runs on success with the new entry's nav args.
     */
    fun migrateTo(
        targetSourceId: Long,
        onDone: (sourceId: Long, entryUrl: String, title: String) -> Unit,
    ) {
        val old = _state.value.local ?: return
        val title = _state.value.details?.title ?: old.title
        _state.value = _state.value.copy(migrateSheetOpen = false, migrating = true)
        scope.launch {
            val result = runCatching {
                val target = binding.source(targetSourceId)
                    ?: error("Source unavailable")
                val match = target.search(1, title).entries.firstOrNull()
                    ?: error("No match on ${target.name}")
                val details = target.details(match.url, match.title)
                    ?: error("Details unavailable on ${target.name}")
                val newLocal = binding.materialize(details, targetSourceId)
                binding.setFavorite(newLocal.id, true)
                binding.addMissingItems(newLocal.id, target.items(match.url))

                // Progress transfer — Android matches chapters by number.
                val oldItems = binding.getLocalItems(old.id)
                val doneNumbers = oldItems.filter { it.done }
                    .mapTo(HashSet()) { it.number }
                val bookmarkNumbers = oldItems.filter { it.bookmarked }
                    .mapTo(HashSet()) { it.number }
                binding.getLocalItems(newLocal.id).forEach { item ->
                    if (item.number in doneNumbers) {
                        binding.setItemDone(item.id, true)
                    }
                    if (item.number in bookmarkNumbers) {
                        binding.setItemBookmark(item.id, true)
                    }
                }

                binding.setFavorite(old.id, false)
                Triple(targetSourceId, match.url, details.title)
            }
            _state.value = _state.value.copy(migrating = false)
            result.fold(
                onSuccess = { (newSourceId, newUrl, newTitle) ->
                    onDone(newSourceId, newUrl, newTitle)
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        error = it.message ?: "Migration failed",
                    )
                },
            )
        }
    }

    /**
     * Android's moveMangaToCollectionsAndAddToLibrary — replaces the entry's
     * memberships with the confirmed selection.
     */
    fun confirmCollections(selectedIds: List<Long>) {
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.setEntryCollections(local.id, selectedIds)
            _state.value = _state.value.copy(collectionSheetOpen = false)
        }
    }

    fun dispose() = scope.cancel()
}
