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
                    entries = applyFilters(
                        entries,
                        _state.value.query,
                        _state.value.selectedCollectionId,
                        _state.value.sort,
                    ),
                )
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

    fun dispose() = scope.cancel()
}
