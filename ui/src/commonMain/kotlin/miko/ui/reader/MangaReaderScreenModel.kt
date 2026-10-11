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
import tachiyomi.core.common.preference.getEnum
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
        /** Android's `fullscreen` pref — system bars hidden while reading. */
        val fullscreen: Boolean = true,
        /** Android's reader_long_tap — hold a page edge to keep scrolling. */
        val longTapScroll: Boolean = true,
        /**
         * Android's pref_image_scale_type_key — 1 fit screen, 2 stretch,
         * 3 fit width, 4 fit height, 5 original size, 6 smart fit.
         */
        val imageScaleType: Int = 1,
        /** Android's reader_tapping_inverted — flips the tap zones. */
        val tappingInverted: TappingInvertMode = TappingInvertMode.NONE,
        /** Android's webtoon_side_padding — percent margins on the strip. */
        val webtoonSidePadding: Int = 0,
        /** Android's navigate_pan — panning past a zoomed edge flips the page. */
        val navigateToPan: Boolean = true,
        /** Android's reader_navigation_mode_pager — tap zone layout. */
        val navMode: Int = 0,
        /**
         * Android's pref_zoom_start_key — 1 auto (start edge per reading
         * direction, center for vertical), 2 left, 3 right, else center.
         * Aligns pages wider than the viewport (fill-height/original).
         */
        val zoomStart: Int = 1,
        /** Android's ColorFilter page — overlay + matrix + brightness. */
        val customBrightness: Boolean = false,
        val customBrightnessValue: Int = 0,
        val colorFilterEnabled: Boolean = false,
        val colorFilterValue: Int = 0,
        val colorFilterMode: Int = 0,
        val grayscale: Boolean = false,
        val invertedColors: Boolean = false,
        /**
         * Android's pref_default_orientation_type_key — ReaderOrientation
         * flagValue (0 default, 8 free, 0x10/0x18 portrait/landscape,
         * 0x20/0x28 locked, 0x30 reverse portrait).
         */
        val orientation: Int = 8,
        /**
         * Android's landscape_zoom — wide (landscape) pages auto-zoom to
         * fill when the scale type is Fit, anchored at the zoom-start
         * edge. Default true, matching Android.
         */
        val landscapeZoom: Boolean = true,
        /** Android's pref_enable_transitions_key — animated page flips. */
        val pageTransitions: Boolean = true,
        /**
         * Android's dual-page family (paged + webtoon): wide pages
         * (w>h) either split into two slots or rotate 90° to fit;
         * inverts flip half order / rotation direction.
         */
        val dualPageSplit: Boolean = false,
        val dualPageInvert: Boolean = false,
        val dualPageRotate: Boolean = false,
        val dualPageRotateInvert: Boolean = false,
        val dualPageSplitWebtoon: Boolean = false,
        val dualPageInvertWebtoon: Boolean = false,
        val dualPageRotateWebtoon: Boolean = false,
        val dualPageRotateInvertWebtoon: Boolean = false,
        /** Android's crop_borders / crop_borders_webtoon — margin crop per viewer. */
        val cropBorders: Boolean = false,
        val cropBordersWebtoon: Boolean = false,
        /** Android's E-Ink group — flash overlay on page change. */
        val flashOnPageChange: Boolean = false,
        val flashDurationMillis: Int = 100,
        val flashInterval: Int = 1,
        val flashColor: ReaderFlashColor = ReaderFlashColor.BLACK,
        /** Increments when a flash should fire — screen watches this. */
        val flashNow: Int = 0,
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

    // Android's readerPreferences.fullscreen — same global key/default.
    private val fullscreenPref = preferenceStore.getBoolean(
        "fullscreen",
        true,
    )

    // Android's readerPreferences.readWithLongTap — same key/default.
    private val longTapPref = preferenceStore.getBoolean(
        "reader_long_tap",
        true,
    )

    // Android's readerPreferences.imageScaleType — same key/default.
    private val imageScaleTypePref = preferenceStore.getInt(
        "pref_image_scale_type_key",
        1,
    )

    // Android's readerPreferences.pagerNavInverted — same key/default.
    private val tappingInvertedPref = preferenceStore.getEnum(
        "reader_tapping_inverted",
        TappingInvertMode.NONE,
    )

    // Android's readerPreferences.webtoonSidePadding — same key/default.
    private val webtoonSidePaddingPref = preferenceStore.getInt(
        "webtoon_side_padding",
        0,
    )

    // Android's readerPreferences.navigateToPan — same key/default.
    private val navigateToPanPref = preferenceStore.getBoolean(
        "navigate_pan",
        true,
    )

    // Android's readerPreferences.navigationModePager — same key/default.
    private val navModePref = preferenceStore.getInt(
        "reader_navigation_mode_pager",
        0,
    )

    // Android's readerPreferences.zoomStart — same key/default.
    private val zoomStartPref = preferenceStore.getInt(
        "pref_zoom_start_key",
        1,
    )

    // Android's ColorFilter-page prefs — same keys/defaults.
    private val customBrightnessPref = preferenceStore.getBoolean(
        "pref_custom_brightness_key",
        false,
    )
    private val customBrightnessValuePref = preferenceStore.getInt(
        "custom_brightness_value",
        0,
    )
    private val colorFilterPref = preferenceStore.getBoolean(
        "pref_color_filter_key",
        false,
    )
    private val colorFilterValuePref = preferenceStore.getInt(
        "color_filter_value",
        0,
    )
    private val colorFilterModePref = preferenceStore.getInt(
        "color_filter_mode",
        0,
    )
    private val grayscalePref = preferenceStore.getBoolean(
        "pref_grayscale",
        false,
    )
    private val invertedColorsPref = preferenceStore.getBoolean(
        "pref_inverted_colors",
        false,
    )

    // Android's defaultOrientationType — same key/default (FREE=0x08).
    private val orientationPref = preferenceStore.getInt(
        "pref_default_orientation_type_key",
        8,
    )

    // Android's ReadingModePage pager prefs — same keys/defaults.
    private val landscapeZoomPref = preferenceStore.getBoolean(
        "landscape_zoom",
        true,
    )
    private val pageTransitionsPref = preferenceStore.getBoolean(
        "pref_enable_transitions_key",
        true,
    )

    // Android's dual-page prefs — same keys/defaults (all false).
    private val dualPageSplitPref = preferenceStore.getBoolean(
        "pref_dual_page_split",
        false,
    )
    private val dualPageInvertPref = preferenceStore.getBoolean(
        "pref_dual_page_invert",
        false,
    )
    private val dualPageRotatePref = preferenceStore.getBoolean(
        "pref_dual_page_rotate",
        false,
    )
    private val dualPageRotateInvertPref = preferenceStore.getBoolean(
        "pref_dual_page_rotate_invert",
        false,
    )
    private val dualPageSplitWebtoonPref = preferenceStore.getBoolean(
        "pref_dual_page_split_webtoon",
        false,
    )
    private val dualPageInvertWebtoonPref = preferenceStore.getBoolean(
        "pref_dual_page_invert_webtoon",
        false,
    )
    private val dualPageRotateWebtoonPref = preferenceStore.getBoolean(
        "pref_dual_page_rotate_webtoon",
        false,
    )
    private val dualPageRotateInvertWebtoonPref = preferenceStore.getBoolean(
        "pref_dual_page_rotate_invert_webtoon",
        false,
    )

    // Android's crop_borders / crop_borders_webtoon — same keys/defaults.
    private val cropBordersPref = preferenceStore.getBoolean(
        "crop_borders",
        false,
    )
    private val cropBordersWebtoonPref = preferenceStore.getBoolean(
        "crop_borders_webtoon",
        false,
    )

    // Android's E-Ink prefs — same keys/defaults.
    private val flashOnPageChangePref = preferenceStore.getBoolean(
        "pref_reader_flash",
        false,
    )
    private val flashDurationPref = preferenceStore.getInt(
        "pref_reader_flash_duration",
        100,
    )
    private val flashIntervalPref = preferenceStore.getInt(
        "pref_reader_flash_interval",
        1,
    )
    private val flashColorPref = preferenceStore.getEnum(
        "pref_reader_flash_mode",
        ReaderFlashColor.BLACK,
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
        scope.launch {
            fullscreenPref.changes().collect { v ->
                _state.value = _state.value.copy(fullscreen = v)
            }
        }
        scope.launch {
            longTapPref.changes().collect { v ->
                _state.value = _state.value.copy(longTapScroll = v)
            }
        }
        scope.launch {
            imageScaleTypePref.changes().collect { v ->
                _state.value = _state.value.copy(imageScaleType = v)
            }
        }
        scope.launch {
            tappingInvertedPref.changes().collect { v ->
                _state.value = _state.value.copy(tappingInverted = v)
            }
        }
        scope.launch {
            webtoonSidePaddingPref.changes().collect { v ->
                _state.value = _state.value.copy(webtoonSidePadding = v)
            }
        }
        scope.launch {
            navigateToPanPref.changes().collect { v ->
                _state.value = _state.value.copy(navigateToPan = v)
            }
        }
        scope.launch {
            navModePref.changes().collect { v ->
                _state.value = _state.value.copy(navMode = v)
            }
        }
        scope.launch {
            zoomStartPref.changes().collect { v ->
                _state.value = _state.value.copy(zoomStart = v)
            }
        }
        scope.launch {
            customBrightnessPref.changes().collect { v ->
                _state.value = _state.value.copy(customBrightness = v)
            }
        }
        scope.launch {
            customBrightnessValuePref.changes().collect { v ->
                _state.value = _state.value.copy(customBrightnessValue = v)
            }
        }
        scope.launch {
            colorFilterPref.changes().collect { v ->
                _state.value = _state.value.copy(colorFilterEnabled = v)
            }
        }
        scope.launch {
            colorFilterValuePref.changes().collect { v ->
                _state.value = _state.value.copy(colorFilterValue = v)
            }
        }
        scope.launch {
            colorFilterModePref.changes().collect { v ->
                _state.value = _state.value.copy(colorFilterMode = v)
            }
        }
        scope.launch {
            grayscalePref.changes().collect { v ->
                _state.value = _state.value.copy(grayscale = v)
            }
        }
        scope.launch {
            invertedColorsPref.changes().collect { v ->
                _state.value = _state.value.copy(invertedColors = v)
            }
        }
        scope.launch {
            orientationPref.changes().collect { v ->
                _state.value = _state.value.copy(orientation = v)
            }
        }
        scope.launch {
            landscapeZoomPref.changes().collect { v ->
                _state.value = _state.value.copy(landscapeZoom = v)
            }
        }
        scope.launch {
            pageTransitionsPref.changes().collect { v ->
                _state.value = _state.value.copy(pageTransitions = v)
            }
        }
        scope.launch {
            dualPageSplitPref.changes().collect { v ->
                _state.value = _state.value.copy(dualPageSplit = v)
            }
        }
        scope.launch {
            dualPageInvertPref.changes().collect { v ->
                _state.value = _state.value.copy(dualPageInvert = v)
            }
        }
        scope.launch {
            dualPageRotatePref.changes().collect { v ->
                _state.value = _state.value.copy(dualPageRotate = v)
            }
        }
        scope.launch {
            dualPageRotateInvertPref.changes().collect { v ->
                _state.value = _state.value.copy(dualPageRotateInvert = v)
            }
        }
        scope.launch {
            dualPageSplitWebtoonPref.changes().collect { v ->
                _state.value = _state.value.copy(dualPageSplitWebtoon = v)
            }
        }
        scope.launch {
            dualPageInvertWebtoonPref.changes().collect { v ->
                _state.value = _state.value.copy(dualPageInvertWebtoon = v)
            }
        }
        scope.launch {
            dualPageRotateWebtoonPref.changes().collect { v ->
                _state.value = _state.value.copy(dualPageRotateWebtoon = v)
            }
        }
        scope.launch {
            dualPageRotateInvertWebtoonPref.changes().collect { v ->
                _state.value = _state.value.copy(dualPageRotateInvertWebtoon = v)
            }
        }
        scope.launch {
            cropBordersPref.changes().collect { v ->
                _state.value = _state.value.copy(cropBorders = v)
            }
        }
        scope.launch {
            cropBordersWebtoonPref.changes().collect { v ->
                _state.value = _state.value.copy(cropBordersWebtoon = v)
            }
        }
        scope.launch {
            flashOnPageChangePref.changes().collect { v ->
                _state.value = _state.value.copy(flashOnPageChange = v)
            }
        }
        scope.launch {
            flashDurationPref.changes().collect { v ->
                _state.value = _state.value.copy(flashDurationMillis = v)
            }
        }
        scope.launch {
            flashIntervalPref.changes().collect { v ->
                _state.value = _state.value.copy(flashInterval = v)
            }
        }
        scope.launch {
            flashColorPref.changes().collect { v ->
                _state.value = _state.value.copy(flashColor = v)
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

    fun setFullscreen(fullscreen: Boolean) = fullscreenPref.set(fullscreen)

    fun setLongTapScroll(on: Boolean) = longTapPref.set(on)

    fun setImageScaleType(type: Int) = imageScaleTypePref.set(type)

    fun setTappingInverted(mode: TappingInvertMode) = tappingInvertedPref.set(mode)

    fun setWebtoonSidePadding(pct: Int) = webtoonSidePaddingPref.set(pct)

    fun setNavigateToPan(on: Boolean) = navigateToPanPref.set(on)

    fun setNavMode(mode: Int) = navModePref.set(mode)

    fun setZoomStart(value: Int) = zoomStartPref.set(value)

    fun setCustomBrightness(on: Boolean) = customBrightnessPref.set(on)

    fun setCustomBrightnessValue(value: Int) = customBrightnessValuePref.set(value)

    fun setColorFilterEnabled(on: Boolean) = colorFilterPref.set(on)

    fun setColorFilterValue(value: Int) = colorFilterValuePref.set(value)

    fun setColorFilterMode(value: Int) = colorFilterModePref.set(value)

    fun setGrayscale(on: Boolean) = grayscalePref.set(on)

    fun setInvertedColors(on: Boolean) = invertedColorsPref.set(on)

    fun setOrientation(flagValue: Int) = orientationPref.set(flagValue)

    fun setLandscapeZoom(on: Boolean) = landscapeZoomPref.set(on)

    fun setPageTransitions(on: Boolean) = pageTransitionsPref.set(on)

    fun setDualPageSplit(on: Boolean) = dualPageSplitPref.set(on)

    fun setDualPageInvert(on: Boolean) = dualPageInvertPref.set(on)

    fun setDualPageRotate(on: Boolean) = dualPageRotatePref.set(on)

    fun setDualPageRotateInvert(on: Boolean) = dualPageRotateInvertPref.set(on)

    fun setDualPageSplitWebtoon(on: Boolean) = dualPageSplitWebtoonPref.set(on)

    fun setDualPageInvertWebtoon(on: Boolean) = dualPageInvertWebtoonPref.set(on)

    fun setDualPageRotateWebtoon(on: Boolean) = dualPageRotateWebtoonPref.set(on)

    fun setDualPageRotateInvertWebtoon(on: Boolean) = dualPageRotateInvertWebtoonPref.set(on)

    fun setCropBorders(on: Boolean) = cropBordersPref.set(on)

    fun setCropBordersWebtoon(on: Boolean) = cropBordersWebtoonPref.set(on)

    fun setFlashOnPageChange(on: Boolean) = flashOnPageChangePref.set(on)

    fun setFlashDuration(millis: Int) = flashDurationPref.set(millis)

    fun setFlashInterval(pages: Int) = flashIntervalPref.set(pages)

    fun setFlashColor(color: ReaderFlashColor) = flashColorPref.set(color)

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

    private var flashCalls = 0

    /** Viewer page position — Android's `lastPageRead` write-through. */
    fun savePageProgress(page: Int) {
        // Android's PageChanged event drives the e-ink flash host —
        // every interval-th change fires one flash.
        if (_state.value.flashOnPageChange) {
            flashCalls += 1
            if (flashCalls % _state.value.flashInterval.coerceAtLeast(1) == 0) {
                _state.value = _state.value.copy(flashNow = _state.value.flashNow + 1)
            }
        }
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
