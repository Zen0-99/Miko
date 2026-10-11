package miko.ui.feed

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.FeedEntry
import tachiyomi.domain.library.model.LibraryDisplayMode

/**
 * Paged source feed (popular / latest / search) — carrier-agnostic: the
 * [binding] resolves the source and maps entries to [FeedEntry], so the
 * same model drives books, manga, and shows feeds.
 */
class SourceFeedScreenModel(
    private val binding: CarrierBinding,
    private val sourceId: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    enum class Listing { Popular, Latest }

    data class State(
        val sourceName: String = "",
        val supportsLatest: Boolean = false,
        val listing: Listing = Listing.Popular,
        val query: String = "",
        val items: List<FeedEntry> = emptyList(),
        val hasNextPage: Boolean = true,
        val isLoading: Boolean = false,
        val initialLoading: Boolean = true,
        val error: String? = null,
        val displayMode: LibraryDisplayMode = LibraryDisplayMode.default,
        val hasFilters: Boolean = false,
    )

    private val scope = CoroutineScope(Job() + dispatcher)
    private val loadMutex = Mutex()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var page = 0

    private val source
        get() = binding.source(sourceId)

    init {
        scope.launch {
            // Plugin sources register asynchronously after isInitialized —
            // collect the map so a late-arriving source still fills the bar
            // and retries a load that failed with "Source unavailable".
            binding.catalogueSources.collect {
                val src = source ?: return@collect
                val retry = _state.value.error == "Source unavailable" &&
                    _state.value.items.isEmpty()
                _state.value = _state.value.copy(
                    sourceName = src.name,
                    supportsLatest = src.supportsLatest,
                )
                if (retry) reset()
            }
        }
        loadNext()
    }

    fun setQuery(query: String) {
        if (query == _state.value.query) return
        _state.value = _state.value.copy(query = query)
        reset()
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        _state.value = _state.value.copy(displayMode = mode)
    }

    fun setListing(listing: Listing) {
        if (listing == _state.value.listing) return
        _state.value = _state.value.copy(listing = listing)
        reset()
    }

    fun refresh() = reset()

    fun retry() = loadNext()

    private fun reset() {
        page = 0
        _state.value = _state.value.copy(
            items = emptyList(),
            hasNextPage = true,
            initialLoading = true,
            error = null,
        )
        loadNext()
    }

    fun loadNext() {
        scope.launch {
            loadMutex.withLock {
                val current = _state.value
                if (current.isLoading || !current.hasNextPage) return@withLock
                val src = source ?: run {
                    _state.value = current.copy(
                        initialLoading = false,
                        error = "Source unavailable",
                    )
                    return@withLock
                }
                _state.value = current.copy(isLoading = true, error = null)
                val nextPage = page + 1
                val result = runCatching {
                    when {
                        current.query.isNotBlank() -> src.search(nextPage, current.query)
                        current.listing == Listing.Latest -> src.latest(nextPage)
                        else -> src.popular(nextPage)
                    }
                }
                result.fold(
                    onSuccess = { feedPage ->
                        page = nextPage
                        _state.value = _state.value.copy(
                            items = (_state.value.items + feedPage.entries)
                                .distinctBy { it.url },
                            hasNextPage = feedPage.hasNextPage,
                            isLoading = false,
                            initialLoading = false,
                        )
                    },
                    onFailure = {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            initialLoading = false,
                            error = it.message ?: "Load failed",
                        )
                    },
                )
            }
        }
    }

    fun dispose() = scope.cancel()
}
