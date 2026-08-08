package eu.kanade.tachiyomi.ui.library.anime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
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
import eu.kanade.presentation.library.DeleteLibraryEntryDialog
import eu.kanade.presentation.library.anime.AnimeLibraryContent
import eu.kanade.presentation.library.anime.AnimeLibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryToolbar
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
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
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

        val onClickRefresh: (Collection?) -> Boolean = { collection ->
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
            useSharedTopBar(
                title = "${state.selection.size}",
                actions = persistentListOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_all),
                        icon = Icons.Outlined.SelectAll,
                        onClick = { screenModel.selectAll(screenModel.activeCollectionIndex) },
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_inverse),
                        icon = Icons.Outlined.FlipToBack,
                        onClick = { screenModel.invertSelection(screenModel.activeCollectionIndex) },
                    ),
                ),
                navigateUp = screenModel::clearSelection,
            )
        } else {
            val libraryActions = buildList {
                add(AppBar.Action(
                    title = stringResource(MR.strings.action_filter),
                    icon = Icons.Outlined.FilterList,
                    iconTint = if (state.hasActiveFilters) MaterialTheme.colorScheme.active else null,
                    onClick = screenModel::showSettingsDialog,
                ))
                add(AppBar.OverflowAction(
                    title = stringResource(MR.strings.action_update_library),
                    onClick = { onClickRefresh(null) },
                ))
                add(AppBar.OverflowAction(
                    title = stringResource(MR.strings.action_update_collection),
                    onClick = { onClickRefresh(state.collections[screenModel.activeCollectionIndex]) },
                ))
                add(AppBar.OverflowAction(
                    title = stringResource(MR.strings.action_open_random_manga),
                    onClick = {
                        scope.launch {
                            val randomItem = screenModel.getRandomAnimelibItemForCurrentCollection()
                            if (randomItem != null) {
                                navigator.push(AnimeScreen(randomItem.libraryAnime.anime.id))
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(MR.strings.information_no_entries_found),
                                )
                            }
                        }
                    },
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

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {},
            bottomBar = {
                LibraryBottomActionMenu(
                    visible = state.selectionMode,
                    onChangeCollectionClicked = screenModel::openChangeCollectionDialog,
                    onMarkAsViewedClicked = { screenModel.markSeenSelection(true) },
                    onMarkAsUnviewedClicked = { screenModel.markSeenSelection(false) },
                    onDownloadClicked = screenModel::runDownloadActionSelection
                        .takeIf { state.selection.fastAll { !it.anime.isLocal() } },
                    onDeleteClicked = screenModel::openDeleteAnimeDialog,
                    isManga = false,
                )
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
            val resolvedContentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = contentPadding.calculateTopPadding() + hostTop,
                bottom = contentPadding.calculateBottomPadding() + hostBottom + overlayExtraBottom,
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
                        onAnimeClicked = { navigator.push(AnimeScreen(it)) },
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
                    ) { state.getAnimelibItemsByPage(it) }
                }
            }
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
            null -> {}
        }

        BackHandler(enabled = state.selectionMode || state.searchQuery != null) {
            when {
                state.selectionMode -> screenModel.clearSelection()
                state.searchQuery != null -> screenModel.search(null)
            }
        }

        LaunchedEffect(state.selectionMode, state.dialog) {
            HomeScreen.showBottomNav(!state.selectionMode)
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }

        LaunchedEffect(Unit) {
            launch { queryEvent.receiveAsFlow().collect(screenModel::search) }
            launch { requestSettingsSheetEvent.receiveAsFlow().collectLatest { screenModel.showSettingsDialog() } }
        }
    }

    // For invoking search from other screen
    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)

    // For opening settings sheet in LibraryController
    private val requestSettingsSheetEvent = Channel<Unit>()
    private suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)
}
