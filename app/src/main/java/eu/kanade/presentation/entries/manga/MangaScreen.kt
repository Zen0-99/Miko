package eu.kanade.presentation.entries.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.components.fullDateText
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.EntryScreenItem
import eu.kanade.presentation.entries.components.ChapterGroupPillRow
import eu.kanade.presentation.entries.components.EntryChapterCarouselCard
import eu.kanade.presentation.entries.components.EntryContinueRow
import eu.kanade.presentation.entries.components.EntryFindProviderButton
import eu.kanade.presentation.entries.components.EntryViewCommentsButton
import eu.kanade.presentation.entries.components.EntryNextUpAction
import eu.kanade.presentation.entries.components.EnhancedEntryInfoBox
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.EntryChapterHeader
import eu.kanade.presentation.entries.components.chapterCountPair
import eu.kanade.presentation.entries.components.RatingMark
import eu.kanade.presentation.entries.components.EntryGenreRow
import eu.kanade.presentation.entries.components.EntryToolbar
import eu.kanade.presentation.entries.components.MissingItemCountListItem
import eu.kanade.presentation.entries.components.enhancedEntryStatusText
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.presentation.entries.manga.components.ExpandableMangaDescription
import eu.kanade.presentation.entries.manga.components.ChapterDownloadIndicator
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
import eu.kanade.tachiyomi.source.manga.getNameForMangaInfo
import eu.kanade.tachiyomi.ui.browse.manga.extension.details.MangaSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel
import eu.kanade.tachiyomi.util.EntryDescriptionParser
import eu.kanade.tachiyomi.util.chapter.ChapterNameGroup
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.api.get
import java.time.Instant

