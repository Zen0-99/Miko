package miko.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import miko.ui.components.SettingDropdown
import miko.ui.components.SettingSlider
import miko.ui.components.SettingSwitch
import miko.ui.download.DownloadManager
import miko.ui.platform.HideSystemBars
import miko.ui.platform.KeepScreenOn
import miko.ui.platform.SetReaderOrientation
import miko.ui.platform.SetScreenBrightness
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.secondaryItemAlpha
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Manga reader — reading mode follows Android's `ReadingMode`: paged LTR /
 * RTL / vertical via [HorizontalPager]/[VerticalPager], or the long-strip
 * scroll for WEBTOON/CONTINUOUS_VERTICAL. Chapters mark themselves read on
 * open; prev/next + the chapter list sit in the bottom bar.
 */
@Composable
fun MangaReaderScreen(
    model: MangaReaderScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()

    // Android's reader keep-screen-on — idle timer off while reading.
    KeepScreenOn(state.keepScreenOn)
    // Android's fullscreen pref — system bars hidden while reading.
    HideSystemBars(state.fullscreen)
    // Android's pref_custom_brightness_key — positive values drive the
    // platform screen brightness; zero disables. Negatives are handled
    // by the shared black overlay below (Android's ReaderContentOverlay).
    SetScreenBrightness(
        when {
            !state.customBrightness || state.customBrightnessValue == 0 -> null
            state.customBrightnessValue > 0 -> state.customBrightnessValue / 100f
            else -> 0.01f
        },
    )
    // Android's pref_default_orientation_type_key — reader orientation lock.
    SetReaderOrientation(state.orientation)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(readerThemeBackground(state.readerTheme)),
        ) {
            when {
                state.loading -> LoadingScreen(modifier = Modifier.weight(1f))
                state.error != null -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { model.openChapter(state.chapterUrl) }) {
                        Text(stringResource(MR.strings.action_retry))
                    }
                }
                else -> PageViewer(
                    state = state,
                    onPageChange = model::savePageProgress,
                    onCenterTap = model::toggleChrome,
                    onPrevChapter = model::prevChapter,
                    onNextChapter = model::nextChapter,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.chromeVisible) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = model::prevChapter,
                        enabled = state.prevChapter != null,
                    ) {
                        Text(stringResource(MR.strings.action_previous_chapter))
                    }
                    IconButton(onClick = model::openChapterSheet) {
                        Icon(
                            Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = stringResource(MR.strings.chapters),
                        )
                    }
                    IconButton(onClick = model::openSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(MR.strings.pref_category_reading_mode),
                        )
                    }
                    TextButton(
                        onClick = model::nextChapter,
                        enabled = state.nextChapter != null,
                    ) {
                        Text(stringResource(MR.strings.action_next_chapter))
                    }
                }
            }
        }
        // Android's DisplayRefreshHost overlay — e-ink flash on page change.
        if (state.flashOnPageChange) {
            ReaderFlashOverlay(
                trigger = state.flashNow,
                durationMillis = state.flashDurationMillis,
                mode = state.flashColor,
            )
        }
        // Android's ReaderContentOverlay — darkening + color-filter rects.
        ReaderFilterOverlay(state)
    }

    if (state.showChapters) {
        ChapterListSheet(
            chapters = state.chapters,
            currentUrl = state.chapterUrl,
            onDismiss = model::closeChapterSheet,
            onSelect = model::openChapter,
        )
    }

    if (state.showSettings) {
        ReaderSettingsSheet(
            state = state,
            onModeSelect = model::setReadingMode,
            onOrientationSelect = model::setOrientation,
            onPageNumberChange = model::setShowPageNumber,
            onTransitionsChange = model::setShowTransitions,
            onThemeSelect = model::setReaderTheme,
            onFullscreenChange = model::setFullscreen,
            onLongTapChange = model::setLongTapScroll,
            onScaleTypeSelect = model::setImageScaleType,
            onNavModeSelect = model::setNavMode,
            onTappingInvertSelect = model::setTappingInverted,
            onSidePaddingChange = model::setWebtoonSidePadding,
            onNavigateToPanChange = model::setNavigateToPan,
            onLandscapeZoomChange = model::setLandscapeZoom,
            onPageTransitionsChange = model::setPageTransitions,
            onDualPageSplitChange = model::setDualPageSplit,
            onDualPageInvertChange = model::setDualPageInvert,
            onDualPageRotateChange = model::setDualPageRotate,
            onDualPageRotateInvertChange = model::setDualPageRotateInvert,
            onDualPageSplitWebtoonChange = model::setDualPageSplitWebtoon,
            onDualPageInvertWebtoonChange = model::setDualPageInvertWebtoon,
            onDualPageRotateWebtoonChange = model::setDualPageRotateWebtoon,
            onDualPageRotateInvertWebtoonChange = model::setDualPageRotateInvertWebtoon,
            onZoomStartSelect = model::setZoomStart,
            onCustomBrightnessChange = model::setCustomBrightness,
            onBrightnessValueChange = model::setCustomBrightnessValue,
            onColorFilterChange = model::setColorFilterEnabled,
            onColorValueChange = model::setColorFilterValue,
            onColorModeSelect = model::setColorFilterMode,
            onGrayscaleChange = model::setGrayscale,
            onInvertedColorsChange = model::setInvertedColors,
            onFlashToggle = model::setFlashOnPageChange,
            onFlashDuration = model::setFlashDuration,
            onFlashInterval = model::setFlashInterval,
            onFlashColorSelect = model::setFlashColor,
            onDismiss = model::closeSettings,
        )
    }
}

/**
 * Android's reader background colors — white/black/gray follow the pref;
 * automatic picks gray in dark mode and white in light (ReaderActivity's
 * automaticBackgroundColor).
 */
@Composable
private fun readerThemeBackground(theme: Int): Color = when (theme) {
    0 -> Color.White
    2 -> GrayReaderBackground
    3 -> if (isSystemInDarkTheme()) GrayReaderBackground else Color.White
    else -> Color.Black
}

private val GrayReaderBackground = Color(0xFF202125)

/** Android's GeneralSettingsPage themes — same order and pref values. */
private val readerThemeOptions = listOf(
    MR.strings.black_background to 1,
    MR.strings.gray_background to 2,
    MR.strings.white_background to 0,
    MR.strings.automatic_background to 3,
)

