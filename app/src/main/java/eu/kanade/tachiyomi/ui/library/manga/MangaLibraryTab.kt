package eu.kanade.tachiyomi.ui.library.manga

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
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import eu.kanade.presentation.library.manga.MangaLibraryContent
import eu.kanade.presentation.library.manga.MangaLibrarySettingsDialog
import eu.kanade.presentation.more.onboarding.GETTING_STARTED_URL
import eu.kanade.presentation.util.Tab
import tachiyomi.presentation.core.theme.active
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.collection.CollectionsTab
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgress
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
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
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.manga.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import tachiyomi.source.local.entries.manga.isLocal

data object MangaLibraryTab : Tab {

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val title = AYMR.strings.label_manga_library
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                index = 1u,
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

        val screenModel = rememberScreenModel { MangaLibraryScreenModel() }
        val settingsScreenModel = rememberScreenModel { MangaLibrarySettingsScreenModel() }
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
        var pendingExportOrder by remember { mutableStateOf<ReadingOrder?>(null) }
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
        var settingsDialogPage by remember { mutableIntStateOf(0) }
        var showSettingsDialog by remember { mutableStateOf(false) }

        // Filter preferences as state
        val filterDownloaded by settingsScreenModel.libraryPreferences.filterDownloadedManga().collectAsStateWithLifecycle()
        val downloadedOnly by settingsScreenModel.preferences.downloadedOnly().collectAsStateWithLifecycle()
        val filterUnread by settingsScreenModel.libraryPreferences.filterUnread().collectAsStateWithLifecycle()
        val filterStarted by settingsScreenModel.libraryPreferences.filterStartedManga().collectAsStateWithLifecycle()
        val filterBookmarked by settingsScreenModel.libraryPreferences.filterBookmarkedManga().collectAsStateWithLifecycle()
        val filterCompleted by settingsScreenModel.libraryPreferences.filterCompletedManga().collectAsStateWithLifecycle()
        val filterOrder by settingsScreenModel.libraryPreferences.filterOrder().collectAsStateWithLifecycle()
        val trackers by settingsScreenModel.trackersFlow.collectAsStateWithLifecycle()

        // Compute combined tracked filter state for the collapsed pill
        val trackedChipState = if (trackers.isEmpty()) {
            TriState.DISABLED
        } else {
            val states = trackers.mapNotNull { tracker ->
                settingsScreenModel.libraryPreferences.filterTrackedManga(tracker.id.toInt()).get()
            }
            when {
                states.any { it == TriState.ENABLED_IS } -> TriState.ENABLED_IS
                states.any { it == TriState.ENABLED_NOT } -> TriState.ENABLED_NOT
                else -> TriState.DISABLED
            }
        }

