package miko.ui.details

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
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.CarrierSource
import miko.ui.carrier.ChapterSortMode
import miko.ui.carrier.LocalEntry
import miko.ui.carrier.RemoteDetails
import miko.ui.carrier.RemoteItem
import miko.ui.carrier.ordered
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.collection.model.Collection

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
            }
        }
        scope.launch {
            binding.collections().collect { collections ->
                _state.value = _state.value.copy(collections = collections)
            }
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
