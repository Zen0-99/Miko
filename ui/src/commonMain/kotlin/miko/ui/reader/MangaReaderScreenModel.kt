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
import miko.ui.carrier.BrowseCarrier
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.ChapterSortMode
import miko.ui.carrier.PageEntry
import miko.ui.carrier.RemoteItem
import miko.ui.carrier.ordered
import miko.ui.download.DownloadManager
import tachiyomi.core.common.preference.PreferenceStore
import kotlin.math.floor

/**
 * Manga image reader — mirrors [ReaderScreenModel]'s shape but loads page
 * images via [CarrierBinding.pageList] instead of chapter text. Marks the
 * local chapter read on open and tracks prev/next from the chapter list.
 * Reading mode follows Android: the manga's viewerFlags override the
 * global `pref_default_reading_mode_key`.
 */
class MangaReaderScreenModel(
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
        val pages: List<PageEntry> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        /** Transition-page neighbors — null when there is no prev/next. */
        val prevChapter: NeighborChapter? = null,
        val nextChapter: NeighborChapter? = null,
        /** Chapter-sheet rows — remote item names with local read state. */
        val chapters: List<ChapterRow> = emptyList(),
        val showChapters: Boolean = false,
        /** Resolved mode — entry viewerFlags or the global default pref. */
        val readingMode: MangaReadingMode = MangaReadingMode.RIGHT_TO_LEFT,
        /** The entry's own flag; 0 = inherit the global pref. */
        val viewerFlag: Int = 0,
        val showPageNumber: Boolean = true,
        /** Android's readerPreferences.keepScreenOn — drives KeepScreenOn. */
        val keepScreenOn: Boolean = true,
        /** Android's always_show_chapter_transition — end-of-chapter cards. */
        val showTransitions: Boolean = true,
        /** Android's pref_reader_theme_key — 0 white, 1 black, 2 gray, 3 auto. */
        val readerTheme: Int = 1,
        val showSettings: Boolean = false,
        /** Android's lastPageRead restore — page to open the viewer at. */
        val initialPage: Int = 0,
        /** Center-tap immersive toggle — hides the bottom nav row. */
        val chromeVisible: Boolean = true,
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State(chapterUrl = initialChapterUrl))
    val state: StateFlow<State> = _state.asStateFlow()

    private var items: List<RemoteItem> = emptyList()
    private var localId: Long? = null

    private val source
        get() = binding.source(sourceId)

    private val defaultModePref = preferenceStore.getInt(
        "pref_default_reading_mode_key",
        MangaReadingMode.RIGHT_TO_LEFT.flagValue,
    )
    private val showPageNumberPref = preferenceStore.getBoolean(
        "pref_show_page_number_key",
        true,
    )

    // Android's readerPreferences.keepScreenOn — same global key.
    private val keepScreenOnPref = preferenceStore.getBoolean(
        "pref_keep_screen_on_key",
        true,
    )

    // Android's readerPreferences.alwaysShowChapterTransition — same key.
    private val transitionsPref = preferenceStore.getBoolean(
        "always_show_chapter_transition",
        true,
    )

    // Android's readerPreferences.readerTheme — same global key/default.
    private val readerThemePref = preferenceStore.getInt(
        "pref_reader_theme_key",
        1,
    )

    init {
        scope.launch {
            binding.isInitialized.collect { initialized ->
                if (initialized && items.isEmpty()) {
                    runCatching { loadItemList() }
                    openChapter(_state.value.chapterUrl)
                }
            }
        }
        scope.launch {
            defaultModePref.changes().collect { flag ->
                resolveMode(_state.value.viewerFlag, flag)
            }
        }
        scope.launch {
            showPageNumberPref.changes().collect { v ->
                _state.value = _state.value.copy(showPageNumber = v)
            }
        }
        scope.launch {
            keepScreenOnPref.changes().collect { v ->
                _state.value = _state.value.copy(keepScreenOn = v)
            }
        }
        scope.launch {
            transitionsPref.changes().collect { v ->
                _state.value = _state.value.copy(showTransitions = v)
            }
        }
        scope.launch {
            readerThemePref.changes().collect { v ->
                _state.value = _state.value.copy(readerTheme = v)
            }
        }
    }

    private fun resolveMode(viewerFlag: Int, defaultFlag: Int) {
        val mode = MangaReadingMode.fromFlag(
            if (viewerFlag != MangaReadingMode.DEFAULT.flagValue) viewerFlag else defaultFlag,
        )
        _state.value = _state.value.copy(readingMode = mode, viewerFlag = viewerFlag)
    }

    /** Persist the mode onto the manga's viewerFlags (Android semantics). */
    fun setReadingMode(mode: MangaReadingMode) {
        resolveMode(mode.flagValue, defaultModePref.get())
        val id = localId ?: return
        scope.launch { binding.setViewer(id, mode.flagValue) }
    }

    fun setShowPageNumber(show: Boolean) = showPageNumberPref.set(show)

    fun setShowTransitions(show: Boolean) = transitionsPref.set(show)

    fun setReaderTheme(theme: Int) = readerThemePref.set(theme)

    /**
     * Android's calculateChapterGap — floor(higher) − floor(lower) − 1;
     * 0 for missing or unrecognized (negative) numbers.
     */
    private fun gapBetween(higher: RemoteItem?, lower: RemoteItem?): Int {
        if (higher == null || lower == null) return 0
        if (higher.number < 0.0 || lower.number < 0.0) return 0
        return floor(higher.number).toInt() - floor(lower.number).toInt() - 1
    }

    private fun RemoteItem.neighbor(gap: Int) = NeighborChapter(
        name = name,
        scanlator = scanlator,
        gap = gap,
    )

    /** Android's center-tap app-bar toggle. */
    fun toggleChrome() {
        _state.value = _state.value.copy(chromeVisible = !_state.value.chromeVisible)
    }

    fun openSettings() {
        _state.value = _state.value.copy(showSettings = true)
    }

    fun closeSettings() {
        _state.value = _state.value.copy(showSettings = false)
    }

    private suspend fun loadItemList() {
        val src = source ?: return
        val local = binding.localEntry(entryUrl, sourceId).first()
        localId = local?.id
        resolveMode(local?.viewer ?: 0, defaultModePref.get())
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
            // lastPageRead is resolved before the viewer composes so
            // key(chapterUrl) seeds the pager/list at the saved page.
            val local = runCatching {
                binding.localEntry(entryUrl, sourceId).first()
            }.getOrNull()
            val localItem = local?.let { entry ->
                runCatching {
                    binding.getLocalItems(entry.id)
                        .firstOrNull { it.url == chapterUrl }
                }.getOrNull()
            }
            val curr = items.getOrNull(index)
            val prev = items.getOrNull(index - 1)
            val next = items.getOrNull(index + 1)
            _state.value = _state.value.copy(
                chapterUrl = chapterUrl,
                chapterName = curr?.name ?: "",
                loading = true,
                error = null,
                prevChapter = prev?.neighbor(gapBetween(curr, prev)),
                nextChapter = next?.neighbor(gapBetween(next, curr)),
                initialPage = localItem?.lastPageRead ?: 0,
            )
            // Offline path — serve the downloaded pages when the chapter's
            // download dir holds the .done marker (Android parity).
            val downloadDir = local?.let { entry ->
                items.getOrNull(index)?.let { item ->
                    DownloadManager.itemDir(
                        BrowseCarrier.MANGA,
                        src.name,
                        entry.title,
                        item.name,
                    )
                }
            }
            val result = runCatching {
                if (downloadDir != null && DownloadManager.isDownloaded(downloadDir)) {
                    DownloadManager.downloadedPages(downloadDir).map { (i, p) ->
                        PageEntry(index = i, url = p, imageUrl = p)
                    }
                } else {
                    binding.pageList(src.id, chapterUrl)
                }
            }
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

    /** Viewer page position — Android's `lastPageRead` write-through. */
    fun savePageProgress(page: Int) {
        val itemId = openItemId ?: return
        scope.launch { binding.setItemPageProgress(itemId, page) }
    }

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