        // Build filter chip data ordered by filterOrder preference
        val filterChips = remember(filterDownloaded, filterUnread, filterStarted, filterBookmarked, filterCompleted, trackedChipState, filterOrder, trackers) {
            val orderChars = filterOrder.ifBlank { LibraryFilterId.DEFAULT_ORDER }
            val allFilters = mapOf(
                LibraryFilterId.DOWNLOADED to FilterChipData(
                    id = LibraryFilterId.DOWNLOADED,
                    labelRes = MR.strings.label_downloaded,
                    state = if (downloadedOnly) TriState.ENABLED_IS else filterDownloaded,
                    enabled = !downloadedOnly,
                    onToggle = { settingsScreenModel.toggleFilter(LibraryPreferences::filterDownloadedManga) },
                ),
                LibraryFilterId.UNREAD to FilterChipData(
                    id = LibraryFilterId.UNREAD,
                    labelRes = MR.strings.action_filter_unread,
                    state = filterUnread,
                    onToggle = { settingsScreenModel.toggleFilter(LibraryPreferences::filterUnread) },
                ),
                LibraryFilterId.STARTED to FilterChipData(
                    id = LibraryFilterId.STARTED,
                    labelRes = MR.strings.label_started,
                    state = filterStarted,
                    onToggle = { settingsScreenModel.toggleFilter(LibraryPreferences::filterStartedManga) },
                ),
                LibraryFilterId.BOOKMARKED to FilterChipData(
                    id = LibraryFilterId.BOOKMARKED,
                    labelRes = MR.strings.action_filter_bookmarked,
                    state = filterBookmarked,
                    onToggle = { settingsScreenModel.toggleFilter(LibraryPreferences::filterBookmarkedManga) },
                ),
                LibraryFilterId.COMPLETED to FilterChipData(
                    id = LibraryFilterId.COMPLETED,
                    labelRes = MR.strings.completed,
                    state = filterCompleted,
                    onToggle = { settingsScreenModel.toggleFilter(LibraryPreferences::filterCompletedManga) },
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
                        onClick = { settingsScreenModel.libraryPreferences.filterDownloadedManga().set(TriState.DISABLED) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.label_downloaded),
                        isSelected = if (downloadedOnly) true else filterDownloaded == TriState.ENABLED_IS,
                        onClick = { settingsScreenModel.libraryPreferences.filterDownloadedManga().set(TriState.ENABLED_IS) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.filter_not_downloaded),
                        isSelected = filterDownloaded == TriState.ENABLED_NOT,
                        onClick = { settingsScreenModel.libraryPreferences.filterDownloadedManga().set(TriState.ENABLED_NOT) },
                    ),
                ),
            ))
            add(FilterSectionData(
                id = LibraryFilterId.UNREAD,
                titleRes = MR.strings.action_filter_unread,
                items = listOf(
                    FilterOptionData(
                        label = stringResource(MR.strings.all),
                        isSelected = filterUnread == TriState.DISABLED,
                        onClick = { settingsScreenModel.libraryPreferences.filterUnread().set(TriState.DISABLED) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.action_filter_unread),
                        isSelected = filterUnread == TriState.ENABLED_IS,
                        onClick = { settingsScreenModel.libraryPreferences.filterUnread().set(TriState.ENABLED_IS) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.filter_read),
                        isSelected = filterUnread == TriState.ENABLED_NOT,
                        onClick = { settingsScreenModel.libraryPreferences.filterUnread().set(TriState.ENABLED_NOT) },
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
                        onClick = { settingsScreenModel.libraryPreferences.filterStartedManga().set(TriState.DISABLED) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.label_started),
                        isSelected = filterStarted == TriState.ENABLED_IS,
                        onClick = { settingsScreenModel.libraryPreferences.filterStartedManga().set(TriState.ENABLED_IS) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.filter_not_started),
                        isSelected = filterStarted == TriState.ENABLED_NOT,
                        onClick = { settingsScreenModel.libraryPreferences.filterStartedManga().set(TriState.ENABLED_NOT) },
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
                        onClick = { settingsScreenModel.libraryPreferences.filterBookmarkedManga().set(TriState.DISABLED) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.action_filter_bookmarked),
                        isSelected = filterBookmarked == TriState.ENABLED_IS,
                        onClick = { settingsScreenModel.libraryPreferences.filterBookmarkedManga().set(TriState.ENABLED_IS) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.filter_not_bookmarked),
                        isSelected = filterBookmarked == TriState.ENABLED_NOT,
                        onClick = { settingsScreenModel.libraryPreferences.filterBookmarkedManga().set(TriState.ENABLED_NOT) },
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
                        onClick = { settingsScreenModel.libraryPreferences.filterCompletedManga().set(TriState.DISABLED) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.completed),
                        isSelected = filterCompleted == TriState.ENABLED_IS,
                        onClick = { settingsScreenModel.libraryPreferences.filterCompletedManga().set(TriState.ENABLED_IS) },
                    ),
                    FilterOptionData(
                        label = stringResource(MR.strings.ongoing),
                        isSelected = filterCompleted == TriState.ENABLED_NOT,
                        onClick = { settingsScreenModel.libraryPreferences.filterCompletedManga().set(TriState.ENABLED_NOT) },
                    ),
                ),
            ))
            if (trackers.isNotEmpty()) {
                add(FilterSectionData(
                    id = LibraryFilterId.TRACKED,
                    titleRes = MR.strings.action_filter_tracked,
                    items = trackers.flatMap { service ->
                        val filterTracker = settingsScreenModel.libraryPreferences.filterTrackedManga(service.id.toInt()).get()
                        listOf(
                            FilterOptionData(
                                label = "${service.name}: ${stringResource(MR.strings.all)}",
                                isSelected = filterTracker == TriState.DISABLED,
                                onClick = { settingsScreenModel.libraryPreferences.filterTrackedManga(service.id.toInt()).set(TriState.DISABLED) },
                            ),
                            FilterOptionData(
                                label = "${service.name}: ${stringResource(MR.strings.action_filter_tracked)}",
                                isSelected = filterTracker == TriState.ENABLED_IS,
                                onClick = { settingsScreenModel.libraryPreferences.filterTrackedManga(service.id.toInt()).set(TriState.ENABLED_IS) },
                            ),
                            FilterOptionData(
                                label = "${service.name}: ${stringResource(MR.strings.filter_not_tracked)}",
                                isSelected = filterTracker == TriState.ENABLED_NOT,
                                onClick = { settingsScreenModel.libraryPreferences.filterTrackedManga(service.id.toInt()).set(TriState.ENABLED_NOT) },
                            ),
                        )
                    },
                ))
            }
        }

        // Clear all filters
        val onClearFilters = {
            settingsScreenModel.libraryPreferences.filterDownloadedManga().set(TriState.DISABLED)
            settingsScreenModel.libraryPreferences.filterUnread().set(TriState.DISABLED)
            settingsScreenModel.libraryPreferences.filterStartedManga().set(TriState.DISABLED)
            settingsScreenModel.libraryPreferences.filterBookmarkedManga().set(TriState.DISABLED)
            settingsScreenModel.libraryPreferences.filterCompletedManga().set(TriState.DISABLED)
            trackers.forEach { service ->
                settingsScreenModel.libraryPreferences.filterTrackedManga(service.id.toInt()).set(TriState.DISABLED)
            }
        }

        val onClickRefresh: (Collection?) -> Boolean = { collection ->
            eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus.refreshRequested()
            MangaLibraryUpdateJob.startNow(context, collection)
        }

        val navigateUp: (() -> Unit)? = null

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
                navigateUp = navigateUp,
                searchEnabled = true,
                searchPlaceholderText = stringResource(MR.strings.search_hint_library),
                searchQuery = state.searchQuery,
                onSearchQueryChange = { query ->
                    screenModel.search(query)
                },
            )
        }

        // Read host padding for nav bar offset (used by persistent filter sheet)
        val hostPadding = eu.kanade.presentation.components.LocalHostScaffoldContentPadding.current
        val hostBottomForSheet = hostPadding?.calculateBottomPadding() ?: androidx.compose.ui.unit.Dp.Hairline

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
                        onMarkAsViewedClicked = { screenModel.markReadSelection(true) },
                        onMarkAsUnviewedClicked = { screenModel.markReadSelection(false) },
                        onDownloadClicked = null,
                        onDeleteClicked = screenModel::openDeleteMangaDialog,
                        isManga = true,
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
                    // Precompute sort labels for all collections (for continuous mode)
                    val sortLabelCache = state.collections.associate { it.id to stringResource(sortLabelResFor(it.sort.type)) }

                    MangaLibraryContent(
                        collections = state.collections,
                        searchQuery = state.searchQuery,
                        selection = state.selection,
                        contentPadding = resolvedContentPadding,
                        currentPage = { screenModel.activeCollectionIndex },
                        hasActiveFilters = state.hasActiveFilters,
                        showPageTabs = state.showCollectionTabs || !state.searchQuery.isNullOrEmpty(),
                        onChangeCurrentPage = { screenModel.activeCollectionIndex = it },
                        onMangaClicked = { mangaId ->
                            scope.launch {
                                val lockedOrders = getLockedReadingOrders.await(mangaId)
                                if (lockedOrders.isNotEmpty()) {
                                    lockDialog = mangaId to lockedOrders
                                } else {
                                    navigator.push(MangaScreen(mangaId))
                                }
                            }
                        },
                        onContinueReadingClicked = { it: LibraryManga ->
                            scope.launchIO {
                                val chapter = screenModel.getNextUnreadChapter(it.manga)
                                if (chapter != null) {
                                    context.startActivity(
                                        ReaderActivity.newIntent(
                                            context,
                                            chapter.mangaId,
                                            chapter.id,
                                        ),
                                    )
                                } else {
                                    snackbarHostState.showSnackbar(
                                        context.stringResource(MR.strings.no_next_chapter),
                                    )
                                }
                            }
                            Unit
                        }.takeIf { state.showMangaContinueButton },
                        onToggleSelection = screenModel::toggleSelection,
                        onToggleRangeSelection = {
                            screenModel.toggleRangeSelection(it)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onRefresh = onClickRefresh,
                        onGlobalSearchClicked = {
                            navigator.push(
                                GlobalMangaSearchScreen(screenModel.state.value.searchQuery ?: ""),
                            )
                        },
                        getNumberOfMangaForCollection = { state.getMangaCountForCollection(it) },
                        getDisplayMode = { screenModel.getDisplayMode() },
                        getColumnsForOrientation = {
                            screenModel.getColumnsPreferenceForCurrentOrientation(
                                it,
                            )
                        },
                        collectionDisplayMode = state.collectionDisplayMode,
                        getLibraryForPage = { state.getLibraryItemsByPage(it) },
                        onReorder = { mangaIds: List<Long> ->
                            val collection = state.collections.getOrNull(screenModel.activeCollectionIndex)
                            if (collection != null) {
                                scope.launchIO {
                                    screenModel.updateCustomOrder(collection.id, mangaIds)
                                }
                            }
                        }.takeIf {
                            val collection = state.collections.getOrNull(screenModel.activeCollectionIndex)
                            collection != null &&
                                collection.sort.type == MangaLibrarySort.Type.CustomOrder
                        },
                        sortLabel = state.collections.getOrNull(screenModel.activeCollectionIndex)
                            ?.let { collection ->
                                stringResource(sortLabelResFor(collection.sort.type))
                            },
                        sortDescending = state.collections.getOrNull(screenModel.activeCollectionIndex)
                            ?.let { collection -> !collection.sort.isAscending },
                        onSortClick = {
                            showSortSheet = true
                        },
                        showLibraryTitle = state.showLibraryTitle,
                        getReadingOrderLayer = { mangaId -> state.getReadingOrderLayer(mangaId) },
                        readingOrderMode = state.readingOrderMode,
                        getPreviousLayerMangaIds = if (state.readingOrderMode) {
                            {
                                state.readingOrderLayers.flatten()
                                    .map { it.id }
                                    .toSet()
                            }
                        } else {
                            null
                        },
                        isEntryLocked = { mangaId -> state.lockedReadingOrderEntryIds.contains(mangaId) },
                        showListAuthor = state.showListAuthor,
                        showListStatus = state.showListStatus,
                        getSortLabelForCollection = { collection ->
                            sortLabelCache[collection.id]
                        },
                        getSortDescendingForCollection = { collection ->
                            !collection.sort.isAscending
                        },
                        onSortClickForCollection = { collection ->
                            screenModel.activeCollectionIndex = state.collections.indexOf(collection)
                            showSortSheet = true
                        },
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
            is MangaLibraryScreenModel.Dialog.SettingsSheet -> run {
                val collection = state.collections.getOrNull(screenModel.activeCollectionIndex)
                if (collection == null) {
                    onDismissRequest()
                    return@run
                }
                MangaLibrarySettingsDialog(
                    onDismissRequest = onDismissRequest,
                    screenModel = settingsScreenModel,
                    collection = collection,
                    onClickReadingOrders = {
                        onDismissRequest()
                        navigator.push(eu.kanade.tachiyomi.ui.readingorder.ReadingOrderListScreen())
                    },
                )
            }
            is MangaLibraryScreenModel.Dialog.ChangeCollection -> {
                ChangeCollectionDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCollections = {
                        screenModel.clearSelection()
                        navigator.push(CollectionsTab)
                        CollectionsTab.showMangaCollection()
                    },
                    onConfirm = { include, exclude ->
                        screenModel.clearSelection()
                        screenModel.setMangaCollections(dialog.manga, include, exclude)
                    },
                )
            }
            is MangaLibraryScreenModel.Dialog.DeleteManga -> {
                DeleteLibraryEntryDialog(
                    containsLocalEntry = dialog.manga.any(Manga::isLocal),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteManga, deleteChapter ->
                        screenModel.removeMangas(dialog.manga, deleteManga, deleteChapter)
                        screenModel.clearSelection()
                    },
                    isManga = true,
                )
            }
            is MangaLibraryScreenModel.Dialog.ReadingOrderPicker -> {
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
            is MangaLibraryScreenModel.Dialog.ReadingOrderRemoveConfirm -> {
                ReadingOrderRemoveConfirmDialog(
                    entryTitle = dialog.manga.manga.title,
                    onDismiss = { screenModel.cancelRemoveDialog() },
                    onConfirm = { screenModel.confirmRemoveFromReadingOrder(dialog.manga) },
                )
            }
            is MangaLibraryScreenModel.Dialog.ReadingOrderMoveDepth -> {
                ReadingOrderMoveDepthDialog(
                    entryTitle = dialog.manga.manga.title,
                    fromDepth = dialog.fromDepth,
                    toDepth = dialog.toDepth,
                    onDismiss = { screenModel.cancelRemoveDialog() },
                    onConfirm = { screenModel.confirmMoveEntryDepth(dialog.manga) },
                )
            }
            null -> {}
        }

        lockDialog?.let { (mangaId, orders) ->
            ReadingOrderLockDialog(
                lockedOrders = orders,
                onDismiss = { lockDialog = null },
                onViewOrder = { orderId ->
                    lockDialog = null
                    navigator.push(ReadingOrderViewerScreen(orderId))
                },
                onViewEntry = {
                    lockDialog = null
                    navigator.push(MangaScreen(mangaId))
                },
                entryKindLabel = "Manga",
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
                    // Currently only categories (0) and ungrouped (5) are meaningful;
                    // tag/source/status/author/tracking/language need collection-building logic
                },
                onDismiss = { showGroupBySheet = false },
            )
        }

        // Display options sheet (3-tab modal)
        if (showDisplayOptionsSheet) {
            val currentDisplayMode by settingsScreenModel.libraryPreferences.displayMode().collectAsStateWithLifecycle()
            DisplayOptionsSheet(
                libraryPreferences = settingsScreenModel.libraryPreferences,
                libraryType = LibraryType.MANGA,
                onDismissRequest = { showDisplayOptionsSheet = false },
                onClickReadingOrders = {
                    showDisplayOptionsSheet = false
                    navigator.push(eu.kanade.tachiyomi.ui.readingorder.ReadingOrderListScreen())
                },
                currentDisplayMode = currentDisplayMode,
            )
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
                        isSelected = sortingMode == MangaLibrarySort.Type.Alphabetical,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirection(sortingMode, MangaLibrarySort.Type.Alphabetical, sortDescending)
                            settingsScreenModel.setSort(collection, MangaLibrarySort.Type.Alphabetical, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_total,
                        icon = Icons.Outlined.Numbers,
                        isSelected = sortingMode == MangaLibrarySort.Type.TotalChapters,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirection(sortingMode, MangaLibrarySort.Type.TotalChapters, sortDescending)
                            settingsScreenModel.setSort(collection, MangaLibrarySort.Type.TotalChapters, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_last_read,
                        icon = Icons.Outlined.History,
                        isSelected = sortingMode == MangaLibrarySort.Type.LastRead,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirection(sortingMode, MangaLibrarySort.Type.LastRead, sortDescending)
                            settingsScreenModel.setSort(collection, MangaLibrarySort.Type.LastRead, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = AYMR.strings.action_sort_last_manga_update,
                        icon = Icons.Outlined.Refresh,
                        isSelected = sortingMode == MangaLibrarySort.Type.LastUpdate,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirection(sortingMode, MangaLibrarySort.Type.LastUpdate, sortDescending)
                            settingsScreenModel.setSort(collection, MangaLibrarySort.Type.LastUpdate, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_unread_count,
                        icon = Icons.Outlined.NewReleases,
                        isSelected = sortingMode == MangaLibrarySort.Type.UnreadCount,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirection(sortingMode, MangaLibrarySort.Type.UnreadCount, sortDescending)
                            settingsScreenModel.setSort(collection, MangaLibrarySort.Type.UnreadCount, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_latest_chapter,
                        icon = Icons.Outlined.Explore,
                        isSelected = sortingMode == MangaLibrarySort.Type.LatestChapter,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirection(sortingMode, MangaLibrarySort.Type.LatestChapter, sortDescending)
                            settingsScreenModel.setSort(collection, MangaLibrarySort.Type.LatestChapter, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_chapter_fetch_date,
                        icon = Icons.Outlined.CalendarMonth,
                        isSelected = sortingMode == MangaLibrarySort.Type.ChapterFetchDate,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirection(sortingMode, MangaLibrarySort.Type.ChapterFetchDate, sortDescending)
                            settingsScreenModel.setSort(collection, MangaLibrarySort.Type.ChapterFetchDate, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_date_added,
                        icon = Icons.Outlined.Grade,
                        isSelected = sortingMode == MangaLibrarySort.Type.DateAdded,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirection(sortingMode, MangaLibrarySort.Type.DateAdded, sortDescending)
                            settingsScreenModel.setSort(collection, MangaLibrarySort.Type.DateAdded, dir)
                        },
                    ))
                    if (trackers.isNotEmpty()) {
                        add(SortModeOption(
                            labelRes = MR.strings.action_sort_tracker_score,
                            icon = Icons.Outlined.FavoriteBorder,
                            isSelected = sortingMode == MangaLibrarySort.Type.TrackerMean,
                            isDescending = sortDescending,
                            isDirectional = true,
                            onClick = {
                                val dir = toggleDirection(sortingMode, MangaLibrarySort.Type.TrackerMean, sortDescending)
                                settingsScreenModel.setSort(collection, MangaLibrarySort.Type.TrackerMean, dir)
                            },
                        ))
                    }
                    add(SortModeOption(
                        labelRes = AYMR.strings.action_sort_custom_order,
                        icon = Icons.Outlined.SwapVert,
                        isSelected = sortingMode == MangaLibrarySort.Type.CustomOrder,
                        isDescending = sortDescending,
                        isDirectional = true,
                        onClick = {
                            val dir = toggleDirection(sortingMode, MangaLibrarySort.Type.CustomOrder, sortDescending)
                            settingsScreenModel.setSort(collection, MangaLibrarySort.Type.CustomOrder, dir)
                        },
                    ))
                    add(SortModeOption(
                        labelRes = MR.strings.action_sort_random,
                        icon = Icons.Outlined.Check,
                        isSelected = sortingMode == MangaLibrarySort.Type.Random,
                        isDescending = false,
                        isDirectional = false,
                        onClick = {
                            settingsScreenModel.setSort(collection, MangaLibrarySort.Type.Random, MangaLibrarySort.Direction.Ascending)
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

        // Settings dialog (kept for non-sort uses)
        if (showSettingsDialog) {
            val collection = state.collections.getOrNull(screenModel.activeCollectionIndex)
            if (collection != null) {
                MangaLibrarySettingsDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    screenModel = settingsScreenModel,
                    collection = collection,
                    onClickReadingOrders = {
                        showSettingsDialog = false
                        navigator.push(eu.kanade.tachiyomi.ui.readingorder.ReadingOrderListScreen())
                    },
                )
            } else {
                showSettingsDialog = false
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

private fun sortLabelResFor(type: MangaLibrarySort.Type): dev.icerock.moko.resources.StringResource {
    return when (type) {
        MangaLibrarySort.Type.Alphabetical -> MR.strings.action_sort_alpha
        MangaLibrarySort.Type.LastRead -> MR.strings.action_sort_last_read
        MangaLibrarySort.Type.LastUpdate -> AYMR.strings.action_sort_last_manga_update
        MangaLibrarySort.Type.UnreadCount -> MR.strings.action_sort_unread_count
        MangaLibrarySort.Type.TotalChapters -> MR.strings.action_sort_total
        MangaLibrarySort.Type.LatestChapter -> MR.strings.action_sort_latest_chapter
        MangaLibrarySort.Type.ChapterFetchDate -> MR.strings.action_sort_chapter_fetch_date
        MangaLibrarySort.Type.DateAdded -> MR.strings.action_sort_date_added
        MangaLibrarySort.Type.TrackerMean -> MR.strings.action_sort_tracker_score
        MangaLibrarySort.Type.CustomOrder -> AYMR.strings.action_sort_custom_order
        MangaLibrarySort.Type.ReadingOrder -> AYMR.strings.reading_order
        MangaLibrarySort.Type.Random -> MR.strings.action_sort_random
    }
}

private fun toggleDirection(
    currentMode: MangaLibrarySort.Type,
    targetMode: MangaLibrarySort.Type,
    currentlyDescending: Boolean,
): MangaLibrarySort.Direction {
    return if (currentMode == targetMode) {
        if (currentlyDescending) MangaLibrarySort.Direction.Ascending
        else MangaLibrarySort.Direction.Descending
    } else {
        if (currentlyDescending) MangaLibrarySort.Direction.Descending
        else MangaLibrarySort.Direction.Ascending
    }
}
