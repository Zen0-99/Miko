package eu.kanade.presentation.entries.novel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.EntryScreenItem
import eu.kanade.presentation.entries.components.ChapterGroupPillRow
import eu.kanade.presentation.entries.components.EnhancedEntryInfoBox
import eu.kanade.presentation.entries.components.EntryChapterCarouselCard
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.EntryGenreRow
import eu.kanade.presentation.entries.components.EntryToolbar
import eu.kanade.presentation.entries.components.enhancedEntryStatusText
import eu.kanade.presentation.entries.novel.components.ExpandableNovelDescription
import eu.kanade.presentation.entries.novel.components.NovelChapterAudioAction
import eu.kanade.presentation.entries.novel.components.NovelChapterDownloadAction
import eu.kanade.presentation.entries.novel.components.NovelChapterAudioIndicator
import eu.kanade.presentation.entries.novel.components.NovelChapterDownloadIndicator
import eu.kanade.presentation.entries.novel.components.NovelChapterHeader
import eu.kanade.presentation.entries.novel.components.NovelEditDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import eu.kanade.presentation.entries.components.EntryContinueRow
import eu.kanade.presentation.entries.components.EntryNextUpAction
import eu.kanade.presentation.entries.components.EntryViewCommentsButton
import tachiyomi.i18n.aniyomi.AYMR
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.source.novel.isLocalOrStub
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterList
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import eu.kanade.tachiyomi.util.chapter.ChapterNameGroup
import eu.kanade.presentation.util.formatChapterNumber
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.api.get

