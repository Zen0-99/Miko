package eu.kanade.presentation.entries.novel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.components.fullDateText
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.EntryScreenItem
import eu.kanade.presentation.entries.components.ChapterGroupPillRow
import eu.kanade.presentation.entries.components.EnhancedEntryInfoBox
import eu.kanade.presentation.entries.components.EntryChapterCarouselCard
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.EntryGenreRow
import eu.kanade.presentation.entries.components.EntryToolbar
import eu.kanade.presentation.entries.components.RatingMark
import eu.kanade.presentation.entries.components.VolumeSectionHeader
import eu.kanade.presentation.entries.components.chapterCountPair
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
import eu.kanade.presentation.entries.components.EntryContinueRow
import eu.kanade.presentation.entries.components.EntryNextUpAction
import eu.kanade.presentation.entries.components.EntryFindProviderButton
import eu.kanade.presentation.entries.components.EntryViewCommentsButton
import tachiyomi.i18n.aniyomi.AYMR
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.source.novel.isLocalOrStub
import eu.kanade.tachiyomi.ui.entries.novel.NovelChapterList
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import eu.kanade.tachiyomi.util.EntryDescriptionParser
import eu.kanade.tachiyomi.util.chapter.ChapterNameGroup
import eu.kanade.presentation.util.formatChapterNumber
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
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
    onDownloadChapter: ((List<NovelChapterList.Item>, NovelChapterDownloadAction, String?) -> Unit)?,
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
    /** Find Content Provider (17-02) — replaces the comments button on
     *  canonical entries with zero bound carriers. */
    onFindProviderClicked: (() -> Unit)? = null,
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
    /** Artwork the caller already shows for this entry (home hero) —
     *  underlays the shared hero image so the morph never draws empty. */
    previewCover: Any? = null,
) {
    Box {
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
            onFindProviderClicked = onFindProviderClicked,
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
            previewCover = previewCover,
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
            onFindProviderClicked = onFindProviderClicked,
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
            previewCover = previewCover,
        )
    }

        // Mint veil — a canonical mint is running on-device; the entry
        // redirects to its `miko:` row when it lands. A dim scrim +
        // elevated progress dot, no copy: the transition itself carries
        // the meaning.
        if (state.isMinting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                ) {
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }
        }
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
    onDownloadChapter: ((List<NovelChapterList.Item>, NovelChapterDownloadAction, String?) -> Unit)?,
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
    /** Find Content Provider (17-02) — replaces the comments button on
     *  canonical entries with zero bound carriers. */
    onFindProviderClicked: (() -> Unit)? = null,
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
    previewCover: Any? = null,
) {
    val chapterListState = rememberLazyListState()

    val chapters = remember(state) { state.processedChapters }
    val isAnySelected = remember(state) { state.isAnySelected }

    // Provider-stat/alt-title noise lives inside scraped descriptions —
    // parse once: rating feeds the info-box star, alt titles their own
    // row, the cleaned text is what the description shows.
    val parsedDescription = remember(state.novel.description) {
        EntryDescriptionParser.parse(state.novel.description)
    }

    // Chapter-group pills — "Volume N"/"Book N"/etc. parsed from chapter
    // names. All-or-nothing: any unmarked chapter disables the row.
    val ungroupedChaptersLabel = stringResource(MR.strings.chapters)
    val markedChapterGroups = remember(chapters) {
        ChapterNameGroup.computeGroups(
            chapters.map { it.chapter.name },
        )
    }
    // Canonical volume boundaries (21-03) supply the groups when name
    // markers don't — one or the other, never double-grouped.
    val volumeGrouping = remember(chapters, state.volumes, ungroupedChaptersLabel) {
        if (markedChapterGroups.size <= 1) {
            state.volumes?.grouping(
                chapters.map { it.chapter.chapterNumber },
                ungroupedChaptersLabel,
            )
        } else {
            null
        }
    }
    val chapterGroups = volumeGrouping?.groups ?: markedChapterGroups
    val chapterGroupLabel = { chapter: NovelChapter ->
        if (volumeGrouping != null) {
            volumeGrouping.chapterToGroup[chapter.chapterNumber] ?: ungroupedChaptersLabel
        } else {
            ChapterNameGroup.parse(chapter.name)
        }
    }
    // Reading position is progress-derived — the group holding the newest
    // unread chapter; falls back to the newest group. Pill taps are
    // browse-only session state: they must not persist as "current" or
    // re-entering the entry would land on the wrong group.
    val progressChapterGroup = remember(chapters, chapterGroups) {
        chapters
            .firstOrNull { !it.chapter.read }
            ?.let { chapterGroupLabel(it.chapter) }
            ?.takeIf { it in chapterGroups }
    }

    // Groups whose chapters are all read — shown with a checkmark pill.
    val completedChapterGroups = remember(chapters, chapterGroups) {
        if (chapterGroups.size <= 1) {
            emptySet()
        } else {
            chapters
                .groupBy { chapterGroupLabel(it.chapter) ?: ungroupedChaptersLabel }
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
        ?: chapterGroups.lastOrNull { it != ungroupedChaptersLabel }
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
                (chapterGroupLabel(item.chapter) ?: ungroupedChaptersLabel) == selectedChapterGroup
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
    // The bar renders but is transparent outside selection — still keep
    // the indicator/hero clear of the status bar.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    PullRefresh(
        refreshing = state.isRefreshingData,
        enabled = !isAnySelected,
        onRefresh = onRefresh,
        indicatorPadding = PaddingValues(top = statusBarTop),
        state = pullState,
    ) {
        Scaffold(
            // The bar stays put (back/download/menu always reachable) but the
            // scroll-reveal is gone — title and background are pinned transparent
            // outside multi-select; action mode forces both to full alpha itself.
            topBar = {
                val selectedChapterCount = remember(chapters) {
                    chapters.count { it.selected }
                }
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
                    titleAlphaProvider = { 0f },
                    backgroundAlphaProvider = { 0f },
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
                        onDownloadChapter!!(selected.toList(), NovelChapterDownloadAction.START, null)
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
                        appBarPadding = maxOf(topPadding, statusBarTop),
                        title = state.novel.title,
                        backdrop = state.novel.asNovelCover(),
                        sharedKey = "hero-image-${state.novel.id}",
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
                        rating = parsedDescription.rating,
                        ratingMark = RatingMark.STAR,
                        onCoverClick = onCoverClick,
                        // Portrait covers — taller hero, no horizontal pan.
                        heroContentPadding = 440.dp,
                        overscrollPullPx = heroOverscrollPx,
                        parallaxOffsetPx = heroParallaxPx,
                        accentColor = state.accentColor,
                        previewCover = previewCover,
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
                        val nextChapterItem = state.nextContinueChapter
                        val nextChapter = nextChapterItem?.chapter
                        val hasStarted = state.chapters.fastAny { it.chapter.read } ||
                            (nextChapterItem?.readProgress ?: 0) > 0
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
                            else -> listOf(
                                EntryNextUpAction(
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                    label = if (nextChapter != null && hasStarted) {
                                        stringResource(
                                            MR.strings.action_continue_named,
                                            nextChapter.name,
                                        )
                                    } else {
                                        stringResource(MR.strings.action_start)
                                    },
                                    onClickLabel = stringResource(MR.strings.action_resume),
                                    onClick = onContinueReading,
                                ),
                            )
                        }
                        EntryContinueRow(
                            sharedKey = "hero-resume-${state.novel.id}",
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
                        description = parsedDescription.description,
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
                    val (logicalChapters, rawChapters) = chapterCountPair(
                        chapters.map { it.chapter.chapterNumber },
                    )
                    NovelChapterHeader(
                        itemCount = logicalChapters,
                        entryCountText = if (rawChapters != logicalChapters) {
                            pluralStringResource(MR.plurals.num_entries, count = rawChapters, rawChapters)
                        } else {
                            null
                        },
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

                if (volumeGrouping != null && selectedChapterGroup != ungroupedChaptersLabel) {
                    item(
                        key = "volume-section-header",
                        contentType = EntryScreenItem.ITEM,
                    ) {
                        val range = filteredChapters.map { it.chapter.chapterNumber }
                        VolumeSectionHeader(
                            label = selectedChapterGroup,
                            meta = volumeGrouping.meta[selectedChapterGroup],
                            firstChapter = range.minOrNull(),
                            lastChapter = range.maxOrNull(),
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
                        // The same chapter carries a thin accent outline.
                        val nextChapterId = filteredChapters
                            .filter { !it.chapter.read }
                            .minByOrNull { it.chapter.chapterNumber }
                            ?.chapter?.id
                        // Cards are 75% of screen width — a negative
                        // initial offset lands the next-up chapter centered
                        // instead of pinned to the start edge.
                        val centeredOffsetPx = with(LocalDensity.current) {
                            (16.dp - LocalConfiguration.current.screenWidthDp.dp * 0.125f).roundToPx()
                        }
                        val rowState = rememberLazyListState(
                            initialFirstVisibleItemIndex = nextIndex,
                            initialFirstVisibleItemScrollOffset = centeredOffsetPx,
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
                                // Per-chapter indicators stay hidden for book
                                // sources — the whole book downloads at once.
                                // Canon rows download through their carriers,
                                // so the stub source is no reason to hide the
                                // download control; audio render stays gated on
                                // a real source (it isn't canon-aware yet).
                                val isCanonChapter = eu.kanade.tachiyomi.data.mikodb
                                    .CanonicalChapterRouter.isCanonUrl(chapterItem.chapter.url)
                                val audioIndicatorEnabled =
                                    !state.source.isLocalOrStub() && !state.isBookSource
                                val downloadIndicatorEnabled = !state.isBookSource &&
                                    (!state.source.isLocalOrStub() || isCanonChapter)
                                EntryChapterCarouselCard(
                                    title = chapterItem.chapter.name,
                                    dateUpload = chapterItem.chapter.dateUpload,
                                    scanlator = chapterItem.chapter.scanlator?.takeIf { it.isNotBlank() },
                                    progress = chapterItem.readProgress?.let {
                                        stringResource(MR.strings.novel_chapter_progress, it)
                                    },
                                    read = chapterItem.chapter.read,
                                    bookmark = chapterItem.chapter.bookmark,
                                    selected = chapterItem.selected,
                                    isNext = chapterItem.chapter.id == nextChapterId,
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
                                        if (audioIndicatorEnabled &&
                                            chapterItem.downloadState == NovelDownload.State.DOWNLOADED
                                        ) {
                                            NovelChapterAudioIndicator(
                                                enabled = audioIndicatorEnabled,
                                                audioStateProvider = { chapterItem.audioState },
                                                audioProgressProvider = { chapterItem.audioProgress },
                                                onClick = { onAudioChapter?.invoke(listOf(chapterItem), it) },
                                            )
                                        }
                                        if (downloadIndicatorEnabled) {
                                            NovelChapterDownloadIndicator(
                                                enabled = downloadIndicatorEnabled,
                                                downloadStateProvider = { chapterItem.downloadState },
                                                downloadProgressProvider = { chapterItem.downloadProgress },
                                                onClick = {
                                                    onDownloadChapter?.invoke(listOf(chapterItem), it, null)
                                                },
                                                downloadCarriers = if (isCanonChapter) {
                                                    state.carrierMatrix?.readable
                                                        ?.map { it.pkgName to it.displayName }
                                                } else {
                                                    null
                                                },
                                                onCarrierPick = { pkg ->
                                                    onDownloadChapter?.invoke(
                                                        listOf(chapterItem),
                                                        NovelChapterDownloadAction.START,
                                                        pkg,
                                                    )
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // The action slot under the chapter cards. Priority:
                // Find Content Provider on zero-carrier canonical
                // entries (17-02) → a spinner while bound carriers are
                // still expanding into chapter rows → comments, which
                // only renders when chapters actually exist.
                // No *usable* carrier — bound-but-uninstalled counts too
                // (dead-source mints bind the dead pkg) — the slot swaps
                // to Find Content Provider; an enabled carrier with no
                // rows yet is still expanding → spinner.
                val hasNoCarriers = state.carrierMatrix
                    ?.let { it.fetched && it.readable.isEmpty() } == true
                val chaptersLoading = state.carrierMatrix
                    ?.let { it.fetched && it.readable.isNotEmpty() } == true &&
                    filteredChapters.isEmpty()
                if (hasNoCarriers && onFindProviderClicked != null) {
                    item(
                        key = "chapter-find-provider-button",
                        contentType = EntryScreenItem.ITEM,
                    ) {
                        EntryFindProviderButton(
                            accentColor = state.accentColor,
                            onClick = onFindProviderClicked,
                        )
                    }
                } else if (chaptersLoading) {
                    item(
                        key = "chapter-loading",
                        contentType = EntryScreenItem.ITEM,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = state.accentColor ?: MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else if (onCommentsClicked != null && filteredChapters.isNotEmpty()) {
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
    onDownloadChapter: ((List<NovelChapterList.Item>, NovelChapterDownloadAction, String?) -> Unit)?,
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
    /** Find Content Provider (17-02) — replaces the comments button on
     *  canonical entries with zero bound carriers. */
    onFindProviderClicked: (() -> Unit)? = null,
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
    previewCover: Any? = null,
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
        onFindProviderClicked = onFindProviderClicked,
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
        previewCover = previewCover,
    )
}
