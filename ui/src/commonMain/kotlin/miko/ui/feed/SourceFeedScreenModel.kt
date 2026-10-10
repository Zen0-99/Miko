package miko.ui.feed

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
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
import tachiyomi.domain.source.novel.service.NovelSourceManager

/**
 * Paged source feed (popular / latest / search). Plain StateFlow model;
 * pages append on [loadNext], and listing/query changes restart at page 1.
 */
class SourceFeedScreenModel(
    private val novelSourceManager: NovelSourceManager,
    private val sourceId: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    enum class Listing { Popular, Latest }

    data class State(
        val sourceName: String = "",
        val supportsLatest: Boolean = false,
        val listing: Listing = Listing.Popular,
        val query: String = "",
        val items: List<SNovel> = emptyList(),
        val hasNextPage: Boolean = true,
        val isLoading: Boolean = false,
        val initialLoading: Boolean = true,
        val error: String? = null,
    )

    private val scope = CoroutineScope(Job() + dispatcher)
    private val loadMutex = Mutex()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var page = 0

    private val source: NovelCatalogueSource?
        get() = novelSourceManager.get(sourceId) as? NovelCatalogueSource

    init {
        scope.launch {
            // Sources finish initializing asynchronously — wait for the map to
            // contain our source before the first page load.
            novelSourceManager.isInitialized.collect { initialized ->
                if (initialized && source == null) return@collect
                if (initialized) {
                    _state.value = _state.value.copy(
                        sourceName = source?.name.orEmpty(),
                        supportsLatest = source?.supportsLatest == true,
                    )
                }
            }
        }
        loadNext()
    }

    fun setQuery(query: String) {
        if (query == _state.value.query) return
        _state.value = _state.value.copy(query = query)
        reset()
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
                        current.query.isNotBlank() -> src.getSearchNovels(
                            nextPage,
                            current.query,
                            src.getFilterList(),
                        )
                        current.listing == Listing.Latest -> src.getLatestUpdates(nextPage)
                        else -> src.getPopularNovels(nextPage)
                    }
                }
                result.fold(
                    onSuccess = { novelsPage ->
                        page = nextPage
                        _state.value = _state.value.copy(
                            items = (_state.value.items + novelsPage.novels)
                                .distinctBy { it.url },
                            hasNextPage = novelsPage.hasNextPage,
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
