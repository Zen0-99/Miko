package miko.ui.reader

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
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
import miko.ui.carrier.RemoteItem

/**
 * Chapter reader (books carrier): fetches `itemText` for the open chapter,
 * converts the HTML payload to plain paragraphs, marks the local chapter
 * read on open, and tracks prev/next from the source's chapter list.
 */
class ReaderScreenModel(
    private val binding: CarrierBinding,
    private val sourceId: Long,
    private val entryUrl: String,
    initialChapterUrl: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class State(
        val chapterUrl: String = "",
        val chapterName: String = "",
        val paragraphs: List<String> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val hasPrev: Boolean = false,
        val hasNext: Boolean = false,
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State(chapterUrl = initialChapterUrl))
    val state: StateFlow<State> = _state.asStateFlow()

    private var items: List<RemoteItem> = emptyList()

    private val source
        get() = binding.source(sourceId)

    init {
        scope.launch {
            // Wait for the source registry before the first fetch — the
            // reader can be reached while sources are still initializing.
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
            val result = runCatching { binding.itemText(src.id, chapterUrl) }
            result.fold(
                onSuccess = { html ->
                    _state.value = _state.value.copy(
                        paragraphs = htmlToParagraphs(html),
                        loading = false,
                    )
                    markRead(chapterUrl)
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Failed to load chapter",
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

    /** Marks the local row read — no-op for non-library entries (no local rows). */
    private suspend fun markRead(chapterUrl: String) {
        val local = binding.localEntry(entryUrl, sourceId).first() ?: return
        val chapter = binding.getLocalItems(local.id)
            .firstOrNull { it.url == chapterUrl }
            ?: return
        if (chapter.done) return
        binding.setItemDone(chapter.id, true)
    }

    fun dispose() = scope.cancel()
}

internal fun htmlToParagraphs(html: String): List<String> {
    if (html.isBlank()) return emptyList()
    if (!html.contains('<')) {
        return html.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val doc: Document = runCatching { Ksoup.parse(html) }.getOrNull()
        ?: return listOf(html)
    val blocks = doc.body()?.select("p, h1, h2, h3, h4, h5, h6, li, blockquote")
        ?.eachText()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
    if (blocks.isNotEmpty()) return blocks
    return doc.body()?.wholeText()?.split("\n")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
}
