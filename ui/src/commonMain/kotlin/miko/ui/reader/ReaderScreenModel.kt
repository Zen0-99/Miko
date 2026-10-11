package miko.ui.reader

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import miko.ui.MikoUiGraph
import tachiyomi.domain.items.chapter.model.NovelChapterUpdate

/**
 * Chapter reader: fetches `getChapterText` for the open chapter, converts the
 * HTML payload to plain paragraphs, marks the local chapter read on open, and
 * tracks prev/next from the source's chapter list.
 */
class ReaderScreenModel(
    private val graph: MikoUiGraph,
    private val sourceId: Long,
    private val novelUrl: String,
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

    private var chapters: List<SNovelChapter> = emptyList()

    private val source: NovelCatalogueSource?
        get() = graph.novelSourceManager.get(sourceId) as? NovelCatalogueSource

    init {
        scope.launch {
            // Wait for the source registry before the first fetch — the
            // reader can be reached while sources are still initializing.
            graph.novelSourceManager.isInitialized.collect { initialized ->
                if (initialized && chapters.isEmpty()) {
                    runCatching { loadChapterList() }
                    openChapter(_state.value.chapterUrl)
                }
            }
        }
    }

    private suspend fun loadChapterList() {
        val src = source ?: return
        val stub = SNovel.create().apply {
            url = novelUrl
            title = novelUrl
        }
        chapters = src.getChapterList(stub)
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
            val index = chapters.indexOfFirst { it.url == chapterUrl }
            _state.value = _state.value.copy(
                chapterUrl = chapterUrl,
                chapterName = chapters.getOrNull(index)?.name ?: "",
                loading = true,
                error = null,
                hasPrev = index > 0,
                hasNext = index >= 0 && index < chapters.lastIndex,
            )
            val result = runCatching {
                src.getChapterText(
                    SNovelChapter.create().apply { url = chapterUrl },
                )
            }
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
        val index = chapters.indexOfFirst { it.url == _state.value.chapterUrl }
        chapters.getOrNull(index - 1)?.let { openChapter(it.url) }
    }

    fun nextChapter() {
        val index = chapters.indexOfFirst { it.url == _state.value.chapterUrl }
        chapters.getOrNull(index + 1)?.let { openChapter(it.url) }
    }

    /** Marks the local row read — no-op for non-library novels (no local rows). */
    private suspend fun markRead(chapterUrl: String) {
        val local = graph.novelRepository.getNovelByUrlAndSourceId(novelUrl, sourceId) ?: return
        val chapter = graph.getNovelChaptersByNovelId.await(local.id)
            .firstOrNull { it.url == chapterUrl }
            ?: return
        if (chapter.read) return
        graph.novelChapterRepository.updateNovelChapter(
            NovelChapterUpdate(id = chapter.id, read = true),
        )
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
