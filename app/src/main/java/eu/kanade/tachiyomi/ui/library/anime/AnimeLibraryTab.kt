package eu.kanade.tachiyomi.ui.library.anime

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import tachiyomi.domain.readingorder.interactor.GetLockedReadingOrders
import tachiyomi.domain.readingorder.model.ReadingOrder
import eu.kanade.tachiyomi.ui.readingorder.ReadingOrderLockDialog
import eu.kanade.tachiyomi.ui.readingorder.ReadingOrderViewerScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.compose.ui.util.fastAll
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.collection.components.ChangeCollectionDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.components.globalOverflowActions
import eu.kanade.presentation.components.useSharedTopBar
import eu.kanade.presentation.components.useSharedTopBarWithSearch
import eu.kanade.presentation.entries.components.LibraryBottomActionMenu
import eu.kanade.presentation.entries.components.ReadingOrderBottomBar
import eu.kanade.presentation.entries.components.ReadingOrderPickerDialog
import eu.kanade.presentation.entries.components.ReadingOrderRemoveConfirmDialog
import eu.kanade.presentation.entries.components.ReadingOrderMoveDepthDialog
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.library.DeleteLibraryEntryDialog
import eu.kanade.presentation.library.anime.AnimeLibraryContent
import eu.kanade.presentation.library.anime.AnimeLibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.library.components.SortBottomSheet
import eu.kanade.presentation.library.components.SortModeOption
import eu.kanade.presentation.library.filter.FilterChipData
import eu.kanade.presentation.library.filter.FilterOptionData
import eu.kanade.presentation.library.filter.FilterSectionData
import eu.kanade.presentation.library.filter.FilterSheetVisibility
import eu.kanade.presentation.library.filter.FullFilterSheet
import eu.kanade.presentation.library.filter.GroupBySheet
import eu.kanade.presentation.library.filter.LibraryFilterId
import eu.kanade.presentation.library.filter.PersistentFilterSheet
import eu.kanade.presentation.library.displayoptions.DisplayOptionsSheet
import eu.kanade.presentation.library.displayoptions.LibraryType
import eu.kanade.presentation.more.onboarding.GETTING_STARTED_URL
import eu.kanade.presentation.util.Tab
import tachiyomi.presentation.core.theme.active
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.collection.CollectionsTab
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgress
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.anime.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.injectLazy

