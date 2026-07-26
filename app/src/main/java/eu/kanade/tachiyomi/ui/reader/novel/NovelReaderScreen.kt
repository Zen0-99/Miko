package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
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
import eu.kanade.tachiyomi.ui.reader.novel.dictionary.DictionaryBottomSheet
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
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
                        val rv = recyclerViewRef
                        if (rv != null) {
                            val transitionStyle = screenModel.preferences.pageTransitionStyle().get()
                            rv.post {
                                // Apply chapter transition animation based on preference
                                when (transitionStyle) {
                                    NovelPageTransitionStyle.INSTANT -> {
                                        scrollToChapterPosition(rv, event.position)
                                    }
                                    NovelPageTransitionStyle.SLIDE -> {
                                        // Fade out, scroll, fade in
                                        rv.animate().alpha(0f).setDuration(150).withEndAction {
                                            scrollToChapterPosition(rv, event.position)
                                            rv.post { rv.animate().alpha(1f).setDuration(200).start() }
                                        }.start()
                                    }
                                    NovelPageTransitionStyle.DEPTH -> {
                                        // Scale down + fade out, scroll, scale up + fade in
                                        rv.animate()
                                            .alpha(0f)
                                            .scaleX(0.85f)
                                            .scaleY(0.85f)
                                            .setDuration(200)
                                            .withEndAction {
                                                scrollToChapterPosition(rv, event.position)
                                                rv.post {
                                                    rv.scaleX = 0.85f
                                                    rv.scaleY = 0.85f
                                                    rv.animate()
                                                        .alpha(1f)
                                                        .scaleX(1f)
                                                        .scaleY(1f)
                                                        .setDuration(250)
                                                        .start()
                                                }
                                            }.start()
                                    }
                                    NovelPageTransitionStyle.BOOK,
                                    NovelPageTransitionStyle.CURL,
                                    NovelPageTransitionStyle.BOOK_FLIP -> {
                                        // These 3D page-curl effects require a pager architecture.
                                        // Fallback to a cross-fade with a slight horizontal shift.
                                        val direction = if (event.position == 0) 1f else -1f
                                        rv.translationX = direction * rv.width * 0.15f
                                        rv.alpha = 0f
                                        scrollToChapterPosition(rv, event.position)
                                        rv.post {
                                            rv.animate()
                                                .alpha(1f)
                                                .translationX(0f)
                                                .setDuration(300)
                                                .start()
                                        }
                                    }
                                }
                            }
                        }
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
            dictionaryQuery != null || !hasWindowFocus

        LaunchedEffect(autoScrollEnabled, smoothAutoScroll, autoScrollInterval, autoScrollOffset, smoothSpeed, isOverlayActive, isUserTouching) {
            if (!autoScrollEnabled) return@LaunchedEffect
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
                    if (rv != null && !isOverlayActive && !isUserTouching) {
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
                    if (rv != null && !isOverlayActive && !isUserTouching) {
                        rv.post { rv.scrollBy(0, autoScrollOffset) }
                    }
                }
            }
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
                onToggleControls = { screenModel.toggleControls() },
                onRecyclerViewReady = { rv ->
                    recyclerViewRef = rv
                    // Track user-initiated scrolls for smooth auto-scroll pause/resume.
                    // SCROLL_STATE_DRAGGING is only set when the user is physically
                    // dragging the list — programmatic scrollBy stays IDLE.
                    rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                            // Track touch state for auto-scroll pause/resume.
                            // DRAGGING = user is physically touching and dragging.
                            // SETTLING = fling in progress (finger lifted).
                            // IDLE = fully stopped.
                            isUserTouching = newState == RecyclerView.SCROLL_STATE_DRAGGING
                        }
                    })

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
                        if (targetIndex > 0 || targetOffset > 0) {
                            // Hide the RV until we've scrolled to the saved position.
                            rv.alpha = 0f
                            var attempts = 0
                            val maxAttempts = 15
                            fun tryRestoreScroll() {
                                val adapter = rv.adapter as? TextAdapter
                                val items = adapter?.currentList
                                if (items.isNullOrEmpty() || items.size <= targetIndex) {
                                    if (++attempts < maxAttempts) {
                                        rv.postDelayed(::tryRestoreScroll, 50L)
                                    } else {
                                        // Give up — show content at default position.
                                        rv.alpha = 1f
                                    }
                                    return
                                }
                                val lm = rv.layoutManager as? LinearLayoutManager
                                if (lm == null) {
                                    rv.alpha = 1f
                                    return
                                }
                                // Scroll to exact saved position: item index + pixel offset.
                                // This is the key difference from the old approach —
                                // we restore the EXACT pixel offset, not just the
                                // paragraph start, eliminating position drift.
                                lm.scrollToPositionWithOffset(targetIndex, targetOffset)
                                // Make the RV visible on the next frame, after
                                // the scroll has been applied and laid out.
                                rv.post { rv.alpha = 1f }
                            }
                            // Start trying on the next frame.
                            rv.post(::tryRestoreScroll)
                        }
                    }
                },
            )

            // Background texture overlay — drawn ON TOP of the RecyclerView but
            // with low alpha so text remains readable. Uses programmatic noise
            // patterns (no bitmap assets needed).
            if (textConfig.backgroundTexture != NovelReaderBackgroundTexture.NONE) {
                NovelTextureOverlay(
                    texture = textConfig.backgroundTexture,
                    strength = textConfig.textureStrength,
                    isDark = textConfig.backgroundColor != android.graphics.Color.WHITE,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // OLED edge gradient: subtle dark gradient at screen edges for
            // OLED displays to reduce burn-in perception.
            if (textConfig.oledEdgeGradient) {
                Canvas(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val edgeWidth = size.width * 0.04f
                    val edgeHeight = size.height * 0.04f
                    // Left edge
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.15f),
                            edgeWidth to Color.Transparent,
                        ),
                    )
                    // Right edge
                    drawRect(
                        brush = Brush.horizontalGradient(
                            (size.width - edgeWidth) to Color.Transparent,
                            size.width to Color.Black.copy(alpha = 0.15f),
                        ),
                    )
                    // Top edge
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.15f),
                            edgeHeight to Color.Transparent,
                        ),
                    )
                    // Bottom edge
                    drawRect(
                        brush = Brush.verticalGradient(
                            (size.height - edgeHeight) to Color.Transparent,
                            size.height to Color.Black.copy(alpha = 0.15f),
                        ),
                    )
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

            NovelReaderChrome(
                isMenuVisible = isControlsVisible,
                title = currentChapter?.name ?: "Loading...",
                subtitle = novel?.title ?: "",
                accentColor = accentColor,
                progressPercent = if (screenModel.preferences.showScrollPercentage().get()) progressPercent else -1,
                estimatedReadingTime = if (screenModel.preferences.showEstimatedReadingTime().get()) {
                    screenModel.positionTracker.getEstimatedReadingTime()
                } else -1,
                wordCount = if (screenModel.preferences.showWordCount().get()) {
                    screenModel.positionTracker.getTotalWordCount()
                } else -1,
                timeToEnd = if (screenModel.preferences.showTimeToEnd().get()) {
                    screenModel.positionTracker.getTimeToEnd()
                } else -1,
                fullscreen = screenModel.preferences.fullscreen().get(),
                showPhoneInfo = screenModel.preferences.showBatteryAndTime().get(),
                readerBackgroundColor = bgColor,
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

        // Translation dialog — shown when the user taps "Translate" in the
        // selection popup and the translation preference is enabled.
        val translationState by screenModel.translationState.collectAsStateWithLifecycle()
        when (val ts = translationState) {
            is TranslationState.Loading -> {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { screenModel.dismissTranslation() },
                    title = { Text("Translating...") },
                    text = {
                        Text(
                            text = ts.originalText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.padding(top = 8.dp),
                            color = accentColor ?: MaterialTheme.colorScheme.primary,
                        )
                    },
                    confirmButton = {},
                )
            }
            is TranslationState.Result -> {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { screenModel.dismissTranslation() },
                    title = { Text("Translation (${ts.targetLang})") },
                    text = {
                        Text(
                            text = ts.translatedText,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Original: ${ts.originalText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { screenModel.dismissTranslation() }) {
                            Text("Close")
                        }
                    },
                )
            }
            is TranslationState.Error -> {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { screenModel.dismissTranslation() },
                    title = { Text("Translation Error") },
                    text = { Text(ts.message) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { screenModel.dismissTranslation() }) {
                            Text("OK")
                        }
                    },
                )
            }
            TranslationState.Idle -> {}
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
        val keepScreenOn by screenModel.readerPreferences.keepScreenOn().collectAsState()
        val readerTheme by screenModel.readerPreferences.readerTheme().collectAsState()

        DisposableEffect(fullscreen, keepScreenOn, readerTheme, activity) {
            val window = activity.window
            val controller = WindowInsetsControllerCompat(window, window.decorView)

            WindowCompat.setDecorFitsSystemWindows(window, !fullscreen)
            if (fullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
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
        onToggleControls: () -> Unit,
        onRecyclerViewReady: (RecyclerView) -> Unit,
    ) {
        var recyclerView by remember { mutableStateOf<RecyclerView?>(null) }
        var adapter by remember { mutableStateOf<TextAdapter?>(null) }

        AndroidView(
            factory = { ctx ->
                val rv = RecyclerView(ctx).apply {
                    layoutManager = LinearLayoutManager(ctx)
                    setHasFixedSize(false)
                    if (bottomPaddingDp > 0) {
                        val density = ctx.resources.displayMetrics.density
                        setPadding(0, 0, 0, (bottomPaddingDp * density).toInt())
                        clipToPadding = false
                    }
                }

                val gestureDetector = GestureDetector(
                    ctx,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            onToggleControls()
                            return true
                        }
                    },
                )
                // Use addOnItemTouchListener (Miko's approach) — more reliable
                // than setOnTouchListener which can be intercepted by the
                // RecyclerView's own touch handling.
                rv.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                        gestureDetector.onTouchEvent(e)
                        return false
                    }
                })

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
                )
                rv.adapter = textAdapter
                adapter = textAdapter
                textAdapter.submitList(contentItems)

                // Scroll listener: dismiss selection popup + infinite-scroll loading
                // + character-position tracking.
                var lastPositionSaveTime = 0L
                val positionSaveIntervalMs = 2000L
                rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                        textAdapter.dismissActiveSelectionPopup()

                        val lm = rv.layoutManager as? LinearLayoutManager ?: return
                        val items = textAdapter.currentList
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
                            val mode = screenModel.readingMode
                            val atBottom = !rv.canScrollVertically(1)
                            if (atBottom && mode != NovelReadingMode.INFINITE_SCROLL) {
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

                        // --- Infinite-scroll loading ---
                        val mode = screenModel.readingMode
                        if (mode == NovelReadingMode.INFINITE_SCROLL) {
                            val totalItemCount = lm.itemCount
                            if (totalItemCount == 0) return
                            if (dy > 0) {
                                val lastVisible = lm.findLastVisibleItemPosition()
                                val scrollPct = (lastVisible + 1).toFloat() / totalItemCount.toFloat()
                                if (scrollPct >= 0.80f && !screenModel.isLoadingNext) {
                                    screenModel.loadNextChapterInBackground()
                                }
                            } else if (dy < 0) {
                                val firstVisible = lm.findFirstVisibleItemPosition()
                                val scrollPct = firstVisible.toFloat() / totalItemCount.toFloat()
                                if (scrollPct <= 0.20f && !screenModel.isLoadingPrevious) {
                                    screenModel.loadPreviousChapterInBackground()
                                }
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
                if (pending != null) {
                    screenModel.pendingScrollAdjustment = null
                    adapter?.submitList(contentItems) {
                        rv.post {
                            val lm = rv.layoutManager as? LinearLayoutManager
                            lm?.scrollToPositionWithOffset(pending, 0)
                        }
                    }
                } else {
                    adapter?.submitList(contentItems)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        DisposableEffect(Unit) {
            onDispose {
                recyclerView?.adapter = null
            }
        }

        DisposableEffect(textConfig) {
            adapter?.notifyDataSetChanged()
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
 * Draws a background texture overlay using programmatic noise patterns.
 * No bitmap assets needed — textures are generated via Canvas drawing commands.
 *
 * - PAPER_GRAIN: fine dot noise, warm tint
 * - LINEN: crosshatch lines, cool tint
 * - PARCHMENT: irregular blotches, warm sepia tint
 *
 * Alpha is controlled by [strength] (0–100, where 50 = moderate, 100 = strong).
 * Drawn on top of the content but with low alpha so text remains readable.
 */
@Composable
private fun NovelTextureOverlay(
    texture: NovelReaderBackgroundTexture,
    strength: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha = (strength / 100f).coerceIn(0f, 1f) * 0.35f
    val baseColor = when (texture) {
        NovelReaderBackgroundTexture.PAPER_GRAIN -> if (isDark) Color(0xFF8B7355) else Color(0xFFD4B896)
        NovelReaderBackgroundTexture.LINEN -> if (isDark) Color(0xFF7A8B9F) else Color(0xFFB8C4D4)
        NovelReaderBackgroundTexture.PARCHMENT -> if (isDark) Color(0xFF9B8060) else Color(0xFFE8D5B7)
        NovelReaderBackgroundTexture.NONE -> return
    }
    val seed = remember(texture) { texture.ordinal * 1000 }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when (texture) {
            NovelReaderBackgroundTexture.PAPER_GRAIN -> {
                // Fine dot noise — small circles at pseudo-random positions
                val cellSize = 6f
                val cols = (w / cellSize).toInt() + 1
                val rows = (h / cellSize).toInt() + 1
                for (y in 0..rows) {
                    for (x in 0..cols) {
                        val hash = ((x * 73856093) xor (y * 19349663) xor seed) and 0xFFFF
                        val rand = hash.toFloat() / 0xFFFFf
                        if (rand > 0.6f) {
                            val cx = x * cellSize + (rand * cellSize)
                            val cy = y * cellSize + ((1f - rand) * cellSize)
                            drawCircle(
                                color = baseColor.copy(alpha = alpha * rand.coerceIn(0.3f, 1f)),
                                radius = 0.8f + rand * 0.7f,
                                center = Offset(cx, cy),
                            )
                        }
                    }
                }
            }
            NovelReaderBackgroundTexture.LINEN -> {
                // Crosshatch — diagonal lines in two directions
                val spacing = 4f
                drawLine(
                    color = baseColor.copy(alpha = alpha * 0.5f),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                    strokeWidth = 0.5f,
                )
                var offset = spacing
                while (offset < w + h) {
                    drawLine(
                        color = baseColor.copy(alpha = alpha * 0.3f),
                        start = Offset(offset, 0f),
                        end = Offset(0f, offset),
                        strokeWidth = 0.5f,
                    )
                    offset += spacing
                }
                offset = spacing
                while (offset < w + h) {
                    drawLine(
                        color = baseColor.copy(alpha = alpha * 0.3f),
                        start = Offset(w - offset, 0f),
                        end = Offset(w, offset),
                        strokeWidth = 0.5f,
                    )
                    offset += spacing
                }
            }
            NovelReaderBackgroundTexture.PARCHMENT -> {
                // Irregular blotches — larger semi-transparent circles
                val cellSize = 40f
                val cols = (w / cellSize).toInt() + 2
                val rows = (h / cellSize).toInt() + 2
                for (y in 0..rows) {
                    for (x in 0..cols) {
                        val hash = ((x * 2654435761.toInt()) xor (y * 40503) xor seed) and 0xFFFF
                        val rand = hash.toFloat() / 0xFFFFf
                        if (rand > 0.7f) {
                            val cx = x * cellSize + (rand * cellSize * 0.5f)
                            val cy = y * cellSize + ((1f - rand) * cellSize * 0.5f)
                            drawCircle(
                                color = baseColor.copy(alpha = alpha * rand * 0.4f),
                                radius = 8f + rand * 20f,
                                center = Offset(cx, cy),
                            )
                        }
                    }
                }
            }
            NovelReaderBackgroundTexture.NONE -> {}
        }
    }
}
