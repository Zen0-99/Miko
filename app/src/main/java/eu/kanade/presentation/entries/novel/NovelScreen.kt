package eu.kanade.presentation.entries.novel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.EntryScreenItem
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.EntryToolbar
import eu.kanade.presentation.entries.components.aurora.AuroraSuggestionsRow
import eu.kanade.presentation.entries.novel.components.ExpandableNovelDescription
import eu.kanade.presentation.entries.novel.components.NovelEditDialog
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.entries.novel.components.NovelActionRow
import eu.kanade.presentation.entries.novel.components.NovelChapterDownloadAction
import eu.kanade.presentation.entries.novel.components.NovelChapterHeader
import eu.kanade.presentation.entries.novel.components.NovelChapterListItem
import eu.kanade.presentation.entries.novel.components.NovelContinueButton
import eu.kanade.presentation.entries.novel.components.NovelInfoBox
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionState
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterList
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import eu.kanade.tachiyomi.source.novel.isLocalOrStub
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.library.service.LibraryPreferences

enum class RangeMode { Read, Unread }

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
    onAddToLibraryClicked: () -> Unit,
    onCoverClick: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onBookmarkFilterClicked: () -> Unit,
    onHighlightsClicked: (() -> Unit)? = null,
    onTrackingClicked: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (String, Boolean) -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCollectionClicked: (() -> Unit)?,
    onEditNovel: ((String, String, String, Long, List<String>) -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onMarkAllReadClicked: (() -> Unit)?,
    onMarkAllUnreadClicked: (() -> Unit)?,
    onRefreshTrackingClicked: (() -> Unit)?,
    onRemoveAllDownloadsClicked: (() -> Unit)?,
    onRemoveNonBookmarkedDownloadsClicked: (() -> Unit)?,
    onRemoveReadDownloadsClicked: (() -> Unit)?,
    onClickLinkedSources: (() -> Unit)? = null,
    onMultiBookmarkClicked: (List<NovelChapter>, Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<NovelChapter>, Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (NovelChapter) -> Unit,
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
    onOpenChapterInWebView: ((NovelChapter) -> Unit)? = null,
    onMarkPreviousChaptersAsRead: (NovelChapter, Boolean) -> Unit = { _, _ -> },
    onMarkChapterRangeAsRead: (NovelChapter, NovelChapter, Boolean) -> Unit = { _, _, _ -> },
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
            onAddToLibraryClicked = onAddToLibraryClicked,
            onCoverClick = onCoverClick,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTagSearch = onTagSearch,
            onFilterButtonClicked = onFilterButtonClicked,
            onBookmarkFilterClicked = onBookmarkFilterClicked,
            onHighlightsClicked = onHighlightsClicked,
            onTrackingClicked = onTrackingClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCollectionClicked = onEditCollectionClicked,
            onEditNovel = onEditNovel,
            onMigrateClicked = onMigrateClicked,
            onMarkAllReadClicked = onMarkAllReadClicked,
            onMarkAllUnreadClicked = onMarkAllUnreadClicked,
            onRefreshTrackingClicked = onRefreshTrackingClicked,
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
            onOpenChapterInWebView = onOpenChapterInWebView,
            onMarkPreviousChaptersAsRead = onMarkPreviousChaptersAsRead,
            onMarkChapterRangeAsRead = onMarkChapterRangeAsRead,
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
            onAddToLibraryClicked = onAddToLibraryClicked,
            onCoverClick = onCoverClick,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTagSearch = onTagSearch,
            onFilterButtonClicked = onFilterButtonClicked,
            onBookmarkFilterClicked = onBookmarkFilterClicked,
            onHighlightsClicked = onHighlightsClicked,
            onTrackingClicked = onTrackingClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCollectionClicked = onEditCollectionClicked,
            onEditNovel = onEditNovel,
            onMigrateClicked = onMigrateClicked,
            onMarkAllReadClicked = onMarkAllReadClicked,
            onMarkAllUnreadClicked = onMarkAllUnreadClicked,
            onRefreshTrackingClicked = onRefreshTrackingClicked,
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
            onOpenChapterInWebView = onOpenChapterInWebView,
            onMarkPreviousChaptersAsRead = onMarkPreviousChaptersAsRead,
            onMarkChapterRangeAsRead = onMarkChapterRangeAsRead,
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
    onAddToLibraryClicked: () -> Unit,
    onCoverClick: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onBookmarkFilterClicked: () -> Unit,
    onHighlightsClicked: (() -> Unit)? = null,
    onTrackingClicked: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (String, Boolean) -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCollectionClicked: (() -> Unit)?,
    onEditNovel: ((String, String, String, Long, List<String>) -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onMarkAllReadClicked: (() -> Unit)?,
    onMarkAllUnreadClicked: (() -> Unit)?,
    onRefreshTrackingClicked: (() -> Unit)?,
    onRemoveAllDownloadsClicked: (() -> Unit)?,
    onRemoveNonBookmarkedDownloadsClicked: (() -> Unit)?,
    onRemoveReadDownloadsClicked: (() -> Unit)?,
    onClickLinkedSources: (() -> Unit)? = null,
    onMultiBookmarkClicked: (List<NovelChapter>, Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<NovelChapter>, Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (NovelChapter) -> Unit,
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
    onOpenChapterInWebView: ((NovelChapter) -> Unit)? = null,
    onMarkPreviousChaptersAsRead: (NovelChapter, Boolean) -> Unit = { _, _ -> },
    onMarkChapterRangeAsRead: (NovelChapter, NovelChapter, Boolean) -> Unit = { _, _, _ -> },
    onDownloadBook: (() -> Unit)? = null,
    onDeleteBook: (() -> Unit)? = null,
    onHideChapter: ((NovelChapter) -> Unit)? = null,
    onUnhideChapters: (() -> Unit)? = null,
) {
    val chapterListState = rememberLazyListState()

    val chapters = remember(state) { state.processedChapters }
    val isAnySelected = remember(state) { state.isAnySelected }
    var showEditDialog by remember { mutableStateOf(false) }
    var longPressChapter by remember { mutableStateOf<NovelChapter?>(null) }
    var rangeMarkStart by remember { mutableStateOf<Pair<NovelChapter, RangeMode>?>(null) }

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
            rangeMarkStart != null -> rangeMarkStart = null
            longPressChapter != null -> longPressChapter = null
            isAnySelected -> onAllChapterSelected(false)
            else -> navigateUp()
        }
    })

    PullRefresh(
        refreshing = state.isRefreshingData,
        enabled = !isAnySelected,
        onRefresh = onRefresh,
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
                onClickRefresh = null,
                onClickMigrate = onMigrateClicked,
                onClickSettings = null,
                onClickMarkAllRead = onMarkAllReadClicked,
                onClickMarkAllUnread = onMarkAllUnreadClicked,
                onClickRefreshTracking = onRefreshTrackingClicked,
                // For book sources, hide the "remove downloads" menu items since
                // the download button already handles deletion.
                onClickRemoveAllDownloads = if (state.isBookSource) null else onRemoveAllDownloadsClicked,
                onClickRemoveNonBookmarkedDownloads = if (state.isBookSource) null else onRemoveNonBookmarkedDownloadsClicked,
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
            )
        },
        snackbarHost = { AchievementStyledSnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()

            val layoutDirection = LocalLayoutDirection.current
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
                    NovelInfoBox(
                        appBarPadding = topPadding,
                        novel = state.novel,
                        sourceName = state.source.name,
                        accentColor = state.accentColor,
                        onCoverClick = onCoverClick,
                        doSearch = onSearch,
                    )
                }

                item(
                    key = EntryScreenItem.ACTION_ROW,
                    contentType = EntryScreenItem.ACTION_ROW,
                ) {
                    NovelActionRow(
                        favorite = state.novel.favorite,
                        accentColor = state.accentColor,
                        onAddToLibraryClicked = onAddToLibraryClicked,
                        onWebViewClicked = onWebViewClicked,
                        onShareClicked = onShareClicked,
                        onHighlightsClicked = onHighlightsClicked,
                        trackingCount = state.trackingCount,
                        onTrackingClicked = onTrackingClicked,
                    )
                }

                item(
                    key = EntryScreenItem.DESCRIPTION_WITH_TAG,
                    contentType = EntryScreenItem.DESCRIPTION_WITH_TAG,
                ) {
                    ExpandableNovelDescription(
                        defaultExpandState = false,
                        description = state.novel.description,
                        tagsProvider = { state.novel.genre },
                        accentColor = state.accentColor,
                        onTagSearch = onTagSearch,
                    )
                }

                item(
                    key = EntryScreenItem.CONTINUE_BUTTON,
                    contentType = EntryScreenItem.CONTINUE_BUTTON,
                ) {
                    // For book sources (Anna's Archive) with no chapters, the
                    // Start button becomes "Download book" and triggers the
                    // EPUB download + splitting instead of opening the reader.
                    val isBookSourceEmpty = state.isBookSource && chapters.isEmpty()
                    NovelContinueButton(
                        chapterItem = state.nextContinueChapter,
                        hasReadChapters = remember(state.chapters) {
                            state.chapters.fastAny { it.chapter.read }
                        },
                        allChaptersRead = state.allChaptersRead,
                        accentColor = state.accentColor,
                        onClick = if (isBookSourceEmpty) {
                            { onDownloadBook?.invoke(); Unit }
                        } else {
                            onContinueReading
                        },
                        isBookSource = state.isBookSource,
                        isDownloadingBook = state.isBookSource && state.isRefreshingData,
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

                items(
                    items = chapters,
                    key = { it.id },
                    contentType = { EntryScreenItem.ITEM },
                ) { chapterItem ->
                    NovelChapterListItem(
                        item = chapterItem,
                        isFromSource = state.isFromSource,
                        // Hide per-chapter download indicators for book sources
                        // (Anna's Archive) — the whole book is downloaded at once.
                        downloadIndicatorEnabled = !isAnySelected && !state.source.isLocalOrStub() && !state.isBookSource,
                        date = relativeDateTimeText(chapterItem.chapter.dateUpload),
                        readProgress = chapterItem.readProgress?.let {
                            stringResource(MR.strings.novel_chapter_progress, it)
                        },
                        scanlator = chapterItem.chapter.scanlator,
                        onClick = {
                            val rangeStart = rangeMarkStart
                            if (rangeStart != null) {
                                onMarkChapterRangeAsRead(rangeStart.first, chapterItem.chapter, rangeStart.second == RangeMode.Read)
                                rangeMarkStart = null
                            } else {
                                onChapterClicked(chapterItem.chapter)
                            }
                        },
                        onLongClick = {
                            val rangeStart = rangeMarkStart
                            if (rangeStart != null) {
                                onMarkChapterRangeAsRead(rangeStart.first, chapterItem.chapter, rangeStart.second == RangeMode.Read)
                                rangeMarkStart = null
                            } else {
                                longPressChapter = chapterItem.chapter
                            }
                        },
                        onDownloadClick = if (onDownloadChapter != null) {
                            { onDownloadChapter(listOf(chapterItem), it) }
                        } else {
                            null
                        },
                        onSwipeLeft = { onChapterSwipe(chapterItem, chapterSwipeStartAction) },
                        onSwipeRight = { onChapterSwipe(chapterItem, chapterSwipeEndAction) },
                        onSelect = { selected -> onChapterSelected(chapterItem, selected, true, false) },
                        chapterSwipeStartAction = chapterSwipeStartAction,
                        chapterSwipeEndAction = chapterSwipeEndAction,
                        accentColor = state.accentColor,
                        isRangeStart = rangeMarkStart?.first?.id == chapterItem.chapter.id,
                    )
                }
            }

            val selected = chapters.filter { it.selected }
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
                    selected.firstOrNull()?.chapter?.let { onMarkPreviousAsReadClicked(it) }
                    Unit
                }.takeIf { selected.size == 1 },
                onDownloadClicked = {
                    onDownloadChapter!!(selected.toList(), NovelChapterDownloadAction.START)
                }.takeIf {
                    onDownloadChapter != null && selected.fastAny { it.downloadState != NovelDownload.State.DOWNLOADED }
                },
                onDeleteClicked = {
                    onMultiDeleteClicked(selected.map { it.chapter })
                }.takeIf { onDownloadChapter != null },
            )
        }
    }

    val longPressChapterVal = longPressChapter
    if (longPressChapterVal != null) {
        NovelChapterLongPressSheet(
            chapterTitle = longPressChapterVal.name,
            onDismiss = { longPressChapter = null },
            onOpenInWebView = {
                onOpenChapterInWebView?.invoke(longPressChapterVal)
            },
            onMarkPreviousAsRead = {
                onMarkPreviousChaptersAsRead(longPressChapterVal, true)
            },
            onMarkPreviousAsUnread = {
                onMarkPreviousChaptersAsRead(longPressChapterVal, false)
            },
            onMarkRangeAsRead = {
                rangeMarkStart = longPressChapterVal to RangeMode.Read
            },
            onMarkRangeAsUnread = {
                rangeMarkStart = longPressChapterVal to RangeMode.Unread
            },
            onHideChapter = onHideChapter?.let { { it(longPressChapterVal) } },
        )
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
    onAddToLibraryClicked: () -> Unit,
    onCoverClick: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onBookmarkFilterClicked: () -> Unit,
    onHighlightsClicked: (() -> Unit)? = null,
    onTrackingClicked: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (String, Boolean) -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCollectionClicked: (() -> Unit)?,
    onEditNovel: ((String, String, String, Long, List<String>) -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onMarkAllReadClicked: (() -> Unit)?,
    onMarkAllUnreadClicked: (() -> Unit)?,
    onRefreshTrackingClicked: (() -> Unit)?,
    onRemoveAllDownloadsClicked: (() -> Unit)?,
    onRemoveNonBookmarkedDownloadsClicked: (() -> Unit)?,
    onRemoveReadDownloadsClicked: (() -> Unit)?,
    onClickLinkedSources: (() -> Unit)? = null,
    onMultiBookmarkClicked: (List<NovelChapter>, Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<NovelChapter>, Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (NovelChapter) -> Unit,
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
    onOpenChapterInWebView: ((NovelChapter) -> Unit)? = null,
    onMarkPreviousChaptersAsRead: (NovelChapter, Boolean) -> Unit = { _, _ -> },
    onMarkChapterRangeAsRead: (NovelChapter, NovelChapter, Boolean) -> Unit = { _, _, _ -> },
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
        onAddToLibraryClicked = onAddToLibraryClicked,
        onCoverClick = onCoverClick,
        onWebViewClicked = onWebViewClicked,
        onWebViewLongClicked = onWebViewLongClicked,
        onTagSearch = onTagSearch,
        onFilterButtonClicked = onFilterButtonClicked,
        onBookmarkFilterClicked = onBookmarkFilterClicked,
        onHighlightsClicked = onHighlightsClicked,
        onTrackingClicked = onTrackingClicked,
        onRefresh = onRefresh,
        onContinueReading = onContinueReading,
        onSearch = onSearch,
        onShareClicked = onShareClicked,
        onDownloadActionClicked = onDownloadActionClicked,
        onEditCollectionClicked = onEditCollectionClicked,
        onEditNovel = onEditNovel,
        onMigrateClicked = onMigrateClicked,
        onMarkAllReadClicked = onMarkAllReadClicked,
        onMarkAllUnreadClicked = onMarkAllUnreadClicked,
        onRefreshTrackingClicked = onRefreshTrackingClicked,
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
        onOpenChapterInWebView = onOpenChapterInWebView,
        onMarkPreviousChaptersAsRead = onMarkPreviousChaptersAsRead,
        onMarkChapterRangeAsRead = onMarkChapterRangeAsRead,
        onDownloadBook = onDownloadBook,
        onDeleteBook = onDeleteBook,
        onHideChapter = onHideChapter,
        onUnhideChapters = onUnhideChapters,
    )
}
