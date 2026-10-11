package miko.ui.search

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.CarrierSource
import miko.ui.carrier.FeedEntry

/**
 * Cross-source search — Android's GlobalSearchScreenModel counterpart,
 * carrier-agnostic via [CarrierBinding]. Fans [search] out to every
 * catalogue source in parallel and tracks a per-source Loading/Success/Error
 * result; rows render as soon as each source answers.
 */
class GlobalSearchScreenModel(
    private val binding: CarrierBinding,
    initialQuery: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    sealed interface SearchItemResult {
        data object Loading : SearchItemResult
        data class Success(val entries: List<FeedEntry>) : SearchItemResult
        data class Error(val message: String?) : SearchItemResult
    }

    data class State(
        val query: String = "",
        val items: Map<CarrierSource, SearchItemResult> = emptyMap(),
        val initialized: Boolean = false,
    )

    private val scope = CoroutineScope(Job() + dispatcher)
    private val searchMutex = Mutex()
    private var searchJob: Job? = null

    private val _state = MutableStateFlow(State(query = initialQuery))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            binding.isInitialized.collect { initialized ->
                if (initialized) {
                    _state.value = _state.value.copy(initialized = true)
                    if (_state.value.query.isNotBlank()) search(_state.value.query)
                }
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        _state.value = _state.value.copy(query = query)
        searchJob?.cancel()
        searchJob = scope.launch {
            searchMutex.withLock {
                val sources = binding.catalogueSources.first()
                    .distinctBy { it.id }
                _state.value = _state.value.copy(
                    items = sources.associateWith { SearchItemResult.Loading },
                )
                coroutineScope {
                    sources.map { source ->
                        async {
                            val result = runCatching {
                                source.search(page = 1, query = query).entries
                            }.fold(
                                onSuccess = { SearchItemResult.Success(it) },
                                onFailure = { SearchItemResult.Error(it.message) },
                            )
                            setResult(source, result)
                        }
                    }.awaitAll()
                }
            }
        }
    }

    /** Per-source favorited urls — Android's in-library badge on search cards. */
    fun favoritedUrls(sourceId: Long): Flow<Set<String>> =
        binding.favoritedUrls(sourceId)

    private fun setResult(source: CarrierSource, result: SearchItemResult) {
        _state.value = _state.value.copy(
            items = _state.value.items + (source to result),
        )
    }

    fun dispose() = scope.cancel()
}
