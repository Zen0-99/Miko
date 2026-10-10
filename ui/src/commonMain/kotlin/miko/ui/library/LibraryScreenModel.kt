package miko.ui.library

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.library.novel.LibraryNovel

/**
 * Library favorites backed by `getLibraryNovels.subscribe()` — re-emits on
 * every DB write (favorite toggles, chapter sync, read marks).
 */
class LibraryScreenModel(
    private val getLibraryNovels: GetLibraryNovels,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class State(
        val initialized: Boolean = false,
        val entries: List<LibraryNovel> = emptyList(),
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            getLibraryNovels.subscribe().collect { entries ->
                _state.value = State(initialized = true, entries = entries)
            }
        }
    }

    fun dispose() = scope.cancel()
}