data object AnimeLibraryTab : Tab {

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val title = AYMR.strings.label_anime_library
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(
                R.drawable.anim_animelibrary_leave,
            )
            return TabOptions(
                index = 0u,
                title = stringResource(title),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        requestOpenSettingsSheet()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        val screenModel = rememberScreenModel { AnimeLibraryScreenModel() }
        val settingsScreenModel = rememberScreenModel { AnimeLibrarySettingsScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(state.readingOrderSavedMessage) {
            state.readingOrderSavedMessage?.let { msg ->
                snackbarHostState.showSnackbar(msg)
                screenModel.clearReadingOrderSavedMessage()
            }
        }
        val getLockedReadingOrders = remember { Injekt.get<GetLockedReadingOrders>() }
        var lockDialog by remember { mutableStateOf<Pair<Long, List<ReadingOrder>>?>(null) }
        val roImportLauncher = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (json != null) {
                        val success = screenModel.importReadingOrder(json)
                        if (success) {
                            snackbarHostState.showSnackbar("Reading order imported")
                        }
                    }
                }
            }
        }
        var pendingExportOrder by remember { mutableStateOf<ReadingOrder?>(null) }
        val roExportLauncher = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                val order = pendingExportOrder
                if (order != null) {
                    scope.launch {
                        val json = screenModel.exportReadingOrder(order)
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        snackbarHostState.showSnackbar("Reading order \"${order.name}\" exported")
                        pendingExportOrder = null
                    }
                }
            }
        }

        // Persistent filter sheet state
        var filterSheetVisibility by remember { mutableStateOf(FilterSheetVisibility.HIDDEN) }
        var showFullFilterSheet by remember { mutableStateOf(false) }
        var showGroupBySheet by remember { mutableStateOf(false) }
        var showDisplayOptionsSheet by remember { mutableStateOf(false) }
        var showSortSheet by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        var settingsDialogPage by remember { mutableIntStateOf(0) }

        // Filter preferences as state
        val filterDownloaded by settingsScreenModel.libraryPreferences.filterDownloadedAnime().collectAsStateWithLifecycle()
        val downloadedOnly by settingsScreenModel.preferences.downloadedOnly().collectAsStateWithLifecycle()
        val filterUnseen by settingsScreenModel.libraryPreferences.filterUnseen().collectAsStateWithLifecycle()
        val filterStarted by settingsScreenModel.libraryPreferences.filterStartedAnime().collectAsStateWithLifecycle()
        val filterBookmarked by settingsScreenModel.libraryPreferences.filterBookmarkedAnime().collectAsStateWithLifecycle()
        val filterCompleted by settingsScreenModel.libraryPreferences.filterCompletedAnime().collectAsStateWithLifecycle()
        val filterOrder by settingsScreenModel.libraryPreferences.filterOrder().collectAsStateWithLifecycle()
        val trackers by settingsScreenModel.trackersFlow.collectAsStateWithLifecycle()

        // Compute combined tracked filter state for the collapsed pill
        val trackedChipState = if (trackers.isEmpty()) {
            TriState.DISABLED
        } else {
            val states = trackers.mapNotNull { tracker ->
                settingsScreenModel.libraryPreferences.filterTrackedAnime(tracker.id.toInt()).get()
            }
            when {
                states.any { it == TriState.ENABLED_IS } -> TriState.ENABLED_IS
                states.any { it == TriState.ENABLED_NOT } -> TriState.ENABLED_NOT
                else -> TriState.DISABLED
            }
        }

        // Build filter chip data ordered by filterOrder preference
        val filterChips = remember(filterDownloaded, filterUnseen, filterStarted, filterBookmarked, filterCompleted, filterOrder, trackedChipState, trackers) {
            val orderChars = filterOrder.ifBlank { LibraryFilterId.DEFAULT_ORDER }
            val allFilters = mapOf(
                LibraryFilterId.DOWNLOADED to FilterChipData(
                    id = LibraryFilterId.DOWNLOADED,
                    labelRes = MR.strings.label_downloaded,
                    state = if (downloadedOnly) TriState.ENABLED_IS else filterDownloaded,
                    enabled = !downloadedOnly,
                    onToggle = { settingsScreenModel.toggleFilter(LibraryPreferences::filterDownloadedAnime) },
                ),
                LibraryFilterId.UNREAD to FilterChipData(
                    id = LibraryFilterId.UNREAD,
                    labelRes = AYMR.strings.action_filter_unseen,
                    state = filterUnseen,
                    onToggle = { settingsScreenModel.toggleFilter(LibraryPreferences::filterUnseen) },
                ),
                LibraryFilterId.STARTED to FilterChipData(
                    id = LibraryFilterId.STARTED,
                    labelRes = MR.strings.label_started,
                    state = filterStarted,
                    onToggle = { settingsScreenModel.toggleFilter(LibraryPreferences::filterStartedAnime) },
                ),
                LibraryFilterId.BOOKMARKED to FilterChipData(
                    id = LibraryFilterId.BOOKMARKED,
                    labelRes = MR.strings.action_filter_bookmarked,
                    state = filterBookmarked,
                    onToggle = { settingsScreenModel.toggleFilter(LibraryPreferences::filterBookmarkedAnime) },
                ),
                LibraryFilterId.COMPLETED to FilterChipData(
                    id = LibraryFilterId.COMPLETED,
                    labelRes = MR.strings.completed,
                    state = filterCompleted,
                    onToggle = { settingsScreenModel.toggleFilter(LibraryPreferences::filterCompletedAnime) },
                ),
                LibraryFilterId.TRACKED to FilterChipData(
                    id = LibraryFilterId.TRACKED,
                    labelRes = MR.strings.action_filter_tracked,
                    state = trackedChipState,
                    enabled = trackers.isNotEmpty(),
                    onToggle = {
                        // Toggle all trackers together
                        trackers.forEach { tracker ->
                            settingsScreenModel.toggleTracker(tracker.id.toInt())
                        }
                    },
                ),
            )
            orderChars.mapNotNull { c -> LibraryFilterId.fromChar(c)?.let { allFilters[it] } }
        }

        // Build full filter sections — each with 3 radio options (All / positive / negative)
        val filterSections = buildList {
            add(FilterSectionData(
                id = LibraryFilterId.DOWNLOADED,
                titleRes = MR.strings.label_downloaded,
                items = listOf(
                    FilterOptionData(
                        label = stringResource(MR.strings.all),
                        isSelected = filterDownloaded == TriState.DISABLED,
                        onClick = { settingsScreenModel.libraryPreferences.filterDownloadedAnime().set(TriState.DISABLED) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.label_downloaded),
                        isSelected = if (downloadedOnly) true else filterDownloaded == TriState.ENABLED_IS,
                        onClick = { settingsScreenModel.libraryPreferences.filterDownloadedAnime().set(TriState.ENABLED_IS) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.filter_not_downloaded),
                        isSelected = filterDownloaded == TriState.ENABLED_NOT,
                        onClick = { settingsScreenModel.libraryPreferences.filterDownloadedAnime().set(TriState.ENABLED_NOT) },
                    ),
                ),
            ))
            add(FilterSectionData(
                id = LibraryFilterId.UNREAD,
                titleRes = MR.strings.action_filter_unread,
                items = listOf(
                    FilterOptionData(
                        label = stringResource(MR.strings.all),
                        isSelected = filterUnseen == TriState.DISABLED,
                        onClick = { settingsScreenModel.libraryPreferences.filterUnseen().set(TriState.DISABLED) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.action_filter_unread),
                        isSelected = filterUnseen == TriState.ENABLED_IS,
                        onClick = { settingsScreenModel.libraryPreferences.filterUnseen().set(TriState.ENABLED_IS) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.filter_read),
                        isSelected = filterUnseen == TriState.ENABLED_NOT,
                        onClick = { settingsScreenModel.libraryPreferences.filterUnseen().set(TriState.ENABLED_NOT) },
                    ),
                ),
            ))
            add(FilterSectionData(
                id = LibraryFilterId.STARTED,
                titleRes = MR.strings.label_started,
                items = listOf(
                    FilterOptionData(
                        label = stringResource(MR.strings.all),
                        isSelected = filterStarted == TriState.DISABLED,
                        onClick = { settingsScreenModel.libraryPreferences.filterStartedAnime().set(TriState.DISABLED) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.label_started),
                        isSelected = filterStarted == TriState.ENABLED_IS,
                        onClick = { settingsScreenModel.libraryPreferences.filterStartedAnime().set(TriState.ENABLED_IS) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.filter_not_started),
                        isSelected = filterStarted == TriState.ENABLED_NOT,
                        onClick = { settingsScreenModel.libraryPreferences.filterStartedAnime().set(TriState.ENABLED_NOT) },
                    ),
                ),
            ))
            add(FilterSectionData(
                id = LibraryFilterId.BOOKMARKED,
                titleRes = MR.strings.action_filter_bookmarked,
                items = listOf(
                    FilterOptionData(
                        label = stringResource(MR.strings.all),
                        isSelected = filterBookmarked == TriState.DISABLED,
                        onClick = { settingsScreenModel.libraryPreferences.filterBookmarkedAnime().set(TriState.DISABLED) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.action_filter_bookmarked),
                        isSelected = filterBookmarked == TriState.ENABLED_IS,
                        onClick = { settingsScreenModel.libraryPreferences.filterBookmarkedAnime().set(TriState.ENABLED_IS) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.filter_not_bookmarked),
                        isSelected = filterBookmarked == TriState.ENABLED_NOT,
                        onClick = { settingsScreenModel.libraryPreferences.filterBookmarkedAnime().set(TriState.ENABLED_NOT) },
                    ),
                ),
            ))
            add(FilterSectionData(
                id = LibraryFilterId.COMPLETED,
                titleRes = MR.strings.completed,
                items = listOf(
                    FilterOptionData(
                        label = stringResource(MR.strings.all),
                        isSelected = filterCompleted == TriState.DISABLED,
                        onClick = { settingsScreenModel.libraryPreferences.filterCompletedAnime().set(TriState.DISABLED) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.completed),
                        isSelected = filterCompleted == TriState.ENABLED_IS,
                        onClick = { settingsScreenModel.libraryPreferences.filterCompletedAnime().set(TriState.ENABLED_IS) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.ongoing),
                        isSelected = filterCompleted == TriState.ENABLED_NOT,
                        onClick = { settingsScreenModel.libraryPreferences.filterCompletedAnime().set(TriState.ENABLED_NOT) },
                    ),
                ),
            ))
            if (trackers.isNotEmpty()) {
                add(FilterSectionData(
                    id = LibraryFilterId.TRACKED,
                    titleRes = MR.strings.action_filter_tracked,
                    items = trackers.flatMap { service ->
                        val filterTracker = settingsScreenModel.libraryPreferences.filterTrackedAnime(service.id.toInt()).get()
                        listOf(
                            FilterOptionData(
                                label = "${service.name}: ${stringResource(MR.strings.all)}",
                                isSelected = filterTracker == TriState.DISABLED,
                                onClick = { settingsScreenModel.libraryPreferences.filterTrackedAnime(service.id.toInt()).set(TriState.DISABLED) },
                            ),
                            FilterOptionData(
                                label = "${service.name}: ${stringResource(MR.strings.action_filter_tracked)}",
                                isSelected = filterTracker == TriState.ENABLED_IS,
                                onClick = { settingsScreenModel.libraryPreferences.filterTrackedAnime(service.id.toInt()).set(TriState.ENABLED_IS) },
                            ),
                            FilterOptionData(
                                label = "${service.name}: ${stringResource(MR.strings.filter_not_tracked)}",
                                isSelected = filterTracker == TriState.ENABLED_NOT,
                                onClick = { settingsScreenModel.libraryPreferences.filterTrackedAnime(service.id.toInt()).set(TriState.ENABLED_NOT) },
                            ),
                        )
                    },
                ))
            }
        }

        // Clear all filters
        val onClearFilters = {
            settingsScreenModel.libraryPreferences.filterDownloadedAnime().set(TriState.DISABLED)
            settingsScreenModel.libraryPreferences.filterUnseen().set(TriState.DISABLED)
            settingsScreenModel.libraryPreferences.filterStartedAnime().set(TriState.DISABLED)
            settingsScreenModel.libraryPreferences.filterBookmarkedAnime().set(TriState.DISABLED)
            settingsScreenModel.libraryPreferences.filterCompletedAnime().set(TriState.DISABLED)
            trackers.forEach { service ->
                settingsScreenModel.libraryPreferences.filterTrackedAnime(service.id.toInt()).set(TriState.DISABLED)
            }
        }

        val onClickRefresh: (Collection?) -> Boolean = { collection ->
            eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus.refreshRequested()
            AnimeLibraryUpdateJob.startNow(context, collection)
        }

        suspend fun openEpisode(episode: Episode) {
            val playerPreferences: PlayerPreferences by injectLazy()
            val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
            MainActivity.startPlayerActivity(context, episode.animeId, episode.id, extPlayer)
        }

        val defaultTitle = stringResource(MR.strings.label_library)

        // Register with shared top bar
        val title = state.getToolbarTitle(
            defaultTitle = defaultTitle,
            defaultCollectionTitle = stringResource(MR.strings.label_default),
            page = screenModel.activeCollectionIndex,
        )
        if (state.selectionMode) {
            val selectionActions = buildList {
                add(AppBar.Action(
                    title = stringResource(MR.strings.action_select_all),
                    icon = Icons.Outlined.SelectAll,
                    onClick = { screenModel.selectAll(screenModel.activeCollectionIndex) },
                ))
                add(AppBar.Action(
                    title = stringResource(MR.strings.action_select_inverse),
                    icon = Icons.Outlined.FlipToBack,
                    onClick = { screenModel.invertSelection(screenModel.activeCollectionIndex) },
                ))
                if (!state.readingOrderMode) {
                    add(AppBar.Action(
                        title = stringResource(MR.strings.action_download),
                        icon = Icons.Outlined.Download,
                        onClick = { screenModel.runDownloadActionSelection(DownloadAction.UNVIEWED_ITEMS) },
                    ))
                }
            }
            useSharedTopBar(
                title = if (state.readingOrderMode) {
                    "Layer ${state.readingOrderCurrentLayer + 1}"
                } else {
                    "${state.selection.size}"
                },
                actions = selectionActions.toPersistentList(),
                navigateUp = if (state.readingOrderMode) screenModel::exitReadingOrderMode else screenModel::clearSelection,
            )
        } else {
            val libraryActions = buildList {
                add(AppBar.Action(
                    title = stringResource(MR.strings.action_filter),
                    icon = Icons.Outlined.FilterList,
                    iconTint = if (state.hasActiveFilters) MaterialTheme.colorScheme.active else null,
                    onClick = {
                        filterSheetVisibility = when (filterSheetVisibility) {
                            FilterSheetVisibility.HIDDEN -> FilterSheetVisibility.COLLAPSED
                            FilterSheetVisibility.COLLAPSED -> FilterSheetVisibility.EXPANDED
                            FilterSheetVisibility.EXPANDED -> FilterSheetVisibility.HIDDEN
                        }
                    },
                ))
                add(AppBar.OverflowAction(
                    title = stringResource(AYMR.strings.reading_order),
                    onClick = { screenModel.showReadingOrderDialog() },
                ))
                addAll(globalOverflowActions(onClickSettings = { navigator.push(eu.kanade.tachiyomi.ui.setting.SettingsScreen()) }))
            }.toPersistentList()
            useSharedTopBarWithSearch(
                title = title.text,
                actions = libraryActions,
                searchEnabled = true,
                searchPlaceholderText = stringResource(MR.strings.search_hint_library),
                searchQuery = state.searchQuery,
                onSearchQueryChange = { query ->
                    if (query == null) {
                        screenModel.search(null)
                    } else {
                        screenModel.search(query)
                    }
                },
            )
        }

        // Read host padding for nav bar offset (used by persistent filter sheet)
        val hostPaddingForSheet = eu.kanade.presentation.components.LocalHostScaffoldContentPadding.current
        val hostBottomForSheet = hostPaddingForSheet?.calculateBottomPadding() ?: androidx.compose.ui.unit.Dp.Hairline

        Box {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {},
            bottomBar = {
                if (state.readingOrderMode) {
                    ReadingOrderBottomBar(
                        visible = state.readingOrderMode,
                        currentLayer = state.readingOrderCurrentLayer,
                        canGoBack = state.readingOrderCurrentLayer > 0 || state.readingOrderLayers.isNotEmpty(),
                        canAdvance = state.selection.isNotEmpty(),
                        canSave = state.readingOrderCurrentLayer > 0 || state.readingOrderLayers.isNotEmpty(),
                        onBack = screenModel::goBackReadingOrderLayer,
                        onNext = screenModel::advanceReadingOrderLayer,
                        onSave = screenModel::saveReadingOrder,
                    )
                } else {
                    LibraryBottomActionMenu(
                        visible = state.selectionMode,
                        onChangeCollectionClicked = screenModel::openChangeCollectionDialog,
                        onMarkAsViewedClicked = { screenModel.markSeenSelection(true) },
                        onMarkAsUnviewedClicked = { screenModel.markSeenSelection(false) },
                        onDownloadClicked = null,
                        onDeleteClicked = screenModel::openDeleteAnimeDialog,
                        isManga = false,
                    )
                }
            },
            snackbarHost = {
                AchievementStyledSnackbarHost(hostState = snackbarHostState)
            },
        ) { contentPadding ->
            val hostPadding = eu.kanade.presentation.components.LocalHostScaffoldContentPadding.current
            val hostTop = hostPadding?.calculateTopPadding() ?: androidx.compose.ui.unit.Dp.Hairline
            val hostBottom = hostPadding?.calculateBottomPadding() ?: androidx.compose.ui.unit.Dp.Hairline
            // Add extra bottom padding when the fetching overlay is visible
            // so the last library items aren't hidden behind it.
            val updateState by LibraryUpdateProgressBus.state.collectAsState()
            val overlayExtraBottom = when (updateState) {
                is LibraryUpdateProgress.Running, is LibraryUpdateProgress.Completed -> 80.dp
                else -> 0.dp
            }
            // Add extra bottom padding for the persistent filter sheet
            val filterSheetExtraBottom = if (filterSheetVisibility != FilterSheetVisibility.HIDDEN) 80.dp else 0.dp
            val resolvedContentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = contentPadding.calculateTopPadding() + hostTop,
                bottom = contentPadding.calculateBottomPadding() + hostBottom + overlayExtraBottom + filterSheetExtraBottom,
            )
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(resolvedContentPadding))
                state.searchQuery.isNullOrEmpty() && !state.hasActiveFilters && state.isLibraryEmpty -> {
                    val handler = LocalUriHandler.current
                    EmptyScreen(
                        stringRes = MR.strings.information_empty_library,
                        modifier = Modifier.padding(resolvedContentPadding),
                        actions = persistentListOf(
                            EmptyScreenAction(
                                stringRes = MR.strings.getting_started_guide,
                                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                                onClick = { handler.openUri(GETTING_STARTED_URL) },
                            ),
                        ),
                    )
                }
                else -> {
                    AnimeLibraryContent(
                        collections = state.collections,
                        searchQuery = state.searchQuery,
                        selection = state.selection,
                        contentPadding = resolvedContentPadding,
                        currentPage = { screenModel.activeCollectionIndex },
                        hasActiveFilters = state.hasActiveFilters,
                        showPageTabs = state.showCollectionTabs || !state.searchQuery.isNullOrEmpty(),
                        onChangeCurrentPage = { screenModel.activeCollectionIndex = it },
                        onAnimeClicked = { animeId ->
                            scope.launch {
                                val lockedOrders = getLockedReadingOrders.await(animeId)
                                if (lockedOrders.isNotEmpty()) {
                                    lockDialog = animeId to lockedOrders
                                } else {
                                    navigator.push(AnimeScreen(animeId))
                                }
                            }
                        },
                        onContinueWatchingClicked = { it: LibraryAnime ->
                            scope.launchIO {
                                val episode = screenModel.getNextUnseenEpisode(it.anime)
                                if (episode != null) openEpisode(episode)
                            }
                            Unit
                        }.takeIf { state.showAnimeContinueButton },
                        onToggleSelection = screenModel::toggleSelection,
                        onToggleRangeSelection = {
                            screenModel.toggleRangeSelection(it)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onRefresh = onClickRefresh,
                        onGlobalSearchClicked = {
                            navigator.push(
                                GlobalAnimeSearchScreen(screenModel.state.value.searchQuery ?: ""),
                            )
                        },
                        getNumberOfAnimeForCollection = { state.getAnimeCountForCollection(it) },
                        getDisplayMode = { screenModel.getDisplayMode() },
                        getColumnsForOrientation = {
                            screenModel.getColumnsPreferenceForCurrentOrientation(
                                it,
                            )
                        },
                        getAnimeLibraryForPage = { state.getAnimelibItemsByPage(it) },
                        sortLabel = state.collections.getOrNull(screenModel.activeCollectionIndex)
                            ?.let { collection ->
                                stringResource(sortLabelResForAnime(collection.sort.type))
                            },
                        sortDescending = state.collections.getOrNull(screenModel.activeCollectionIndex)
                            ?.let { collection -> !collection.sort.isAscending },
                        onSortClick = {
                            showSortSheet = true
                        },
                        showLibraryTitle = state.showLibraryTitle,
                        getReadingOrderLayer = { animeId -> state.getReadingOrderLayer(animeId) },
                        readingOrderMode = state.readingOrderMode,
                        getPreviousLayerAnimeIds = if (state.readingOrderMode) {
                            {
                                state.readingOrderLayers.flatten()
                                    .map { it.id }
                                    .toSet()
                            }
                        } else {
                            null
                        },
                        isEntryLocked = { animeId -> state.lockedReadingOrderEntryIds.contains(animeId) },
                    )
                }
            }
        }

        // Persistent filter sheet — positioned above the floating nav bar
        PersistentFilterSheet(
            visibility = filterSheetVisibility,
            filters = filterChips,
            bottomOffset = hostBottomForSheet,
            onFilterButtonClick = { showFullFilterSheet = true },
            onGroupByClick = {
                showGroupBySheet = true
            },
            onDisplayClick = {
                showDisplayOptionsSheet = true
            },
            onClearFilters = onClearFilters,
            showExpandCollapse = state.collections.size > 1,
            allExpanded = null,
            onExpandCollapseClick = {},
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
        )
        }

        val onDismissRequest = screenModel::closeDialog
        when (val dialog = state.dialog) {
            is AnimeLibraryScreenModel.Dialog.SettingsSheet -> run {
                val collection = state.collections.getOrNull(screenModel.activeCollectionIndex)
                if (collection == null) {
                    onDismissRequest()
                    return@run
                }
                AnimeLibrarySettingsDialog(
                    onDismissRequest = onDismissRequest,
                    screenModel = settingsScreenModel,
                    collection = collection,
                    onClickReadingOrders = {
                        onDismissRequest()
                        navigator.push(eu.kanade.tachiyomi.ui.readingorder.ReadingOrderListScreen("anime"))
                    },
                )
            }
            is AnimeLibraryScreenModel.Dialog.ChangeCollection -> {
                ChangeCollectionDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCollections = {
                        screenModel.clearSelection()
                        navigator.push(CollectionsTab)
                    },
                    onConfirm = { include, exclude ->
                        screenModel.clearSelection()
                        screenModel.setAnimeCollections(dialog.anime, include, exclude)
                    },
                )
            }
            is AnimeLibraryScreenModel.Dialog.DeleteAnime -> {
                DeleteLibraryEntryDialog(
                    containsLocalEntry = dialog.anime.any(Anime::isLocal),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteAnime, deleteEpisode ->
                        screenModel.removeAnimes(dialog.anime, deleteAnime, deleteEpisode)
                        screenModel.clearSelection()
                    },
                    isManga = false,
                )
            }
            is AnimeLibraryScreenModel.Dialog.ReadingOrderPicker -> {
                ReadingOrderPickerDialog(
                    orders = dialog.orders,
                    onDismiss = onDismissRequest,
                    onSelectExisting = { order -> screenModel.editExistingReadingOrder(order.id) },
                    onCreateNew = { name -> screenModel.createNewReadingOrder(name) },
                    onDelete = { order -> screenModel.confirmDeleteReadingOrder(order) },
                    onEdit = { order, newName -> screenModel.confirmEditReadingOrder(order, newName) },
                    onExport = { order ->
                        pendingExportOrder = order
                        val fileName = "${order.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}_reading_order.json"
                        roExportLauncher.launch(fileName)
                    },
                    onImport = { roImportLauncher.launch(arrayOf("application/json")) },
                )
            }
            is AnimeLibraryScreenModel.Dialog.ReadingOrderRemoveConfirm -> {
                ReadingOrderRemoveConfirmDialog(
                    entryTitle = dialog.anime.anime.title,
                    onDismiss = { screenModel.cancelRemoveDialog() },
                    onConfirm = { screenModel.confirmRemoveFromReadingOrder(dialog.anime) },
                )
            }
            is AnimeLibraryScreenModel.Dialog.ReadingOrderMoveDepth -> {
                ReadingOrderMoveDepthDialog(
                    entryTitle = dialog.anime.anime.title,
                    fromDepth = dialog.fromDepth,
                    toDepth = dialog.toDepth,
                    onDismiss = { screenModel.cancelRemoveDialog() },
                    onConfirm = { screenModel.confirmMoveEntryDepth(dialog.anime) },
                )
            }
            null -> {}
        }

        lockDialog?.let { (animeId, orders) ->
            ReadingOrderLockDialog(
                lockedOrders = orders,
                onDismiss = { lockDialog = null },
                onViewOrder = { orderId ->
                    lockDialog = null
                    navigator.push(ReadingOrderViewerScreen(orderId))
                },
                onViewEntry = {
                    lockDialog = null
                    navigator.push(AnimeScreen(animeId))
                },
                entryKindLabel = "Anime",
            )
        }

        // Full filter sheet (modal)
        if (showFullFilterSheet) {
            FullFilterSheet(
                sections = filterSections,
                onDismiss = { showFullFilterSheet = false },
                onClearFilters = onClearFilters,
                onReorderClick = { newOrder ->
                    settingsScreenModel.libraryPreferences.filterOrder().set(newOrder)
                },
            )
        }

        // Group By sheet (modal)
        LaunchedEffect(showGroupBySheet, showDisplayOptionsSheet, showSortSheet, showSettingsDialog) {
            LibraryUpdateProgressBus.setSheetVisible(
                showGroupBySheet || showDisplayOptionsSheet || showSortSheet || showSettingsDialog,
            )
        }
        if (showGroupBySheet) {
            val currentGroup by settingsScreenModel.libraryPreferences.groupLibraryBy().collectAsStateWithLifecycle()
            GroupBySheet(
                currentGroup = currentGroup,
                onSelect = { groupId ->
                    settingsScreenModel.libraryPreferences.groupLibraryBy().set(groupId)
                    // TODO: implement actual collection regrouping by the selected criterion
                },
                onDismiss = { showGroupBySheet = false },
            )
        }

        // Display options sheet (3-tab modal)
        if (showDisplayOptionsSheet) {
            val currentDisplayMode by settingsScreenModel.libraryPreferences.displayMode().collectAsStateWithLifecycle()
            DisplayOptionsSheet(
                libraryPreferences = settingsScreenModel.libraryPreferences,
                libraryType = LibraryType.ANIME,
                onDismissRequest = { showDisplayOptionsSheet = false },
                onClickReadingOrders = {
                    showDisplayOptionsSheet = false
                    navigator.push(eu.kanade.tachiyomi.ui.readingorder.ReadingOrderListScreen("anime"))
                },
                currentDisplayMode = currentDisplayMode,
            )
        }

        // Settings dialog for Sort (opened from sort chip in tab row)
        if (showSettingsDialog) {
            val collection = state.collections.getOrNull(screenModel.activeCollectionIndex)
            if (collection != null) {
                AnimeLibrarySettingsDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    screenModel = settingsScreenModel,
                    collection = collection,
                    onClickReadingOrders = {
                        showSettingsDialog = false
                        navigator.push(eu.kanade.tachiyomi.ui.readingorder.ReadingOrderListScreen("anime"))
                    },
                )
            } else {
                showSettingsDialog = false
            }
        }

        // Sort sheet (modal) — opened from sort chip in tab row
        if (showSortSheet) {
            val collection = state.collections.getOrNull(screenModel.activeCollectionIndex)
            if (collection != null) {
                val sortingMode = collection.sort.type
                val sortDescending = !collection.sort.isAscending
                val trackers by settingsScreenModel.trackersFlow.collectAsStateWithLifecycle()
                val sortOptions = buildList {
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_alpha,
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        isSelected = sortingMode == AnimeLibrarySort.Type.Alphabetical,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirectionAnime(sortingMode, AnimeLibrarySort.Type.Alphabetical, sortDescending)
                            settingsScreenModel.setSort(collection, AnimeLibrarySort.Type.Alphabetical, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_total,
                        icon = Icons.Outlined.Numbers,
                        isSelected = sortingMode == AnimeLibrarySort.Type.TotalEpisodes,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirectionAnime(sortingMode, AnimeLibrarySort.Type.TotalEpisodes, sortDescending)
                            settingsScreenModel.setSort(collection, AnimeLibrarySort.Type.TotalEpisodes, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_last_read,
                        icon = Icons.Outlined.History,
                        isSelected = sortingMode == AnimeLibrarySort.Type.LastSeen,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirectionAnime(sortingMode, AnimeLibrarySort.Type.LastSeen, sortDescending)
                            settingsScreenModel.setSort(collection, AnimeLibrarySort.Type.LastSeen, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = AYMR.strings.action_sort_last_anime_update,
                        icon = Icons.Outlined.Refresh,
                        isSelected = sortingMode == AnimeLibrarySort.Type.LastUpdate,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirectionAnime(sortingMode, AnimeLibrarySort.Type.LastUpdate, sortDescending)
                            settingsScreenModel.setSort(collection, AnimeLibrarySort.Type.LastUpdate, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_unread_count,
                        icon = Icons.Outlined.NewReleases,
                        isSelected = sortingMode == AnimeLibrarySort.Type.UnseenCount,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirectionAnime(sortingMode, AnimeLibrarySort.Type.UnseenCount, sortDescending)
                            settingsScreenModel.setSort(collection, AnimeLibrarySort.Type.UnseenCount, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_latest_chapter,
                        icon = Icons.Outlined.Explore,
                        isSelected = sortingMode == AnimeLibrarySort.Type.LatestEpisode,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirectionAnime(sortingMode, AnimeLibrarySort.Type.LatestEpisode, sortDescending)
                            settingsScreenModel.setSort(collection, AnimeLibrarySort.Type.LatestEpisode, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_chapter_fetch_date,
                        icon = Icons.Outlined.CalendarMonth,
                        isSelected = sortingMode == AnimeLibrarySort.Type.EpisodeFetchDate,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirectionAnime(sortingMode, AnimeLibrarySort.Type.EpisodeFetchDate, sortDescending)
                            settingsScreenModel.setSort(collection, AnimeLibrarySort.Type.EpisodeFetchDate, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_date_added,
                        icon = Icons.Outlined.Grade,
                        isSelected = sortingMode == AnimeLibrarySort.Type.DateAdded,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirectionAnime(sortingMode, AnimeLibrarySort.Type.DateAdded, sortDescending)
                            settingsScreenModel.setSort(collection, AnimeLibrarySort.Type.DateAdded, dir)
                        },
                    ))
                    if (trackers.isNotEmpty()) {
                        add(SortModeOption(
                            labelRes = MR.strings.action_sort_tracker_score,
                            icon = Icons.Outlined.FavoriteBorder,
                            isSelected = sortingMode == AnimeLibrarySort.Type.TrackerMean,
                            isDescending = sortDescending,
                            isDirectional = true,
                            onClick = {
                                val dir = toggleDirectionAnime(sortingMode, AnimeLibrarySort.Type.TrackerMean, sortDescending)
                                settingsScreenModel.setSort(collection, AnimeLibrarySort.Type.TrackerMean, dir)
                            },
                        ))
                    }
                    add(SortModeOption(
                        labelRes = AYMR.strings.action_sort_custom_order,
                        icon = Icons.Outlined.SwapVert,
                        isSelected = sortingMode == AnimeLibrarySort.Type.CustomOrder,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirectionAnime(sortingMode, AnimeLibrarySort.Type.CustomOrder, sortDescending)
                            settingsScreenModel.setSort(collection, AnimeLibrarySort.Type.CustomOrder, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = AYMR.strings.action_sort_airing_time,
                        icon = Icons.Outlined.Schedule,
                        isSelected = sortingMode == AnimeLibrarySort.Type.AiringTime,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirectionAnime(sortingMode, AnimeLibrarySort.Type.AiringTime, sortDescending)
                            settingsScreenModel.setSort(collection, AnimeLibrarySort.Type.AiringTime, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_random,
                        icon = Icons.Outlined.Check,
                        isSelected = sortingMode == AnimeLibrarySort.Type.Random,
                        isDescending = false,
                        isDirectional = false,
                        onClick = {
                            settingsScreenModel.setSort(collection, AnimeLibrarySort.Type.Random, AnimeLibrarySort.Direction.Ascending)
                        },
                    ))
                }
                SortBottomSheet(
                    title = stringResource(MR.strings.action_sort),
                    options = sortOptions,
                    onDismiss = { showSortSheet = false },
                )
            } else {
                showSortSheet = false
            }
        }

        BackHandler(enabled = state.selectionMode || state.searchQuery != null || filterSheetVisibility != FilterSheetVisibility.HIDDEN) {
            when {
                filterSheetVisibility != FilterSheetVisibility.HIDDEN -> filterSheetVisibility = FilterSheetVisibility.HIDDEN
                state.selectionMode -> screenModel.clearSelection()
                state.searchQuery != null -> screenModel.search(null)
            }
        }

        LaunchedEffect(state.selectionMode, state.dialog) {
            HomeScreen.showBottomNav(!state.selectionMode)
            if (state.selectionMode) filterSheetVisibility = FilterSheetVisibility.HIDDEN
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    scope.launch { screenModel.loadSavedReadingOrderLayers() }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(Unit) {
            launch { queryEvent.receiveAsFlow().collect(screenModel::search) }
            launch { requestSettingsSheetEvent.receiveAsFlow().collectLatest {
                filterSheetVisibility = when (filterSheetVisibility) {
                    FilterSheetVisibility.HIDDEN -> FilterSheetVisibility.COLLAPSED
                    FilterSheetVisibility.COLLAPSED -> FilterSheetVisibility.EXPANDED
                    FilterSheetVisibility.EXPANDED -> FilterSheetVisibility.HIDDEN
                }
            } }
        }
    }

    // For invoking search from other screen
    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)

    // For opening settings sheet in LibraryController
    private val requestSettingsSheetEvent = Channel<Unit>()
    private suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)
}

private fun sortLabelResForAnime(type: AnimeLibrarySort.Type): dev.icerock.moko.resources.StringResource {
    return when (type) {
        AnimeLibrarySort.Type.Alphabetical -> MR.strings.action_sort_alpha
        AnimeLibrarySort.Type.LastSeen -> MR.strings.action_sort_last_read
        AnimeLibrarySort.Type.LastUpdate -> AYMR.strings.action_sort_last_anime_update
        AnimeLibrarySort.Type.UnseenCount -> MR.strings.action_sort_unread_count
        AnimeLibrarySort.Type.TotalEpisodes -> MR.strings.action_sort_total
        AnimeLibrarySort.Type.LatestEpisode -> MR.strings.action_sort_latest_chapter
        AnimeLibrarySort.Type.EpisodeFetchDate -> MR.strings.action_sort_chapter_fetch_date
        AnimeLibrarySort.Type.DateAdded -> MR.strings.action_sort_date_added
        AnimeLibrarySort.Type.TrackerMean -> MR.strings.action_sort_tracker_score
        AnimeLibrarySort.Type.CustomOrder -> AYMR.strings.action_sort_custom_order
        AnimeLibrarySort.Type.ReadingOrder -> AYMR.strings.reading_order
        AnimeLibrarySort.Type.AiringTime -> AYMR.strings.action_sort_airing_time
        AnimeLibrarySort.Type.Random -> MR.strings.action_sort_random
    }
}

private fun toggleDirectionAnime(
    currentMode: AnimeLibrarySort.Type,
    targetMode: AnimeLibrarySort.Type,
    currentlyDescending: Boolean,
): AnimeLibrarySort.Direction {
    return if (currentMode == targetMode) {
        if (currentlyDescending) AnimeLibrarySort.Direction.Ascending
        else AnimeLibrarySort.Direction.Descending
    } else {
        if (currentlyDescending) AnimeLibrarySort.Direction.Descending
        else AnimeLibrarySort.Direction.Ascending
    }
}
