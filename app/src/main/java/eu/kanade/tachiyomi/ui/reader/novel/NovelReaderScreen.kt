package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.flow.collectLatest
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
                screenModel.saveCurrentPosition()
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
                            rv.post {
                                if (event.position == 0) {
                                    rv.scrollToPosition(0)
                                } else if (event.position == -1) {
                                    val lm = rv.layoutManager as? LinearLayoutManager
                                    lm?.scrollToPosition(rv.adapter?.itemCount?.minus(1) ?: 0)
                                    rv.post { rv.scrollBy(0, Int.MAX_VALUE / 2) }
                                }
                            }
                        }
                    }
                    is NovelReaderEvent.AdjustScrollOffset -> {
                        val rv = recyclerViewRef
                        if (rv != null && event.delta > 0) {
                            rv.post {
                                val lm = rv.layoutManager as? LinearLayoutManager
                                // Offset scroll by the number of prepended items so the
                                // user stays at the same visual position.
                                lm?.scrollToPositionWithOffset(event.delta, 0)
                            }
                        }
                    }
                    is NovelReaderEvent.ScrollToCharacter -> {
                        val rv = recyclerViewRef
                        if (rv != null && event.characterPosition > 0) {
                            // submitList is async, so the adapter may not have
                            // items yet. Retry a few times with increasing delay.
                            val targetPos = event.characterPosition
                            var attempts = 0
                            val maxAttempts = 5
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
                                } else if (++attempts < maxAttempts) {
                                    // Target paragraph not found yet — may still be loading.
                                    rv.postDelayed(::tryScroll, 100L * attempts)
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
                onRecyclerViewReady = { rv -> recyclerViewRef = rv },
            )

            NovelReaderChrome(
                isMenuVisible = isControlsVisible,
                title = currentChapter?.name ?: "Loading...",
                subtitle = novel?.title ?: "",
                progressPercent = if (screenModel.preferences.showReadingProgress().get()) progressPercent else -1,
                estimatedReadingTime = if (screenModel.preferences.showEstimatedReadingTime().get()) {
                    screenModel.positionTracker.getEstimatedReadingTime()
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
                onDismissRequest = { screenModel.dismissSettings() },
                onShowMenus = { screenModel.setMenuVisible(true) },
                accentColor = accentColor,
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
            )
        }

        if (isCommentsDialogVisible) {
            NovelCommentsDialog(
                comments = comments,
                isLoading = isLoadingComments,
                error = commentsError,
                onDismiss = { screenModel.dismissComments() },
                onRefresh = { screenModel.refreshComments() },
                accentColor = accentColor,
            )
        }

        ApplyReaderWindowSettings(activity, screenModel)

        dictionaryQuery?.let { query ->
            DictionaryBottomSheet(
                selectedText = query,
                onDismiss = { screenModel.dismissDictionary() },
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
                                if (firstView != null) {
                                    val scrollOffset = -firstView.top
                                    val totalHeight = firstView.height
                                    if (totalHeight > 0) {
                                        val scrollPct = (scrollOffset.toFloat() / totalHeight).coerceIn(0f, 1f)
                                        val visibleItem = items.getOrNull(firstVisiblePos) as? TextItem.Paragraph
                                        if (visibleItem != null && visibleItem.chapterId == currentChapterId) {
                                            val paraChars = visibleItem.endCharIndex - visibleItem.startCharIndex
                                            charPos += (paraChars * scrollPct).toInt()
                                        }
                                    }
                                }
                                screenModel.updateCharacterPosition(charPos)
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
                adapter?.submitList(contentItems)
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
