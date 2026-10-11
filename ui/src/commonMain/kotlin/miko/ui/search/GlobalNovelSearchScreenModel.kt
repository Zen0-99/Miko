package miko.ui.search

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.source.novel.service.NovelSourceManager

/**
 * Cross-source novel search — Android's `GlobalNovelSearchScreenModel`
 * counterpart. Fans [search] out to every catalogue source in parallel and
 * tracks a per-source Loading/Success/Error result; rows render as soon as
 * each source answers.
 */
class GlobalNovelSearchScreenModel(
    private val novelSourceManager: NovelSourceManager,
    initialQuery: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    sealed interface SearchItemResult {
        data object Loading : SearchItemResult
        data class Success(val novels: List<SNovel>) : SearchItemResult
        data class Error(val message: String?) : SearchItemResult
    }

    data class State(
        val query: String = "",
        val items: Map<NovelCatalogueSource, SearchItemResult> = emptyMap(),
        val initialized: Boolean = false,
    )

    private val scope = CoroutineScope(Job() + dispatcher)
    private val searchMutex = Mutex()
    private var searchJob: Job? = null

    private val _state = MutableStateFlow(State(query = initialQuery))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            novelSourceManager.isInitialized.collect { initialized ->
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
                val sources = novelSourceManager.getCatalogueSources()
                    .distinctBy { it.id }
                _state.value = _state.value.copy(
                    items = sources.associateWith { SearchItemResult.Loading },
                )
                coroutineScope {
                    sources.map { source ->
                        async {
                            val result = runCatching {
                                source.getSearchNovels(
                                    page = 1,
                                    query = query,
                                    filters = source.getFilterList(),
                                ).novels
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

    private fun setResult(source: NovelCatalogueSource, result: SearchItemResult) {
        _state.value = _state.value.copy(
            items = _state.value.items + (source to result),
        )
    }

    fun dispose() = scope.cancel()
}