/** Android's ImageScaleType list — pref values are index + 1. */
private val imageScaleOptions = listOf(
    MR.strings.scale_type_fit_screen,
    MR.strings.scale_type_stretch,
    MR.strings.scale_type_fit_width,
    MR.strings.scale_type_fit_height,
    MR.strings.scale_type_original_size,
    MR.strings.scale_type_smart_fit,
)

/** Android's ZoomStart list — pref values match the index. */
private val zoomStartOptions = listOf(
    MR.strings.zoom_start_automatic,
    MR.strings.zoom_start_left,
    MR.strings.zoom_start_right,
    MR.strings.zoom_start_center,
)

/** Android's ReaderOrientation entries — pref stores the flagValue. */
private val orientationOptions = listOf(
    MR.strings.label_default to 0x00,
    MR.strings.rotation_free to 0x08,
    MR.strings.rotation_portrait to 0x10,
    MR.strings.rotation_landscape to 0x18,
    MR.strings.rotation_force_portrait to 0x20,
    MR.strings.rotation_force_landscape to 0x28,
    MR.strings.rotation_reverse_portrait to 0x30,
)

/** Android's ColorFilterMode entries — pref value is the index. */
private val colorFilterModeOptions = listOf(
    MR.strings.label_default to 0,
    MR.strings.filter_mode_multiply to 1,
    MR.strings.filter_mode_screen to 2,
    MR.strings.filter_mode_overlay to 3,
    MR.strings.filter_mode_lighten to 4,
    MR.strings.filter_mode_darken to 5,
)

/**
 * Android's ColorFilterPage channel sliders — writes one RGBA byte of the
 * packed int (shift 24=A,16=R,8=G,0=B) leaving the other bytes untouched.
 */
private fun packChannel(packed: Int, shift: Int, channel: Int): Int {
    val mask = (0xFFL shl shift).inv().toInt()
    return (packed and mask) or (channel.coerceIn(0, 255) shl shift)
}

/** Dispatches to the paged or long-strip viewer per the resolved mode. */
@Composable
private fun PageViewer(
    state: MangaReaderScreenModel.State,
    onPageChange: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.readingMode) {
        MangaReadingMode.LEFT_TO_RIGHT,
        MangaReadingMode.RIGHT_TO_LEFT,
        MangaReadingMode.VERTICAL,
        -> PagedViewer(state, onPageChange, onCenterTap, onPrevChapter, onNextChapter, modifier)
        else -> Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Android's webtoon viewer toggles the app bar on any tap.
                    detectTapGestures { onCenterTap() }
                },
        ) {
            WebtoonViewer(state, onPageChange, onPrevChapter, onNextChapter, Modifier.fillMaxSize())
        }
    }
}

/**
 * Android's *PagerViewer: one page per screen, swipes advance. RTL renders
 * the same page order with a reversed pager so swiping left moves forward.
 */