@Composable
fun MangaScreen(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction, String?) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    onCommentsClicked: (() -> Unit)? = null,
    /** Find Content Provider (17-02) — replaces the comments button on
     *  canonical entries with zero bound carriers. */
    onFindProviderClicked: (() -> Unit)? = null,

    // For tags menu
    onTagSearch: (String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCollectionClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter, Boolean) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For chapter swipe
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // For overflow menu
    onMarkAllReadClicked: (() -> Unit)? = null,
    onMarkAllUnreadClicked: (() -> Unit)? = null,
    onRemoveAllDownloadsClicked: (() -> Unit)? = null,
    onRemoveNonBookmarkedDownloadsClicked: (() -> Unit)? = null,
    onRemoveReadDownloadsClicked: (() -> Unit)? = null,
    onSuggestionClick: (SuggestionItem) -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onRetrySuggestions: () -> Unit = {},
    /** Canonical carrier chip (16-04) — opens the preferred-source
     *  picker on canonical entries. */
    onCarrierPickerClicked: (() -> Unit)? = null,
    /** Per-row carrier label resolver + chapter-scoped override tap
     *  (16-04). Null on non-canonical entries. */
    chapterCarrierLabel: (suspend (Chapter) -> String?)? = null,
    onChapterCarrierClicked: ((Chapter) -> Unit)? = null,
    /** Artwork the caller already shows for this entry (home hero) —
     *  underlays the shared hero image so the morph never draws empty. */
    previewCover: Any? = null,
) {
    val context = LocalContext.current
    val onCopyTagToClipboard: (tag: String) -> Unit = {
        if (it.isNotEmpty()) {
            context.copyToClipboard(it, it)
        }
    }

    val navigator = LocalNavigator.currentOrThrow
    val onSettingsClicked: (() -> Unit)? = {
        navigator.push(MangaSourcePreferencesScreen(state.source.id))
    }.takeIf { state.source is ConfigurableSource }

    Box {
        if (!isTabletUi) {
            MangaScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            navigateUp = navigateUp,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onTrackingClicked = onTrackingClicked,
            onCommentsClicked = onCommentsClicked,
            onFindProviderClicked = onFindProviderClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCollectionClicked = onEditCollectionClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            onSettingsClicked = onSettingsClicked,
            onMarkAllReadClicked = onMarkAllReadClicked,
            onMarkAllUnreadClicked = onMarkAllUnreadClicked,
            onRemoveAllDownloadsClicked = onRemoveAllDownloadsClicked,
            onRemoveNonBookmarkedDownloadsClicked = onRemoveNonBookmarkedDownloadsClicked,
            onRemoveReadDownloadsClicked = onRemoveReadDownloadsClicked,
            onSuggestionClick = onSuggestionClick,
            onOpenSuggestions = onOpenSuggestions,
            onRetrySuggestions = onRetrySuggestions,
            onCarrierPickerClicked = onCarrierPickerClicked,
            chapterCarrierLabel = chapterCarrierLabel,
            onChapterCarrierClicked = onChapterCarrierClicked,
            previewCover = previewCover,
        )
    } else {
        MangaScreenLargeImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            nextUpdate = nextUpdate,
            navigateUp = navigateUp,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onTrackingClicked = onTrackingClicked,
            onCommentsClicked = onCommentsClicked,
            onFindProviderClicked = onFindProviderClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCollectionClicked = onEditCollectionClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            onSettingsClicked = onSettingsClicked,
            onMarkAllReadClicked = onMarkAllReadClicked,
            onMarkAllUnreadClicked = onMarkAllUnreadClicked,
            onRemoveAllDownloadsClicked = onRemoveAllDownloadsClicked,
            onRemoveNonBookmarkedDownloadsClicked = onRemoveNonBookmarkedDownloadsClicked,
            onRemoveReadDownloadsClicked = onRemoveReadDownloadsClicked,
            onSuggestionClick = onSuggestionClick,
            onOpenSuggestions = onOpenSuggestions,
            onRetrySuggestions = onRetrySuggestions,
            onCarrierPickerClicked = onCarrierPickerClicked,
            chapterCarrierLabel = chapterCarrierLabel,
            onChapterCarrierClicked = onChapterCarrierClicked,
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
private fun MangaScreenSmallImpl(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction, String?) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    onCommentsClicked: (() -> Unit)? = null,
    /** Find Content Provider (17-02) — replaces the comments button on
     *  canonical entries with zero bound carriers. */
    onFindProviderClicked: (() -> Unit)? = null,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCollectionClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onSettingsClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter, Boolean) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For chapter swipe
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // For overflow menu
    onMarkAllReadClicked: (() -> Unit)? = null,
    onMarkAllUnreadClicked: (() -> Unit)? = null,
    onRemoveAllDownloadsClicked: (() -> Unit)? = null,
    onRemoveNonBookmarkedDownloadsClicked: (() -> Unit)? = null,
    onRemoveReadDownloadsClicked: (() -> Unit)? = null,
    onSuggestionClick: (SuggestionItem) -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onRetrySuggestions: () -> Unit = {},
    /** Canonical carrier chip (16-04) — opens the preferred-source
     *  picker on canonical entries. */
    onCarrierPickerClicked: (() -> Unit)? = null,
    /** Per-row carrier label resolver + chapter-scoped override tap
     *  (16-04). Null on non-canonical entries. */
    chapterCarrierLabel: (suspend (Chapter) -> String?)? = null,
    onChapterCarrierClicked: ((Chapter) -> Unit)? = null,
    previewCover: Any? = null,
) {
    val chapterListState = rememberLazyListState()

    // Logical vs raw counts — sub-part numbering (1.1/1.2, "Part N")
    // collapses to whole chapters while the entry list counts rows.
    val (chapterCountText, entryCountText) = run {
        val (logical, raw) = chapterCountPair(
            state.processedChapters.map { it.chapter.chapterNumber },
        )
        pluralStringResource(MR.plurals.manga_num_chapters, count = logical, logical) to
            if (raw != logical) {
                pluralStringResource(MR.plurals.num_entries, count = raw, raw)
            } else {
                null
            }
    }

    val (chapters, listItem, isAnySelected) = remember(state) {
        Triple(
            first = state.processedChapters,
            second = state.chapterListItems,
            third = state.isAnySelected,
        )
    }

    // Provider-stat/alt-title noise lives inside scraped descriptions —
    // parse once: rating feeds the info-box star, alt titles their own
    // row, the cleaned text is what the description shows.
    val parsedDescription = remember(state.manga.description) {
        EntryDescriptionParser.parse(state.manga.description)
    }

    // Chapter-group pills — "Volume N"/"Book N"/etc. parsed from chapter
    // names. All-or-nothing: any unmarked chapter disables the row.
    val ungroupedChaptersLabel = stringResource(MR.strings.chapters)
    val chapterGroups = remember(listItem) {
        ChapterNameGroup.computeGroups(
            listItem.mapNotNull { (it as? ChapterList.Item)?.chapter?.name },
        )
    }
    // Reading position is progress-derived — the group holding the newest
    // unread chapter; falls back to the newest group. Pill taps are
    // browse-only session state: they must not persist as "current" or
    // re-entering the entry would land on the wrong group.
    val progressChapterGroup = remember(listItem, chapterGroups) {
        listItem
            .firstOrNull { it is ChapterList.Item && !it.chapter.read }
            ?.let { ChapterNameGroup.parse((it as ChapterList.Item).chapter.name) }
            ?.takeIf { it in chapterGroups }
    }

    // Groups whose chapters are all read — shown with a checkmark pill.
    val completedChapterGroups = remember(listItem, chapterGroups) {
        if (chapterGroups.size <= 1) {
            emptySet()
        } else {
            listItem.mapNotNull { (it as? ChapterList.Item)?.chapter }
                .groupBy { ChapterNameGroup.parse(it.name) ?: ungroupedChaptersLabel }
                .filterValues { chs -> chs.all { it.read } }
                .keys
        }
    }

    // null = follow progress (auto-advances as chapters get read); a pill
    // tap sets a browse override so the user can look at other groups
    // without changing their reading position.
    var chapterGroupBrowseOverride by remember(state.manga.id) {
        mutableStateOf<String?>(null)
    }
    // Once a group is being displayed it stays displayed for the session:
    // finishing it (progress advancing to the next group) must not yank the
    // viewed group away mid-browse. Re-entering resets.
    var pinnedChapterGroup by remember(state.manga.id) { mutableStateOf<String?>(null) }
    var lastProgressGroup by remember(state.manga.id) { mutableStateOf<String?>(null) }
    var progressGroupSeen by remember(state.manga.id) { mutableStateOf(false) }
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
    val filteredChapterListItem = remember(listItem, selectedChapterGroup) {
        if (chapterGroups.size <= 1) {
            listItem
        } else {
            listItem.filter { item ->
                item !is ChapterList.Item ||
                    (ChapterNameGroup.parse(item.chapter.name) ?: ungroupedChaptersLabel) == selectedChapterGroup
            }
        }
    }

    val carrierMatrix = state.carrierMatrix
    val carrierNote = carrierMatrix
        ?.takeIf { it.fetched && it.readable.isEmpty() }
        ?.let {
            if (it.installable.isNotEmpty()) {
                stringResource(MR.strings.carrier_install_short)
            } else {
                stringResource(MR.strings.carrier_unavailable)
            }
        }
    val carrierLabel = carrierMatrix
        ?.takeIf { it.fetched && it.carriers.isNotEmpty() }
        ?.let { m ->
            state.preferredCarrier
                ?.let { pkg -> m.carriers.firstOrNull { it.pkgName == pkg }?.displayName }
                ?: stringResource(MR.strings.carrier_auto)
        }
    val carrierFetched = carrierMatrix?.fetched == true
    val carrierReadable = carrierMatrix?.readable.orEmpty().size
    val rowCarrierProvider = if (carrierFetched && carrierReadable > 0) {
        chapterCarrierLabel
    } else {
        null
    }
    val rowCarrierClick = if (carrierFetched && carrierReadable > 1) {
        onChapterCarrierClicked
    } else {
        null
    }
    // No *usable* carrier on a fetched canonical entry — bound-but-
    // uninstalled carriers count too (a dead-source mint binds the dead
    // pkg) — the comments slot swaps to Find Content Provider (17-02).
    val hasNoCarriers = carrierMatrix?.let { it.fetched && it.readable.isEmpty() } == true
    // Enabled carriers but no chapter rows yet — the extension fetch is
    // still expanding; the slot shows a spinner. An empty readable set
    // can't expand — that's the Find Provider state, not loading.
    val chaptersLoading = carrierMatrix?.let { it.fetched && it.readable.isNotEmpty() } == true &&
        listItem.none { it is ChapterList.Item }

    BackHandler(onBack = {
        if (isAnySelected) {
            onAllChapterSelected(false)
        } else {
            navigateUp()
        }
    })

    Scaffold(
        // The bar stays put (back/download/menu always reachable) but the
        // scroll-reveal is gone — title and background are pinned transparent
        // outside multi-select; action mode forces both to full alpha itself.
        topBar = {
            val selectedChapterCount: Int = remember(chapters) {
                chapters.count { it.selected }
            }
            EntryToolbar(
                title = state.manga.title,
                hasFilters = state.filterActive,
                navigateUp = navigateUp,
                onClickFilter = null,
                onClickShare = onShareClicked,
                onClickDownload = onDownloadActionClicked,
                onClickEditCollection = onEditCollectionClicked,
                onClickMigrate = onMigrateClicked,
                onClickSettings = onSettingsClicked,
                changeAnimeSkipIntro = null,
                actionModeCounter = selectedChapterCount,
                onCancelActionMode = { onAllChapterSelected(false) },
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = { onInvertSelection() },
                titleAlphaProvider = { 0f },
                backgroundAlphaProvider = { 0f },
                isManga = true,
                toolbarBackgroundColor = MaterialTheme.colorScheme.background,
                onClickRemoveAllDownloads = onRemoveAllDownloadsClicked,
                onClickRemoveNonBookmarkedDownloads = onRemoveNonBookmarkedDownloadsClicked,
                onClickRemoveReadDownloads = onRemoveReadDownloadsClicked,
                onClickWebView = onWebViewClicked,
            )
        },
        bottomBar = {
            val selectedChapters = remember(chapters) {
                chapters.filter { it.selected }
            }
            SharedMangaBottomActionMenu(
                selected = selectedChapters,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                onDownloadChapter = onDownloadChapter,
                onMultiDeleteClicked = onMultiDeleteClicked,
                fillFraction = 1f,
            )
        },
        snackbarHost = { AchievementStyledSnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()
        // The bar renders but is transparent outside selection — still keep
        // the indicator/hero clear of the status bar.
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        val pullState = rememberPullToRefreshState()
        // Pull distance in px — drives the Nuvio-style hero zoom and tracks
        // the finger via the pull-to-refresh gesture itself.
        val heroOverscrollPx = pullState.distanceFraction *
            with(LocalDensity.current) { 80.dp.toPx() }
        PullRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            enabled = !isAnySelected,
            indicatorPadding = PaddingValues(top = maxOf(topPadding, statusBarTop)),
            state = pullState,
        ) {
            val layoutDirection = LocalLayoutDirection.current
            val heroParallaxPx = if (chapterListState.firstVisibleItemIndex == 0) {
                chapterListState.firstVisibleItemScrollOffset.toFloat()
            } else {
                0f
            }
            VerticalFastScroller(
                listState = chapterListState,
                thumbAllowed = { false },
                topContentPadding = topPadding,
                endContentPadding = contentPadding.calculateEndPadding(layoutDirection),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight(),
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
                            title = state.manga.title,
                            backdrop = state.manga,
                            sharedKey = "hero-image-${state.manga.id}",
                            logoUrl = null,
                            metaItems = listOfNotNull(
                                state.manga.author?.takeIf { it.isNotBlank() },
                                enhancedEntryStatusText(state.manga.status),
                                remember {
                                    if (CanonicalCatalog.workIdFromMangaUrl(state.manga.url) != null) {
                                        "MikoDB"
                                    } else {
                                        state.source.getNameForMangaInfo()
                                    }
                                },
                            ),
                            rating = parsedDescription.rating,
                            ratingMark = RatingMark.STAR,
                            onCoverClick = onCoverClicked,
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
                        Column {
                            val nextChapter = remember(chapters) {
                                // Lowest-numbered unread — the "next in
                                // line" chapter regardless of the display
                                // sort direction (firstOrNull on a
                                // descending list would return the newest
                                // unread chapter, not the next one).
                                chapters
                                    .filter { !it.chapter.read }
                                    .minByOrNull { it.chapter.chapterNumber }
                                    ?.chapter
                            }
                            val hasReadChapters = remember(state.chapters) {
                                state.chapters.fastAny { it.chapter.read }
                            }
                            val hasStarted = hasReadChapters ||
                                (nextChapter?.lastPageRead ?: 0L) > 0
                            val allRead = chapters.isNotEmpty() &&
                                chapters.fastAll { it.chapter.read }
                            EntryContinueRow(
                                sharedKey = "hero-resume-${state.manga.id}",
                                actions = listOf(
                                    EntryNextUpAction(
                                        icon = if (allRead) {
                                            Icons.Filled.CheckCircle
                                        } else {
                                            Icons.AutoMirrored.Filled.MenuBook
                                        },
                                        label = when {
                                            allRead -> stringResource(
                                                AYMR.strings.fetching_overlay_all_up_to_date,
                                            )
                                            nextChapter != null && hasStarted ->
                                                stringResource(
                                                    MR.strings.action_continue_named,
                                                    if (state.manga.displayMode ==
                                                        Manga.CHAPTER_DISPLAY_NUMBER
                                                    ) {
                                                        stringResource(
                                                            MR.strings.display_mode_chapter,
                                                            formatChapterNumber(
                                                                nextChapter.chapterNumber,
                                                            ),
                                                        )
                                                    } else {
                                                        nextChapter.name
                                                    },
                                                )
                                            else -> stringResource(MR.strings.action_start)
                                        },
                                        onClickLabel = stringResource(MR.strings.action_resume),
                                        onClick = onContinueReading,
                                    ),
                                ),
                                actionsEnabled = !allRead,
                                accentColor = state.accentColor,
                                favorite = state.manga.favorite,
                                allDone = allRead,
                                favoriteContentDescription = stringResource(
                                    if (state.manga.favorite) {
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
                                pills = state.manga.genre.orEmpty(),
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
                        ExpandableMangaDescription(
                            defaultExpandState = false,
                            description = parsedDescription.description,
                            // Genres live in the pill scroll row — no pills
                            // inside the description.
                            tagsProvider = { null },
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                            accentColor = state.accentColor,
                        )
                    }

                    // Similar titles — temporarily disabled
//                    item(key = "SUGGESTIONS", contentType = "SUGGESTIONS") {
//                        AuroraSuggestionsRow(
//                            state = state.suggestions,
//                            onSuggestionClick = onSuggestionClick,
//                            onOpenSuggestions = onOpenSuggestions,
//                            onRetryClick = onRetrySuggestions,
//                            modifier = Modifier.fillMaxWidth(),
//                            accentColor = state.accentColor,
//                        )
//                    }

                    item(
                        key = EntryScreenItem.ITEM_HEADER,
                        contentType = EntryScreenItem.ITEM_HEADER,
                    ) {
                        EntryChapterHeader(
                            itemCountText = chapterCountText,
                            entryCountText = entryCountText,
                            onClick = onFilterClicked,
                            accentColor = state.accentColor,
                            intervalDays = state.intervalDays,
                            showInterval = state.showInterval,
                            carrierLabel = carrierLabel,
                            onCarrierClick = onCarrierPickerClicked,
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

                    sharedChapterItems(
                        manga = state.manga,
                        chapters = filteredChapterListItem,
                        isAnyChapterSelected = chapters.fastAny { it.selected },
                        onChapterClicked = onChapterClicked,
                        onDownloadChapter = onDownloadChapter,
                        onChapterSelected = onChapterSelected,
                        accentColor = state.accentColor,
                        commentsCount = state.commentsCount,
                        onCommentsClicked = onCommentsClicked,
                        hasNoCarriers = hasNoCarriers,
                        onFindProviderClicked = onFindProviderClicked,
                        chaptersLoading = chaptersLoading,
                        carrierNote = carrierNote,
                        carrierNoteWarning = true,
                        carrierLabelProvider = rowCarrierProvider,
                        carrierLabelVersion = state.chapterCarriers to state.preferredCarrier,
                        onCarrierLabelClick = rowCarrierClick,
                        downloadCarriers = carrierMatrix?.readable
                            ?.map { it.pkgName to it.displayName },
                    )
                }
            }
        }
    }
}

@Composable
fun MangaScreenLargeImpl(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction, String?) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    onCommentsClicked: (() -> Unit)? = null,
    /** Find Content Provider (17-02) — replaces the comments button on
     *  canonical entries with zero bound carriers. */
    onFindProviderClicked: (() -> Unit)? = null,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCollectionClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onSettingsClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter, Boolean) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For swipe actions
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // For overflow menu
    onMarkAllReadClicked: (() -> Unit)? = null,
    onMarkAllUnreadClicked: (() -> Unit)? = null,
    onRemoveAllDownloadsClicked: (() -> Unit)? = null,
    onRemoveNonBookmarkedDownloadsClicked: (() -> Unit)? = null,
    onRemoveReadDownloadsClicked: (() -> Unit)? = null,
    onSuggestionClick: (SuggestionItem) -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onRetrySuggestions: () -> Unit = {},
    /** Canonical carrier chip (16-04) — opens the preferred-source
     *  picker on canonical entries. */
    onCarrierPickerClicked: (() -> Unit)? = null,
    /** Per-row carrier label resolver + chapter-scoped override tap
     *  (16-04). Null on non-canonical entries. */
    chapterCarrierLabel: (suspend (Chapter) -> String?)? = null,
    onChapterCarrierClicked: ((Chapter) -> Unit)? = null,
    previewCover: Any? = null,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val (chapters, listItem, isAnySelected) = remember(state) {
        Triple(
            first = state.processedChapters,
            second = state.chapterListItems,
            third = state.isAnySelected,
        )
    }

    // Logical vs raw counts — sub-part numbering (1.1/1.2, "Part N")
    // collapses to whole chapters while the entry list counts rows.
    val (chapterCountText, entryCountText) = run {
        val (logical, raw) = chapterCountPair(
            state.processedChapters.map { it.chapter.chapterNumber },
        )
        pluralStringResource(MR.plurals.manga_num_chapters, count = logical, logical) to
            if (raw != logical) {
                pluralStringResource(MR.plurals.num_entries, count = raw, raw)
            } else {
                null
            }
    }

    // Provider-stat/alt-title noise lives inside scraped descriptions —
    // parse once: rating feeds the info-box star, alt titles their own
    // row, the cleaned text is what the description shows.
    val parsedDescription = remember(state.manga.description) {
        EntryDescriptionParser.parse(state.manga.description)
    }

    // Chapter-group pills — "Volume N"/"Book N"/etc. parsed from chapter
    // names. All-or-nothing: any unmarked chapter disables the row.
    val ungroupedChaptersLabel = stringResource(MR.strings.chapters)
    val chapterGroups = remember(listItem) {
        ChapterNameGroup.computeGroups(
            listItem.mapNotNull { (it as? ChapterList.Item)?.chapter?.name },
        )
    }
    // Reading position is progress-derived — the group holding the newest
    // unread chapter; falls back to the newest group. Pill taps are
    // browse-only session state: they must not persist as "current" or
    // re-entering the entry would land on the wrong group.
    val progressChapterGroup = remember(listItem, chapterGroups) {
        listItem
            .firstOrNull { it is ChapterList.Item && !it.chapter.read }
            ?.let { ChapterNameGroup.parse((it as ChapterList.Item).chapter.name) }
            ?.takeIf { it in chapterGroups }
    }

    // Groups whose chapters are all read — shown with a checkmark pill.
    val completedChapterGroups = remember(listItem, chapterGroups) {
        if (chapterGroups.size <= 1) {
            emptySet()
        } else {
            listItem.mapNotNull { (it as? ChapterList.Item)?.chapter }
                .groupBy { ChapterNameGroup.parse(it.name) ?: ungroupedChaptersLabel }
                .filterValues { chs -> chs.all { it.read } }
                .keys
        }
    }

    // null = follow progress (auto-advances as chapters get read); a pill
    // tap sets a browse override so the user can look at other groups
    // without changing their reading position.
    var chapterGroupBrowseOverride by remember(state.manga.id) {
        mutableStateOf<String?>(null)
    }
    // Once a group is being displayed it stays displayed for the session:
    // finishing it (progress advancing to the next group) must not yank the
    // viewed group away mid-browse. Re-entering resets.
    var pinnedChapterGroup by remember(state.manga.id) { mutableStateOf<String?>(null) }
    var lastProgressGroup by remember(state.manga.id) { mutableStateOf<String?>(null) }
    var progressGroupSeen by remember(state.manga.id) { mutableStateOf(false) }
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
    val filteredChapterListItem = remember(listItem, selectedChapterGroup) {
        if (chapterGroups.size <= 1) {
            listItem
        } else {
            listItem.filter { item ->
                item !is ChapterList.Item ||
                    (ChapterNameGroup.parse(item.chapter.name) ?: ungroupedChaptersLabel) == selectedChapterGroup
            }
        }
    }

    val carrierMatrix = state.carrierMatrix
    val carrierNote = carrierMatrix
        ?.takeIf { it.fetched && it.readable.isEmpty() }
        ?.let {
            if (it.installable.isNotEmpty()) {
                stringResource(MR.strings.carrier_install_short)
            } else {
                stringResource(MR.strings.carrier_unavailable)
            }
        }
    val carrierLabel = carrierMatrix
        ?.takeIf { it.fetched && it.carriers.isNotEmpty() }
        ?.let { m ->
            state.preferredCarrier
                ?.let { pkg -> m.carriers.firstOrNull { it.pkgName == pkg }?.displayName }
                ?: stringResource(MR.strings.carrier_auto)
        }
    val carrierFetched = carrierMatrix?.fetched == true
    val carrierReadable = carrierMatrix?.readable.orEmpty().size
    val rowCarrierProvider = if (carrierFetched && carrierReadable > 0) {
        chapterCarrierLabel
    } else {
        null
    }
    val rowCarrierClick = if (carrierFetched && carrierReadable > 1) {
        onChapterCarrierClicked
    } else {
        null
    }
    // No *usable* carrier on a fetched canonical entry — bound-but-
    // uninstalled carriers count too (a dead-source mint binds the dead
    // pkg) — the comments slot swaps to Find Content Provider (17-02).
    val hasNoCarriers = carrierMatrix?.let { it.fetched && it.readable.isEmpty() } == true
    // Enabled carriers but no chapter rows yet — the extension fetch is
    // still expanding; the slot shows a spinner. An empty readable set
    // can't expand — that's the Find Provider state, not loading.
    val chaptersLoading = carrierMatrix?.let { it.fetched && it.readable.isNotEmpty() } == true &&
        listItem.none { it is ChapterList.Item }

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    var topBarHeight by remember { mutableIntStateOf(0) }

    val chapterListState = rememberLazyListState()

    BackHandler(onBack = {
        if (isAnySelected) {
            onAllChapterSelected(false)
        } else {
            navigateUp()
        }
    })

    Scaffold(
        topBar = {
            val selectedChapterCount = remember(chapters) {
                chapters.count { it.selected }
            }
            EntryToolbar(
                modifier = Modifier.onSizeChanged { topBarHeight = it.height },
                title = state.manga.title,
                hasFilters = state.filterActive,
                navigateUp = navigateUp,
                onClickFilter = null,
                onClickShare = onShareClicked,
                onClickDownload = onDownloadActionClicked,
                onClickEditCollection = onEditCollectionClicked,
                onClickMigrate = onMigrateClicked,
                onCancelActionMode = { onAllChapterSelected(false) },
                onClickSettings = onSettingsClicked,
                changeAnimeSkipIntro = null,
                actionModeCounter = selectedChapterCount,
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = { onInvertSelection() },
                titleAlphaProvider = { 1f },
                backgroundAlphaProvider = { 1f },
                isManga = true,
                toolbarBackgroundColor = MaterialTheme.colorScheme.background,
                onClickRemoveAllDownloads = onRemoveAllDownloadsClicked,
                onClickRemoveNonBookmarkedDownloads = onRemoveNonBookmarkedDownloadsClicked,
                onClickRemoveReadDownloads = onRemoveReadDownloadsClicked,
                onClickWebView = onWebViewClicked,
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                val selectedChapters = remember(chapters) {
                    chapters.filter { it.selected }
                }
                SharedMangaBottomActionMenu(
                    selected = selectedChapters,
                    onMultiBookmarkClicked = onMultiBookmarkClicked,
                    onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                    onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                    onDownloadChapter = onDownloadChapter,
                    onMultiDeleteClicked = onMultiDeleteClicked,
                    fillFraction = 0.5f,
                )
            }
        },
        snackbarHost = { AchievementStyledSnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        val pullState = rememberPullToRefreshState()
        val heroOverscrollPx = pullState.distanceFraction *
            with(LocalDensity.current) { 80.dp.toPx() }
        PullRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            enabled = !isAnySelected,
            indicatorPadding = PaddingValues(
                start = insetPadding.calculateStartPadding(layoutDirection),
                top = with(density) { topBarHeight.toDp() },
                end = insetPadding.calculateEndPadding(layoutDirection),
            ),
            state = pullState,
        ) {
            TwoPanelBox(
                modifier = Modifier.padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                ),
                startContent = {
                    val heroScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .verticalScroll(heroScrollState)
                            .padding(bottom = contentPadding.calculateBottomPadding()),
                    ) {
                        EnhancedEntryInfoBox(
                            appBarPadding = contentPadding.calculateTopPadding(),
                            title = state.manga.title,
                            backdrop = state.manga,
                            sharedKey = "hero-image-${state.manga.id}",
                            logoUrl = null,
                            metaItems = listOfNotNull(
                                state.manga.author?.takeIf { it.isNotBlank() },
                                enhancedEntryStatusText(state.manga.status),
                                remember {
                                    if (CanonicalCatalog.workIdFromMangaUrl(state.manga.url) != null) {
                                        "MikoDB"
                                    } else {
                                        state.source.getNameForMangaInfo()
                                    }
                                },
                            ),
                            rating = parsedDescription.rating,
                            ratingMark = RatingMark.STAR,
                            onCoverClick = onCoverClicked,
                            heroContentPadding = 440.dp,
                            overscrollPullPx = heroOverscrollPx,
                            parallaxOffsetPx = heroScrollState.value.toFloat(),
                            accentColor = state.accentColor,
                            previewCover = previewCover,
                        )
                        run {
                            val nextChapter = remember(chapters) {
                                // Lowest-numbered unread — the "next in
                                // line" chapter regardless of the display
                                // sort direction (firstOrNull on a
                                // descending list would return the newest
                                // unread chapter, not the next one).
                                chapters
                                    .filter { !it.chapter.read }
                                    .minByOrNull { it.chapter.chapterNumber }
                                    ?.chapter
                            }
                            val hasReadChapters = remember(state.chapters) {
                                state.chapters.fastAny { it.chapter.read }
                            }
                            val hasStarted = hasReadChapters ||
                                (nextChapter?.lastPageRead ?: 0L) > 0
                            val allRead = chapters.isNotEmpty() &&
                                chapters.fastAll { it.chapter.read }
                            EntryContinueRow(
                                sharedKey = "hero-resume-${state.manga.id}",
                                actions = listOf(
                                    EntryNextUpAction(
                                        icon = if (allRead) {
                                            Icons.Filled.CheckCircle
                                        } else {
                                            Icons.AutoMirrored.Filled.MenuBook
                                        },
                                        label = when {
                                            allRead -> stringResource(
                                                AYMR.strings.fetching_overlay_all_up_to_date,
                                            )
                                            nextChapter != null && hasStarted ->
                                                stringResource(
                                                    MR.strings.action_continue_named,
                                                    if (state.manga.displayMode ==
                                                        Manga.CHAPTER_DISPLAY_NUMBER
                                                    ) {
                                                        stringResource(
                                                            MR.strings.display_mode_chapter,
                                                            formatChapterNumber(
                                                                nextChapter.chapterNumber,
                                                            ),
                                                        )
                                                    } else {
                                                        nextChapter.name
                                                    },
                                                )
                                            else -> stringResource(MR.strings.action_start)
                                        },
                                        onClickLabel = stringResource(MR.strings.action_resume),
                                        onClick = onContinueReading,
                                    ),
                                ),
                                actionsEnabled = !allRead,
                                accentColor = state.accentColor,
                                favorite = state.manga.favorite,
                                allDone = allRead,
                                favoriteContentDescription = stringResource(
                                    if (state.manga.favorite) {
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
                                pills = state.manga.genre.orEmpty(),
                                onPillClick = { onSearch(it, true) },
                                accentColor = state.accentColor,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        ExpandableMangaDescription(
                            defaultExpandState = false,
                            description = parsedDescription.description,
                            tagsProvider = { null },
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                            accentColor = state.accentColor,
                        )
                        // Similar titles — temporarily disabled
//                        AuroraSuggestionsRow(
//                            state = state.suggestions,
//                            onSuggestionClick = onSuggestionClick,
//                            onOpenSuggestions = onOpenSuggestions,
//                            onRetryClick = onRetrySuggestions,
//                            modifier = Modifier.fillMaxWidth(),
//                            accentColor = state.accentColor,
//                        )
                    }
                },
                endContent = {
                    VerticalFastScroller(
                        listState = chapterListState,
                        thumbAllowed = { false },
                        topContentPadding = contentPadding.calculateTopPadding(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxHeight(),
                            state = chapterListState,
                            contentPadding = PaddingValues(
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                        ) {
                            item(
                                key = EntryScreenItem.ITEM_HEADER,
                                contentType = EntryScreenItem.ITEM_HEADER,
                            ) {
                                EntryChapterHeader(
                                    itemCountText = chapterCountText,
                                    entryCountText = entryCountText,
                                    onClick = onFilterButtonClicked,
                                    accentColor = state.accentColor,
                                    intervalDays = state.intervalDays,
                                    showInterval = state.showInterval,
                                    carrierLabel = carrierLabel,
                                    onCarrierClick = onCarrierPickerClicked,
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

                            sharedChapterItems(
                                manga = state.manga,
                                chapters = filteredChapterListItem,
                                isAnyChapterSelected = chapters.fastAny { it.selected },
                                onChapterClicked = onChapterClicked,
                                onDownloadChapter = onDownloadChapter,
                                onChapterSelected = onChapterSelected,
                                accentColor = state.accentColor,
                                commentsCount = state.commentsCount,
                                onCommentsClicked = onCommentsClicked,
                                hasNoCarriers = hasNoCarriers,
                                onFindProviderClicked = onFindProviderClicked,
                                chaptersLoading = chaptersLoading,
                                carrierNote = carrierNote,
                                carrierNoteWarning = true,
                                carrierLabelProvider = rowCarrierProvider,
                                carrierLabelVersion = state.chapterCarriers to state.preferredCarrier,
                                onCarrierLabelClick = rowCarrierClick,
                                downloadCarriers = carrierMatrix?.readable
                                    ?.map { it.pkgName to it.displayName },
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SharedMangaBottomActionMenu(
    selected: List<ChapterList.Item>,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter, Boolean) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction, String?) -> Unit)?,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    fillFraction: Float,
    modifier: Modifier = Modifier,
) {
    EntryBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = modifier.fillMaxWidth(fillFraction),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAll { it.chapter.bookmark } },
        onMarkAsViewedClicked = {
            onMultiMarkAsReadClicked(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.read } },
        onMarkAsUnviewedClicked = {
            onMultiMarkAsReadClicked(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAny { it.chapter.read || it.chapter.lastPageRead > 0L } },
        onMarkPreviousAsViewedClicked = {
            onMarkPreviousAsReadClicked(selected[0].chapter, true)
        }.takeIf { selected.size == 1 },
        onDownloadClicked = {
            onDownloadChapter!!(selected.toList(), ChapterDownloadAction.START, null)
        }.takeIf {
            onDownloadChapter != null && selected.fastAny { it.downloadState != MangaDownload.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.fastMap { it.chapter })
        }.takeIf {
            selected.fastAny { it.downloadState == MangaDownload.State.DOWNLOADED }
        },
        isManga = true,
    )
}

private fun LazyListScope.sharedChapterItems(
    manga: Manga,
    chapters: List<ChapterList>,
    isAnyChapterSelected: Boolean,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction, String?) -> Unit)?,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    accentColor: Color? = null,
    commentsCount: Long? = null,
    onCommentsClicked: (() -> Unit)? = null,
    /** True when the canonical work has zero bound carriers — swaps the
     *  comments slot to Find Content Provider (17-02). */
    hasNoCarriers: Boolean = false,
    onFindProviderClicked: (() -> Unit)? = null,
    /** Carriers are bound but chapter rows haven't materialized yet —
     *  the extension fetch is running; show a spinner in the slot. */
    chaptersLoading: Boolean = false,
    /** Canonical-entry carrier note shown on chapter cards when no
     *  installed carrier can serve the work (16-04). */
    carrierNote: String? = null,
    carrierNoteWarning: Boolean = false,
    /** Per-row carrier label provider (suspend — resolved via the
     *  router's cheap direct-candidate peek). [carrierLabelVersion]
     *  retriggers resolution when per-chapter overrides change. */
    carrierLabelProvider: (suspend (Chapter) -> String?)? = null,
    carrierLabelVersion: Any? = null,
    /** Row-note tap — opens the chapter-scoped carrier picker. Pass
     *  only when ≥2 readable carriers exist (otherwise there's no
     *  choice to make). */
    onCarrierLabelClick: ((Chapter) -> Unit)? = null,
    /** (pkgName → display name) for the per-chapter download carrier
     *  picker — only consulted on canon rows. */
    downloadCarriers: List<Pair<String, String>>? = null,
) {
    // Horizontal chapter carousel — Nuvio episode-card styling without a
    // still. Opens snapped to the next unread chapter; long-press drives
    // selection, swipe actions are gone (they collide with the row scroll).
    if (chapters.isNotEmpty()) item(
        key = "chapter-carousel",
        contentType = EntryScreenItem.ITEM,
    ) {
        // Land on the lowest-numbered unread chapter — "next up" regardless
        // of the user's sort direction (indexOfFirst finds the newest
        // unread in descending lists, which is the wrong end).
        val nextIndex = chapters.mapIndexedNotNull { index, item ->
            (item as? ChapterList.Item)
                ?.takeIf { !it.chapter.read }
                ?.let { index to it.chapter.chapterNumber }
        }.minByOrNull { it.second }?.first ?: 0
        // The same chapter carries a thin accent outline on its card.
        val nextChapterId = chapters.filterIsInstance<ChapterList.Item>()
            .filter { !it.chapter.read }
            .minByOrNull { it.chapter.chapterNumber }
            ?.chapter?.id
        // Cards are 75% of screen width — a negative initial offset lands
        // the next-up card centered instead of pinned to the start edge.
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
                items = chapters,
                key = { item ->
                    when (item) {
                        is ChapterList.MissingCount -> "missing-count-${item.id}"
                        is ChapterList.Item -> "chapter-${item.id}"
                    }
                },
                contentType = { EntryScreenItem.ITEM },
            ) { item ->
                val haptic = LocalHapticFeedback.current

                when (item) {
                    is ChapterList.MissingCount -> {
                        MissingItemCountListItem(
                            count = item.count,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    is ChapterList.Item -> {
                        val rowLabel = if (carrierLabelProvider != null) {
                            produceState<String?>(
                                null,
                                item.chapter.id,
                                carrierLabelVersion,
                            ) {
                                value = carrierLabelProvider(item.chapter)
                            }.value
                        } else {
                            null
                        }
                        EntryChapterCarouselCard(
                            title = if (manga.displayMode == Manga.CHAPTER_DISPLAY_NUMBER) {
                                stringResource(
                                    MR.strings.display_mode_chapter,
                                    formatChapterNumber(item.chapter.chapterNumber),
                                )
                            } else {
                                item.chapter.name
                            },
                            dateUpload = item.chapter.dateUpload,
                            scanlator = item.chapter.scanlator.takeIf { !it.isNullOrBlank() },
                            progress = item.chapter.lastPageRead
                                .takeIf { !item.chapter.read && it > 0L }
                                ?.let {
                                    stringResource(MR.strings.chapter_progress, it + 1)
                                },
                            read = item.chapter.read,
                            bookmark = item.chapter.bookmark,
                            selected = item.selected,
                            isNext = item.chapter.id == nextChapterId,
                            carrierNote = rowLabel ?: carrierNote,
                            carrierNoteWarning = carrierNoteWarning && rowLabel == null,
                            onCarrierNoteClick = onCarrierLabelClick
                                ?.let { cb -> { cb(item.chapter) } },
                            onLongClick = {
                                onChapterSelected(item, !item.selected, true, true)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onClick = {
                                onChapterItemClick(
                                    chapterItem = item,
                                    isAnyChapterSelected = isAnyChapterSelected,
                                    onToggleSelection = {
                                        onChapterSelected(item, !item.selected, true, false)
                                    },
                                    onChapterClicked = onChapterClicked,
                                )
                            },
                            accentColor = accentColor,
                            trailing = {
                                if (!manga.isLocal()) {
                                    ChapterDownloadIndicator(
                                        enabled = true,
                                        downloadStateProvider = { item.downloadState },
                                        downloadProgressProvider = { item.downloadProgress },
                                        onClick = {
                                            onDownloadChapter?.invoke(listOf(item), it, null)
                                        },
                                        downloadCarriers = if (
                                            eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter
                                                .isCanonUrl(item.chapter.url)
                                        ) {
                                            downloadCarriers
                                        } else {
                                            null
                                        },
                                        onCarrierPick = { pkg ->
                                            onDownloadChapter?.invoke(
                                                listOf(item),
                                                ChapterDownloadAction.START,
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
    }

    // The action slot under the chapter cards. Priority: Find Content
    // Provider on zero-carrier canonical entries (17-02) → a spinner
    // while bound carriers are still expanding into chapter rows →
    // comments, which only renders when chapters actually exist.
    val hasChapterItems = chapters.any { it is ChapterList.Item }
    if (hasNoCarriers && onFindProviderClicked != null) {
        item(
            key = "chapter-find-provider-button",
            contentType = EntryScreenItem.ITEM,
        ) {
            EntryFindProviderButton(
                accentColor = accentColor,
                onClick = onFindProviderClicked,
            )
        }
    } else if (chaptersLoading && !hasChapterItems) {
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
                    color = accentColor ?: MaterialTheme.colorScheme.primary,
                )
            }
        }
    } else if (onCommentsClicked != null && hasChapterItems) {
        item(
            key = "chapter-comments-button",
            contentType = EntryScreenItem.ITEM,
        ) {
            EntryViewCommentsButton(
                commentsCount = commentsCount,
                accentColor = accentColor,
                onClick = onCommentsClicked,
            )
        }
    }
}

private fun onChapterItemClick(
    chapterItem: ChapterList.Item,
    isAnyChapterSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    onChapterClicked: (Chapter) -> Unit,
) {
    when {
        chapterItem.selected -> onToggleSelection(false)
        isAnyChapterSelected -> onToggleSelection(true)
        else -> onChapterClicked(chapterItem.chapter)
    }
}