@Composable
fun NovelScreen(
    state: NovelScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    isTabletUi: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (NovelChapter) -> Unit,
    onDownloadChapter: ((List<NovelChapterList.Item>, NovelChapterDownloadAction) -> Unit)?,
    onAudioChapter: ((List<NovelChapterList.Item>, NovelChapterAudioAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onCoverClick: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onBookmarkFilterClicked: () -> Unit,
    onHighlightsClicked: (() -> Unit)? = null,
    onTrackingClicked: (() -> Unit)? = null,
    onCommentsClicked: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onContinueListening: (() -> Unit)? = null,
    onSearch: (String, Boolean) -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCollectionClicked: (() -> Unit)?,
    onEditNovel: ((String, String, String, Long, List<String>) -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onMarkAllReadClicked: (() -> Unit)?,
    onMarkAllUnreadClicked: (() -> Unit)?,
    onRemoveAllDownloadsClicked: (() -> Unit)?,
    onRemoveNonBookmarkedDownloadsClicked: (() -> Unit)?,
    onRemoveReadDownloadsClicked: (() -> Unit)?,
    onMultiBookmarkClicked: (List<NovelChapter>, Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<NovelChapter>, Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (NovelChapter, Boolean) -> Unit,
    onMultiDeleteClicked: (List<NovelChapter>) -> Unit,
    onChapterSwipe: (NovelChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onChapterSelected: (NovelChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onFetchNewChapters: (() -> Unit)? = null,
    onFetchAllChapters: (() -> Unit)? = null,
    onSuggestionClick: (SuggestionItem) -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onRetrySuggestions: () -> Unit = {},
    onDownloadBook: (() -> Unit)? = null,
    onDeleteBook: (() -> Unit)? = null,
    onHideChapter: ((NovelChapter) -> Unit)? = null,
    onUnhideChapters: (() -> Unit)? = null,
) {
    if (isTabletUi) {
        NovelScreenLargeImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            navigateUp = navigateUp,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAudioChapter = onAudioChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onCoverClick = onCoverClick,
            onWebViewClicked = onWebViewClicked,
            onTagSearch = onTagSearch,
            onFilterButtonClicked = onFilterButtonClicked,
            onBookmarkFilterClicked = onBookmarkFilterClicked,
            onHighlightsClicked = onHighlightsClicked,
            onTrackingClicked = onTrackingClicked,
            onCommentsClicked = onCommentsClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onContinueListening = onContinueListening,
            onSearch = onSearch,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCollectionClicked = onEditCollectionClicked,
            onEditNovel = onEditNovel,
            onMigrateClicked = onMigrateClicked,
            onMarkAllReadClicked = onMarkAllReadClicked,
            onMarkAllUnreadClicked = onMarkAllUnreadClicked,
            onRemoveAllDownloadsClicked = onRemoveAllDownloadsClicked,
            onRemoveNonBookmarkedDownloadsClicked = onRemoveNonBookmarkedDownloadsClicked,
            onRemoveReadDownloadsClicked = onRemoveReadDownloadsClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            onFetchNewChapters = onFetchNewChapters,
            onFetchAllChapters = onFetchAllChapters,
            onSuggestionClick = onSuggestionClick,
            onOpenSuggestions = onOpenSuggestions,
            onRetrySuggestions = onRetrySuggestions,
            onDownloadBook = onDownloadBook,
            onDeleteBook = onDeleteBook,
            onHideChapter = onHideChapter,
            onUnhideChapters = onUnhideChapters,
        )
    } else {
        NovelScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            navigateUp = navigateUp,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAudioChapter = onAudioChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onCoverClick = onCoverClick,
            onWebViewClicked = onWebViewClicked,
            onTagSearch = onTagSearch,
            onFilterButtonClicked = onFilterButtonClicked,
            onBookmarkFilterClicked = onBookmarkFilterClicked,
            onHighlightsClicked = onHighlightsClicked,
            onTrackingClicked = onTrackingClicked,
            onCommentsClicked = onCommentsClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onContinueListening = onContinueListening,
            onSearch = onSearch,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCollectionClicked = onEditCollectionClicked,
            onEditNovel = onEditNovel,
            onMigrateClicked = onMigrateClicked,
            onMarkAllReadClicked = onMarkAllReadClicked,
            onMarkAllUnreadClicked = onMarkAllUnreadClicked,
            onRemoveAllDownloadsClicked = onRemoveAllDownloadsClicked,
            onRemoveNonBookmarkedDownloadsClicked = onRemoveNonBookmarkedDownloadsClicked,
            onRemoveReadDownloadsClicked = onRemoveReadDownloadsClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            onFetchNewChapters = onFetchNewChapters,
            onFetchAllChapters = onFetchAllChapters,
            onSuggestionClick = onSuggestionClick,
            onOpenSuggestions = onOpenSuggestions,
            onRetrySuggestions = onRetrySuggestions,
            onDownloadBook = onDownloadBook,
            onDeleteBook = onDeleteBook,
            onHideChapter = onHideChapter,
            onUnhideChapters = onUnhideChapters,
        )
    }
}

@Composable
private fun NovelScreenSmallImpl(
    state: NovelScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (NovelChapter) -> Unit,
    onDownloadChapter: ((List<NovelChapterList.Item>, NovelChapterDownloadAction) -> Unit)?,
    onAudioChapter: ((List<NovelChapterList.Item>, NovelChapterAudioAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onCoverClick: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onBookmarkFilterClicked: () -> Unit,
    onHighlightsClicked: (() -> Unit)? = null,
    onTrackingClicked: (() -> Unit)? = null,
    onCommentsClicked: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onContinueListening: (() -> Unit)? = null,
    onSearch: (String, Boolean) -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCollectionClicked: (() -> Unit)?,
    onEditNovel: ((String, String, String, Long, List<String>) -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onMarkAllReadClicked: (() -> Unit)?,
    onMarkAllUnreadClicked: (() -> Unit)?,
    onRemoveAllDownloadsClicked: (() -> Unit)?,
    onRemoveNonBookmarkedDownloadsClicked: (() -> Unit)?,
    onRemoveReadDownloadsClicked: (() -> Unit)?,
    onMultiBookmarkClicked: (List<NovelChapter>, Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<NovelChapter>, Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (NovelChapter, Boolean) -> Unit,
    onMultiDeleteClicked: (List<NovelChapter>) -> Unit,
    onChapterSwipe: (NovelChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onChapterSelected: (NovelChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onFetchNewChapters: (() -> Unit)? = null,
    onFetchAllChapters: (() -> Unit)? = null,
    onSuggestionClick: (SuggestionItem) -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onRetrySuggestions: () -> Unit = {},
    onDownloadBook: (() -> Unit)? = null,
    onDeleteBook: (() -> Unit)? = null,
    onHideChapter: ((NovelChapter) -> Unit)? = null,
    onUnhideChapters: (() -> Unit)? = null,
) {
    val chapterListState = rememberLazyListState()

    val chapters = remember(state) { state.processedChapters }
    val isAnySelected = remember(state) { state.isAnySelected }

    // Chapter-group pills — "Volume N"/"Book N"/etc. parsed from chapter
    // names. All-or-nothing: any unmarked chapter disables the row.
    val ungroupedChaptersLabel = stringResource(MR.strings.chapters)
    val chapterGroups = remember(chapters) {
        ChapterNameGroup.computeGroups(
            chapters.map { it.chapter.name },
        )
    }
    // Reading position is progress-derived — the group holding the newest
    // unread chapter; falls back to the newest group. Pill taps are
    // browse-only session state: they must not persist as "current" or
    // re-entering the entry would land on the wrong group.
    val progressChapterGroup = remember(chapters, chapterGroups) {
        chapters
            .firstOrNull { !it.chapter.read }
            ?.let { ChapterNameGroup.parse(it.chapter.name) }
            ?.takeIf { it in chapterGroups }
    }

    // Groups whose chapters are all read — shown with a checkmark pill.
    val completedChapterGroups = remember(chapters, chapterGroups) {
        if (chapterGroups.size <= 1) {
            emptySet()
        } else {
            chapters
                .groupBy { ChapterNameGroup.parse(it.chapter.name) ?: ungroupedChaptersLabel }
                .filterValues { items -> items.all { it.chapter.read } }
                .keys
        }
    }

    // null = follow progress (auto-advances as chapters get read); a pill
    // tap sets a browse override so the user can look at other groups
    // without changing their reading position.
    var chapterGroupBrowseOverride by remember(state.novel.id) {
        mutableStateOf<String?>(null)
    }
    // Once a group is being displayed it stays displayed for the session:
    // finishing it (progress advancing to the next group) must not yank the
    // viewed group away mid-browse. Re-entering resets.
    var pinnedChapterGroup by remember(state.novel.id) { mutableStateOf<String?>(null) }
    var lastProgressGroup by remember(state.novel.id) { mutableStateOf<String?>(null) }
    var progressGroupSeen by remember(state.novel.id) { mutableStateOf(false) }
    val selectedChapterGroup = chapterGroupBrowseOverride?.takeIf { it in chapterGroups }
        ?: pinnedChapterGroup?.takeIf { it in chapterGroups }
        ?: progressChapterGroup
        ?: chapterGroups.lastOrNull()
        ?: ungroupedChaptersLabel
    LaunchedEffect(progressChapterGroup) {
        val p = progressChapterGroup ?: return@LaunchedEffect
        if (!progressGroupSeen) {
            progressGroupSeen = true
            lastProgressGroup = p
        } else if (p != lastProgressGroup) {
            if (pinnedChapterGroup == null && chapterGroupBrowseOverride == null) {
                pinnedChapterGroup = lastProgressGroup
            }
            lastProgressGroup = p
        }
    }
    val filteredChapters = remember(chapters, selectedChapterGroup) {
        if (chapterGroups.size <= 1) {
            chapters
        } else {
            chapters.filter { item ->
                (ChapterNameGroup.parse(item.chapter.name) ?: ungroupedChaptersLabel) == selectedChapterGroup
            }
        }
    }
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        NovelEditDialog(
            novel = state.novel,
            onDismiss = { showEditDialog = false },
            onSave = { title, author, description, status, tags ->
                onEditNovel?.invoke(title, author, description, status, tags)
                showEditDialog = false
            },
        )
    }

    BackHandler(onBack = {
        when {
            isAnySelected -> onAllChapterSelected(false)
            else -> navigateUp()
        }
    })

    val pullState = rememberPullToRefreshState()
    val heroOverscrollPx = pullState.distanceFraction *
        with(LocalDensity.current) { 80.dp.toPx() }
    PullRefresh(
        refreshing = state.isRefreshingData,
        enabled = !isAnySelected,
        onRefresh = onRefresh,
        state = pullState,
    ) {
        Scaffold(
            topBar = {
                val selectedChapterCount = remember(chapters) {
                    chapters.count { it.selected }
                }
                // Scroll-dependent alpha: top-bar opacity gradually increases as the user
                // scrolls down, proportional to how far the first item has scrolled.
                // At the top (offset=0): alpha=0 (transparent). Past one item height: alpha=1.
                val titleAlpha by remember {
                    derivedStateOf {
                        if (chapterListState.firstVisibleItemIndex == 0) {
                            // Gradually increase based on scroll offset of first item
                            val offset = chapterListState.firstVisibleItemScrollOffset
                            val layoutInfo = chapterListState.layoutInfo
                            val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                            (offset.toFloat() / itemHeight.toFloat()).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                    }
                }
                val backgroundAlpha = titleAlpha
                EntryToolbar(
                    title = state.novel.title,
                    hasFilters = state.filterActive,
                    navigateUp = navigateUp,
                    onClickFilter = null,
                    onClickShare = onShareClicked,
                    // For book sources (Anna's Archive), the download button only
                    // shows "Delete book" when chapters exist. When empty, the
                    // Start button handles the "Download book" action.
                    onClickDownload = if (state.isBookSource && chapters.isNotEmpty()) {
                        { _: DownloadAction ->
                            onDeleteBook?.invoke()
                            Unit
                        }
                    } else if (state.isBookSource) {
                        null // Hide download icon when book source has no chapters
                    } else {
                        onDownloadActionClicked
                    },
                    onClickEditCollection = if (onEditNovel != null) {
                        { showEditDialog = true }
                    } else {
                        onEditCollectionClicked
                    },
                    onClickMigrate = onMigrateClicked,
                    onClickSettings = null,
                    // Mark-all lives on the next-up card's checkmark toggle;
                    // web view / highlights moved here from the action row.
                    onClickWebView = onWebViewClicked,
                    onClickHighlights = onHighlightsClicked,
                    // For book sources, hide the "remove downloads" menu items since
                    // the download button already handles deletion.
                    onClickRemoveAllDownloads = if (state.isBookSource) null else onRemoveAllDownloadsClicked,
                    onClickRemoveNonBookmarkedDownloads = if (state.isBookSource) {
                        null
                    } else {
                        onRemoveNonBookmarkedDownloadsClicked
                    },
                    onClickRemoveReadDownloads = onRemoveReadDownloadsClicked,
                    onClickUnhideChapters = onUnhideChapters,
                    changeAnimeSkipIntro = null,
                    actionModeCounter = selectedChapterCount,
                    onCancelActionMode = { onAllChapterSelected(false) },
                    onSelectAll = { onAllChapterSelected(true) },
                    onInvertSelection = { onInvertSelection() },
                    titleAlphaProvider = { titleAlpha },
                    backgroundAlphaProvider = { backgroundAlpha },
                    isManga = true, // Novels use chapter/unread terminology like manga
                    toolbarBackgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    // For book sources with downloaded chapters, skip the chapter-count
                    // dropdown and directly trigger deletion (the whole book is one unit).
                    directDownloadAction = state.isBookSource && chapters.isNotEmpty(),
                )
            },
            bottomBar = {
                val selected = remember(chapters) { chapters.filter { it.selected } }
                val hideChapter = onHideChapter
                EntryBottomActionMenu(
                    visible = isAnySelected,
                    isManga = true, // Novels use chapter/unread terminology like manga
                    onBookmarkClicked = {
                        onMultiBookmarkClicked(selected.map { it.chapter }, true)
                    }.takeIf { selected.fastAny { !it.chapter.bookmark } },
                    onRemoveBookmarkClicked = {
                        onMultiBookmarkClicked(selected.map { it.chapter }, false)
                    }.takeIf { selected.fastAny { it.chapter.bookmark } },
                    onMarkAsViewedClicked = {
                        onMultiMarkAsReadClicked(selected.map { it.chapter }, true)
                    }.takeIf { selected.fastAny { !it.chapter.read } },
                    onMarkAsUnviewedClicked = {
                        onMultiMarkAsReadClicked(selected.map { it.chapter }, false)
                    }.takeIf { selected.fastAny { it.chapter.read } },
                    onMarkPreviousAsViewedClicked = {
                        selected.firstOrNull()?.chapter?.let { onMarkPreviousAsReadClicked(it, true) }
                        Unit
                    }.takeIf { selected.size == 1 },
                    onDownloadClicked = {
                        onDownloadChapter!!(selected.toList(), NovelChapterDownloadAction.START)
                    }.takeIf {
                        onDownloadChapter != null &&
                            selected.fastAny { it.downloadState != NovelDownload.State.DOWNLOADED }
                    },
                    onDeleteClicked = {
                        onMultiDeleteClicked(selected.map { it.chapter })
                    }.takeIf { onDownloadChapter != null },
                    onHideClicked = if (hideChapter != null) {
                        { selected.forEach { hideChapter(it.chapter) } }
                    } else {
                        null
                    },
                )
            },
            snackbarHost = { AchievementStyledSnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            val topPadding = contentPadding.calculateTopPadding()

            val layoutDirection = LocalLayoutDirection.current
            val heroParallaxPx = if (chapterListState.firstVisibleItemIndex == 0) {
                chapterListState.firstVisibleItemScrollOffset.toFloat()
            } else {
                0f
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .consumeWindowInsets(contentPadding),
                state = chapterListState,
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            ) {
                item(
                    key = EntryScreenItem.INFO_BOX,
                    contentType = EntryScreenItem.INFO_BOX,
                ) {
                    EnhancedEntryInfoBox(
                        appBarPadding = topPadding,
                        title = state.novel.title,
                        backdrop = state.novel.asNovelCover(),
                        logoUrl = null,
                        metaItems = listOfNotNull(
                            state.novel.author?.takeIf { it.isNotBlank() },
                            enhancedEntryStatusText(state.novel.status),
                            if (CanonicalCatalog.workIdFromNovelUrl(state.novel.url) != null) {
                                "MikoDB"
                            } else {
                                state.source.name
                            },
                        ),
                        rating = null,
                        onCoverClick = onCoverClick,
                        // Portrait covers — taller hero, no horizontal pan.
                        heroContentPadding = 440.dp,
                        overscrollPullPx = heroOverscrollPx,
                        parallaxOffsetPx = heroParallaxPx,
                        accentColor = state.accentColor,
                    )
                }

                item(
                    key = EntryScreenItem.ACTION_ROW,
                    contentType = EntryScreenItem.ACTION_ROW,
                ) {
                    // Continue row: accent action bar flanked by
                    // favourite / mark-all toggle boxes. Genre pills
                    // stay in the row below.
                    Column {
                        // For book sources (Anna's Archive) with no
                        // chapters, the action zone becomes "Download
                        // book" and triggers the EPUB download + splitting
                        // instead of opening the reader.
                        val isBookSourceEmpty = state.isBookSource && chapters.isEmpty()
                        val allRead = state.allChaptersRead
                        val nextChapter = state.nextContinueChapter?.chapter
                        val actions = when {
                            isBookSourceEmpty -> listOf(
                                EntryNextUpAction(
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                    label = stringResource(MR.strings.download_book),
                                    onClickLabel = stringResource(MR.strings.download_book),
                                    onClick = {
                                        onDownloadBook?.invoke()
                                        Unit
                                    },
                                ),
                            )
                            allRead -> listOf(
                                EntryNextUpAction(
                                    icon = Icons.Filled.CheckCircle,
                                    label = stringResource(
                                        AYMR.strings.fetching_overlay_all_up_to_date,
                                    ),
                                    onClickLabel = stringResource(
                                        AYMR.strings.fetching_overlay_all_up_to_date,
                                    ),
                                    onClick = {},
                                ),
                            )
                            nextChapter != null && onContinueListening != null -> listOf(
                                EntryNextUpAction(
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                    label = stringResource(AYMR.strings.action_read),
                                    onClickLabel = stringResource(MR.strings.action_resume),
                                    onClick = onContinueReading,
                                ),
                                EntryNextUpAction(
                                    icon = Icons.Filled.Headphones,
                                    label = stringResource(AYMR.strings.action_listen),
                                    onClickLabel = stringResource(
                                        AYMR.strings.audiobook_continue_listening_cd,
                                    ),
                                    onClick = onContinueListening,
                                ),
                            )
                            else -> listOf(
                                EntryNextUpAction(
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                    label = stringResource(
                                        if (nextChapter != null && state.chapters.fastAny {
                                                it.chapter.read
                                            }
                                        ) {
                                            MR.strings.action_continue
                                        } else {
                                            MR.strings.action_start
                                        },
                                    ),
                                    onClickLabel = stringResource(MR.strings.action_resume),
                                    onClick = onContinueReading,
                                ),
                            )
                        }
                        EntryContinueRow(
                            actions = actions,
                            actionsEnabled = when {
                                isBookSourceEmpty -> !state.isRefreshingData
                                allRead -> false
                                else -> nextChapter != null
                            },
                            accentColor = state.accentColor,
                            favorite = state.novel.favorite,
                            allDone = allRead,
                            favoriteContentDescription = stringResource(
                                if (state.novel.favorite) {
                                    MR.strings.in_library
                                } else {
                                    MR.strings.add_to_library
                                },
                            ),
                            doneContentDescription = stringResource(
                                if (allRead) {
                                    MR.strings.action_mark_all_as_unread
                                } else {
                                    MR.strings.action_mark_all_as_read
                                },
                            ),
                            onFavoriteClick = onAddToLibraryClicked,
                            onToggleDoneClick = {
                                if (allRead) {
                                    onMarkAllUnreadClicked?.invoke()
                                } else {
                                    onMarkAllReadClicked?.invoke()
                                }
                            },
                        )
                        EntryGenreRow(
                            pills = state.novel.genre.orEmpty(),
                            onPillClick = { onSearch(it, true) },
                            accentColor = state.accentColor,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }

                item(
                    key = EntryScreenItem.DESCRIPTION_WITH_TAG,
                    contentType = EntryScreenItem.DESCRIPTION_WITH_TAG,
                ) {
                    ExpandableNovelDescription(
                        defaultExpandState = false,
                        description = state.novel.description,
                        // Genres live in the pill scroll row — no pills
                        // inside the description.
                        tagsProvider = { null },
                        accentColor = state.accentColor,
                        onTagSearch = onTagSearch,
                    )
                }

                // Similar titles — temporarily disabled
//                item(key = "SUGGESTIONS", contentType = "SUGGESTIONS") {
//                    AuroraSuggestionsRow(
//                        state = state.suggestions,
//                        onSuggestionClick = onSuggestionClick,
//                        onOpenSuggestions = onOpenSuggestions,
//                        onRetryClick = onRetrySuggestions,
//                        modifier = Modifier.fillMaxWidth(),
//                        accentColor = state.accentColor,
//                    )
//                }

                item(
                    key = EntryScreenItem.ITEM_HEADER,
                    contentType = EntryScreenItem.ITEM_HEADER,
                ) {
                    NovelChapterHeader(
                        itemCount = chapters.size,
                        onClick = onFilterButtonClicked,
                        accentColor = state.accentColor,
                        intervalDays = state.intervalDays,
                        showInterval = state.showInterval,
                    )
                }

                if (chapterGroups.size > 1) {
                    item(
                        key = "chapter-group-pills",
                        contentType = EntryScreenItem.ITEM,
                    ) {
                        ChapterGroupPillRow(
                            groups = chapterGroups,
                            selectedGroup = selectedChapterGroup,
                            completedGroups = completedChapterGroups,
                            onGroupSelected = { chapterGroupBrowseOverride = it },
                            accentColor = state.accentColor,
                        )
                    }
                }

                // Horizontal chapter carousel — Nuvio episode-card styling
                // without a still. Opens snapped to the next unread chapter;
                // long-press still drives selection, swipe actions are gone
                // (they collide with horizontal scrolling).
                if (filteredChapters.isNotEmpty()) {
                    item(
                        key = "chapter-carousel",
                        contentType = EntryScreenItem.ITEM,
                    ) {
                        // Land on the lowest-numbered unread chapter —
                        // "next up" regardless of sort direction.
                        val nextIndex = filteredChapters.mapIndexedNotNull { index, item ->
                            item.takeIf { !it.chapter.read }
                                ?.let { index to it.chapter.chapterNumber }
                        }.minByOrNull { it.second }?.first ?: 0
                        val rowState = rememberLazyListState(
                            initialFirstVisibleItemIndex = nextIndex,
                        )
                        LazyRow(
                            state = rowState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
                        ) {
                            items(
                                items = filteredChapters,
                                key = { it.id },
                                contentType = { EntryScreenItem.ITEM },
                            ) { chapterItem ->
                                val haptic = LocalHapticFeedback.current
                                // Per-chapter download indicators stay hidden
                                // for book sources — the whole book downloads
                                // at once.
                                val downloadIndicatorEnabled =
                                    !state.source.isLocalOrStub() && !state.isBookSource
                                EntryChapterCarouselCard(
                                    title = chapterItem.chapter.name,
                                    date = chapterItem.chapter.dateUpload
                                        .takeIf { it > 0L }
                                        ?.let { relativeDateTimeText(it) },
                                    metaParts = listOfNotNull(
                                        chapterItem.readProgress?.let {
                                            stringResource(MR.strings.novel_chapter_progress, it)
                                        },
                                        chapterItem.chapter.scanlator?.takeIf { it.isNotBlank() },
                                    ),
                                    progressFraction = chapterItem.readProgress
                                        ?.let { it / 100f },
                                    read = chapterItem.chapter.read,
                                    bookmark = chapterItem.chapter.bookmark,
                                    selected = chapterItem.selected,
                                    onLongClick = {
                                        onChapterSelected(chapterItem, !chapterItem.selected, true, true)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onClick = {
                                        if (chapterItem.selected) {
                                            onChapterSelected(chapterItem, false, true, false)
                                        } else if (isAnySelected) {
                                            onChapterSelected(chapterItem, true, true, false)
                                        } else {
                                            onChapterClicked(chapterItem.chapter)
                                        }
                                    },
                                    accentColor = state.accentColor,
                                    trailing = {
                                        if (downloadIndicatorEnabled &&
                                            chapterItem.downloadState == NovelDownload.State.DOWNLOADED
                                        ) {
                                            NovelChapterAudioIndicator(
                                                enabled = downloadIndicatorEnabled,
                                                audioStateProvider = { chapterItem.audioState },
                                                audioProgressProvider = { chapterItem.audioProgress },
                                                onClick = { onAudioChapter?.invoke(listOf(chapterItem), it) },
                                                accentColor = state.accentColor,
                                            )
                                        }
                                        if (downloadIndicatorEnabled) {
                                            NovelChapterDownloadIndicator(
                                                enabled = downloadIndicatorEnabled,
                                                downloadStateProvider = { chapterItem.downloadState },
                                                downloadProgressProvider = { chapterItem.downloadProgress },
                                                onClick = { onDownloadChapter?.invoke(listOf(chapterItem), it) },
                                                accentColor = state.accentColor,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // Comments live under the chapter cards — accent-outlined
                // button, same bottom sheet as the removed header icon.
                if (onCommentsClicked != null) {
                    item(
                        key = "chapter-comments-button",
                        contentType = EntryScreenItem.ITEM,
                    ) {
                        EntryViewCommentsButton(
                            commentsCount = state.commentsCount,
                            accentColor = state.accentColor,
                            onClick = onCommentsClicked,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelScreenLargeImpl(
    state: NovelScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (NovelChapter) -> Unit,
    onDownloadChapter: ((List<NovelChapterList.Item>, NovelChapterDownloadAction) -> Unit)?,
    onAudioChapter: ((List<NovelChapterList.Item>, NovelChapterAudioAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onCoverClick: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onBookmarkFilterClicked: () -> Unit,
    onHighlightsClicked: (() -> Unit)? = null,
    onTrackingClicked: (() -> Unit)? = null,
    onCommentsClicked: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onContinueListening: (() -> Unit)? = null,
    onSearch: (String, Boolean) -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCollectionClicked: (() -> Unit)?,
    onEditNovel: ((String, String, String, Long, List<String>) -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onMarkAllReadClicked: (() -> Unit)?,
    onMarkAllUnreadClicked: (() -> Unit)?,
    onRemoveAllDownloadsClicked: (() -> Unit)?,
    onRemoveNonBookmarkedDownloadsClicked: (() -> Unit)?,
    onRemoveReadDownloadsClicked: (() -> Unit)?,
    onMultiBookmarkClicked: (List<NovelChapter>, Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<NovelChapter>, Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (NovelChapter, Boolean) -> Unit,
    onMultiDeleteClicked: (List<NovelChapter>) -> Unit,
    onChapterSwipe: (NovelChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    onChapterSelected: (NovelChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onFetchNewChapters: (() -> Unit)? = null,
    onFetchAllChapters: (() -> Unit)? = null,
    onSuggestionClick: (SuggestionItem) -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onRetrySuggestions: () -> Unit = {},
    onDownloadBook: (() -> Unit)? = null,
    onDeleteBook: (() -> Unit)? = null,
    onHideChapter: ((NovelChapter) -> Unit)? = null,
    onUnhideChapters: (() -> Unit)? = null,
) {
    NovelScreenSmallImpl(
        state = state,
        snackbarHostState = snackbarHostState,
        chapterSwipeStartAction = chapterSwipeStartAction,
        chapterSwipeEndAction = chapterSwipeEndAction,
        navigateUp = navigateUp,
        onChapterClicked = onChapterClicked,
        onDownloadChapter = onDownloadChapter,
        onAudioChapter = onAudioChapter,
        onAddToLibraryClicked = onAddToLibraryClicked,
        onCoverClick = onCoverClick,
        onWebViewClicked = onWebViewClicked,
        onTagSearch = onTagSearch,
        onFilterButtonClicked = onFilterButtonClicked,
        onBookmarkFilterClicked = onBookmarkFilterClicked,
        onHighlightsClicked = onHighlightsClicked,
        onTrackingClicked = onTrackingClicked,
        onCommentsClicked = onCommentsClicked,
        onRefresh = onRefresh,
        onContinueReading = onContinueReading,
        onContinueListening = onContinueListening,
        onSearch = onSearch,
        onShareClicked = onShareClicked,
        onDownloadActionClicked = onDownloadActionClicked,
        onEditCollectionClicked = onEditCollectionClicked,
        onEditNovel = onEditNovel,
        onMigrateClicked = onMigrateClicked,
        onMarkAllReadClicked = onMarkAllReadClicked,
        onMarkAllUnreadClicked = onMarkAllUnreadClicked,
        onRemoveAllDownloadsClicked = onRemoveAllDownloadsClicked,
        onRemoveNonBookmarkedDownloadsClicked = onRemoveNonBookmarkedDownloadsClicked,
        onRemoveReadDownloadsClicked = onRemoveReadDownloadsClicked,
        onMultiBookmarkClicked = onMultiBookmarkClicked,
        onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
        onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
        onMultiDeleteClicked = onMultiDeleteClicked,
        onChapterSwipe = onChapterSwipe,
        onChapterSelected = onChapterSelected,
        onAllChapterSelected = onAllChapterSelected,
        onInvertSelection = onInvertSelection,
        onFetchNewChapters = onFetchNewChapters,
        onFetchAllChapters = onFetchAllChapters,
        onSuggestionClick = onSuggestionClick,
        onOpenSuggestions = onOpenSuggestions,
        onRetrySuggestions = onRetrySuggestions,
        onDownloadBook = onDownloadBook,
        onDeleteBook = onDeleteBook,
        onHideChapter = onHideChapter,
        onUnhideChapters = onUnhideChapters,
    )
}