@Composable
private fun PagedViewer(
    state: MangaReaderScreenModel.State,
    onPageChange: (Int) -> Unit,
    onCenterTap: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Reset page position whenever the chapter changes; lastPageRead
    // seeds the resume page (Android's preserveReadingPosition).
    key(state.chapterUrl) {
        // Android wraps the pages with prev/next ChapterTransition cards —
        // index 0 is the prev transition, pageCount-1 the next one, real
        // pages sit between them. Offsets below convert to page indices.
        val transitions = if (state.showTransitions) 1 else 0
        // Android's dual-page split — a decoded-wide page emits a second
        // slot (InsertPage); first half follows reading direction
        // (L2R left, else right), invert flips. Discovery is lazy like
        // Android's — slots grow as intrinsics arrive.
        val widePages = remember { mutableStateMapOf<Int, Boolean>() }
        val l2r = state.readingMode == MangaReadingMode.LEFT_TO_RIGHT
        val slots = buildList<Pair<Int, Int?>> {
            state.pages.forEachIndexed { i, _ ->
                if (state.dualPageSplit && widePages[i] == true) {
                    var first = if (l2r) 0 else 1
                    if (state.dualPageInvert) first = 1 - first
                    add(i to first)
                    add(i to 1 - first)
                } else {
                    add(i to null as Int?)
                }
            }
        }
        val pagerState = rememberPagerState(
            initialPage = (
                slots.indexOfFirst { it.first == state.initialPage }
                    .coerceAtLeast(0) + transitions
                )
                .coerceIn(
                    transitions,
                    (slots.lastIndex + transitions).coerceAtLeast(transitions),
                ),
            pageCount = { slots.size + transitions * 2 },
        )
        val scope = rememberCoroutineScope()
        val zoom = remember { ReaderZoomState() }
        LaunchedEffect(pagerState.currentPage) {
            // Android resets the zoom on page settle.
            zoom.reset()
            onPageChange(
                slots.getOrNull(pagerState.currentPage - transitions)?.first
                    ?.coerceIn(0, state.pages.lastIndex.coerceAtLeast(0))
                    ?: 0,
            )
        }
        val rtl = state.readingMode == MangaReadingMode.RIGHT_TO_LEFT
        Box(
            modifier = modifier
                .fillMaxSize()
                // Android's tap zones: outer thirds flip pages (mirrored in
                // RTL); the center third toggles chrome. Swipes still go
                // through — the pager consumes drags before we see them.
                // reader_long_tap: holding an edge keeps scrolling until
                // release (the long-press consumes the up, so no tap fires).
                .pointerInput(state.readingMode, state.longTapScroll) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress != null) {
                            if (state.longTapScroll) {
                                val maxPage =
                                    (state.pages.size + transitions * 2 - 1).coerceAtLeast(0)
                                val job = scope.launch {
                                    while (pagerState.currentPage < maxPage) {
                                        flipToPage(
                                            pagerState,
                                            pagerState.currentPage + 1,
                                            state.pageTransitions,
                                        )
                                    }
                                }
                                waitForUpOrCancellation()
                                job.cancel()
                            } else {
                                waitForUpOrCancellation()
                            }
                            longPress.consume()
                        } else {
                            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                            if (up.isConsumed) return@awaitEachGesture
                            // Android's onDoubleTap: a second tap inside the
                            // double-tap window zooms at the tap point; the
                            // tap zones only run once the window expires —
                            // the same delay onSingleTapConfirmed pays.
                            val secondDown = withTimeoutOrNull(
                                viewConfiguration.doubleTapTimeoutMillis,
                            ) {
                                awaitFirstDown()
                            }
                            if (secondDown != null) {
                                val secondUp = waitForUpOrCancellation()
                                    ?: return@awaitEachGesture
                                if (!secondUp.isConsumed) {
                                    up.consume()
                                    secondUp.consume()
                                    scope.launch {
                                        zoom.animateDoubleTap(up.position, size)
                                    }
                                }
                                return@awaitEachGesture
                            }
                            up.consume()
                            // Android's ViewerNavigation: normalized region
                            // rects per nav layout, inverted by mirroring the
                            // rects (not the action); uncovered taps = menu.
                            val px = up.position.x / size.width
                            val py = up.position.y / size.height
                            val region = navRegions(state.navMode, state.tappingInverted)
                                .firstOrNull { px in it.l..it.r && py in it.t..it.b }
                            val target = when {
                                region == null -> 0
                                state.readingMode == MangaReadingMode.VERTICAL -> {
                                    region.zone.delta
                                }
                                region.zone == NavZone.LEFT -> if (rtl) 1 else -1
                                region.zone == NavZone.RIGHT -> if (rtl) -1 else 1
                                else -> region.zone.delta
                            }
                            if (target != 0) {
                                val page = (pagerState.currentPage + target)
                                    .coerceIn(
                                        0,
                                        state.pages.size + transitions * 2 - 1,
                                    )
                                if (page != pagerState.currentPage) {
                                    scope.launch {
                                        flipToPage(pagerState, page, state.pageTransitions)
                                    }
                                }
                            } else {
                                onCenterTap()
                            }
                        }
                    }
                },
        ) {
            val pageContent: @Composable PagerScope.(Int) -> Unit = { index ->
                val slot = slots.getOrNull(index - transitions)
                when {
                    transitions == 1 && index == 0 -> ChapterTransitionBlock(
                        goingNext = false,
                        currentName = state.chapterName,
                        neighbor = state.prevChapter,
                        onNavigate = onPrevChapter,
                        modifier = Modifier.fillMaxSize(),
                    )
                    transitions == 1 && index == slots.size + 1 -> ChapterTransitionBlock(
                        goingNext = true,
                        currentName = state.chapterName,
                        neighbor = state.nextChapter,
                        onNavigate = onNextChapter,
                        modifier = Modifier.fillMaxSize(),
                    )
                    slot != null -> PageImage(
                        state.pages[slot.first],
                        Modifier.fillMaxSize(),
                        scaleType = state.imageScaleType,
                        alignment = zoomStartAlignment(state.zoomStart, state.readingMode),
                        colorFilter = pageColorFilter(state.grayscale, state.invertedColors),
                        landscapeZoom = state.landscapeZoom,
                        cropHalf = slot.second,
                        rotateToFit = state.dualPageRotate && !state.dualPageSplit,
                        rotateInvert = state.dualPageRotateInvert,
                        onIntrinsic = { size ->
                            widePages[slot.first] = size.width > size.height
                        },
                    )
                    else -> Unit
                }
            }
            // Android's PagerPageHolder zoom: two-finger pinch scales at the
            // centroid; while zoomed, single-finger drags pan instead of
            // flipping pages. Unzoomed single-pointer gestures pass through
            // untouched so swipes and tap zones keep working.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    }
                    .pointerInput(state.navigateToPan) {
                        val panNavThreshold = 64.dp.toPx()
                        awaitEachGesture {
                            val downPos = awaitFirstDown(requireUnconsumed = false).position
                            var prevSpan = 0f
                            var prevCentroid = Offset.Zero
                            var pinchActive = false
                            var panActive = false
                            var overscroll = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) {
                                    // Swallow the trailing ups of a zoom/pan
                                    // gesture so the tap zones don't fire.
                                    if (pinchActive || panActive) {
                                        event.changes.forEach { it.consume() }
                                    }
                                    break
                                }
                                if (pressed.size >= 2) {
                                    pinchActive = true
                                    val p1 = pressed[0].position
                                    val p2 = pressed[1].position
                                    val span = (p1 - p2).getDistance()
                                    val centroid = (p1 + p2) / 2f
                                    if (prevSpan > 0f && span > 0f) {
                                        val oldScale = zoom.scale
                                        val newScale = (oldScale * span / prevSpan)
                                            .coerceIn(1f, MAX_ZOOM_SCALE)
                                        val c = centroid - Offset(
                                            size.width / 2f,
                                            size.height / 2f,
                                        )
                                        zoom.offset = zoom.offset -
                                            c * (newScale - oldScale) +
                                            (centroid - prevCentroid)
                                        zoom.scale = newScale
                                        zoom.clamp(size)
                                    }
                                    prevSpan = span
                                    prevCentroid = centroid
                                    event.changes.forEach { it.consume() }
                                } else if (pinchActive || zoom.scale > 1f) {
                                    val change = pressed.first()
                                    if (panActive ||
                                        pinchActive ||
                                        (change.position - downPos).getDistance() >
                                        viewConfiguration.touchSlop
                                    ) {
                                        panActive = true
                                        zoom.offset += change.position - change.previousPosition
                                        zoom.clamp(size)
                                        // Android's navigate_pan — pushing a
                                        // zoomed page past its horizontal edge
                                        // flips to the adjacent page.
                                        if (state.navigateToPan) {
                                            val maxX = size.width * (zoom.scale - 1f) / 2f
                                            val dx = change.position.x - change.previousPosition.x
                                            overscroll = when {
                                                dx > 0f && zoom.offset.x >= maxX - 1f -> {
                                                    overscroll + dx
                                                }
                                                dx < 0f && zoom.offset.x <= -maxX + 1f -> {
                                                    overscroll + dx
                                                }
                                                else -> 0f
                                            }
                                            if (abs(overscroll) > panNavThreshold) {
                                                val dir = if (overscroll < 0f) 1 else -1
                                                val page = (pagerState.currentPage + dir)
                                                    .coerceIn(
                                                        0,
                                                        state.pages.size + transitions * 2 - 1,
                                                    )
                                                scope.launch {
                                                    flipToPage(
                                                        pagerState,
                                                        page,
                                                        state.pageTransitions,
                                                    )
                                                }
                                                // Drain the trailing ups so the
                                                // tap zones don't also fire.
                                                while (true) {
                                                    val e = awaitPointerEvent()
                                                    e.changes.forEach { it.consume() }
                                                    if (e.changes.none { it.pressed }) break
                                                }
                                                break
                                            }
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                    prevSpan = 0f
                                } else {
                                    prevSpan = 0f
                                }
                            }
                        }
                    },
            ) {
                if (state.readingMode == MangaReadingMode.VERTICAL) {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageContent = pageContent,
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        reverseLayout = state.readingMode == MangaReadingMode.RIGHT_TO_LEFT,
                        modifier = Modifier.fillMaxSize(),
                        pageContent = pageContent,
                    )
                }
            }
            if (state.showPageNumber && state.pages.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                ) {
                    Text(
                        text = "${(pagerState.currentPage + 1 - transitions).coerceIn(
                            1,
                            state.pages.size,
                        )} / ${state.pages.size}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Android's WebtoonViewer: continuous vertical strip, full-width images.
 * CONTINUOUS_VERTICAL shares the strip (the gap distinction between the
 * two long-strip modes isn't meaningful without stitched transitions).
 */
@Composable
private fun WebtoonViewer(
    state: MangaReaderScreenModel.State,
    onPageChange: (Int) -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Item layout mirrors Android's stitched strip: [prev transition]
    // [chapter header] [pages…] [spacer] [next transition]. The saved index
    // strips the leading items so it stays a page index, not a row index.
    val headerItems = 1 + (if (state.showTransitions) 1 else 0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (state.initialPage + headerItems)
            .coerceIn(
                headerItems,
                (state.pages.lastIndex + headerItems).coerceAtLeast(headerItems),
            ),
    )
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageChange(
            (listState.firstVisibleItemIndex - headerItems)
                .coerceIn(0, state.pages.lastIndex.coerceAtLeast(0)),
        )
    }
    // Android's webtoon_side_padding — percent of viewport width as
    // horizontal margins on the strip (WebtoonPageHolder's margin calc).
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sidePad = maxWidth * (state.webtoonSidePadding / 100f)
        // Wide-page discovery for the webtoon dual-page prefs —
        // splitAndMerge stacks halves (right on top; invert swaps).
        val widePages = remember(state.chapterUrl) { mutableStateMapOf<Int, Boolean>() }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = sidePad, vertical = 4.dp),
        ) {
            if (state.showTransitions) {
                item {
                    ChapterTransitionBlock(
                        goingNext = false,
                        currentName = state.chapterName,
                        neighbor = state.prevChapter,
                        onNavigate = onPrevChapter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            }
            item {
                Text(
                    text = state.chapterName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(state.pages, key = { it.index }) { page ->
                val onIntrinsic: (Size) -> Unit = { size ->
                    widePages[page.index] = size.width > size.height
                }
                if (state.dualPageSplitWebtoon && widePages[page.index] == true) {
                    // Android's splitAndMerge — halves stacked, right on
                    // top by default (invert swaps), each full width.
                    val upper = if (state.dualPageInvertWebtoon) 0 else 1
                    Column(Modifier.fillMaxWidth()) {
                        PageImage(page, Modifier.fillMaxWidth(), cropHalf = upper, onIntrinsic = onIntrinsic)
                        PageImage(page, Modifier.fillMaxWidth(), cropHalf = 1 - upper, onIntrinsic = onIntrinsic)
                    }
                } else {
                    PageImage(
                        page,
                        Modifier.fillMaxWidth(),
                        alignment = zoomStartAlignment(state.zoomStart, state.readingMode),
                        colorFilter = pageColorFilter(state.grayscale, state.invertedColors),
                        rotateToFit = state.dualPageRotateWebtoon && !state.dualPageSplitWebtoon,
                        rotateInvert = state.dualPageRotateInvertWebtoon,
                        onIntrinsic = onIntrinsic,
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            if (state.showTransitions) {
                item {
                    ChapterTransitionBlock(
                        goingNext = true,
                        currentName = state.chapterName,
                        neighbor = state.nextChapter,
                        onNavigate = onNextChapter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageImage(
    page: miko.ui.carrier.PageEntry,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
    scaleType: Int = 0,
    alignment: Alignment = Alignment.TopCenter,
    colorFilter: ColorFilter? = null,
    landscapeZoom: Boolean = false,
    cropHalf: Int? = null,
    rotateToFit: Boolean = false,
    rotateInvert: Boolean = false,
    onIntrinsic: (Size) -> Unit = {},
) {
    val src = page.imageUrl ?: page.url
    // Downloaded pages carry miko-file:// paths — read them off disk;
    // remote urls go straight to Coil like before.
    val fileBytes by produceState<ByteArray?>(initialValue = null, src) {
        if (src.startsWith(DownloadManager.FILE_SCHEME)) {
            value = withContext(Dispatchers.Default) {
                runCatching {
                    kotlinx.io.files.SystemFileSystem
                        .source(kotlinx.io.files.Path(src.removePrefix(DownloadManager.FILE_SCHEME)))
                        .buffered().use { it.readByteArray() }
                }.getOrNull()
            }
        }
    }
    var intrinsic by remember(src) { mutableStateOf<Size?>(null) }
    // Android's pref_image_scale_type_key — smart fit enlarges landscape
    // pages (spreads) to fill the height; portrait pages fit whole.
    val pagedScale = when (scaleType) {
        1 -> ContentScale.Fit
        2 -> ContentScale.FillBounds
        3 -> ContentScale.FillWidth
        4 -> ContentScale.FillHeight
        5 -> ContentScale.None
        6 -> intrinsic?.let {
            if (it.width > it.height) ContentScale.FillHeight else ContentScale.Fit
        } ?: ContentScale.Fit
        else -> contentScale
    }
    // Android's landscape_zoom — wide pages auto-zoom to fill when the
    // scale type is Fit, anchored at the zoom-start edge via `alignment`.
    val baseScale = if (scaleType != 0) pagedScale else contentScale
    val effectiveScale = if (
        landscapeZoom &&
        baseScale == ContentScale.Fit &&
        intrinsic?.let { it.width > it.height } == true
    ) {
        ContentScale.FillHeight
    } else {
        baseScale
    }
    val imageModel = if (src.startsWith(DownloadManager.FILE_SCHEME)) fileBytes else src
    val intr = intrinsic
    val isWide = intr?.let { it.width > it.height } == true
    when {
        // Android's splitInHalf — render one half of a wide page: the
        // image lays out at 2x slot width, clipped, offset by a slot.
        cropHalf != null -> BoxWithConstraints(modifier.clipToBounds()) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = effectiveScale,
                alignment = alignment,
                colorFilter = colorFilter,
                onSuccess = {
                    intrinsic = it.painter.intrinsicSize
                    onIntrinsic(it.painter.intrinsicSize)
                },
                modifier = Modifier
                    .requiredWidth(maxWidth * 2)
                    .fillMaxHeight()
                    .offset(x = if (cropHalf == 0) 0.dp else -maxWidth),
            )
        }
        // Android's rotateDualPage — wide pages rotate 90° to fill a
        // portrait viewport; the swapped-dims box makes the turn fill.
        rotateToFit && isWide -> BoxWithConstraints(modifier) {
            val bounded = constraints.maxHeight != Int.MAX_VALUE
            // Rotated footprint = pre-rotation dims swapped. In the
            // strip (unbounded height) claim w × w/aspect up front;
            // in the bounded pager the slot swap covers it.
            val aspect = intr!!.height / intr.width
            val outerW = if (bounded) null else maxWidth
            val outerH = if (bounded) null else maxWidth / aspect
            val boxW = if (bounded) maxHeight else maxWidth / aspect
            val boxH = maxWidth
            Box(
                modifier = if (bounded) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.size(width = outerW!!, height = outerH!!)
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = boxW, height = boxH)
                        .graphicsLayer { rotationZ = if (rotateInvert) -90f else 90f },
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                        onSuccess = {
                            intrinsic = it.painter.intrinsicSize
                            onIntrinsic(it.painter.intrinsicSize)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        else -> AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = effectiveScale,
            alignment = alignment,
            colorFilter = colorFilter,
            onSuccess = {
                intrinsic = it.painter.intrinsicSize
                onIntrinsic(it.painter.intrinsicSize)
            },
            modifier = modifier,
        )
    }
}

/**
 * Android's grayscale/inverted-colors layer paint — one combined
 * ColorMatrix over each page (NTSC luminance for grayscale, the -1/255
 * inversion matrix; both = invert×gray, matching Android's postConcat).
 */
private fun pageColorFilter(grayscale: Boolean, inverted: Boolean): ColorFilter? {
    val values = when {
        grayscale && inverted -> floatArrayOf(
            -0.213f, -0.715f, -0.072f, 0f, 255f,
            -0.213f, -0.715f, -0.072f, 0f, 255f,
            -0.213f, -0.715f, -0.072f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
        grayscale -> floatArrayOf(
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        inverted -> floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
        else -> return null
    }
    return ColorFilter.colorMatrix(ColorMatrix(values))
}

/**
 * Android's pref_zoom_start_key — the alignment a wider-than-viewport
 * page pins to. 1 auto resolves by reading direction (L2R left, R2L
 * right, vertical/webtoon center); 2 left, 3 right, else center.
 */
private fun zoomStartAlignment(
    zoomStart: Int,
    readingMode: MangaReadingMode,
): Alignment = when (zoomStart) {
    1 -> when (readingMode) {
        MangaReadingMode.LEFT_TO_RIGHT -> Alignment.TopStart
        MangaReadingMode.RIGHT_TO_LEFT -> Alignment.TopEnd
        else -> Alignment.TopCenter
    }
    2 -> Alignment.TopStart
    3 -> Alignment.TopEnd
    else -> Alignment.TopCenter
}

/**
 * Android's ReaderTransitionView — the card between chapters. Prev shows
 * "Previous: <neighbor>" over "Current: <name>"; Next shows "Finished:
 * <name>" over "Next: <neighbor>". Chapters aren't stitched here, so the
 * block taps through to navigate; null neighbors get the "no chapter" card.
 */
@Composable
private fun ChapterTransitionBlock(
    goingNext: Boolean,
    currentName: String,
    neighbor: NeighborChapter?,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 460.dp)
            .then(
                if (neighbor != null) {
                    Modifier.clickable(onClick = onNavigate)
                } else {
                    Modifier
                },
            ),
    ) {
        if (goingNext) {
            TransitionChapterText(header = stringResource(MR.strings.transition_finished), chapter = currentName)
        } else if (neighbor != null) {
            TransitionChapterText(
                header = stringResource(MR.strings.transition_previous),
                chapter = neighbor.name,
                scanlator = neighbor.scanlator,
            )
        } else {
            TransitionFallbackCard(text = stringResource(MR.strings.transition_no_previous))
        }

        Spacer(Modifier.height(24.dp))

        if (neighbor != null && neighbor.gap > 0) {
            OutlinedCard(colors = transitionCardColors()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        tint = MaterialTheme.colorScheme.error,
                        contentDescription = null,
                    )
                    Text(
                        text = pluralStringResource(
                            MR.plurals.missing_chapters_warning,
                            count = neighbor.gap,
                            neighbor.gap,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (goingNext) {
            if (neighbor != null) {
                TransitionChapterText(
                    header = stringResource(MR.strings.transition_next),
                    chapter = neighbor.name,
                    scanlator = neighbor.scanlator,
                )
            } else {
                TransitionFallbackCard(text = stringResource(MR.strings.transition_no_next))
            }
        } else {
            TransitionChapterText(header = stringResource(MR.strings.transition_current), chapter = currentName)
        }
    }
}

@Composable
private fun TransitionChapterText(
    header: String,
    chapter: String,
    scanlator: String? = null,
) {
    Column {
        Text(
            text = header,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = chapter,
            fontSize = 20.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge,
        )
        scanlator?.let {
            Text(
                text = it,
                modifier = Modifier
                    .secondaryItemAlpha()
                    .padding(top = 2.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TransitionFallbackCard(text: String) {
    OutlinedCard(colors = transitionCardColors()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null,
            )
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun transitionCardColors() = CardDefaults.outlinedCardColors(
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * Android's in-reader settings (reduced): the per-manga reading mode picker
 * and the page-number toggle.
 */
@Composable
private fun ReaderSettingsSheet(
    state: MangaReaderScreenModel.State,
    onModeSelect: (MangaReadingMode) -> Unit,
    onOrientationSelect: (Int) -> Unit,
    onPageNumberChange: (Boolean) -> Unit,
    onTransitionsChange: (Boolean) -> Unit,
    onThemeSelect: (Int) -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onLongTapChange: (Boolean) -> Unit,
    onScaleTypeSelect: (Int) -> Unit,
    onNavModeSelect: (Int) -> Unit,
    onTappingInvertSelect: (TappingInvertMode) -> Unit,
    onSidePaddingChange: (Int) -> Unit,
    onNavigateToPanChange: (Boolean) -> Unit,
    onLandscapeZoomChange: (Boolean) -> Unit,
    onPageTransitionsChange: (Boolean) -> Unit,
    onDualPageSplitChange: (Boolean) -> Unit,
    onDualPageInvertChange: (Boolean) -> Unit,
    onDualPageRotateChange: (Boolean) -> Unit,
    onDualPageRotateInvertChange: (Boolean) -> Unit,
    onDualPageSplitWebtoonChange: (Boolean) -> Unit,
    onDualPageInvertWebtoonChange: (Boolean) -> Unit,
    onDualPageRotateWebtoonChange: (Boolean) -> Unit,
    onDualPageRotateInvertWebtoonChange: (Boolean) -> Unit,
    onZoomStartSelect: (Int) -> Unit,
    onCustomBrightnessChange: (Boolean) -> Unit,
    onBrightnessValueChange: (Int) -> Unit,
    onColorFilterChange: (Boolean) -> Unit,
    onColorValueChange: (Int) -> Unit,
    onColorModeSelect: (Int) -> Unit,
    onGrayscaleChange: (Boolean) -> Unit,
    onInvertedColorsChange: (Boolean) -> Unit,
    onFlashToggle: (Boolean) -> Unit,
    onFlashDuration: (Int) -> Unit,
    onFlashInterval: (Int) -> Unit,
    onFlashColorSelect: (ReaderFlashColor) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(MR.strings.pref_category_reading_mode),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SettingDropdown(
                labelRes = MR.strings.pref_category_reading_mode,
                current = stringResource(MangaReadingMode.fromFlag(state.viewerFlag).titleRes),
                options = MangaReadingMode.entries.map { it to it.titleRes },
                onSelect = onModeSelect,
            )
            // Android's rotation_type chips — flagValues match ReaderOrientation.
            Text(
                text = stringResource(MR.strings.rotation_type),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                orientationOptions.forEach { (labelRes, value) ->
                    FilterChip(
                        selected = state.orientation == value,
                        onClick = { onOrientationSelect(value) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            Text(
                text = stringResource(MR.strings.pref_reader_theme),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                readerThemeOptions.forEach { (labelRes, value) ->
                    FilterChip(
                        selected = state.readerTheme == value,
                        onClick = { onThemeSelect(value) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            SettingDropdown(
                labelRes = MR.strings.pref_image_scale_type,
                current = stringResource(
                    imageScaleOptions[
                        (state.imageScaleType - 1).coerceIn(imageScaleOptions.indices),
                    ],
                ),
                options = imageScaleOptions.mapIndexed { i, res -> (i + 1) to res },
                onSelect = onScaleTypeSelect,
            )
            SettingDropdown(
                labelRes = MR.strings.pref_zoom_start,
                current = stringResource(
                    zoomStartOptions[state.zoomStart.coerceIn(zoomStartOptions.indices)],
                ),
                options = zoomStartOptions.mapIndexed { i, res -> i to res },
                onSelect = onZoomStartSelect,
            )
            SettingDropdown(
                labelRes = MR.strings.pref_viewer_nav,
                current = stringResource(
                    navModeOptions[state.navMode.coerceIn(navModeOptions.indices)],
                ),
                options = navModeOptions.mapIndexed { i, res -> i to res },
                onSelect = onNavModeSelect,
            )
            // Android hides the invert chips for the disabled layout.
            if (state.navMode != 5) {
                SettingDropdown(
                    labelRes = MR.strings.pref_read_with_tapping_inverted,
                    current = stringResource(state.tappingInverted.titleRes),
                    options = TappingInvertMode.entries.map { it to it.titleRes },
                    onSelect = onTappingInvertSelect,
                )
            }
            SettingSlider(
                labelRes = MR.strings.pref_webtoon_side_padding,
                value = state.webtoonSidePadding.toFloat(),
                range = 0f..25f,
                onChange = { onSidePaddingChange(it.roundToInt()) },
            )
            SettingSwitch(
                labelRes = MR.strings.pref_landscape_zoom,
                checked = state.landscapeZoom,
                onChange = onLandscapeZoomChange,
            )
            SettingSwitch(
                labelRes = MR.strings.pref_navigate_pan,
                checked = state.navigateToPan,
                onChange = onNavigateToPanChange,
            )
            // Android's dual-page group — gated per viewer type like
            // ReadingModePage (pager controls vs webtoon controls).
            if (state.readingMode == MangaReadingMode.WEBTOON ||
                state.readingMode == MangaReadingMode.CONTINUOUS_VERTICAL
            ) {
                SettingSwitch(
                    labelRes = MR.strings.pref_dual_page_split,
                    checked = state.dualPageSplitWebtoon,
                    onChange = onDualPageSplitWebtoonChange,
                )
                if (state.dualPageSplitWebtoon) {
                    SettingSwitch(
                        labelRes = MR.strings.pref_dual_page_invert,
                        checked = state.dualPageInvertWebtoon,
                        onChange = onDualPageInvertWebtoonChange,
                    )
                }
                SettingSwitch(
                    labelRes = MR.strings.pref_page_rotate,
                    checked = state.dualPageRotateWebtoon,
                    onChange = onDualPageRotateWebtoonChange,
                )
                if (state.dualPageRotateWebtoon) {
                    SettingSwitch(
                        labelRes = MR.strings.pref_page_rotate_invert,
                        checked = state.dualPageRotateInvertWebtoon,
                        onChange = onDualPageRotateInvertWebtoonChange,
                    )
                }
            } else {
                SettingSwitch(
                    labelRes = MR.strings.pref_dual_page_split,
                    checked = state.dualPageSplit,
                    onChange = onDualPageSplitChange,
                )
                if (state.dualPageSplit) {
                    SettingSwitch(
                        labelRes = MR.strings.pref_dual_page_invert,
                        checked = state.dualPageInvert,
                        onChange = onDualPageInvertChange,
                    )
                }
                SettingSwitch(
                    labelRes = MR.strings.pref_page_rotate,
                    checked = state.dualPageRotate,
                    onChange = onDualPageRotateChange,
                )
                if (state.dualPageRotate) {
                    SettingSwitch(
                        labelRes = MR.strings.pref_page_rotate_invert,
                        checked = state.dualPageRotateInvert,
                        onChange = onDualPageRotateInvertChange,
                    )
                }
            }
            SettingSwitch(
                labelRes = MR.strings.pref_show_page_number,
                checked = state.showPageNumber,
                onChange = onPageNumberChange,
            )
            SettingSwitch(
                labelRes = MR.strings.pref_always_show_chapter_transition,
                checked = state.showTransitions,
                onChange = onTransitionsChange,
            )
            SettingSwitch(
                labelRes = MR.strings.pref_page_transitions,
                checked = state.pageTransitions,
                onChange = onPageTransitionsChange,
            )
            SettingSwitch(
                labelRes = MR.strings.pref_fullscreen,
                checked = state.fullscreen,
                onChange = onFullscreenChange,
            )
            SettingSwitch(
                labelRes = MR.strings.pref_read_with_long_tap,
                checked = state.longTapScroll,
                onChange = onLongTapChange,
            )
            // Android's E-Ink group — sub-controls disabled until the
            // master switch is on.
            SettingSwitch(
                labelRes = MR.strings.pref_flash_page,
                checked = state.flashOnPageChange,
                onChange = onFlashToggle,
            )
            SettingSlider(
                labelRes = MR.strings.pref_flash_duration,
                value = state.flashDurationMillis / 100f,
                range = 1f..15f,
                onChange = { onFlashDuration((it * 100).roundToInt()) },
                enabled = state.flashOnPageChange,
            )
            SettingSlider(
                labelRes = MR.strings.pref_flash_page_interval,
                value = state.flashInterval.toFloat(),
                range = 1f..10f,
                onChange = { onFlashInterval(it.roundToInt()) },
                enabled = state.flashOnPageChange,
            )
            SettingDropdown(
                labelRes = MR.strings.pref_flash_with,
                current = stringResource(state.flashColor.titleRes),
                options = ReaderFlashColor.entries.map { it to it.titleRes },
                onSelect = onFlashColorSelect,
                enabled = state.flashOnPageChange,
            )
            // Android's Color Filter tab — darkening/color overlays plus
            // page color matrices, in Android's row order.
            Text(
                text = stringResource(MR.strings.pref_custom_color_filter),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SettingSwitch(
                labelRes = MR.strings.pref_custom_brightness,
                checked = state.customBrightness,
                onChange = onCustomBrightnessChange,
            )
            // Android shows the sub-rows only while their toggle is on.
            if (state.customBrightness) {
                SettingSlider(
                    labelRes = MR.strings.pref_custom_brightness,
                    value = state.customBrightnessValue.toFloat(),
                    range = -75f..100f,
                    onChange = { onBrightnessValueChange(it.roundToInt()) },
                )
            }
            SettingSwitch(
                labelRes = MR.strings.pref_custom_color_filter,
                checked = state.colorFilterEnabled,
                onChange = onColorFilterChange,
            )
            if (state.colorFilterEnabled) {
                SettingSlider(
                    labelRes = MR.strings.color_filter_r_value,
                    value = ((state.colorFilterValue shr 16) and 0xFF).toFloat(),
                    range = 0f..255f,
                    onChange = {
                        onColorValueChange(
                            packChannel(state.colorFilterValue, 16, it.roundToInt()),
                        )
                    },
                )
                SettingSlider(
                    labelRes = MR.strings.color_filter_g_value,
                    value = ((state.colorFilterValue shr 8) and 0xFF).toFloat(),
                    range = 0f..255f,
                    onChange = {
                        onColorValueChange(
                            packChannel(state.colorFilterValue, 8, it.roundToInt()),
                        )
                    },
                )
                SettingSlider(
                    labelRes = MR.strings.color_filter_b_value,
                    value = (state.colorFilterValue and 0xFF).toFloat(),
                    range = 0f..255f,
                    onChange = {
                        onColorValueChange(
                            packChannel(state.colorFilterValue, 0, it.roundToInt()),
                        )
                    },
                )
                SettingSlider(
                    labelRes = MR.strings.color_filter_a_value,
                    value = ((state.colorFilterValue ushr 24) and 0xFF).toFloat(),
                    range = 0f..255f,
                    onChange = {
                        onColorValueChange(
                            packChannel(state.colorFilterValue, 24, it.roundToInt()),
                        )
                    },
                )
                Text(
                    text = stringResource(MR.strings.pref_color_filter_mode),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                ) {
                    colorFilterModeOptions.forEach { (labelRes, value) ->
                        FilterChip(
                            selected = state.colorFilterMode == value,
                            onClick = { onColorModeSelect(value) },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
            }
            SettingSwitch(
                labelRes = MR.strings.pref_grayscale,
                checked = state.grayscale,
                onChange = onGrayscaleChange,
            )
            SettingSwitch(
                labelRes = MR.strings.pref_inverted_colors,
                checked = state.invertedColors,
                onChange = onInvertedColorsChange,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private const val MAX_ZOOM_SCALE = 3f
private const val DOUBLE_TAP_ZOOM_SCALE = 2.5f

/**
 * Android's usePageTransitions — pref off turns programmatic page
 * flips (tap zones, long-tap scroll, edge pan) into instant snaps.
 */
private suspend fun flipToPage(
    pagerState: androidx.compose.foundation.pager.PagerState,
    page: Int,
    animated: Boolean,
) {
    if (animated) {
        pagerState.animateScrollToPage(page)
    } else {
        pagerState.scrollToPage(page)
    }
}

/** Android's ViewerNavigation.NavigationRegion — LEFT/RIGHT are spatial. */
private enum class NavZone(val delta: Int) {
    MENU(0),
    PREV(-1),
    NEXT(1),
    LEFT(-1),
    RIGHT(1),
}

/** Android's Region — normalized rect (l,t,r,b) mapped to a zone. */
private data class NavRegion(
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
    val zone: NavZone,
)

private val RIGHT_AND_LEFT_REGIONS = listOf(
    NavRegion(0f, 0f, 0.33f, 1f, NavZone.LEFT),
    NavRegion(0.66f, 0f, 1f, 1f, NavZone.RIGHT),
)

private val L_REGIONS = listOf(
    NavRegion(0f, 0.33f, 0.33f, 0.66f, NavZone.PREV),
    NavRegion(0f, 0f, 1f, 0.33f, NavZone.PREV),
    NavRegion(0.66f, 0.33f, 1f, 0.66f, NavZone.NEXT),
    NavRegion(0f, 0.66f, 1f, 1f, NavZone.NEXT),
)

private val KINDLISH_REGIONS = listOf(
    NavRegion(0.33f, 0.33f, 1f, 1f, NavZone.NEXT),
    NavRegion(0f, 0.33f, 0.33f, 1f, NavZone.PREV),
)

private val EDGE_REGIONS = listOf(
    NavRegion(0f, 0f, 0.33f, 1f, NavZone.NEXT),
    NavRegion(0.33f, 0.66f, 0.66f, 1f, NavZone.PREV),
    NavRegion(0.66f, 0f, 1f, 1f, NavZone.NEXT),
)

/**
 * Android's nav layouts per `reader_navigation_mode_pager` — 0/4 are the
 * right-and-left thirds, 5 is Disabled (every tap = menu). Inversion
 * mirrors the rects like `RectF.invert`, not the action.
 */
private fun navRegions(mode: Int, invert: TappingInvertMode): List<NavRegion> {
    val base = when (mode) {
        1 -> L_REGIONS
        2 -> KINDLISH_REGIONS
        3 -> EDGE_REGIONS
        5 -> emptyList()
        else -> RIGHT_AND_LEFT_REGIONS
    }
    return base.map { region ->
        when {
            invert.shouldInvertHorizontal && invert.shouldInvertVertical -> region.copy(
                l = 1f - region.r,
                r = 1f - region.l,
                t = 1f - region.b,
                b = 1f - region.t,
            )
            invert.shouldInvertHorizontal -> region.copy(
                l = 1f - region.r,
                r = 1f - region.l,
            )
            invert.shouldInvertVertical -> region.copy(
                t = 1f - region.b,
                b = 1f - region.t,
            )
            else -> region
        }
    }
}

/**
 * Android's DisplayRefreshHost — a brief overlay on page change that
 * clears e-ink ghosting. WHITE_BLACK flashes white then black; the other
 * modes hold a single color for the whole duration.
 */
@Composable
private fun ReaderFlashOverlay(
    trigger: Int,
    durationMillis: Int,
    mode: ReaderFlashColor,
) {
    var currentColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        val half = (durationMillis / 2L).coerceAtLeast(1)
        currentColor = if (mode == ReaderFlashColor.WHITE) Color.White else Color.Black
        delay(half)
        if (mode == ReaderFlashColor.WHITE_BLACK) {
            currentColor = Color.Black
        }
        delay(half)
        currentColor = null
    }
    currentColor?.let { color ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color),
        )
    }
}

/** Android's TapZones list — pref values are the list index. */
private val navModeOptions = listOf(
    MR.strings.label_default,
    MR.strings.l_nav,
    MR.strings.kindlish_nav,
    MR.strings.edge_nav,
    MR.strings.right_and_left_nav,
    MR.strings.disabled_nav,
)

/**
 * Android's page zoom state (SubsamplingScaleImageView scale + pan vTranslate,
 * center pivot). Resets to 1x whenever the pager settles on a new page.
 */
private class ReaderZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun clamp(viewSize: IntSize) {
        val maxX = viewSize.width * (scale - 1f) / 2f
        val maxY = viewSize.height * (scale - 1f) / 2f
        offset = Offset(
            offset.x.coerceIn(-maxX, maxX),
            offset.y.coerceIn(-maxY, maxY),
        )
    }

    /**
     * Android's double-tap zoom: toggles between 1x and a fixed scale,
     * keeping the tapped content point under the finger.
     */
    suspend fun animateDoubleTap(pos: Offset, viewSize: IntSize) {
        val center = Offset(viewSize.width / 2f, viewSize.height / 2f)
        val targetScale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM_SCALE
        // With a center pivot, holding the tap point stationary means
        // offset -= p * (newScale - oldScale), p in center-relative px.
        val targetOffset = if (targetScale == 1f) {
            Offset.Zero
        } else {
            offset - (pos - center) * (targetScale - scale)
        }
        val fromScale = scale
        val fromOffset = offset
        Animatable(0f).animateTo(1f, tween(300)) {
            scale = lerp(fromScale, targetScale, value)
            offset = lerp(fromOffset, targetOffset, value)
        }
        clamp(viewSize)
    }
}

/**
 * Android's ReaderContentOverlay — a single Canvas over the page layer.
 * First rect darkens for negative custom brightness; second paints the
 * packed-ARGB filter color with the chosen blend mode.
 */
@Composable
private fun ReaderFilterOverlay(state: MangaReaderScreenModel.State) {
    val darkAlpha = if (state.customBrightness && state.customBrightnessValue < 0) {
        kotlin.math.abs(state.customBrightnessValue) / 100f
    } else {
        0f
    }
    val overlayColor = if (state.colorFilterEnabled) {
        Color(state.colorFilterValue)
    } else {
        Color.Transparent
    }
    val blendMode = when (state.colorFilterMode) {
        1 -> BlendMode.Modulate
        2 -> BlendMode.Screen
        3 -> BlendMode.Overlay
        4 -> BlendMode.Lighten
        5 -> BlendMode.Darken
        else -> BlendMode.SrcOver
    }
    if (darkAlpha == 0f && overlayColor == Color.Transparent) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (darkAlpha > 0f) {
            drawRect(Color.Black.copy(alpha = darkAlpha))
        }
        drawRect(overlayColor, blendMode = blendMode)
    }
}
