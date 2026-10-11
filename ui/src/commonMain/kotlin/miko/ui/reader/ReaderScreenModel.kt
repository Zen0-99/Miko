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
import miko.ui.carrier.BrowseCarrier
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.ChapterSortMode
import miko.ui.carrier.LocalEntry
import miko.ui.carrier.RemoteItem
import miko.ui.carrier.ordered
import miko.ui.download.DownloadManager
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
        val sidePadding: Int = 16,
        val keepScreenOn: Boolean = true,
        val showProgress: Boolean = true,
        /** Android's pref_novel_fullscreen — system bars hidden while reading. */
        val fullscreen: Boolean = false,
        /** 1-based chapter position within the entry — the progress label. */
        val chapterIndex: Int = 0,
        val chapterCount: Int = 0,
        /**
         * Saved `lastCharRead` to restore on this chapter open; -1 once
         * consumed or when there is no position to resume from.
         */
        val restoreCharOffset: Int = -1,
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
    private val sidePaddingPref = preferenceStore.getInt("pref_novel_side_padding", 16)
    private val keepScreenOnPref =
        preferenceStore.getBoolean("pref_novel_keep_screen_on", true)
    private val showProgressPref =
        preferenceStore.getBoolean("pref_novel_show_progress", true)
    private val fullscreenPref =
        preferenceStore.getBoolean("pref_novel_fullscreen", false)

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
        scope.launch {
            sidePaddingPref.changes().collect { v ->
                _state.value = _state.value.copy(sidePadding = v)
            }
        }
        scope.launch {
            keepScreenOnPref.changes().collect { v ->
                _state.value = _state.value.copy(keepScreenOn = v)
            }
        }
        scope.launch {
            showProgressPref.changes().collect { v ->
                _state.value = _state.value.copy(showProgress = v)
            }
        }
        scope.launch {
            fullscreenPref.changes().collect { v ->
                _state.value = _state.value.copy(fullscreen = v)
            }
        }
    }

    fun setTextSize(size: Float) = textSizePref.set(size)
    fun setLineHeight(height: Float) = lineHeightPref.set(height)
    fun setParagraphSpacing(spacing: Int) = paragraphSpacingPref.set(spacing)
    fun setTextAlignment(alignment: TextAlignment) = textAlignmentPref.set(alignment)
    fun setBackgroundColor(color: NovelReaderBackgroundColor) = backgroundColorPref.set(color)
    fun setSidePadding(padding: Int) = sidePaddingPref.set(padding)
    fun setKeepScreenOn(on: Boolean) = keepScreenOnPref.set(on)
    fun setShowProgress(on: Boolean) = showProgressPref.set(on)
    fun setFullscreen(on: Boolean) = fullscreenPref.set(on)

    private suspend fun loadItemList() {
        val src = source ?: return
        val local = binding.localEntry(entryUrl, sourceId).first()
        val remoteItems = src.items(entryUrl)
        // Android sorts the reader's chapter list by the entry's chapterFlags.
        items = (local?.sortMode ?: ChapterSortMode.NUMBER)
            .ordered(remoteItems, local?.sortDescending == true)
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
            val local = runCatching {
                binding.localEntry(entryUrl, sourceId).first()
            }.getOrNull()
            _state.value = _state.value.copy(
                chapterUrl = chapterUrl,
                chapterName = items.getOrNull(index)?.name ?: "",
                loading = true,
                error = null,
                hasPrev = index > 0,
                hasNext = index >= 0 && index < items.lastIndex,
                chapterIndex = if (index >= 0) index + 1 else 0,
                chapterCount = items.size,
            )
            // Offline path — serve the downloaded chapter text when its
            // download dir holds the .done marker (Android parity).
            val downloadDir = local?.let { entry ->
                items.getOrNull(index)?.let { item ->
                    DownloadManager.itemDir(
                        BrowseCarrier.BOOKS,
                        src.name,
                        entry.title,
                        item.name,
                    )
                }
            }
            val result = runCatching {
                if (downloadDir != null && DownloadManager.isDownloaded(downloadDir)) {
                    DownloadManager.readFile(downloadDir, "content.txt")
                        ?.decodeToString()
                        ?: binding.itemText(src.id, chapterUrl)
                } else {
                    binding.itemText(src.id, chapterUrl)
                }
            }
            result.fold(
                onSuccess = { html ->
                    val localItem = local?.let { entry ->
                        binding.getLocalItems(entry.id)
                            .firstOrNull { it.url == chapterUrl }
                    }
                    _state.value = _state.value.copy(
                        paragraphs = htmlToParagraphs(html),
                        loading = false,
                        restoreCharOffset = localItem
                            ?.takeIf { it.lastPageRead > 0 }
                            ?.lastPageRead
                            ?: -1,
                    )
                    markRead(chapterUrl, local)
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

    /** Latest reported scroll position — flushed on switch/dispose. */
    private var pendingCharOffset: Int = 0

    /**
     * Marks the local row read + records the history session — no-op for
     * non-library entries (no local rows). Re-reading an already-read
     * chapter still opens a history session, matching Android.
     */
    private suspend fun markRead(chapterUrl: String, local: LocalEntry?) {
        local ?: return
        val chapter = binding.getLocalItems(local.id)
            .firstOrNull { it.url == chapterUrl }
            ?: return
        openItemId?.takeIf { it != chapter.id }?.let { flushItemClose(it) }
        if (!chapter.done) binding.setItemDone(chapter.id, true)
        openItemId = chapter.id
        pendingCharOffset = 0
        binding.recordItemOpen(chapter.id)
    }

    /** Android's `lastCharRead` write-through — debounced by the screen. */
    fun saveCharPosition(offset: Int) {
        pendingCharOffset = offset
        val id = openItemId ?: return
        scope.launch { binding.setItemPageProgress(id, offset) }
    }

    fun consumeRestore() {
        _state.value = _state.value.copy(restoreCharOffset = -1)
    }

    /** Final position write + history-session close for a finished chapter. */
    private suspend fun flushItemClose(itemId: Long) {
        if (pendingCharOffset > 0) binding.setItemPageProgress(itemId, pendingCharOffset)
        binding.recordItemClose(itemId)
    }

    fun dispose() {
        val open = openItemId
        val offset = pendingCharOffset
        openItemId = null
        scope.cancel()
        // The reader scope is gone — close the session synchronously; the
        // upsert is a single-row write.
        if (open != null) {
            runBlocking {
                if (offset > 0) binding.setItemPageProgress(open, offset)
                binding.recordItemClose(open)
            }
        }
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
