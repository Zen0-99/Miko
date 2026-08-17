package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import android.view.GestureDetector
import android.view.ViewConfiguration
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import tachiyomi.presentation.core.util.collectAsState
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.novel.reader.NovelChaptersSheet
import eu.kanade.presentation.novel.reader.NovelCommentsDialog
import eu.kanade.presentation.novel.reader.NovelReaderChrome
import eu.kanade.presentation.novel.reader.NovelReaderSettingsDialog
import eu.kanade.presentation.novel.reader.NovelTtsControlsBar
import eu.kanade.presentation.novel.reader.NovelReaderSettingsScreenModel
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.novel.NovelAutoScrollChapterEndBehavior
import eu.kanade.tachiyomi.ui.reader.novel.dictionary.DictionaryBottomSheet
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import kotlin.math.hypot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import tachiyomi.presentation.core.screens.LoadingScreen

class NovelReaderScreen(
    private val novelId: Long,
    private val chapterId: Long? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val activity = remember(context) { context.findActivity() }
        val screenModel = rememberScreenModel {
            NovelReaderScreenModel(novelId, chapterId)
        }

        LaunchedEffect(Unit) {
            screenModel.initContext(context)
            screenModel.initHighlightManager(context)
            screenModel.initialize()
        }

        DisposableEffect(Unit) {
            onDispose {
                // Save position synchronously — the screen model's scope is
                // about to be cancelled, so launchIO might not complete.
                kotlinx.coroutines.runBlocking {
                    screenModel.saveCurrentPositionBlocking()
                }
                screenModel.stopTtsPlayback()
                screenModel.unbindTtsService()
                screenModel.shutdownTts()
            }
        }

        val state by screenModel.state.collectAsStateWithLifecycle()
        val contentItems by screenModel.contentItems.collectAsStateWithLifecycle()
        val isLoading by screenModel.isLoading.collectAsStateWithLifecycle()
        val currentChapter by screenModel.currentChapter.collectAsStateWithLifecycle()
        val novel by screenModel.novel.collectAsStateWithLifecycle()
        val chapters by screenModel.chapters.collectAsStateWithLifecycle()
        val isControlsVisible by screenModel.isControlsVisible.collectAsStateWithLifecycle()
        val isSettingsVisible by screenModel.isSettingsVisible.collectAsStateWithLifecycle()
        val isChaptersSheetVisible by screenModel.isChaptersSheetVisible.collectAsStateWithLifecycle()
        val isCommentsDialogVisible by screenModel.isCommentsDialogVisible.collectAsStateWithLifecycle()
        val comments by screenModel.comments.collectAsStateWithLifecycle()
        val isLoadingComments by screenModel.isLoadingComments.collectAsStateWithLifecycle()
        val commentsError by screenModel.commentsError.collectAsStateWithLifecycle()
        val textConfig by screenModel.textConfig.collectAsStateWithLifecycle()
        val dictionaryQuery by screenModel.dictionaryQuery.collectAsStateWithLifecycle()
        val progressPercent by screenModel.progressPercent.collectAsStateWithLifecycle()
        val ttsPlaybackState by screenModel.ttsPlaybackState.collectAsStateWithLifecycle()

        // Settings dialog scroll states — persist across open/close within the same
        // chapter session, but reset when the chapter changes.
        val generalScrollState = remember { androidx.compose.foundation.ScrollState(0) }
        val textScrollState = remember { androidx.compose.foundation.ScrollState(0) }
        val ttsScrollState = remember { androidx.compose.foundation.ScrollState(0) }
        val savedSettingsPage = remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val currentChapterId = currentChapter?.id
        androidx.compose.runtime.LaunchedEffect(currentChapterId) {
            // Reset scroll states and page when chapter changes
            generalScrollState.scrollTo(0)
            textScrollState.scrollTo(0)
            ttsScrollState.scrollTo(0)
            savedSettingsPage.intValue = 0
        }

        // Accent color: use cover-derived color if the preference is enabled
        val useCoverAccent by screenModel.preferences.useCoverAccentColor().collectAsState()
        val rawAccentColor by screenModel.accentColor.collectAsStateWithLifecycle()
        val accentColor = if (useCoverAccent && rawAccentColor != null) {
            val isDark = when (screenModel.readerPreferences.readerTheme().get()) {
                0 -> false
                1, 2 -> true
                else -> false
            }
            Color(eu.kanade.presentation.entries.components.adjustForTheme(rawAccentColor!!, isDark))
        } else {
            null
        }

        val themeBackgroundColor = MaterialTheme.colorScheme.background.toArgb()
        val themeBackgroundColorCompose = MaterialTheme.colorScheme.background
        val themeTextColor = MaterialTheme.colorScheme.onBackground.toArgb()
        LaunchedEffect(themeBackgroundColor, themeTextColor) {
            screenModel.refreshTextConfig(themeBackgroundColor, themeTextColor)
        }

        // Hoisted RecyclerView reference so event handlers can drive scroll.
        var recyclerViewRef by remember { mutableStateOf<RecyclerView?>(null) }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                when (event) {
                    is NovelReaderEvent.ShowError -> {}
                    is NovelReaderEvent.ShowMessage -> {}
                    is NovelReaderEvent.ChapterChanged -> {}
                    is NovelReaderEvent.ScrollToPosition -> {
                        // Store the transition position — it will be applied in the
                        // AndroidView's update callback after submitList completes.
                        // This ensures the adapter has the new items before the
                        // animation runs, and avoids postDelayed which can cause
                        // system bars to flash.
                        screenModel.pendingTransitionPosition = event.position
                    }
                    is NovelReaderEvent.ScrollToCharacter -> {
                        val rv = recyclerViewRef
                        if (rv != null && event.characterPosition > 0) {
                            // submitList is async, so the adapter may not have
                            // items yet. Retry several times with increasing delay.
                            val targetPos = event.characterPosition
                            var attempts = 0
                            val maxAttempts = 10
                            fun tryScroll() {
                                val adapter = rv.adapter as? TextAdapter
                                val items = adapter?.currentList
                                if (items.isNullOrEmpty()) {
                                    if (++attempts < maxAttempts) {
                                        rv.postDelayed(::tryScroll, 100L * attempts)
                                    }
                                    return
                                }
                                val paragraphs = items.filterIsInstance<TextItem.Paragraph>()
                                if (paragraphs.isEmpty()) {
                                    if (++attempts < maxAttempts) {
                                        rv.postDelayed(::tryScroll, 100L * attempts)
                                    }
                                    return
                                }
                                // The saved position is per-chapter (0-based).
                                // The paragraph startCharIndex/endCharIndex are
                                // also per-chapter (each chapter starts at 0).
                                val target = paragraphs.find { p ->
                                    targetPos >= p.startCharIndex &&
                                        targetPos <= p.endCharIndex
                                }
                                if (target != null) {
                                    val adapterPos = items.indexOf(target)
                                    if (adapterPos >= 0) {
                                        val lm = rv.layoutManager as? LinearLayoutManager
                                        lm?.scrollToPositionWithOffset(adapterPos, 1)
                                    }
                                } else {
                                    // Fallback: find the closest paragraph by
                                    // startCharIndex (the one just before targetPos).
                                    val closest = paragraphs.minByOrNull { p ->
                                        if (p.startCharIndex <= targetPos) {
                                            targetPos - p.startCharIndex
                                        } else {
                                            p.startCharIndex - targetPos + 10000
                                        }
                                    }
                                    if (closest != null) {
                                        val adapterPos = items.indexOf(closest)
                                        if (adapterPos >= 0) {
                                            val lm = rv.layoutManager as? LinearLayoutManager
                                            lm?.scrollToPositionWithOffset(adapterPos, 1)
                                        }
                                    }
                                    if (++attempts < maxAttempts) {
                                        rv.postDelayed(::tryScroll, 100L * attempts)
                                    }
                                }
                            }
                            rv.post(::tryScroll)
                        }
                    }
                }
            }
        }

        if (state.loading && contentItems.isEmpty()) {
            LoadingScreen(color = accentColor ?: MaterialTheme.colorScheme.primary)
            return
        }

        state.error?.let { error ->
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = error, color = Color.Red)
            }
            return
        }

        val bgColor = Color(textConfig.backgroundColor)

        // Auto-scroll: coroutine loop that scrolls the RecyclerView.
        // Two modes:
        //  - Stepped: scrolls by a fixed pixel step at a fixed interval
        //  - Smooth: scrolls continuously pixel-by-pixel at a configurable speed
        // Both modes pause when controls are visible. Smooth mode also pauses
        // when the user manually scrolls, then resumes after 1 second.
        val autoScrollEnabled by screenModel.preferences.autoScroll().collectAsState()
        val smoothAutoScroll by screenModel.preferences.smoothAutoScroll().collectAsState()
        val autoScrollInterval by screenModel.preferences.autoScrollInterval().collectAsState()
        val autoScrollOffset by screenModel.preferences.autoScrollOffset().collectAsState()
        val smoothSpeed by screenModel.preferences.smoothAutoScrollSpeed().collectAsState()

        // Track whether the user is currently touching/dragging the list.
        // Auto-scroll does NOT resume until the finger is lifted.
        var isUserTouching by remember { mutableStateOf(false) }

        // Track whether text selection is active — auto-scroll pauses during
        // text selection so the user can select text without it scrolling away.
        var isTextSelectionActive by remember { mutableStateOf(false) }

        // Track window focus — when the user pulls down the notification shade
        // or opens the Android app bar, the activity loses window focus.
        var hasWindowFocus by remember { mutableStateOf(true) }
        DisposableEffect(activity) {
            val view = activity?.window?.decorView
            val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                hasWindowFocus = hasFocus
            }
            view?.viewTreeObserver?.addOnWindowFocusChangeListener(listener)
            onDispose {
                view?.viewTreeObserver?.removeOnWindowFocusChangeListener(listener)
            }
        }

        // Auto-scroll should only run when the user is in full focus of the text —
        // not when any overlay (settings, chapters, comments, dictionary, TTS
        // controls, translation dialog) is open, not while the user is
        // actively touching the screen, and not when the window lost focus
        // (notification shade pulled down).
        val isOverlayActive = isControlsVisible || isSettingsVisible ||
            isChaptersSheetVisible || isCommentsDialogVisible ||
            dictionaryQuery != null || !hasWindowFocus || isTextSelectionActive

        LaunchedEffect(autoScrollEnabled, smoothAutoScroll, autoScrollInterval, autoScrollOffset, smoothSpeed, isOverlayActive, isUserTouching, isLoading) {
            if (!autoScrollEnabled) return@LaunchedEffect
            // Chapter end behavior: what to do when auto-scroll reaches bottom
            val endBehavior = screenModel.preferences.autoScrollChapterEndBehavior().get()
            if (smoothAutoScroll) {
                // Smooth mode: scroll continuously using frame timing.
                // speed is in pixels/second. We use a fractional pixel
                // accumulator so low speeds (1px/s) scroll at the correct rate
                // instead of rounding to 0 or jumping to 60px/s.
                val frameIntervalMs = 16L
                val pixelsPerFrame = smoothSpeed.toFloat() / 60f
                var pixelAccumulator = 0f
                while (isActive) {
                    delay(frameIntervalMs)
                    val rv = recyclerViewRef
                    if (rv != null && !isOverlayActive && !isUserTouching && !isLoading) {
                        // Check if we've reached the bottom
                        if (!rv.canScrollVertically(1)) {
                            when (endBehavior) {
                                NovelAutoScrollChapterEndBehavior.StopAtEnd -> {
                                    screenModel.preferences.autoScroll().set(false)
                                    return@LaunchedEffect
                                }
                                NovelAutoScrollChapterEndBehavior.AdvanceAndStop -> {
                                    val advanced = screenModel.advanceToNextChapter()
                                    screenModel.preferences.autoScroll().set(false)
                                    if (!advanced) return@LaunchedEffect
                                    return@LaunchedEffect
                                }
                                NovelAutoScrollChapterEndBehavior.ContinuousReading -> {
                                    if (!screenModel.advanceToNextChapter()) {
                                        screenModel.preferences.autoScroll().set(false)
                                        return@LaunchedEffect
                                    }
                                    // Wait for the new chapter to load before continuing
                                    delay(1500)
                                }
                            }
                        }
                        pixelAccumulator += pixelsPerFrame
                        val pixelsToScroll = pixelAccumulator.toInt()
                        if (pixelsToScroll > 0) {
                            pixelAccumulator -= pixelsToScroll
                            rv.post { rv.scrollBy(0, pixelsToScroll) }
                        }
                    }
                }
            } else {
                // Stepped mode: scroll by fixed offset at fixed interval
                while (isActive) {
                    delay(autoScrollInterval.toLong())
                    val rv = recyclerViewRef
                    if (rv != null && !isOverlayActive && !isUserTouching && !isLoading) {
                        // Check if we've reached the bottom
                        if (!rv.canScrollVertically(1)) {
                            when (endBehavior) {
                                NovelAutoScrollChapterEndBehavior.StopAtEnd -> {
                                    screenModel.preferences.autoScroll().set(false)
                                    return@LaunchedEffect
                                }
                                NovelAutoScrollChapterEndBehavior.AdvanceAndStop -> {
                                    screenModel.advanceToNextChapter()
                                    screenModel.preferences.autoScroll().set(false)
                                    return@LaunchedEffect
                                }
                                NovelAutoScrollChapterEndBehavior.ContinuousReading -> {
                                    if (!screenModel.advanceToNextChapter()) {
                                        screenModel.preferences.autoScroll().set(false)
                                        return@LaunchedEffect
                                    }
                                    delay(1500)
                                }
                            }
                        }
                        rv.post { rv.scrollBy(0, autoScrollOffset) }
                    }
                }
            }
        }

        // Tap-to-scroll and swipe gesture state
        // Collect tap-to-navigate as state so it updates live when the user
        // toggles it in settings. The AndroidView factory captures a holder
        // that gets refreshed in the update callback.
        val tapToScrollEnabled by screenModel.preferences.tapToScroll().collectAsState()
        val tapToScrollHolder = remember { mutableStateOf(tapToScrollEnabled) }
        LaunchedEffect(tapToScrollEnabled) {
            tapToScrollHolder.value = tapToScrollEnabled
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
        ) {
            NovelReaderContent(
                screenModel = screenModel,
                activity = activity,
                contentItems = contentItems,
                textConfig = textConfig,
                isLoading = isLoading,
                accentColor = accentColor,
                bottomPaddingDp = if (screenModel.preferences.showBatteryAndTime().get()) 28 else 0,
                tapToScrollEnabled = tapToScrollHolder.value,
                tapToScrollEnabledGetter = { tapToScrollHolder.value },
                onScrollStateChanged = { isDragging -> isUserTouching = isDragging },
                onToggleControls = { screenModel.toggleControls() },
                onSelectionActiveChange = { active -> isTextSelectionActive = active },
                onRecyclerViewReady = { rv ->
                    recyclerViewRef = rv

                    // Apply pending scroll restoration (Tadami-style).
                    // The saved item index + pixel offset are applied before
                    // the RecyclerView becomes visible, so the user never sees
                    // content at position 0 — no visual jump.
                    // The RV alpha is set to 0 (invisible) until the scroll is
                    // applied, then set back to 1. This doesn't block the UI
                    // thread — the rest of the app renders normally while the
                    // RV waits for its adapter to have items.
                    val pending = screenModel.pendingScrollRestore
                    if (pending != null) {
                        screenModel.pendingScrollRestore = null
                        val (targetIndex, targetOffset) = pending
                        Log.d("NovelReader", "onRecyclerViewReady: pendingScrollRestore targetIndex=$targetIndex targetOffset=$targetOffset")
                        if (targetIndex > 0 || targetOffset > 0) {
                            // Hide the RV until we've scrolled to the saved position.
                            rv.alpha = 0f
                            var attempts = 0
                            val maxAttempts = 15
                            fun tryRestoreScroll() {
                                val adapter = rv.adapter as? TextAdapter
                                val items = adapter?.currentList
                                if (items.isNullOrEmpty()) {
                                    Log.d("NovelReader", "tryRestoreScroll: attempt=$attempts items=null targetIndex=$targetIndex — waiting")
                                    if (++attempts < maxAttempts) {
                                        rv.postDelayed(::tryRestoreScroll, 50L)
                                    } else {
                                        Log.w("NovelReader", "tryRestoreScroll: GAVE UP (no items) after $maxAttempts attempts")
                                        rv.alpha = 1f
                                    }
                                    return
                                }
                                // If the target index is beyond the list size, the
                                // saved position was from infinite-scroll mode where
                                // multiple chapters were appended. Clamp to the last
                                // available item instead of giving up at position 0.
                                val effectiveIndex = if (targetIndex >= items.size) {
                                    Log.w("NovelReader", "tryRestoreScroll: targetIndex=$targetIndex > items.size=${items.size}, clamping to last item")
                                    items.size - 1
                                } else {
                                    targetIndex
                                }
                                val lm = rv.layoutManager as? LinearLayoutManager
                                if (lm == null) {
                                    Log.w("NovelReader", "tryRestoreScroll: layoutManager is null, showing at default")
                                    rv.alpha = 1f
                                    return
                                }
                                // Scroll to exact saved position: item index + pixel offset.
                                // This is the key difference from the old approach —
                                // we restore the EXACT pixel offset, not just the
                                // paragraph start, eliminating position drift.
                                Log.d("NovelReader", "tryRestoreScroll: scrolling to index=$effectiveIndex offset=$targetOffset (items=${items.size})")
                                lm.scrollToPositionWithOffset(effectiveIndex, targetOffset)
                                // Make the RV visible on the next frame, after
                                // the scroll has been applied and laid out.
                                rv.post { rv.alpha = 1f }
                            }
                            // Start trying on the next frame.
                            rv.post(::tryRestoreScroll)
                        } else {
                            Log.d("NovelReader", "onRecyclerViewReady: targetIndex=0 offset=0, no restore needed")
                        }
                    } else {
                        Log.d("NovelReader", "onRecyclerViewReady: no pendingScrollRestore")
                    }
                },
            )

            // Background texture overlay — drawn ON TOP of the RecyclerView.
            // Uses bitmap textures (WebP) with ShaderBrush tiling for paper grain
            // and linen, and radial gradients for parchment. Textures are
            // grayscale/color-agnostic and blend with any background via alpha.
            if (textConfig.backgroundTexture != NovelReaderBackgroundTexture.NONE) {
                NovelTextureOverlay(
                    texture = textConfig.backgroundTexture,
                    strengthPercent = textConfig.textureStrength,
                    backgroundColor = bgColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // OLED edge gradient: radial vignette from center to edges.
            // Only active in dark theme — creates a subtle darkening at screen
            // edges using a radial gradient (transparent center → black edges).
            // Uses farthest-corner radius so the gradient reaches all corners.
            if (textConfig.oledEdgeGradient) {
                val bgLum = 0.299f * bgColor.red + 0.587f * bgColor.green + 0.114f * bgColor.blue
                val isDarkTheme = bgLum < 0.5f
                if (isDarkTheme) {
                    Canvas(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val center = Offset(size.width * 0.5f, size.height * 0.5f)
                        // Farthest-corner radius: distance from center to the
                        // farthest screen corner, so the gradient covers everything.
                        val tl = hypot(center.x.toDouble(), center.y.toDouble()).toFloat()
                        val tr = hypot((size.width - center.x).toDouble(), center.y.toDouble()).toFloat()
                        val bl = hypot(center.x.toDouble(), (size.height - center.y).toDouble()).toFloat()
                        val br = hypot((size.width - center.x).toDouble(), (size.height - center.y).toDouble()).toFloat()
                        val radius = maxOf(tl, tr, bl, br)
                        drawRect(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.38f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.36f),
                                ),
                                center = center,
                                radius = radius,
                            ),
                        )
                    }
                }
            }

            // Page edge shadow: subtle inner shadow at page edges for depth.
            if (textConfig.pageEdgeShadowEnabled) {
                Canvas(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val alpha = textConfig.pageEdgeShadowAlpha.coerceIn(0f, 1f)
                    val edgeWidth = size.width * 0.03f
                    // Left edge shadow
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = alpha),
                            edgeWidth to Color.Transparent,
                        ),
                    )
                    // Right edge shadow
                    drawRect(
                        brush = Brush.horizontalGradient(
                            (size.width - edgeWidth) to Color.Transparent,
                            size.width to Color.Black.copy(alpha = alpha),
                        ),
                    )
                }
            }

            // Edge fade: top-only gradient that makes text fade out as it
            // scrolls under the top edge. The bottom edge fade is handled
            // entirely by the info overlay itself (NovelPhoneInfoOverlay),
            // which is a gradient from transparent (top) to opaque (bottom).
            if (textConfig.edgeFadeEnabled) {
                NovelEdgeFadeOverlay(
                    backgroundColor = bgColor,
                    texture = textConfig.backgroundTexture,
                    textureStrength = textConfig.textureStrength,
                    fadeHeightFraction = 0.08f,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            NovelReaderChrome(
                isMenuVisible = isControlsVisible,
                title = currentChapter?.name ?: "Loading...",
                subtitle = novel?.title ?: "",
                accentColor = accentColor,
                progressPercent = if (screenModel.preferences.showScrollPercentage().get()) progressPercent else -1,
                wordCount = if (screenModel.preferences.showWordCount().get()) {
                    screenModel.positionTracker.getTotalWordCount()
                } else -1,
                fullscreen = screenModel.preferences.fullscreen().get(),
                showPhoneInfo = screenModel.preferences.showBatteryAndTime().get(),
                estimatedReadingTime = if (screenModel.preferences.showEstimatedReadingTime().get()) {
                    screenModel.positionTracker.getEstimatedReadingTime()
                } else -1,
                readerBackgroundColor = bgColor,
                backgroundTexture = textConfig.backgroundTexture,
                textureStrength = textConfig.textureStrength,
                edgeFadeEnabled = textConfig.edgeFadeEnabled,
                showCommentsButton = screenModel.supportsComments,
                isTtsActive = screenModel.isTtsActive,
                onBackClick = { navigator.pop() },
                onChaptersClick = { screenModel.showChapters() },
                onWebviewClick = { screenModel.openChapterInWebView() },
                onHighlightsClick = {
                    novel?.let { n ->
                        navigator.push(NovelHighlightsScreen(n.title, n.id))
                    }
                },
                onSettingsClick = { screenModel.showSettings() },
                onCommentsClick = { screenModel.showComments() },
                onTtsClick = {
                    if (screenModel.isTtsActive) {
                        screenModel.stopTtsPlayback()
                    } else {
                        screenModel.startTtsPlayback()
                    }
                },
            )

            // TTS playback controls bar — shown above the bottom bar when TTS is active
            if (screenModel.ttsPreferences.showTtsControls().get()) {
                NovelTtsControlsBar(
                    state = ttsPlaybackState,
                    onPlay = { screenModel.resumeTtsPlayback() },
                    onPause = { screenModel.pauseTtsPlayback() },
                    onNext = { screenModel.nextTtsParagraph() },
                    onPrevious = { screenModel.previousTtsParagraph() },
                    onStop = { screenModel.stopTtsPlayback() },
                    accentColor = accentColor,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (isControlsVisible) 72.dp else 0.dp),
                )
            }

            // Auto-scroll floating button — quick toggle when enabled in settings.
            // Only visible when auto-scroll is enabled (it's a pause/resume button,
            // not an enable button). Positioned above the phone info area and the
            // bottom controls bar. Animates position when overlay appears/disappears.
            val autoScrollEnabled by screenModel.preferences.autoScroll().collectAsState()
            if (screenModel.preferences.showAutoScrollFloatingButton().get() && autoScrollEnabled) {
                val showPhoneInfo = screenModel.preferences.showBatteryAndTime().get()
                // Target bottom padding: phone info bar (28dp) + controls bar (72dp when visible)
                val targetBottomPadding = when {
                    isControlsVisible -> 72.dp + if (showPhoneInfo) 28.dp else 0.dp
                    showPhoneInfo -> 28.dp
                    else -> 0.dp
                }
                // Animate the padding change for smooth movement
                val animatedBottomPadding by animateDpAsState(
                    targetValue = targetBottomPadding,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                    label = "fab_bottom_padding",
                )
                FloatingActionButton(
                    onClick = {
                        screenModel.preferences.autoScroll().set(false)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .padding(bottom = animatedBottomPadding),
                    containerColor = accentColor ?: MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause auto-scroll",
                    )
                }
            }

            // Vertical seekbar — scroll position slider on the right edge.
            // Thinner and taller, with auto-fade after inactivity.
            // Value is inverted (100 - progress) so the thumb moves DOWN as the
            // user scrolls down, matching natural scroll position expectation.
            if (screenModel.preferences.verticalSeekbar().get()) {
                val rv = recyclerViewRef
                if (rv != null && contentItems.isNotEmpty()) {
                    val seekbarAccent = accentColor ?: MaterialTheme.colorScheme.primary
                    // Track whether the user is actively dragging the seekbar
                    var isDragging by remember { mutableStateOf(false) }
                    // Inverted: 100 = top, 0 = bottom (thumb at bottom = scrolled to end)
                    val seekbarValue = remember { mutableFloatStateOf(100f - progressPercent.toFloat()) }
                    // Only sync from scroll position when NOT dragging — prevents jitter
                    LaunchedEffect(progressPercent) {
                        if (!isDragging) {
                            seekbarValue.floatValue = 100f - progressPercent.toFloat()
                        }
                    }

                    // Auto-fade: track user interaction and fade out after inactivity.
                    // Use animateFloatAsState for smooth fade transitions.
                    var seekbarVisible by remember { mutableStateOf(true) }
                    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    // Update interaction time on any scroll or drag
                    LaunchedEffect(progressPercent, isDragging) {
                        lastInteractionTime = System.currentTimeMillis()
                        seekbarVisible = true
                    }
                    // Fade out after 3 seconds of inactivity
                    LaunchedEffect(lastInteractionTime) {
                        kotlinx.coroutines.delay(3000)
                        if (!isDragging && System.currentTimeMillis() - lastInteractionTime >= 3000) {
                            seekbarVisible = false
                        }
                    }
                    val animatedAlpha by animateFloatAsState(
                        targetValue = if (seekbarVisible) 1f else 0f,
                        animationSpec = tween(durationMillis = 300),
                        label = "seekbar_alpha",
                    )

                    // The Slider is laid out horizontally then rotated -90°.
                    // After rotation: width becomes visual height, height becomes visual width.
                    // We use requiredWidth/requiredHeight to override the Box's constraints,
                    // otherwise the Box's small width would clamp the Slider's width.
                    val sliderLength = 320.dp
                    val sliderThickness = 20.dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .height(sliderLength)
                            .width(sliderThickness)
                            .graphicsLayer { alpha = animatedAlpha },
                        contentAlignment = Alignment.Center,
                    ) {
                        Slider(
                            value = seekbarValue.floatValue,
                            onValueChange = { newValue ->
                                isDragging = true
                                seekbarVisible = true
                                lastInteractionTime = System.currentTimeMillis()
                                // Calculate pixel delta from PREVIOUS position.
                                // MUST capture prevValue BEFORE updating seekbarValue.
                                val prevValue = seekbarValue.floatValue
                                seekbarValue.floatValue = newValue
                                // Invert: slider value 100 = top (0% progress), 0 = bottom (100% progress)
                                val prevProgress = 100f - prevValue
                                val newProgress = 100f - newValue
                                val totalRange = rv.computeVerticalScrollRange()
                                val pixelDelta = ((newProgress - prevProgress) / 100f * totalRange).toInt()
                                if (pixelDelta != 0) {
                                    rv.scrollBy(0, pixelDelta)
                                }
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            valueRange = 0f..100f,
                            modifier = Modifier
                                .graphicsLayer(rotationZ = -90f)
                                .requiredWidth(sliderLength)
                                .requiredHeight(sliderThickness),
                            colors = SliderDefaults.colors(
                                thumbColor = seekbarAccent,
                                activeTrackColor = seekbarAccent,
                                inactiveTrackColor = seekbarAccent.copy(alpha = 0.2f),
                            ),
                        )
                    }
                }
            }
        }

        if (isSettingsVisible) {
            val hasDisplayCutout = remember { activity?.hasDisplayCutout() == true }
            NovelReaderSettingsDialog(
                onDismissRequest = {
                    screenModel.dismissSettings()
                },
                onShowMenus = { screenModel.setMenuVisible(true) },
                accentColor = accentColor,
                generalScrollState = generalScrollState,
                textScrollState = textScrollState,
                ttsScrollState = ttsScrollState,
                savedPage = savedSettingsPage.intValue,
                onPageSaved = { page -> savedSettingsPage.intValue = page },
                screenModel = remember {
                    NovelReaderSettingsScreenModel(
                        hasDisplayCutout = hasDisplayCutout,
                        onReadingModeChange = {
                            screenModel.onReadingModeChanged()
                            screenModel.refreshTextConfig(themeBackgroundColor, themeTextColor)
                        },
                        onBackgroundColorChange = { theme ->
                            screenModel.applyReaderTheme(
                                theme,
                                themeBackgroundColor,
                                themeTextColor,
                            )
                        },
                        onOrientationChange = { _ -> screenModel.applyOrientation() },
                        onTextSettingChange = { screenModel.refreshTextConfig(themeBackgroundColor, themeTextColor) },
                        installedNeuralVoices = screenModel.installedNeuralVoices,
                        downloadingVoiceId = screenModel.downloadingVoiceId,
                        voiceDownloadProgress = screenModel.voiceDownloadProgress,
                        onDownloadVoice = { entry -> screenModel.downloadNeuralVoice(entry) },
                        onUninstallVoice = { voiceId -> screenModel.uninstallNeuralVoice(voiceId) },
                        onSelectNeuralVoice = { voice -> screenModel.selectNeuralVoice(voice) },
                    )
                },
            )
        }

        if (isChaptersSheetVisible) {
            NovelChaptersSheet(
                chapters = chapters,
                currentChapterId = currentChapter?.id,
                onChapterClick = { chapter ->
                    screenModel.dismissChapters()
                    screenModel.setMenuVisible(true)
                    screenModel.loadChapterById(chapter.id)
                },
                onDismiss = { screenModel.dismissChapters() },
                accentColor = accentColor,
            )
        }

        // Reload comments when the current chapter changes during infinite scroll
        // and the comments dialog is open.
        LaunchedEffect(currentChapter?.id, isCommentsDialogVisible) {
            if (isCommentsDialogVisible && currentChapter != null) {
                screenModel.refreshComments()
            }
        }

        if (isCommentsDialogVisible) {
            NovelCommentsDialog(
                comments = comments,
                isLoading = isLoadingComments,
                error = commentsError,
                onDismiss = { screenModel.dismissComments() },
                onRefresh = { screenModel.refreshComments() },
                accentColor = accentColor,
                chapterTitle = currentChapter?.name,
            )
        }

        ApplyReaderWindowSettings(activity, screenModel)

        dictionaryQuery?.let { query ->
            DictionaryBottomSheet(
                selectedText = query,
                onDismiss = { screenModel.dismissDictionary() },
            )
        }

        // Translation bottom sheet — shown when the user taps "Translate" in
        // the selection popup and the translation preference is enabled.
        // Uses a ModalBottomSheet (same design as the dictionary lookup).
        val translationState by screenModel.translationState.collectAsStateWithLifecycle()
        if (translationState !is TranslationState.Idle) {
            TranslationBottomSheet(
                state = translationState,
                accentColor = accentColor,
                onDismiss = { screenModel.dismissTranslation() },
            )
        }

        // The highlight color picker is now inline in the selection popup
        // (MAIN → bookmark icon → COLORS state), so the separate dialog is gone.
    }

    @Composable
    private fun ApplyReaderWindowSettings(
        activity: Activity?,
        screenModel: NovelReaderScreenModel,
    ) {
        if (activity == null) return

        val fullscreen by screenModel.preferences.fullscreen().collectAsState()
        val keepScreenOn by screenModel.preferences.keepScreenOn().collectAsState()
        val readerTheme by screenModel.readerPreferences.readerTheme().collectAsState()
        val cutoutShort by screenModel.preferences.cutoutShort().collectAsState()

        DisposableEffect(fullscreen, keepScreenOn, readerTheme, cutoutShort, activity) {
            val window = activity.window
            val controller = WindowInsetsControllerCompat(window, window.decorView)

            WindowCompat.setDecorFitsSystemWindows(window, !fullscreen)
            if (fullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                // cutoutShort: extend content into the display cutout area
                if (cutoutShort) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                if (cutoutShort) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
            }

            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            val isDark = when (readerTheme) {
                0 -> false
                1, 2 -> true
                else -> false
            }
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark

            onDispose {
                // Restore edge-to-edge (app default) — don't force fitsSystemWindows
                // as that causes the detail screen to shift when system bars reappear.
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                // Restore default cutout mode
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }

    @Composable
    private fun NovelReaderContent(
        screenModel: NovelReaderScreenModel,
        activity: Activity?,
        contentItems: List<TextItem>,
        textConfig: TextConfig,
        isLoading: Boolean,
        accentColor: Color?,
        bottomPaddingDp: Int = 0,
        tapToScrollEnabled: Boolean = false,
        tapToScrollEnabledGetter: () -> Boolean = { tapToScrollEnabled },
        onScrollStateChanged: (Boolean) -> Unit = {},
        onToggleControls: () -> Unit,
        onRecyclerViewReady: (RecyclerView) -> Unit,
        onSelectionActiveChange: (Boolean) -> Unit = {},
    ) {
        var recyclerView by remember { mutableStateOf<RecyclerView?>(null) }

        // Create the adapter ONCE and persist it across recompositions.
        // The old implementation (Miko-Yokai-Old) used a by-lazy singleton
        // adapter that lived for the activity's entire lifetime. We must
        // do the same — if the adapter is recreated (e.g. when the
        // AndroidView's factory runs again after onRelease), all
        // ViewHolders are recreated and the Editor's selection controllers
        // are not properly initialized, breaking long-press text selection.
        var adapter by remember { mutableStateOf<TextAdapter?>(null) }
        if (adapter == null) {
            val textAdapter = TextAdapter(
                getConfig = { screenModel.textConfigValue },
                activity = activity,
                onNavigationClick = { direction ->
                    when (direction) {
                        TextItem.LoadDirection.PREVIOUS -> screenModel.navigateToPreviousChapter()
                        TextItem.LoadDirection.NEXT -> screenModel.navigateToNextChapter()
                    }
                },
                onTextSelected = { selectedText ->
                    screenModel.showDictionary(selectedText)
                },
                onHighlightWithColor = { selectedText, _, _, colorHex ->
                    screenModel.saveHighlightWithColor(selectedText, colorHex)
                },
                onCopy = { selectedText ->
                    screenModel.copyToClipboard(selectedText)
                },
                onShare = { selectedText ->
                    screenModel.shareText(selectedText)
                },
                onReadAloud = { selectedText ->
                    screenModel.readAloud(selectedText)
                },
                onTranslate = { selectedText ->
                    screenModel.translateText(selectedText)
                },
                getHighlightManager = { screenModel.highlightManager },
                getNovelTitle = { screenModel.novel.value?.title ?: "" },
                getNovelId = { screenModel.novel.value?.id },
                getChapterNumber = { screenModel.currentChapter.value?.chapterNumber ?: 0.0 },
                onHighlightDeleted = {
                    // Refresh the list to re-apply remaining highlights.
                    adapter?.notifyDataSetChanged()
                },
                getAccentColor = {
                    accentColor?.toArgb()
                },
                onSelectionActiveChange = { active ->
                    onSelectionActiveChange(active)
                },
            )
            // Wire up multi-paragraph range selection callbacks.
            textAdapter.onRangeSelectComplete = { combinedText ->
                screenModel.copyToClipboard(combinedText)
            }
            textAdapter.onShowMessage = { msg ->
                screenModel.showMessage(msg)
            }
            textAdapter.onRangeSelectModeChange = { active ->
                android.util.Log.d("NovelTextSelect", "Range selection mode: $active")
            }
            adapter = textAdapter
        }

        AndroidView(
            factory = { ctx ->
                val rv = RecyclerView(ctx).apply {
                    layoutManager = LinearLayoutManager(ctx)
                    setHasFixedSize(false)
                    // Disable item animations — they interfere with touch dispatching
                    // after submitList (e.g. during infinite scroll). The animation
                    // holds onto views and prevents the TextView from receiving the
                    // full touch sequence needed for long-press text selection.
                    itemAnimator = null
                    if (bottomPaddingDp > 0) {
                        val density = ctx.resources.displayMetrics.density
                        setPadding(0, 0, 0, (bottomPaddingDp * density).toInt())
                        clipToPadding = false
                    }
                }

                // Tap zone detection: manually track touch down/up to detect taps.
                // This is more reliable than GestureDetector in a RecyclerView context
                // where child views may consume touch events.
                val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
                var tapDownX = 0f
                var tapDownY = 0f
                var tapDownTime = 0L
                val tapTimeout = ViewConfiguration.getTapTimeout() + ViewConfiguration.getLongPressTimeout()

                // Swipe-to-change-chapter: detect vertical swipes
                val swipeThreshold = 200 // px — minimum swipe distance to trigger
                var swipeStartY = 0f
                val swipeToNextEnabled = screenModel.preferences.swipeToNextChapter().get()
                val swipeToPrevEnabled = screenModel.preferences.swipeToPrevChapter().get()

                rv.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                        when (e.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                tapDownX = e.x
                                tapDownY = e.y
                                tapDownTime = System.currentTimeMillis()
                                swipeStartY = e.y
                            }
                            MotionEvent.ACTION_UP -> {
                                val dx = kotlin.math.abs(e.x - tapDownX)
                                val dy = kotlin.math.abs(e.y - tapDownY)
                                val elapsed = System.currentTimeMillis() - tapDownTime
                                // Detect tap: small movement, within timeout
                                val isTap = dx < touchSlop && dy < touchSlop && elapsed < tapTimeout

                                // Swipe gesture detection (takes priority over tap)
                                if (swipeToNextEnabled || swipeToPrevEnabled) {
                                    val deltaY = e.y - swipeStartY
                                    if (kotlin.math.abs(deltaY) > swipeThreshold) {
                                        if (deltaY < 0 && swipeToNextEnabled) {
                                            screenModel.navigateToNextChapter()
                                            return false
                                        } else if (deltaY > 0 && swipeToPrevEnabled) {
                                            screenModel.navigateToPreviousChapter()
                                            return false
                                        }
                                    }
                                }

                                if (isTap) {
                                    val mode = screenModel.readingMode
                                    // Scale: 100% slider = 10% viewport scroll.
                                    // The slider value (0-100) maps to 0-10% of viewport height.
                                    val tapAmountPct = screenModel.preferences.tapToScrollAmount().get() * 0.1f
                                    val x = e.x
                                    val y = e.y
                                    val screenWidth = rv.width
                                    val screenHeight = rv.height
                                    val zoneThreshold = 0.30f
                                    val tapScrollOn = tapToScrollEnabledGetter()

                                    if (mode == NovelReadingMode.INFINITE_SCROLL) {
                                        // Infinite scroll: top/bottom zones for scrolling (if tap-to-scroll enabled)
                                        // No left/right chapter navigation zones.
                                        if (tapScrollOn) {
                                            val isTopZone = y < screenHeight * zoneThreshold
                                            val isBottomZone = y > screenHeight * (1f - zoneThreshold)
                                            if (isBottomZone || isTopZone) {
                                                val visibleHeight = rv.height - rv.paddingTop - rv.paddingBottom
                                                val scrollDist = (visibleHeight * tapAmountPct / 100).toInt()
                                                val direction = if (isBottomZone) scrollDist else -scrollDist
                                                smoothScrollByAnimated(rv, direction)
                                                return false
                                            }
                                        }
                                    } else {
                                        // Page mode (DEFAULT):
                                        // Left/right 30% = chapter navigation (always active)
                                        val isLeftZone = x < screenWidth * zoneThreshold
                                        val isRightZone = x > screenWidth * (1f - zoneThreshold)
                                        if (isRightZone) {
                                            screenModel.navigateToNextChapter()
                                            return false
                                        } else if (isLeftZone) {
                                            screenModel.navigateToPreviousChapter()
                                            return false
                                        }
                                        // Top/bottom 30% in the center area = tap-to-scroll (if enabled)
                                        if (tapScrollOn) {
                                            val isTopZone = y < screenHeight * zoneThreshold
                                            val isBottomZone = y > screenHeight * (1f - zoneThreshold)
                                            if (isBottomZone || isTopZone) {
                                                val visibleHeight = rv.height - rv.paddingTop - rv.paddingBottom
                                                val scrollDist = (visibleHeight * tapAmountPct / 100).toInt()
                                                val direction = if (isBottomZone) scrollDist else -scrollDist
                                                smoothScrollByAnimated(rv, direction)
                                                return false
                                            }
                                        }
                                    }
                                }

                                // If it was a tap but not in a zone, toggle controls
                                if (isTap) {
                                    onToggleControls()
                                }
                            }
                        }
                        return false
                    }
                })

                // Use the persistent adapter (created via remember above).
                // Don't create a new one — that breaks text selection.
                val ad = adapter!!
                rv.adapter = ad
                ad.submitList(contentItems)

                // Scroll listener: dismiss selection popup + infinite-scroll loading
                // + character-position tracking.
                var lastPositionSaveTime = 0L
                val positionSaveIntervalMs = 2000L
                // Cooldown for infinite-scroll chapter loading — prevents chain
                // reactions where prepending a chapter shifts scroll position
                // and immediately triggers another load.
                var lastInfiniteLoadTime = 0L
                val infiniteLoadCooldownMs = 1500L
                // Track the last scroll direction so we know which way the user
                // was scrolling when they stopped (for infinite-scroll triggers).
                var lastScrollDirection = 0 // 1 = down, -1 = up, 0 = none
                rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                        // Notify outer scope for auto-scroll pause/resume.
                        onScrollStateChanged(newState == RecyclerView.SCROLL_STATE_DRAGGING)

                        // --- Infinite-scroll loading ---
                        // Trigger when the scroll settles to IDLE (finger lifted
                        // or fling finished). This is the reliable trigger point —
                        // onScrolled fires during DRAGGING/SETTLING but not after
                        // IDLE, so checking IDLE inside onScrolled never fires.
                        // Checking here when transitioning TO IDLE ensures we
                        // always evaluate the trigger after the user stops.
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            val mode = screenModel.readingMode
                            if (mode == NovelReadingMode.INFINITE_SCROLL) {
                                val now = System.currentTimeMillis()
                                if (now - lastInfiniteLoadTime >= infiniteLoadCooldownMs) {
                                    val lm = rv.layoutManager as? LinearLayoutManager
                                    if (lm != null) {
                                        val totalItemCount = lm.itemCount
                                        if (totalItemCount > 0) {
                                            // Guard: don't trigger infinite-scroll loading
                                            // when the current content has no paragraphs
                                            // (empty/corrupt chapter). A non-scrollable RV
                                            // would falsely trigger both directions.
                                            val hasParagraphs = ad.currentList.any { it is TextItem.Paragraph }
                                            if (hasParagraphs) {
                                                // Check if near the bottom (load next)
                                                val lastVisible = lm.findLastVisibleItemPosition()
                                                val scrollPctDown = (lastVisible + 1).toFloat() / totalItemCount.toFloat()
                                                val atBottom = !rv.canScrollVertically(1)
                                                if ((scrollPctDown >= 0.80f || atBottom) &&
                                                    !screenModel.isLoadingNext &&
                                                    lastScrollDirection >= 0
                                                ) {
                                                    lastInfiniteLoadTime = now
                                                    screenModel.loadNextChapterInBackground()
                                                }
                                                // Check if near the top (load previous)
                                                val firstVisible = lm.findFirstVisibleItemPosition()
                                                val scrollPctUp = firstVisible.toFloat() / totalItemCount.toFloat()
                                                val atTop = !rv.canScrollVertically(-1)
                                                if ((scrollPctUp <= 0.20f || atTop) &&
                                                    !screenModel.isLoadingPrevious &&
                                                    lastScrollDirection <= 0
                                                ) {
                                                    lastInfiniteLoadTime = now
                                                    screenModel.loadPreviousChapterInBackground()
                                                }
                                            }
                                        }
                                    }
                                }
                                // Reset direction after processing — the next
                                // scroll gesture will set it again.
                                lastScrollDirection = 0
                            }
                        }
                    }

                    override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                        ad.dismissActiveSelectionPopup()

                        // Track scroll direction for infinite-scroll trigger
                        if (dy > 0) lastScrollDirection = 1
                        else if (dy < 0) lastScrollDirection = -1

                        val lm = rv.layoutManager as? LinearLayoutManager ?: return
                        val items = ad.currentList
                        if (items.isEmpty()) return

                        // --- Update current chapter from scroll position FIRST ---
                        // This must happen before progress calculation so that
                        // the progress uses the correct chapter. When the user
                        // scrolls into the next chapter's header, the chapter
                        // updates before progress is calculated.
                        val firstCompletePos = lm.findFirstCompletelyVisibleItemPosition()
                        if (firstCompletePos != RecyclerView.NO_POSITION) {
                            val visibleItem = items.getOrNull(firstCompletePos)
                            val visibleChapterId = when (visibleItem) {
                                is TextItem.Paragraph -> visibleItem.chapterId
                                is TextItem.ChapterHeader -> visibleItem.chapterId
                                is TextItem.Loading -> visibleItem.chapterId
                                is TextItem.Error -> visibleItem.chapterId
                                is TextItem.ChapterNavigation -> null
                                null -> null
                            }
                            if (visibleChapterId != null && visibleChapterId > 0) {
                                screenModel.updateCurrentChapterById(visibleChapterId)
                            }
                        }

                        // --- Character-position tracking ---
                        val firstVisiblePos = lm.findFirstVisibleItemPosition()
                        if (firstVisiblePos != RecyclerView.NO_POSITION) {
                            // For non-infinite scroll: if we can't scroll further down,
                            // we're at the bottom — force 100%.
                            // For infinite scroll: rely on chapter detection + per-chapter
                            // progress, so we don't force 100% (the next chapter header
                            // being visible means we're starting a new chapter at 0%).
                            // EXCEPTION: if this is the last chapter (no next chapter),
                            // force 100% at the bottom so the user can mark it as read.
                            val mode = screenModel.readingMode
                            val atBottom = !rv.canScrollVertically(1)
                            val isLastChapter = screenModel.isLastChapter()
                            // Guard: only force-complete when the current chapter
                            // actually has paragraph content. A chapter with only
                            // a header (empty/corrupt content) would be non-scrollable
                            // and falsely trigger forceProgressComplete, marking the
                            // chapter as read without the user reading it.
                            val hasParagraphs = items.any { it is TextItem.Paragraph }
                            if (atBottom && hasParagraphs && (mode != NovelReadingMode.INFINITE_SCROLL || isLastChapter)) {
                                screenModel.forceProgressComplete()
                            } else {
                                // Calculate per-chapter character position.
                                // Each chapter's paragraphs start at startCharIndex=0,
                                // so we only count paragraphs belonging to the CURRENT
                                // chapter (determined by updateCurrentChapterById above).
                                val currentChapterId = screenModel.currentChapter.value?.id ?: 0L
                                var charPos = 0
                                for (i in 0 until firstVisiblePos) {
                                    val item = items.getOrNull(i) as? TextItem.Paragraph ?: continue
                                    if (item.chapterId != currentChapterId) continue
                                    charPos = item.endCharIndex + 1
                                }
                                val firstView = lm.findViewByPosition(firstVisiblePos)
                                // Capture the exact pixel offset for Tadami-style
                                // scroll restoration (no drift on resume).
                                var pixelOffset = 0
                                if (firstView != null) {
                                    pixelOffset = -firstView.top
                                    val totalHeight = firstView.height
                                    if (totalHeight > 0) {
                                        val scrollPct = (pixelOffset.toFloat() / totalHeight).coerceIn(0f, 1f)
                                        val visibleItem = items.getOrNull(firstVisiblePos) as? TextItem.Paragraph
                                        if (visibleItem != null && visibleItem.chapterId == currentChapterId) {
                                            val paraChars = visibleItem.endCharIndex - visibleItem.startCharIndex
                                            charPos += (paraChars * scrollPct).toInt()
                                        }
                                    }
                                }
                                // Pass item index + pixel offset for exact save/restore.
                                screenModel.updateCharacterPosition(
                                    characterPosition = charPos,
                                    itemIndex = firstVisiblePos,
                                    pixelOffset = pixelOffset,
                                )
                            }

                            // Debounced save.
                            val now = System.currentTimeMillis()
                            if (now - lastPositionSaveTime > positionSaveIntervalMs) {
                                lastPositionSaveTime = now
                                screenModel.saveCurrentPosition()
                            }
                        }
                    }
                })

                recyclerView = rv
                onRecyclerViewReady(rv)
                rv
            },
            update = { rv ->
                val pending = screenModel.pendingScrollAdjustment
                val pendingTransition = screenModel.pendingTransitionPosition
                val pendingRestore = screenModel.pendingScrollRestore
                if (pending != null) {
                    Log.d("NovelReader", "update: applying pendingScrollAdjustment=$pending, contentItems=${contentItems.size}")
                    screenModel.pendingScrollAdjustment = null
                    adapter?.submitList(contentItems) {
                        rv.post {
                            val lm = rv.layoutManager as? LinearLayoutManager
                            lm?.scrollToPositionWithOffset(pending, 0)
                        }
                    }
                } else if (pendingTransition != null) {
                    Log.d("NovelReader", "update: applying pendingTransitionPosition=$pendingTransition, contentItems=${contentItems.size}")
                    screenModel.pendingTransitionPosition = null
                    val transitionStyle = screenModel.preferences.pageTransitionStyle().get()
                    adapter?.submitList(contentItems) {
                        rv.post { applyChapterTransition(rv, pendingTransition, transitionStyle) }
                    }
                } else if (pendingRestore != null && contentItems.isNotEmpty()) {
                    // Apply pending scroll restoration when content arrives.
                    // This handles the case where the RecyclerView was created
                    // before the content was loaded (e.g. downloaded chapters
                    // that don't show a loading indicator but still need time
                    // to read and parse the file).
                    Log.d("NovelReader", "update: applying pendingScrollRestore in update callback, contentItems=${contentItems.size}")
                    screenModel.pendingScrollRestore = null
                    val (targetIndex, targetOffset) = pendingRestore
                    adapter?.submitList(contentItems) {
                        rv.post {
                            val lm = rv.layoutManager as? LinearLayoutManager
                            if (lm != null) {
                                // Clamp targetIndex to available items (same fix as
                                // tryRestoreScroll — handles infinite-scroll positions).
                                val effectiveIdx = if (targetIndex >= contentItems.size) {
                                    Log.w("NovelReader", "update: targetIndex=$targetIndex > contentSize=${contentItems.size}, clamping")
                                    contentItems.size - 1
                                } else {
                                    targetIndex
                                }
                                Log.d("NovelReader", "update: scrollToPositionWithOffset index=$effectiveIdx offset=$targetOffset")
                                lm.scrollToPositionWithOffset(effectiveIdx, targetOffset)
                            } else {
                                Log.w("NovelReader", "update: cannot restore — lm is null")
                            }
                        }
                    }
                } else {
                    adapter?.submitList(contentItems)
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { rv ->
                // Save the current scroll position before the RecyclerView is
                // destroyed. When the user returns to the reader (without a new
                // chapter load), pendingScrollRestore will be set so the new
                // RecyclerView can restore the exact position.
                val lm = rv.layoutManager as? LinearLayoutManager
                val firstVisible = lm?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                if (firstVisible != RecyclerView.NO_POSITION) {
                    val view = lm?.findViewByPosition(firstVisible)
                    val offset = view?.top ?: 0
                    Log.d("NovelReader", "onRelease: saving scroll position firstVisible=$firstVisible offset=$offset")
                    screenModel.saveCurrentScrollPosition(firstVisible, offset)
                } else {
                    Log.w("NovelReader", "onRelease: firstVisible=NO_POSITION, cannot save scroll position")
                }
                // Don't null the adapter — it's persisted via remember and
                // will be re-attached to the new RecyclerView when factory
                // runs again. Nulling it forces a new adapter creation which
                // breaks text selection (Editor's selection controllers are
                // not properly initialized on new ViewHolders).
            },
        )

        DisposableEffect(textConfig) {
            // Force re-bind of all visible items so they pick up the new config
            // (bold/italic/alignment/etc). notifyDataSetChanged() alone doesn't
            // reliably re-bind on ListAdapter, so we use notifyItemRangeChanged
            // with a payload to force onBindViewHolder to run on every visible item.
            val a = adapter
            if (a != null) {
                a.notifyItemRangeChanged(0, a.itemCount, "config")
            }
            onDispose {}
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = accentColor ?: MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Smooth scroll with a custom duration and decelerate interpolator for
 * a smoother feel than the default smoothScrollBy.
 */
private fun smoothScrollByAnimated(rv: RecyclerView, dy: Int) {
    if (dy == 0) return
    val animator = android.animation.ValueAnimator.ofInt(0, dy)
    animator.duration = 450
    animator.interpolator = android.view.animation.DecelerateInterpolator(1.5f)
    animator.addUpdateListener { rv.scrollBy(0, it.animatedValue as Int) }
    animator.start()
}

/**
 * Scroll the RecyclerView to the start (position 0) or end (position -1) of
 * the chapter content. Used by chapter navigation transitions.
 */
private fun scrollToChapterPosition(rv: RecyclerView, position: Int) {
    if (position == 0) {
        rv.scrollToPosition(0)
    } else if (position == -1) {
        val lm = rv.layoutManager as? LinearLayoutManager
        lm?.scrollToPosition(rv.adapter?.itemCount?.minus(1) ?: 0)
        rv.post { rv.scrollBy(0, Int.MAX_VALUE / 2) }
    }
}

/**
 * Apply the chapter transition animation based on the user's preference.
 * Called after submitList completes so the adapter has the new items.
 * position == 0 → next chapter (scroll to top)
 * position == -1 → previous chapter (scroll to bottom)
 */
private fun applyChapterTransition(rv: RecyclerView, position: Int, transitionStyle: NovelPageTransitionStyle) {
    val isNext = position == 0
    rv.cameraDistance = rv.width * 12f

    when (transitionStyle) {
        NovelPageTransitionStyle.INSTANT -> {
            scrollToChapterPosition(rv, position)
        }
        NovelPageTransitionStyle.SLIDE -> {
            // Next chapter: old slides LEFT, new enters from RIGHT
            // Prev chapter: old slides RIGHT, new enters from LEFT
            val slideOut = if (isNext) -rv.width.toFloat() else rv.width.toFloat()
            rv.animate().translationX(slideOut).setDuration(200).withEndAction {
                scrollToChapterPosition(rv, position)
                rv.translationX = -slideOut
                rv.post { rv.animate().translationX(0f).setDuration(250).start() }
            }.start()
        }
        NovelPageTransitionStyle.DEPTH -> {
            // Zoom out + slide slightly in direction of travel, then zoom in
            val slideOut = if (isNext) -rv.width * 0.3f else rv.width * 0.3f
            rv.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .translationX(slideOut)
                .setDuration(200)
                .withEndAction {
                    scrollToChapterPosition(rv, position)
                    rv.translationX = -slideOut
                    rv.post {
                        rv.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationX(0f)
                            .setDuration(250)
                            .start()
                    }
                }.start()
        }
        NovelPageTransitionStyle.BOOK -> {
            // Book page turn: rotate around the binding edge.
            // Next chapter: pivot at LEFT edge, page rotates from right to left (forward turn)
            // Prev chapter: pivot at RIGHT edge, page rotates from left to right (backward turn)
            val pivotX = if (isNext) rv.width.toFloat() else 0f
            rv.pivotX = pivotX
            val direction = if (isNext) 90f else -90f
            rv.animate()
                .rotationY(direction)
                .setDuration(300)
                .withEndAction {
                    scrollToChapterPosition(rv, position)
                    rv.rotationY = -direction
                    rv.pivotX = if (isNext) 0f else rv.width.toFloat()
                    rv.post {
                        rv.animate()
                            .rotationY(0f)
                            .setDuration(300)
                            .start()
                    }
                }.start()
        }
    }
}

/**
 * Draws a background texture overlay using bitmap textures with ShaderBrush tiling.
 *
 * All textures are WebP bitmaps tiled seamlessly via ImageShader + TileMode.Repeated.
 * Textures are RGBA with low alpha — the pattern is in the alpha channel, making
 * them color-agnostic and naturally subtle.
 *
 * [strengthPercent] range is 0–100 (100 = maximum). Alpha scales linearly.
 */
@Composable
private fun NovelTextureOverlay(
    texture: NovelReaderBackgroundTexture,
    strengthPercent: Int,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    if (texture == NovelReaderBackgroundTexture.NONE) return

    // Linear alpha: 0% = 0, 100% = 1.0
    val alpha = (strengthPercent.coerceIn(0, 100) / 100f)

    val imageRes = when (texture) {
        NovelReaderBackgroundTexture.PAPER_GRAIN -> eu.kanade.tachiyomi.R.drawable.texture_paper
        NovelReaderBackgroundTexture.LINEN -> eu.kanade.tachiyomi.R.drawable.texture_linen
        NovelReaderBackgroundTexture.CANVAS -> eu.kanade.tachiyomi.R.drawable.texture_canvas
        NovelReaderBackgroundTexture.KRAFT -> eu.kanade.tachiyomi.R.drawable.texture_kraft
        NovelReaderBackgroundTexture.DOTTED -> eu.kanade.tachiyomi.R.drawable.texture_dotted
        NovelReaderBackgroundTexture.NONE -> return
    }

    val imageBitmap = ImageBitmap.imageResource(id = imageRes)
    val brush = remember(imageBitmap) {
        ShaderBrush(
            ImageShader(
                image = imageBitmap,
                tileModeX = TileMode.Repeated,
                tileModeY = TileMode.Repeated,
            ),
        )
    }
    Canvas(modifier = modifier) {
        if (alpha > 0f) {
            drawRect(brush = brush, alpha = alpha)
        }
    }
}

/**
 * Translation bottom sheet — same design as the dictionary lookup sheet.
 * Shows the translated text, original text, loading state, or error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationBottomSheet(
    state: TranslationState,
    accentColor: Color?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp * 0.6f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Header row — close button + title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (state) {
                        is TranslationState.Loading -> "Translating..."
                        is TranslationState.Result -> "Translation (${state.targetLang})"
                        is TranslationState.Error -> "Translation Error"
                        TranslationState.Idle -> ""
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            when (state) {
                is TranslationState.Loading -> {
                    Text(
                        text = state.originalText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(
                        color = accentColor ?: MaterialTheme.colorScheme.primary,
                    )
                }
                is TranslationState.Result -> {
                    Text(
                        text = state.translatedText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Original",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    Text(
                        text = state.originalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                is TranslationState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                TranslationState.Idle -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Edge fade overlay: draws a smooth gradient fade at the top and bottom of
 * the reader that makes text appear to fade out as it scrolls under the edges.
 *
 * Uses stacked semi-transparent layers (Wakely-style) to produce a smooth
 * gradient without color interpolation artifacts. Each layer has the
 * background color with decreasing alpha, creating a natural fade.
 *
 * The [fadeHeightFraction] controls how much of the screen height the fade
 * covers at each edge (e.g. 0.08 = 8% of screen height at top and bottom).
 */
@Composable
private fun NovelEdgeFadeOverlay(
    backgroundColor: Color,
    texture: NovelReaderBackgroundTexture,
    textureStrength: Int,
    fadeHeightFraction: Float = 0.08f,
    modifier: Modifier = Modifier,
) {
    // Build the texture brush if a texture is enabled, so the fade area
    // also shows the texture pattern (matching the rest of the background).
    val textureBrush: ShaderBrush? = if (texture != NovelReaderBackgroundTexture.NONE) {
        val imageRes = when (texture) {
            NovelReaderBackgroundTexture.PAPER_GRAIN -> eu.kanade.tachiyomi.R.drawable.texture_paper
            NovelReaderBackgroundTexture.LINEN -> eu.kanade.tachiyomi.R.drawable.texture_linen
            NovelReaderBackgroundTexture.CANVAS -> eu.kanade.tachiyomi.R.drawable.texture_canvas
            NovelReaderBackgroundTexture.KRAFT -> eu.kanade.tachiyomi.R.drawable.texture_kraft
            NovelReaderBackgroundTexture.DOTTED -> eu.kanade.tachiyomi.R.drawable.texture_dotted
            NovelReaderBackgroundTexture.NONE -> null
        }
        if (imageRes != null) {
            val imageBitmap = ImageBitmap.imageResource(id = imageRes)
            remember(imageBitmap) {
                ShaderBrush(
                    ImageShader(
                        image = imageBitmap,
                        tileModeX = TileMode.Repeated,
                        tileModeY = TileMode.Repeated,
                    ),
                )
            }
        } else {
            null
        }
    } else {
        null
    }

    val textureAlpha = (textureStrength.coerceIn(0, 100) / 100f)

    // Top-only fade: the bottom fade is handled entirely by the
    // NovelPhoneInfoOverlay, which is itself a gradient from transparent
    // (top) to opaque (bottom). This avoids double gradients.
    Canvas(modifier = modifier) {
        val fadeHeight = size.height * fadeHeightFraction.coerceIn(0.01f, 0.3f)
        val layerCount = 24
        val layerHeight = fadeHeight / layerCount

        // Top fade: opaque at the very top → transparent towards center.
        for (i in 0 until layerCount) {
            val fraction = i.toFloat() / (layerCount - 1)
            val alpha = 1f - fraction
            drawRect(
                color = backgroundColor.copy(alpha = alpha),
                topLeft = androidx.compose.ui.geometry.Offset(0f, i * layerHeight),
                size = androidx.compose.ui.geometry.Size(size.width, layerHeight + 1f),
            )
        }

        // Draw texture in the top fade area with matching alpha gradient
        if (textureBrush != null && textureAlpha > 0f) {
            for (i in 0 until layerCount) {
                val fraction = i.toFloat() / (layerCount - 1)
                val topAlpha = textureAlpha * (1f - fraction)
                drawRect(
                    brush = textureBrush,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, i * layerHeight),
                    size = androidx.compose.ui.geometry.Size(size.width, layerHeight + 1f),
                    alpha = topAlpha,
                )
            }
        }
    }
}
