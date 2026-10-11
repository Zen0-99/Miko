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
import kotlinx.coroutines.runBlocking
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.RemoteItem
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * Chapter reader (books carrier): fetches `itemText` for the open chapter,
 * converts the HTML payload to plain paragraphs, marks the local chapter
 * read on open, and tracks prev/next from the source's chapter list.
 * Display prefs share Android's pref_novel_* keys.
 */
class ReaderScreenModel(
    private val binding: CarrierBinding,
    private val sourceId: Long,
    private val entryUrl: String,
    initialChapterUrl: String,
    preferenceStore: PreferenceStore,
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
        /** Chapter-sheet rows — remote item names with local read state. */
        val chapters: List<ChapterRow> = emptyList(),
        val showChapters: Boolean = false,
        val textSize: Float = 16f,
        val lineHeight: Float = 1.5f,
        val paragraphSpacing: Int = 16,
        val textAlignment: TextAlignment = TextAlignment.JUSTIFY,
        val backgroundColor: NovelReaderBackgroundColor =
            NovelReaderBackgroundColor.GRAY,
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State(chapterUrl = initialChapterUrl))
    val state: StateFlow<State> = _state.asStateFlow()

    private var items: List<RemoteItem> = emptyList()

    private val source
        get() = binding.source(sourceId)

    private val textSizePref = preferenceStore.getFloat("pref_novel_text_size", 16f)
    private val lineHeightPref = preferenceStore.getFloat("pref_novel_line_height", 1.5f)
    private val paragraphSpacingPref = preferenceStore.getInt("pref_novel_paragraph_spacing", 16)
    private val textAlignmentPref = preferenceStore.getEnum(
        "pref_novel_text_alignment",
        TextAlignment.JUSTIFY,
    )
    private val backgroundColorPref = preferenceStore.getEnum(
        "pref_novel_bg_color_mode",
        NovelReaderBackgroundColor.GRAY,
    )

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
        scope.launch {
            textSizePref.changes().collect { v ->
                _state.value = _state.value.copy(textSize = v)
            }
        }
        scope.launch {
            lineHeightPref.changes().collect { v ->
                _state.value = _state.value.copy(lineHeight = v)
            }
        }
        scope.launch {
            paragraphSpacingPref.changes().collect { v ->
                _state.value = _state.value.copy(paragraphSpacing = v)
            }
        }
        scope.launch {
            textAlignmentPref.changes().collect { v ->
                _state.value = _state.value.copy(textAlignment = v)
            }
        }
        scope.launch {
            backgroundColorPref.changes().collect { v ->
                _state.value = _state.value.copy(backgroundColor = v)
            }
        }
    }

    fun setTextSize(size: Float) = textSizePref.set(size)
    fun setLineHeight(height: Float) = lineHeightPref.set(height)
    fun setParagraphSpacing(spacing: Int) = paragraphSpacingPref.set(spacing)
    fun setTextAlignment(alignment: TextAlignment) = textAlignmentPref.set(alignment)
    fun setBackgroundColor(color: NovelReaderBackgroundColor) = backgroundColorPref.set(color)

    private suspend fun loadItemList() {
        val src = source ?: return
        val local = binding.localEntry(entryUrl, sourceId).first()
        val remoteItems = src.items(entryUrl)
        // Android sorts the reader's chapter list by the entry's chapterFlags.
        items = if (local?.sortDescending == true) {
            remoteItems.sortedByDescending { it.number }
        } else {
            remoteItems.sortedBy { it.number }
        }
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

    /** Chapter id with an open read session — closed on switch/dispose. */
    private var openItemId: Long? = null

    /**
     * Marks the local row read + records the history session — no-op for
     * non-library entries (no local rows). Re-reading an already-read
     * chapter still opens a history session, matching Android.
     */
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
        // The reader scope is gone — close the session synchronously; the
        // upsert is a single-row write.
        if (open != null) runBlocking { binding.recordItemClose(open) }
    }
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
