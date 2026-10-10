package miko.ui.sources

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import tachiyomi.domain.source.novel.service.NovelSourceManager

/**
 * Installed-source list for the browse tab. Plain StateFlow screen model
 * (Phase 27-02 convention): the platform shell owns the instance lifetime.
 */
class SourcesScreenModel(
    private val novelSourceManager: NovelSourceManager,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val scope = CoroutineScope(Job() + dispatcher)

    data class State(
        val isInitialized: Boolean = false,
        val sources: List<NovelCatalogueSource> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            combine(
                novelSourceManager.isInitialized,
                novelSourceManager.catalogueSources,
            ) { initialized, sources ->
                State(
                    isInitialized = initialized,
                    sources = sources.sortedWith(
                        compareBy({ it.lang }, { it.name.lowercase() }),
                    ),
                )
            }.collect { _state.value = it }
        }
    }

    fun dispose() = scope.cancel()
}
