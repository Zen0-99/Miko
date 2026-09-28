package eu.kanade.presentation.entries.novel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.MaterialTheme
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
import eu.kanade.presentation.entries.components.EnhancedEntryActionRow
import eu.kanade.presentation.entries.components.EnhancedEntryInfoBox
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.EntryGenreRow
import eu.kanade.presentation.entries.components.EntryIconAction
import eu.kanade.presentation.entries.components.EntryToolbar
import eu.kanade.presentation.entries.components.enhancedEntryStatusText
import eu.kanade.presentation.entries.novel.components.ExpandableNovelDescription
import eu.kanade.presentation.entries.novel.components.NovelChapterAudioAction
import eu.kanade.presentation.entries.novel.components.NovelChapterDownloadAction
import eu.kanade.presentation.entries.novel.components.NovelChapterHeader
import eu.kanade.presentation.entries.novel.components.NovelChapterListItem
import eu.kanade.presentation.entries.novel.components.NovelContinueButton
import eu.kanade.presentation.entries.novel.components.NovelEditDialog
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.source.novel.isLocalOrStub
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterList
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import eu.kanade.tachiyomi.util.chapter.ChapterNameGroup
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
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
    onWebViewLongClicked: (() -> Unit)?,
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
    onClickLinkedSources: (() -> Unit)? = null,
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
            onWebViewLongClicked = onWebViewLongClicked,
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
            onClickLinkedSources = onClickLinkedSources,
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
            onWebViewLongClicked = onWebViewLongClicked,
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
            onClickLinkedSources = onClickLinkedSources,
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
    onWebViewLongClicked: (() -> Unit)?,
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
    onClickLinkedSources: (() -> Unit)? = null,
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
    // names; the ungrouped remainder lands in a trailing "Chapters" pill.
    val ungroupedChaptersLabel = stringResource(MR.strings.chapters)
    val chapterGroups = remember(chapters) {
        ChapterNameGroup.computeGroups(
            chapters.map { it.chapter.name },
            ungroupedChaptersLabel,
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
                    onClickMarkAllRead = onMarkAllReadClicked,
                    onClickMarkAllUnread = onMarkAllUnreadClicked,
                    // For book sources, hide the "remove downloads" menu items since
                    // the download button already handles deletion.
                    onClickRemoveAllDownloads = if (state.isBookSource) null else onRemoveAllDownloadsClicked,
                    onClickRemoveNonBookmarkedDownloads = if (state.isBookSource) {
                        null
                    } else {
                        onRemoveNonBookmarkedDownloadsClicked
                    },
                    onClickRemoveReadDownloads = onRemoveReadDownloadsClicked,
                    onClickLinkedSources = onClickLinkedSources,
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
                            state.source.name,
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
                    // Enhanced order: wide continue button first, then the
                    // in-library heart and icon actions, with genre pills in
                    // a horizontal scroll row below.
                    Column {
                        // For book sources (Anna's Archive) with no
                        // chapters, the Start button becomes "Download
                        // book" and triggers the EPUB download + splitting
                        // instead of opening the reader.
                        val isBookSourceEmpty = state.isBookSource && chapters.isEmpty()
                        EnhancedEntryActionRow(
                            favorite = state.novel.favorite,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            accentColor = state.accentColor,
                            extraActions = {
                                EntryIconAction(
                                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                                    contentDescription = stringResource(AYMR.strings.highlights),
                                    tint = state.accentColor
                                        ?: MaterialTheme.colorScheme.primary,
                                    onClick = onHighlightsClicked,
                                )
                                EntryIconAction(
                                    icon = Icons.AutoMirrored.Outlined.Comment,
                                    contentDescription = stringResource(MR.strings.comments_title),
                                    tint = state.accentColor
                                        ?: MaterialTheme.colorScheme.primary,
                                    onClick = onCommentsClicked,
                                )
                            },
                        ) {
                            NovelContinueButton(
                                chapterItem = state.nextContinueChapter,
                                hasReadChapters = remember(state.chapters) {
                                    state.chapters.fastAny { it.chapter.read }
                                },
                                allChaptersRead = state.allChaptersRead,
                                accentColor = state.accentColor,
                                onClick = if (isBookSourceEmpty) {
                                    {
                                        onDownloadBook?.invoke()
                                        Unit
                                    }
                                } else {
                                    onContinueReading
                                },
                                modifier = Modifier.weight(1.6f),
                                isBookSource = state.isBookSource,
                                isDownloadingBook = state.isBookSource && state.isRefreshingData,
                                horizontalPadding = 0.dp,
                                onListenClick = onContinueListening,
                            )
                        }
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

                items(
                    items = filteredChapters,
                    key = { it.id },
                    contentType = { EntryScreenItem.ITEM },
                ) { chapterItem ->
                    val haptic = LocalHapticFeedback.current
                    NovelChapterListItem(
                        item = chapterItem,
                        isFromSource = state.isFromSource,
                        // Hide per-chapter download indicators for book sources
                        // (Anna's Archive) — the whole book is downloaded at once.
                        downloadIndicatorEnabled = !state.source.isLocalOrStub() && !state.isBookSource,
                        // No upload timestamp → no date shown; the card keeps
                        // the meta line's height regardless.
                        date = chapterItem.chapter.dateUpload
                            .takeIf { it > 0L }
                            ?.let { relativeDateTimeText(it) },
                        readProgress = chapterItem.readProgress?.let {
                            stringResource(MR.strings.novel_chapter_progress, it)
                        },
                        scanlator = chapterItem.chapter.scanlator,
                        onClick = {
                            if (isAnySelected) {
                                onChapterSelected(chapterItem, !chapterItem.selected, true, false)
                            } else {
                                onChapterClicked(chapterItem.chapter)
                            }
                        },
                        onLongClick = {
                            onChapterSelected(chapterItem, !chapterItem.selected, true, true)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDownloadClick = if (onDownloadChapter != null) {
                            { onDownloadChapter(listOf(chapterItem), it) }
                        } else {
                            null
                        },
                        onAudioClick = if (onAudioChapter != null) {
                            { onAudioChapter(listOf(chapterItem), it) }
                        } else {
                            null
                        },
                        audioStateProvider = { chapterItem.audioState },
                        audioProgressProvider = { chapterItem.audioProgress },
                        onSwipeLeft = { onChapterSwipe(chapterItem, chapterSwipeStartAction) },
                        onSwipeRight = { onChapterSwipe(chapterItem, chapterSwipeEndAction) },
                        onSelect = { selected -> onChapterSelected(chapterItem, selected, true, false) },
                        chapterSwipeStartAction = chapterSwipeStartAction,
                        chapterSwipeEndAction = chapterSwipeEndAction,
                        accentColor = state.accentColor,
                    )
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
    onWebViewLongClicked: (() -> Unit)?,
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
    onClickLinkedSources: (() -> Unit)? = null,
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
        onWebViewLongClicked = onWebViewLongClicked,
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
        onClickLinkedSources = onClickLinkedSources,
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
