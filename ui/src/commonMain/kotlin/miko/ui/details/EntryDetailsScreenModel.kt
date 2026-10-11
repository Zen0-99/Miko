package miko.ui.details

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.LocalEntry
import miko.ui.carrier.RemoteDetails
import miko.ui.carrier.RemoteItem

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
        /** Entry chapterFlags semantics — sort direction + unread filter. */
        val sortDescending: Boolean = false,
        val unreadOnly: Boolean = false,
    ) {
        /** First undone item — Android's `nextContinueChapter`. */
        val nextItem: RemoteItem?
            get() = displayItems.firstOrNull { it.url !in doneUrls }

        /** Items after the entry's chapterFlags — what the list renders. */
        val displayItems: List<RemoteItem>
            get() {
                val filtered = if (unreadOnly) {
                    items.filter { it.url !in doneUrls }
                } else {
                    items
                }
                return if (sortDescending) {
                    filtered.sortedByDescending { it.number }
                } else {
                    filtered.sortedBy { it.number }
                }
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
                    unreadOnly = local?.unreadOnly == true,
                )
                if (local != null) subscribeLocalItems(local.id)
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
                )
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
    fun toggleUnreadFilter() {
        scope.launch {
            val local = _state.value.local ?: return@launch
            binding.setChapterUnreadOnly(local.id, !_state.value.unreadOnly)
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

    fun dispose() = scope.cancel()
}
