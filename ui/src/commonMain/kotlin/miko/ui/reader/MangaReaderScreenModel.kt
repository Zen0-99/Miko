package miko.ui.reader

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
import kotlinx.coroutines.runBlocking
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.PageEntry
import miko.ui.carrier.RemoteItem

/**
 * Manga image reader — mirrors [ReaderScreenModel]'s shape but loads page
 * images via [CarrierBinding.pageList] instead of chapter text. Marks the
 * local chapter read on open and tracks prev/next from the chapter list.
 */
class MangaReaderScreenModel(
    private val binding: CarrierBinding,
    private val sourceId: Long,
    private val entryUrl: String,
    initialChapterUrl: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class State(
        val chapterUrl: String = "",
        val chapterName: String = "",
        val pages: List<PageEntry> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val hasPrev: Boolean = false,
        val hasNext: Boolean = false,
        /** Chapter-sheet rows — remote item names with local read state. */
        val chapters: List<ChapterRow> = emptyList(),
        val showChapters: Boolean = false,
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State(chapterUrl = initialChapterUrl))
    val state: StateFlow<State> = _state.asStateFlow()

    private var items: List<RemoteItem> = emptyList()

    private val source
        get() = binding.source(sourceId)

    init {
        scope.launch {
            binding.isInitialized.collect { initialized ->
                if (initialized && items.isEmpty()) {
                    runCatching { loadItemList() }
                    openChapter(_state.value.chapterUrl)
                }
            }
        }
    }

    private suspend fun loadItemList() {
        val src = source ?: return
        items = src.items(entryUrl)
        val local = binding.localEntry(entryUrl, sourceId).first()
        val doneUrls = local?.let { entry ->
            binding.getLocalItems(entry.id)
                .filter { it.done }
                .map { it.url }
                .toSet()
        }.orEmpty()
        _state.value = _state.value.copy(
            chapters = items.map {
                ChapterRow(
                    url = it.url,
                    name = it.name,
                    number = it.number,
                    done = it.url in doneUrls,
                )
            },
        )
    }

    fun openChapterSheet() {
        _state.value = _state.value.copy(showChapters = true)
    }

    fun closeChapterSheet() {
        _state.value = _state.value.copy(showChapters = false)
    }

    fun openChapter(chapterUrl: String) {
        scope.launch {
            val src = source ?: run {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Source unavailable",
                )
                return@launch
            }
            val index = items.indexOfFirst { it.url == chapterUrl }
            _state.value = _state.value.copy(
                chapterUrl = chapterUrl,
                chapterName = items.getOrNull(index)?.name ?: "",
                loading = true,
                error = null,
                hasPrev = index > 0,
                hasNext = index >= 0 && index < items.lastIndex,
            )
            val result = runCatching { binding.pageList(src.id, chapterUrl) }
            result.fold(
                onSuccess = { pages ->
                    _state.value = _state.value.copy(
                        pages = pages,
                        loading = false,
                    )
                    markRead(chapterUrl)
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Failed to load pages",
                    )
                },
            )
        }
    }

    fun prevChapter() {
        val index = items.indexOfFirst { it.url == _state.value.chapterUrl }
        items.getOrNull(index - 1)?.let { openChapter(it.url) }
    }

    fun nextChapter() {
        val index = items.indexOfFirst { it.url == _state.value.chapterUrl }
        items.getOrNull(index + 1)?.let { openChapter(it.url) }
    }

    /** Chapter id with an open read session — closed on switch/dispose. */
    private var openItemId: Long? = null

    private suspend fun markRead(chapterUrl: String) {
        val local = binding.localEntry(entryUrl, sourceId).first() ?: return
        val chapter = binding.getLocalItems(local.id)
            .firstOrNull { it.url == chapterUrl }
            ?: return
        openItemId?.takeIf { it != chapter.id }?.let { binding.recordItemClose(it) }
        if (!chapter.done) binding.setItemDone(chapter.id, true)
        openItemId = chapter.id
        binding.recordItemOpen(chapter.id)
    }

    fun dispose() {
        val open = openItemId
        openItemId = null
        scope.cancel()
        if (open != null) runBlocking { binding.recordItemClose(open) }
    }
}
