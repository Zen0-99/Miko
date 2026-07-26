package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifNovelSourcesLoaded
import eu.kanade.presentation.browse.RemoveEntryDialog
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.browse.novel.BrowseNovelSourceContent
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.browse.novel.MissingNovelSourceScreen
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.browse.novel.components.BrowseNovelSourceToolbar
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.entries.novel.DuplicateNovelDialog
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.more.components.SavedSearchesDialog
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialog
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

data class BrowseNovelSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        val sourcesLoaded = ifNovelSourcesLoaded()
        Log.d("NovelSearch", "[BrowseNovelSourceScreen] Content() - sourceId=$sourceId, listingQuery='$listingQuery', sourcesLoaded=$sourcesLoaded")
        if (!sourcesLoaded) {
            Log.w("NovelSearch", "[BrowseNovelSourceScreen] Content() - novel sources NOT loaded, showing LoadingScreen")
            LoadingScreen()
            return
        }

        val screenModel = rememberScreenModel { BrowseNovelSourceScreenModel(sourceId, listingQuery) }
        val state by screenModel.state.collectAsState()

        val navigator = LocalNavigator.currentOrThrow
        val navigateUp: () -> Unit = {
            Log.d("NovelSearch", "[BrowseNovelSourceScreen] navigateUp called - isUserQuery=${state.isUserQuery}, toolbarQuery='${state.toolbarQuery}'")
            Log.d("NovelSearch", "[BrowseNovelSourceScreen] navigateUp stacktrace:", Throwable("navigateUp caller"))
            when {
                !state.isUserQuery && state.toolbarQuery != null -> {
                    Log.d("NovelSearch", "[BrowseNovelSourceScreen] navigateUp - clearing toolbar query (was non-user query)")
                    screenModel.setToolbarQuery(null)
                }
                else -> {
                    Log.d("NovelSearch", "[BrowseNovelSourceScreen] navigateUp - navigator.pop() (closing source browse)")
                    navigator.pop()
                }
            }
        }

        if (screenModel.source is StubNovelSource) {
            Log.w("NovelSearch", "[BrowseNovelSourceScreen] Content() - source is StubNovelSource (not installed): ${screenModel.source}, showing MissingNovelSourceScreen")
            MissingNovelSourceScreen(
                source = screenModel.source,
                navigateUp = navigateUp,
            )
            return
        }
        Log.d("NovelSearch", "[BrowseNovelSourceScreen] Content() - source loaded: ${screenModel.source.name} (id=${screenModel.source.id}), listing=${state.listing}, toolbarQuery='${state.toolbarQuery}'")

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val snackbarHostState = remember { SnackbarHostState() }

        val onWebViewClick = f@{
            val source = screenModel.source as? NovelHttpSource ?: return@f
            navigator.push(
                WebViewScreen(
                    url = source.baseUrl,
                    initialTitle = source.name,
                    sourceId = source.id,
                ),
            )
        }

        LaunchedEffect(screenModel.source) {
            assistUrl = (screenModel.source as? NovelHttpSource)?.baseUrl
        }

        var topBarHeight by remember { mutableIntStateOf(0) }
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .onSizeChanged { topBarHeight = it.height },
                ) {
                    BrowseNovelSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = screenModel::setToolbarQuery,
                        source = screenModel.source,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        navigateUp = navigateUp,
                        onWebViewClick = onWebViewClick,
                        onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                        onSettingsClick = { },
                        onSearch = screenModel::search,
                    )

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = state.listing == Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(Listing.Popular)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = { Text(text = stringResource(MR.strings.popular)) },
                        )
                        if ((screenModel.source as NovelCatalogueSource).supportsLatest) {
                            FilterChip(
                                selected = state.listing == Listing.Latest,
                                onClick = {
                                    screenModel.resetFilters()
                                    screenModel.setListing(Listing.Latest)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = { Text(text = stringResource(MR.strings.latest)) },
                            )
                        }
                        if (state.filters.isNotEmpty()) {
                            FilterChip(
                                selected = state.listing is Listing.Search,
                                onClick = screenModel::openFilterSheet,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = { Text(text = stringResource(MR.strings.action_filter)) },
                            )
                        }
                        if (state.isUserQuery || state.filters.isNotEmpty()) {
                            FilterChip(
                                selected = false,
                                onClick = screenModel::openSavedSearches,
                                label = { Text(text = stringResource(MR.strings.saved_searches)) },
                            )
                        }
                    }
                    HorizontalDivider()
                }
            },
            snackbarHost = { AchievementStyledSnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            BrowseNovelSourceContent(
                source = screenModel.source,
                novelList = screenModel.novelPagerFlowFlow.collectAsLazyPagingItems(),
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                entries = screenModel.getColumnsPreferenceForCurrentOrientation(LocalConfiguration.current.orientation),
                topBarHeight = topBarHeight,
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = onWebViewClick,
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onNovelClick = {
                    Log.d("NovelSearch", "[BrowseNovelSourceScreen] onNovelClick - '${it.title}' (id=${it.id}), pushing NovelScreen")
                    navigator.push(NovelScreen(it.id, true))
                },
                onNovelLongClick = { novel ->
                    scope.launchIO {
                        val duplicateNovel = screenModel.getDuplicateLibraryNovel(novel)
                        when {
                            novel.favorite -> screenModel.setDialog(
                                BrowseNovelSourceScreenModel.Dialog.RemoveNovel(novel),
                            )
                            duplicateNovel != null -> screenModel.setDialog(
                                BrowseNovelSourceScreenModel.Dialog.AddDuplicateNovel(
                                    novel,
                                    duplicateNovel,
                                ),
                            )
                            else -> screenModel.addFavorite(novel)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is BrowseNovelSourceScreenModel.Dialog.Filter -> {
                SourceFilterNovelDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = { screenModel.search(filters = state.filters) },
                    onUpdate = screenModel::setFilters,
                )
            }
            is BrowseNovelSourceScreenModel.Dialog.SavedSearches -> {
                val savedSearches by screenModel.savedSearchesFlow.collectAsState()
                SavedSearchesDialog(
                    onDismissRequest = onDismissRequest,
                    savedSearches = savedSearches,
                    onSaveSearch = screenModel::saveSearch,
                    onApplySearch = screenModel::applySavedSearch,
                    onDeleteSearch = screenModel::deleteSavedSearch,
                )
            }
            is BrowseNovelSourceScreenModel.Dialog.AddDuplicateNovel -> {
                DuplicateNovelDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.novel) },
                    onOpenNovel = { navigator.push(NovelScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        screenModel.setDialog(
                            BrowseNovelSourceScreenModel.Dialog.Migrate(dialog.novel, dialog.duplicate),
                        )
                    },
                )
            }
            is BrowseNovelSourceScreenModel.Dialog.Migrate -> {
                MigrateNovelDialog(
                    oldNovel = dialog.oldNovel,
                    newNovel = dialog.newNovel,
                    screenModel = MigrateNovelDialogScreenModel(),
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(NovelScreen(dialog.oldNovel.id)) },
                    onPopScreen = {
                        onDismissRequest()
                    },
                )
            }
            is BrowseNovelSourceScreenModel.Dialog.RemoveNovel -> {
                RemoveEntryDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.changeNovelFavorite(dialog.novel)
                    },
                    entryToRemove = dialog.novel.title,
                )
            }
            is BrowseNovelSourceScreenModel.Dialog.ChangeNovelCollection -> {}
            null -> {}
        }

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow()
                .collectLatest {
                    Log.d("NovelSearch", "[BrowseNovelSourceScreen] queryEvent received: $it")
                    when (it) {
                        is SearchType.Genre -> screenModel.searchGenre(it.txt)
                        is SearchType.Text -> screenModel.search(it.txt)
                    }
                }
        }
    }

    suspend fun search(query: String) = queryEvent.send(SearchType.Text(query))
    suspend fun searchGenre(name: String) = queryEvent.send(SearchType.Genre(name))

    companion object {
        private val queryEvent = Channel<SearchType>()
    }

    sealed class SearchType(val txt: String) {
        class Text(txt: String) : SearchType(txt)
        class Genre(txt: String) : SearchType(txt)
    }
}
