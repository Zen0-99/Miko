package miko.ui.library

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import miko.ui.MikoUiGraph
import miko.ui.carrier.BrowseCarrier
import miko.ui.download.DownloadManager
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.library.model.LibraryDisplayMode

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
        /** Source language — Android's LanguageBadge text (EN/JA/…). */
        val sourceLanguage: String = "",
        /** Android's isLocal — local-source entries show a folder badge. */
        val isLocal: Boolean = false,
        /** Android's library filter fields — tri-state sheet criteria. */
        val hasStarted: Boolean = false,
        val hasBookmarks: Boolean = false,
        val completed: Boolean = false,
        /** LibraryX sort fields — Android's library sort criteria. */
        val totalCount: Long = 0,
        val latestUpload: Long = 0,
        val chapterFetchedAt: Long = 0,
        val lastRead: Long = 0,
        val lastUpdate: Long = 0,
        val dateAdded: Long = 0,
    )

    /**
     * Android's library sort types — serialized "TYPE,DIRECTION" matching
     * the domain's `*LibrarySort.serialize()` so values interop with the
     * same per-carrier pref keys (library_sorting_mode etc.).
     */
    enum class SortType {
        ALPHABETICAL,
        LAST_READ,
        LAST_UPDATE,
        UNREAD_COUNT,
        TOTAL_CHAPTERS,
        LATEST_CHAPTER,
        CHAPTER_FETCH_DATE,
        DATE_ADDED,
    }

    /** Android's per-carrier tri-state filter set (tracked + custom
     *  interval stay deferred with tracker infra). */
    data class LibraryFilters(
        val downloaded: TriState = TriState.DISABLED,
        val unread: TriState = TriState.DISABLED,
        val started: TriState = TriState.DISABLED,
        val bookmarked: TriState = TriState.DISABLED,
        val completed: TriState = TriState.DISABLED,
    )

    /** Filter ids — per-carrier `pref_filter_*` keys match Android's. */
    enum class LibraryFilterId {
        DOWNLOADED,
        UNREAD,
        STARTED,
        BOOKMARKED,
        COMPLETED,
    }

    data class LibrarySort(
        val type: SortType = SortType.ALPHABETICAL,
        val ascending: Boolean = true,
    ) {
        fun serialize() = "$type,${if (ascending) "ASCENDING" else "DESCENDING"}"

        companion object {
            fun deserialize(value: String): LibrarySort {
                val parts = value.split(",")
                val type = parts.getOrNull(0)
                    ?.let { raw -> SortType.entries.firstOrNull { it.name == raw } }
                    ?: SortType.ALPHABETICAL
                return LibrarySort(type, parts.getOrNull(1) != "DESCENDING")
            }
        }
    }

    data class State(
        val initialized: Boolean = false,
        val entries: List<LibraryEntry> = emptyList(),
        /** Android's display_library_title — covers show title overlays. */
        val showTitle: Boolean = true,
        /** Toolbar search filter — title substring, case-insensitive. */
        val query: String = "",
        /** Android's library collection tabs — the chip row + selection. */
        val collections: List<Collection> = emptyList(),
        val selectedCollectionId: Long? = null,
        /** Android's pref_display_mode_library — compact/comfortable/list. */
        val displayMode: LibraryDisplayMode = LibraryDisplayMode.default,
        /** Android's per-carrier library sort — the settings sheet's Sort tab. */
        val sort: LibrarySort = LibrarySort(),
        /** Android's tri-state library filters — the sheet's Filter tab. */
        val filters: LibraryFilters = LibraryFilters(),
        /** Android's selection mode — ids of the selected entries. */
        val selected: Set<Long> = emptySet(),
        /** Android's Badges-tab toggles — gate which cover badges render. */
        val badges: LibraryBadges = LibraryBadges(),
        /**
         * Entry id → downloaded item count — populated only while the
         * download badge pref is on (a filesystem scan per emission is
         * too hot to run unconditionally, same as the downloaded filter).
         */
        val downloadCounts: Map<Long, Int> = emptyMap(),
        /** Active carrier — marks read vs seen + download-dir naming. */
        val carrier: BrowseCarrier = BrowseCarrier.MANGA,
    )

    /** Android's display_*_badge pref set — the sheet's Badges tab. */
    data class LibraryBadges(
        val downloaded: Boolean = false,
        val unread: Boolean = true,
        val local: Boolean = true,
        val language: Boolean = false,
    )

    /** Badge ids — `display_*_badge` pref keys match Android's. */
    enum class LibraryBadgeId {
        DOWNLOADED,
        UNREAD,
        LOCAL,
        LANGUAGE,
    }

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var rawEntries: List<LibraryEntry> = emptyList()

    init {
        scope.launch {
            carrierFlow.flatMapLatest { carrier ->
                val binding = graph.binding(carrier)
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
                                sourceLanguage = binding
                                    .source(it.anime.source)?.lang ?: "",
                                hasStarted = it.hasStarted,
                                hasBookmarks = it.hasBookmarks,
                                completed = it.anime.status ==
                                    SAnime.COMPLETED.toLong(),
                                totalCount = it.totalCount,
                                latestUpload = it.latestUpload,
                                chapterFetchedAt = it.episodeFetchedAt,
                                lastRead = it.lastSeen,
                                lastUpdate = it.anime.lastUpdate,
                                dateAdded = it.anime.dateAdded,
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
                                sourceLanguage = binding
                                    .source(it.manga.source)?.lang ?: "",
                                hasStarted = it.hasStarted,
                                hasBookmarks = it.hasBookmarks,
                                completed = it.manga.status ==
                                    SManga.COMPLETED.toLong(),
                                totalCount = it.totalChapters,
                                latestUpload = it.latestUpload,
                                chapterFetchedAt = it.chapterFetchedAt,
                                lastRead = it.lastRead,
                                lastUpdate = it.manga.lastUpdate,
                                dateAdded = it.manga.dateAdded,
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
                                sourceLanguage = binding
                                    .source(it.novel.source)?.lang ?: "",
                                hasStarted = it.hasStarted,
                                hasBookmarks = it.hasBookmarks,
                                completed = it.novel.status ==
                                    SNovel.COMPLETED.toLong(),
                                totalCount = it.totalChapters,
                                latestUpload = it.latestUpload,
                                chapterFetchedAt = it.chapterFetchedAt,
                                lastRead = it.lastRead,
                                lastUpdate = it.novel.lastUpdate,
                                dateAdded = it.novel.dateAdded,
                            )
                        }
                    }
                }
            }.collect { entries ->
                rawEntries = entries
                _state.value = _state.value.copy(
                    initialized = true,
                    carrier = carrierFlow.value,
                    // Carrier switches drop the selection — ids are
                    // carrier-scoped like Android's per-tab models.
                    selected = if (_state.value.carrier == carrierFlow.value) {
                        _state.value.selected
                    } else {
                        emptySet()
                    },
                    entries = applyFilters(
                        entries,
                        _state.value.query,
                        _state.value.selectedCollectionId,
                        _state.value.sort,
                    ),
                )
                rescanDownloadCounts()
            }
        }
        // Android's per-carrier library sort pref — each carrier keeps its
        // own key so switching tabs preserves each sort independently.
        scope.launch {
            carrierFlow.flatMapLatest { carrier ->
                graph.preferenceStore
                    .getString(sortPrefKey(carrier))
                    .changes()
            }.collect { serialized ->
                val sort = LibrarySort.deserialize(serialized)
                _state.value = _state.value.copy(
                    sort = sort,
                    entries = applyFilters(
                        rawEntries,
                        _state.value.query,
                        _state.value.selectedCollectionId,
                        sort,
                    ),
                )
            }
        }
        // Android's per-carrier filter prefs — each emits a refreshed
        // LibraryFilters; re-applying uses the same pipeline as search.
        scope.launch {
            carrierFlow.flatMapLatest { carrier ->
                combine(
                    LibraryFilterId.entries.map { id ->
                        graph.preferenceStore
                            .getEnum(filterPrefKey(id, carrier), TriState.DISABLED)
                            .changes()
                    },
                ) { values ->
                    LibraryFilters(
                        downloaded = values[0],
                        unread = values[1],
                        started = values[2],
                        bookmarked = values[3],
                        completed = values[4],
                    )
                }
            }.collect { filters ->
                _state.value = _state.value.copy(
                    filters = filters,
                    entries = applyFilters(
                        rawEntries,
                        _state.value.query,
                        _state.value.selectedCollectionId,
                        _state.value.sort,
                    ),
                )
            }
        }
        scope.launch {
            carrierFlow.flatMapLatest { carrier ->
                graph.binding(carrier).collections()
            }.collect { collections ->
                val selected = _state.value.selectedCollectionId
                    ?.takeIf { id -> collections.any { it.id == id } }
                _state.value = _state.value.copy(
                    collections = collections,
                    selectedCollectionId = selected,
                    entries = applyFilters(
                        rawEntries,
                        _state.value.query,
                        selected,
                        _state.value.sort,
                    ),
                )
            }
        }
        scope.launch {
            graph.preferenceStore
                .getBoolean("display_library_title", true)
                .changes()
                .collect { show -> _state.value = _state.value.copy(showTitle = show) }
        }
        scope.launch {
            graph.preferenceStore
                .getObject(
                    "pref_display_mode_library",
                    LibraryDisplayMode.default,
                    LibraryDisplayMode.Serializer::serialize,
                    LibraryDisplayMode.Serializer::deserialize,
                )
                .changes()
                .collect { mode ->
                    _state.value = _state.value.copy(displayMode = mode)
                }
        }
        // Android's getLibraryItemPreferencesFlow — the four display_*_badge
        // prefs feed the cover badges; the download one also gates the
        // per-entry directory scan.
        scope.launch {
            combine(
                LibraryBadgeId.entries.map { id ->
                    graph.preferenceStore
                        .getBoolean(badgePrefKey(id), badgeDefault(id))
                        .changes()
                },
            ) { values ->
                LibraryBadges(
                    downloaded = values[0],
                    unread = values[1],
                    local = values[2],
                    language = values[3],
                )
            }.collect { badges ->
                _state.value = _state.value.copy(badges = badges)
                rescanDownloadCounts()
            }
        }
        scope.launch {
            // A finished/removed download flips the count without a DB write.
            DownloadManager.changes.collect { rescanDownloadCounts() }
        }
    }

    /** Android's DownloadCache-backed count — scanned per visible entry. */
    private suspend fun rescanDownloadCounts() {
        if (!_state.value.badges.downloaded) {
            if (_state.value.downloadCounts.isNotEmpty()) {
                _state.value = _state.value.copy(downloadCounts = emptyMap())
            }
            return
        }
        val carrier = carrierFlow.value
        val binding = graph.binding(carrier)
        val counts = rawEntries.mapNotNull { entry ->
            val sourceName = binding.source(entry.sourceId)?.name
                ?: return@mapNotNull null
            val count = DownloadManager
                .downloadedItemNames(carrier, sourceName, entry.title).size
            if (count > 0) entry.id to count else null
        }.toMap()
        _state.value = _state.value.copy(downloadCounts = counts)
    }

    /** Android's display-mode sheet selection — writes the library pref. */
    fun setDisplayMode(mode: LibraryDisplayMode) {
        scope.launch {
            graph.preferenceStore
                .getObject(
                    "pref_display_mode_library",
                    LibraryDisplayMode.default,
                    LibraryDisplayMode.Serializer::serialize,
                    LibraryDisplayMode.Serializer::deserialize,
                )
                .set(mode)
        }
    }

    /** Android's library toolbar search — filters by title substring. */
    fun setQuery(query: String) {
        scope.launch {
            _state.value = _state.value.copy(
                query = query,
                entries = applyFilters(
                    rawEntries,
                    query,
                    _state.value.selectedCollectionId,
                    _state.value.sort,
                ),
            )
        }
    }

    /** Android's library collection tab — null selects "All". */
    fun selectCollection(collectionId: Long?) {
        scope.launch {
            _state.value = _state.value.copy(
                selectedCollectionId = collectionId,
                entries = applyFilters(
                    rawEntries,
                    _state.value.query,
                    collectionId,
                    _state.value.sort,
                ),
            )
        }
    }

    /** Android's library settings Sort tab — writes the carrier's sort pref. */
    fun setSort(sort: LibrarySort) {
        scope.launch {
            graph.preferenceStore
                .getString(sortPrefKey(carrierFlow.value))
                .set(sort.serialize())
        }
    }

    private fun sortPrefKey(carrier: BrowseCarrier) = when (carrier) {
        BrowseCarrier.SHOWS -> "animelib_sorting_mode"
        BrowseCarrier.MANGA -> "library_sorting_mode"
        BrowseCarrier.BOOKS -> "novellib_sorting_mode"
    }

    /** Android's LibraryPreferences pref_filter_* keys — same names. */
    private fun filterPrefKey(id: LibraryFilterId, carrier: BrowseCarrier): String {
        val infix = when (carrier) {
            BrowseCarrier.SHOWS -> "animelib"
            BrowseCarrier.MANGA -> "library"
            BrowseCarrier.BOOKS -> "novellib"
        }
        val suffix = when (id) {
            LibraryFilterId.DOWNLOADED -> "downloaded_v2"
            LibraryFilterId.UNREAD -> "unread_v2"
            LibraryFilterId.STARTED -> "started_v2"
            LibraryFilterId.BOOKMARKED -> "bookmarked_v2"
            LibraryFilterId.COMPLETED -> "completed_v2"
        }
        return "pref_filter_${infix}_$suffix"
    }

    private fun badgePrefKey(id: LibraryBadgeId) = when (id) {
        LibraryBadgeId.DOWNLOADED -> "display_download_badge"
        LibraryBadgeId.UNREAD -> "display_unread_badge"
        LibraryBadgeId.LOCAL -> "display_local_badge"
        LibraryBadgeId.LANGUAGE -> "display_language_badge"
    }

    private fun badgeDefault(id: LibraryBadgeId) = when (id) {
        LibraryBadgeId.DOWNLOADED -> false
        LibraryBadgeId.UNREAD -> true
        LibraryBadgeId.LOCAL -> true
        LibraryBadgeId.LANGUAGE -> false
    }

    /** Android's Badges-tab checkbox — flips the display_*_badge pref. */
    fun toggleBadge(id: LibraryBadgeId) {
        scope.launch {
            val pref = graph.preferenceStore
                .getBoolean(badgePrefKey(id), badgeDefault(id))
            pref.set(!pref.get())
        }
    }

    /** Android's toggleFilter — DISABLED → INCLUDE → EXCLUDE → off. */
    fun toggleFilter(id: LibraryFilterId) {
        scope.launch {
            val pref = graph.preferenceStore.getEnum(
                filterPrefKey(id, carrierFlow.value),
                TriState.DISABLED,
            )
            pref.set(pref.get().next())
        }
    }

    /** Android's long-press entry — toggles the entry's selection. */
    fun toggleSelect(entryId: Long) {
        val current = _state.value.selected
        _state.value = _state.value.copy(
            selected = if (entryId in current) current - entryId else current + entryId,
        )
    }

    /** Android's select-all toolbar action — every displayed entry. */
    fun selectAll() {
        _state.value = _state.value.copy(
            selected = _state.value.entries.map { it.id }.toSet(),
        )
    }

    /** Android's invert-selection toolbar action. */
    fun invertSelection() {
        val current = _state.value.selected
        _state.value = _state.value.copy(
            selected = _state.value.entries.map { it.id }.toSet() - current,
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = emptySet())
    }

    /**
     * Android's selection action — marks every item read/seen (or
     * unread/unseen) on each selected entry, then exits action mode.
     */
    fun markSelectedDone(done: Boolean) {
        val ids = _state.value.selected
        clearSelection()
        scope.launch {
            val binding = graph.binding(carrierFlow.value)
            ids.forEach { binding.setAllItemsDone(it, done) }
        }
    }

    /**
     * Android's ChangeCollectionDialog confirm — replaces each selected
     * entry's memberships with the checked set.
     */
    fun setSelectedCollections(collectionIds: List<Long>) {
        val ids = _state.value.selected
        clearSelection()
        scope.launch {
            val binding = graph.binding(carrierFlow.value)
            ids.forEach { binding.setEntryCollections(it, collectionIds) }
        }
    }

    /**
     * Android's DeleteLibraryEntryDialog confirm — unfavorites the
     * selection and/or deletes downloaded items. A selection stays in the
     * library when only downloads are removed.
     */
    fun removeSelected(fromLibrary: Boolean, deleteDownloads: Boolean) {
        // Resolve from rawEntries, not the displayed list — a selected
        // entry filtered out of view still gets removed.
        val entries = rawEntries.filter { it.id in _state.value.selected }
        clearSelection()
        if (!fromLibrary && !deleteDownloads) return
        scope.launch {
            val carrier = carrierFlow.value
            val binding = graph.binding(carrier)
            entries.forEach { entry ->
                if (deleteDownloads) {
                    binding.source(entry.sourceId)?.name?.let { sourceName ->
                        DownloadManager.deleteEntry(carrier, sourceName, entry.title)
                    }
                }
                if (fromLibrary) binding.setFavorite(entry.id, false)
            }
        }
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Android's library pull-to-refresh — the per-carrier update job. */
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

    private suspend fun applyFilters(
        entries: List<LibraryEntry>,
        query: String,
        collectionId: Long?,
        sort: LibrarySort,
    ): List<LibraryEntry> {
        val needle = query.trim()
        var filtered = if (needle.isEmpty()) {
            entries
        } else {
            entries.filter { it.title.contains(needle, ignoreCase = true) }
        }
        if (collectionId != null) {
            val memberIds = graph.binding(carrierFlow.value)
                .filterEntriesByCollection(collectionId, filtered.map { it.id })
            filtered = filtered.filter { it.id in memberIds }
        }
        // Android's tri-state filters — the downloaded one resolves item
        // dirs on disk only while enabled (a filesystem scan per library
        // emission is too hot to run unconditionally).
        val filters = _state.value.filters
        val carrier = carrierFlow.value
        if (filters != LibraryFilters()) {
            val binding = graph.binding(carrier)
            val downloadedIds = if (filters.downloaded != TriState.DISABLED) {
                filtered.mapNotNull { entry ->
                    val sourceName = binding.source(entry.sourceId)?.name
                        ?: return@mapNotNull null
                    entry.id.takeIf {
                        DownloadManager
                            .downloadedItemNames(carrier, sourceName, entry.title)
                            .isNotEmpty()
                    }
                }.toSet()
            } else {
                emptySet()
            }
            filtered = filtered.filter { entry ->
                triStateMatch(filters.downloaded, entry.id in downloadedIds) &&
                    triStateMatch(filters.unread, entry.unreadCount > 0) &&
                    triStateMatch(filters.started, entry.hasStarted) &&
                    triStateMatch(filters.bookmarked, entry.hasBookmarks) &&
                    triStateMatch(filters.completed, entry.completed)
            }
        }
        val sorted = when (sort.type) {
            SortType.ALPHABETICAL -> filtered.sortedBy { it.title.lowercase() }
            SortType.LAST_READ -> filtered.sortedBy { it.lastRead }
            SortType.LAST_UPDATE -> filtered.sortedBy { it.lastUpdate }
            SortType.UNREAD_COUNT -> filtered.sortedBy { it.unreadCount }
            SortType.TOTAL_CHAPTERS -> filtered.sortedBy { it.totalCount }
            SortType.LATEST_CHAPTER -> filtered.sortedBy { it.latestUpload }
            SortType.CHAPTER_FETCH_DATE -> filtered.sortedBy { it.chapterFetchedAt }
            SortType.DATE_ADDED -> filtered.sortedBy { it.dateAdded }
        }
        return if (sort.ascending) sorted else sorted.asReversed()
    }

    private fun triStateMatch(state: TriState, matches: Boolean) = when (state) {
        TriState.DISABLED -> true
        TriState.ENABLED_IS -> matches
        TriState.ENABLED_NOT -> !matches
    }

    fun dispose() = scope.cancel()
}
