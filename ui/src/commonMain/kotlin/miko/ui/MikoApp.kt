package miko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import miko.ui.carrier.BrowseCarrier
import miko.ui.details.EntryDetailsScreen
import miko.ui.details.EntryDetailsScreenModel
import miko.ui.extensions.ExtensionCatalog
import miko.ui.extensions.ExtensionsScreen
import miko.ui.extensions.ExtensionsScreenModel
import miko.ui.feed.SourceFeedScreen
import miko.ui.feed.SourceFeedScreenModel
import miko.ui.library.LibraryScreen
import miko.ui.library.LibraryScreenModel
import miko.ui.navigation.Destination
import miko.ui.navigation.Navigator
import miko.ui.navigation.rememberNavigator
import miko.ui.player.EpisodePlayerScreen
import miko.ui.player.EpisodePlayerScreenModel
import miko.ui.reader.MangaReaderScreen
import miko.ui.reader.MangaReaderScreenModel
import miko.ui.reader.ReaderScreen
import miko.ui.reader.ReaderScreenModel
import miko.ui.repos.ExtensionReposScreen
import miko.ui.repos.ExtensionReposScreenModel
import miko.ui.search.GlobalSearchScreen
import miko.ui.search.GlobalSearchScreenModel
import miko.ui.sources.SourcesScreen
import miko.ui.sources.SourcesScreenModel
import miko.ui.updates.UpdatesScreen
import miko.ui.updates.UpdatesScreenModel
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Shared app shell: hand-rolled navigation host + bottom tabs (Browse /
 * Library) at the root. Platform entry points (ios-app's MainViewController,
 * the Android shell at convergence) call this with their [MikoUiGraph].
 */
enum class BrowseMode { BROWSE, EXTENSIONS }

@Composable
fun MikoApp(graph: MikoUiGraph) {
    val navigator = rememberNavigator(Destination.Browse)

    // Browse-tab search state — mirrors Android's SharedTopBarState fields:
    // non-null query = search open ("" = just opened). Reset when leaving
    // Browse, same as Android's per-tab resetSearch.
    var browseSearchQuery by remember { mutableStateOf<String?>(null) }
    var browseMode by remember { mutableStateOf(BrowseMode.BROWSE) }
    // The app-wide content carrier — Android's contentMode: one selection
    // (shows/manga/books) drives the sources list, feed, details, search,
    // and library. Shared across tabs like Android's Home switch.
    val browseCarrier = remember { MutableStateFlow(BrowseCarrier.BOOKS) }
    LaunchedEffect(navigator.current) {
        if (navigator.current != Destination.Browse) browseSearchQuery = null
    }

    // The global-search screen's model lives at app scope so the top bar's
    // search field can submit into it (Android embeds the toolbar in the
    // screen's own Scaffold; the shared shell hoists it instead).
    val globalSearchDest = navigator.current as? Destination.GlobalSearch
    val globalSearchModel = remember(globalSearchDest) {
        globalSearchDest?.let {
            GlobalSearchScreenModel(graph.binding(it.carrier), it.query)
        }
    }
    DisposableEffect(globalSearchModel) {
        onDispose { globalSearchModel?.dispose() }
    }

    // Source-feed model + local search — hoisted so the top bar can reach it
    // (Android's BrowseSourceScreen owns its toolbar; the shared shell's
    // bar lives above the nav host instead).
    val feedDest = navigator.current as? Destination.SourceFeed
    val feedModel = remember(feedDest) {
        feedDest?.let {
            SourceFeedScreenModel(graph.binding(it.carrier), it.sourceId)
        }
    }
    DisposableEffect(feedModel) {
        onDispose { feedModel?.dispose() }
    }
    var feedSearchQuery by remember(feedDest) { mutableStateOf<String?>(null) }
    LaunchedEffect(feedSearchQuery) {
        // Android debounces the feed query 300ms inside the screen model.
        kotlinx.coroutines.delay(300L)
        feedModel?.setQuery(feedSearchQuery.orEmpty())
    }

    // Browse-tab catalog + screen models live at app scope — Android's
    // rememberScreenModel keeps them alive across navigation, so the lists
    // don't refetch every time the tab remounts after a push/pop.
    val browseCatalog = remember {
        ExtensionCatalog(
            jsPluginApi = graph.jsPluginApi,
            pluginRepository = graph.novelPluginRepository,
        )
    }
    val sourcesModel = remember {
        SourcesScreenModel(graph, browseCatalog, browseCarrier)
    }
    val extensionsModel = remember {
        ExtensionsScreenModel(
            catalog = browseCatalog,
            installer = graph.novelPluginInstaller,
            carrierFlow = browseCarrier,
        )
    }
    // Updates tab model lives at app scope too — the feeds are long-lived
    // DB subscriptions; recreating them per visit would flicker the list.
    val updatesModel = remember {
        UpdatesScreenModel(graph, browseCarrier)
    }
    // Updates-toolbar search — Android's HistoriesTab subscribe(query).
    var updatesSearchQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(updatesSearchQuery) {
        updatesModel.setHistoryQuery(updatesSearchQuery.orEmpty())
    }

    MikoTheme {
        Scaffold(
            topBar = {
                val current = navigator.current
                when {
                    navigator.canPop -> {
                        if (current is Destination.GlobalSearch) {
                            // Always-open search field — Android's
                            // GlobalNovelSearchToolbar equivalent.
                            var query by remember { mutableStateOf(current.query) }
                            MikoTopBar(
                                title = "",
                                canSearch = true,
                                searchQuery = query,
                                onSearchQueryChange = { query = it ?: current.query },
                                onSearch = { globalSearchModel?.search(it) },
                                searchPlaceholderText = stringResource(MR.strings.action_search_hint),
                                navigateUp = { navigator.pop() },
                                actions = {},
                            )
                        } else if (current is Destination.SourceFeed) {
                            // Android's BrowseNovelSourceScreen bar: back +
                            // source name + trailing search icon (SearchToolbar
                            // style) + display-mode dropdown. Back while
                            // searching resets to the listing, same as Android.
                            MikoTopBar(
                                title = current.sourceName,
                                canSearch = true,
                                searchQuery = feedSearchQuery,
                                onSearchQueryChange = { feedSearchQuery = it },
                                onSearch = { feedModel?.setQuery(it) },
                                searchPlaceholderText = stringResource(MR.strings.action_search_hint),
                                navigateUp = {
                                    if (feedSearchQuery != null) {
                                        feedSearchQuery = null
                                    } else {
                                        navigator.pop()
                                    }
                                },
                                searchStyle = MikoTopBarSearchStyle.TrailingIcon,
                                actions = {
                                    val feedDisplayMode = feedModel?.state
                                        ?.collectAsStateWithLifecycle()
                                        ?.value?.displayMode
                                        ?: LibraryDisplayMode.default
                                    FeedDisplayModeAction(
                                        displayMode = feedDisplayMode,
                                        onDisplayModeChange = { feedModel?.setDisplayMode(it) },
                                    )
                                },
                            )
                        } else {
                            MikoTopBar(
                                title = screenTitle(current),
                                canSearch = false,
                                searchQuery = null,
                                onSearchQueryChange = {},
                                onSearch = {},
                                searchPlaceholderText = null,
                                navigateUp = { navigator.pop() },
                                actions = {},
                            )
                        }
                    }
                    else -> MikoTopBar(
                        title = screenTitle(current),
                        canSearch = current == Destination.Browse ||
                            current == Destination.Updates,
                        searchQuery = if (current == Destination.Updates) {
                            updatesSearchQuery
                        } else {
                            browseSearchQuery
                        },
                        onSearchQueryChange = { query ->
                            if (current == Destination.Updates) {
                                updatesSearchQuery = query
                            } else {
                                browseSearchQuery = query
                            }
                        },
                        onSearch = { query ->
                            if (browseMode == BrowseMode.BROWSE) {
                                // Android pushes GlobalSearchScreen and
                                // closes the browse search.
                                navigator.push(
                                    Destination.GlobalSearch(query, browseCarrier.value),
                                )
                                browseSearchQuery = null
                            }
                        },
                        searchPlaceholderText = when (current) {
                            Destination.Browse -> stringResource(
                                if (browseMode == BrowseMode.EXTENSIONS) {
                                    MR.strings.search_hint_extensions
                                } else {
                                    MR.strings.search_hint_browse
                                },
                            )
                            Destination.Updates -> stringResource(
                                if (updatesModel.state
                                        .collectAsStateWithLifecycle().value.mode ==
                                    UpdatesScreenModel.Mode.History
                                ) {
                                    MR.strings.search_hint_history
                                } else {
                                    MR.strings.search_hint_updates
                                },
                            )
                            else -> null
                        },
                        navigateUp = null,
                        actions = {
                            if (current == Destination.Browse) {
                                BrowseOverflowMenu(
                                    onOpenExtensionRepos = {
                                        navigator.push(Destination.ExtensionRepos)
                                    },
                                )
                            }
                        },
                        pillContent = if (current == Destination.Browse) {
                            {
                                BrowseModePill(
                                    browseMode = browseMode,
                                    onModeChange = { browseMode = it },
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            },
            bottomBar = {
                if (!navigator.canPop) {
                    // Android's nav tail order: Library | Updates | Browse.
                    NavigationBar {
                        NavigationBarItem(
                            selected = navigator.current == Destination.Library,
                            onClick = { navigator.replace(Destination.Library) },
                            icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                            label = { Text(stringResource(MR.strings.label_library)) },
                        )
                        NavigationBarItem(
                            selected = navigator.current == Destination.Updates,
                            onClick = { navigator.replace(Destination.Updates) },
                            icon = { Icon(Icons.Filled.NewReleases, contentDescription = null) },
                            label = { Text(stringResource(MR.strings.label_recent_updates)) },
                        )
                        NavigationBarItem(
                            selected = navigator.current == Destination.Browse,
                            onClick = { navigator.replace(Destination.Browse) },
                            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            label = { Text(stringResource(MR.strings.browse)) },
                        )
                    }
                }
            },
        ) { padding ->
            when (val destination = navigator.current) {
                Destination.Browse -> BrowseDestination(
                    navigator = navigator,
                    sourcesModel = sourcesModel,
                    extensionsModel = extensionsModel,
                    browseMode = browseMode,
                    searchQuery = browseSearchQuery,
                    modifier = Modifier.padding(padding),
                )
                Destination.Library -> LibraryDestination(
                    graph = graph,
                    navigator = navigator,
                    carrierFlow = browseCarrier,
                    modifier = Modifier.padding(padding),
                )
                Destination.Updates -> UpdatesDestination(
                    model = updatesModel,
                    navigator = navigator,
                    carrierFlow = browseCarrier,
                    modifier = Modifier.padding(padding),
                )
                is Destination.SourceFeed -> SourceFeedDestination(
                    model = feedModel,
                    navigator = navigator,
                    sourceId = destination.sourceId,
                    carrier = destination.carrier,
                    modifier = Modifier.padding(padding),
                )
                is Destination.Details -> DetailsDestination(
                    graph = graph,
                    navigator = navigator,
                    destination = destination,
                    modifier = Modifier.padding(padding),
                )
                Destination.ExtensionRepos -> ExtensionReposDestination(
                    graph = graph,
                    modifier = Modifier.padding(padding),
                )
                is Destination.GlobalSearch -> GlobalSearchDestination(
                    model = globalSearchModel,
                    navigator = navigator,
                    carrier = destination.carrier,
                    modifier = Modifier.padding(padding),
                )
                is Destination.Reader -> ReaderDestination(
                    graph = graph,
                    destination = destination,
                    modifier = Modifier.padding(padding),
                )
                is Destination.MangaReader -> MangaReaderDestination(
                    graph = graph,
                    destination = destination,
                    modifier = Modifier.padding(padding),
                )
                is Destination.EpisodePlayer -> EpisodePlayerDestination(
                    graph = graph,
                    destination = destination,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun screenTitle(destination: Destination): String = when (destination) {
    Destination.Browse -> stringResource(MR.strings.browse)
    Destination.Library -> stringResource(MR.strings.label_library)
    Destination.Updates -> stringResource(MR.strings.label_recent_updates)
    Destination.ExtensionRepos -> stringResource(MR.strings.label_extension_repos)
    is Destination.SourceFeed -> destination.sourceName
    is Destination.GlobalSearch -> stringResource(MR.strings.action_search_hint)
    is Destination.Details -> destination.title
    is Destination.Reader -> destination.chapterUrl
    is Destination.MangaReader -> destination.chapterUrl
    is Destination.EpisodePlayer -> destination.episodeUrl
}

/**
 * Android's display-mode toolbar action: a ViewModule/ViewList icon that
 * opens a dropdown of radio items (ComfortableGrid / CompactGrid / List).
 */
@Composable
private fun FeedDisplayModeAction(
    displayMode: LibraryDisplayMode,
    onDisplayModeChange: (LibraryDisplayMode) -> Unit,
) {
    var selectingDisplayMode by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { selectingDisplayMode = true }) {
            Icon(
                imageVector = if (displayMode == LibraryDisplayMode.List) {
                    Icons.AutoMirrored.Filled.ViewList
                } else {
                    Icons.Filled.ViewModule
                },
                contentDescription = stringResource(MR.strings.action_display_mode),
            )
        }
        DropdownMenu(
            expanded = selectingDisplayMode,
            onDismissRequest = { selectingDisplayMode = false },
        ) {
            listOf(
                MR.strings.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
                MR.strings.action_display_grid to LibraryDisplayMode.CompactGrid,
                MR.strings.action_display_list to LibraryDisplayMode.List,
            ).forEach { (labelRes, mode) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = {
                        selectingDisplayMode = false
                        onDisplayModeChange(mode)
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = if (displayMode == mode) {
                                Icons.Outlined.RadioButtonChecked
                            } else {
                                Icons.Outlined.RadioButtonUnchecked
                            },
                            contentDescription = null,
                            tint = if (displayMode == mode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun BrowseOverflowMenu(onOpenExtensionRepos: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.label_extension_repos)) },
                onClick = {
                    expanded = false
                    onOpenExtensionRepos()
                },
            )
        }
    }
}

/**
 * Browse tab content — sources list always mounted underneath (Android keeps
 * the source tab composed during search for icon/scroll preservation); the
 * extensions list overlays it as opaque search content while EXTENSIONS
 * mode is active. The BROWSE|EXTENSIONS pill itself renders inside the top
 * bar, appearing only while search is open — same as Android.
 */
@Composable
private fun BrowseDestination(
    navigator: Navigator,
    sourcesModel: SourcesScreenModel,
    extensionsModel: ExtensionsScreenModel,
    browseMode: BrowseMode,
    searchQuery: String?,
    modifier: Modifier = Modifier,
) {
    // Push the live query into the extension model only while EXTENSIONS
    // search is active — mirrors Android's LaunchedEffect wiring.
    LaunchedEffect(searchQuery, browseMode) {
        extensionsModel.search(if (browseMode == BrowseMode.EXTENSIONS) searchQuery else null)
    }

    Box(modifier = modifier.fillMaxSize()) {
        SourcesScreen(
            model = sourcesModel,
            extensionsModel = extensionsModel,
            onSourceClick = { sourceId, name, carrier ->
                navigator.push(Destination.SourceFeed(sourceId, name, carrier))
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Search overlay — extensions list while EXTENSIONS mode is active.
        // Opaque background hides the sources tab beneath (Android parity).
        val showExtensionOverlay = searchQuery != null && browseMode == BrowseMode.EXTENSIONS
        if (showExtensionOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                ExtensionsScreen(
                    model = extensionsModel,
                    onOpenExtensionRepos = { navigator.push(Destination.ExtensionRepos) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Port of the Android `BrowseSearchPill` — two rounded segments in a subtle
 * container; selected segment gets a primary-tinted background + bold text.
 */
@Composable
private fun BrowseModePill(
    browseMode: BrowseMode,
    onModeChange: (BrowseMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        stringResource(MR.strings.browse) to BrowseMode.BROWSE,
        stringResource(MR.strings.label_extensions) to BrowseMode.EXTENSIONS,
    )
    val pillShape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(pillShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (label, mode) ->
            val isSelected = browseMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(pillShape)
                    .then(
                        if (isSelected) {
                            Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onModeChange(mode) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ExtensionReposDestination(
    graph: MikoUiGraph,
    modifier: Modifier = Modifier,
) {
    val model = remember {
        ExtensionReposScreenModel(
            getAnimeExtensionRepo = graph.getAnimeExtensionRepo,
            getMangaExtensionRepo = graph.getMangaExtensionRepo,
            getNovelExtensionRepo = graph.getNovelExtensionRepo,
            createAnimeExtensionRepo = graph.createAnimeExtensionRepo,
            createMangaExtensionRepo = graph.createMangaExtensionRepo,
            createNovelExtensionRepo = graph.createNovelExtensionRepo,
            deleteAnimeExtensionRepo = graph.deleteAnimeExtensionRepo,
            deleteMangaExtensionRepo = graph.deleteMangaExtensionRepo,
            deleteNovelExtensionRepo = graph.deleteNovelExtensionRepo,
            replaceAnimeExtensionRepo = graph.replaceAnimeExtensionRepo,
            replaceMangaExtensionRepo = graph.replaceMangaExtensionRepo,
            replaceNovelExtensionRepo = graph.replaceNovelExtensionRepo,
            updateAnimeExtensionRepo = graph.updateAnimeExtensionRepo,
            updateMangaExtensionRepo = graph.updateMangaExtensionRepo,
            updateNovelExtensionRepo = graph.updateNovelExtensionRepo,
            extensionRepoService = graph.extensionRepoService,
        )
    }
    DisposableEffect(model) {
        onDispose { model.dispose() }
    }
    ExtensionReposScreen(model = model, modifier = modifier.fillMaxSize())
}

@Composable
private fun GlobalSearchDestination(
    model: GlobalSearchScreenModel?,
    navigator: Navigator,
    carrier: BrowseCarrier,
    modifier: Modifier = Modifier,
) {
    if (model != null) {
        GlobalSearchScreen(
            model = model,
            onSourceClick = { sourceId, name ->
                navigator.push(Destination.SourceFeed(sourceId, name, carrier))
            },
            onEntryClick = { sourceId, entryUrl, title ->
                navigator.push(Destination.Details(sourceId, entryUrl, title, carrier))
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SourceFeedDestination(
    model: SourceFeedScreenModel?,
    navigator: Navigator,
    sourceId: Long,
    carrier: BrowseCarrier,
    modifier: Modifier = Modifier,
) {
    if (model != null) {
        SourceFeedScreen(
            model = model,
            onEntryClick = { entry ->
                navigator.push(Destination.Details(sourceId, entry.url, entry.title, carrier))
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DetailsDestination(
    graph: MikoUiGraph,
    navigator: Navigator,
    destination: Destination.Details,
    modifier: Modifier = Modifier,
) {
    val sourceId = destination.sourceId
    val entryKey = destination.entryKey
    val carrier = destination.carrier
    val model = remember(sourceId, entryKey, carrier) {
        EntryDetailsScreenModel(graph.binding(carrier), sourceId, entryKey)
    }
    DisposableEffect(model) {
        onDispose { model.dispose() }
    }
    EntryDetailsScreen(
        model = model,
        carrier = carrier,
        onItemClick = { item ->
            navigator.push(
                when (carrier) {
                    BrowseCarrier.SHOWS ->
                        Destination.EpisodePlayer(sourceId, entryKey, item.url)
                    BrowseCarrier.MANGA ->
                        Destination.MangaReader(sourceId, entryKey, item.url)
                    BrowseCarrier.BOOKS ->
                        Destination.Reader(sourceId, entryKey, item.url)
                },
            )
        },
        // Genre taps run the same search as Android's genre pill click.
        onGenreClick = { genre ->
            navigator.push(Destination.GlobalSearch(genre, carrier))
        },
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun ReaderDestination(
    graph: MikoUiGraph,
    destination: Destination.Reader,
    modifier: Modifier = Modifier,
) {
    val model = remember(destination.sourceId, destination.novelUrl) {
        ReaderScreenModel(
            binding = graph.novelBinding,
            sourceId = destination.sourceId,
            entryUrl = destination.novelUrl,
            initialChapterUrl = destination.chapterUrl,
        )
    }
    DisposableEffect(model) {
        onDispose { model.dispose() }
    }
    ReaderScreen(model = model, modifier = modifier.fillMaxSize())
}

@Composable
private fun MangaReaderDestination(
    graph: MikoUiGraph,
    destination: Destination.MangaReader,
    modifier: Modifier = Modifier,
) {
    val model = remember(destination.sourceId, destination.mangaUrl) {
        MangaReaderScreenModel(
            binding = graph.mangaBinding,
            sourceId = destination.sourceId,
            entryUrl = destination.mangaUrl,
            initialChapterUrl = destination.chapterUrl,
        )
    }
    DisposableEffect(model) {
        onDispose { model.dispose() }
    }
    MangaReaderScreen(model = model, modifier = modifier.fillMaxSize())
}

@Composable
private fun EpisodePlayerDestination(
    graph: MikoUiGraph,
    destination: Destination.EpisodePlayer,
    modifier: Modifier = Modifier,
) {
    val model = remember(destination.sourceId, destination.episodeUrl) {
        EpisodePlayerScreenModel(
            binding = graph.animeBinding,
            sourceId = destination.sourceId,
            entryUrl = destination.entryUrl,
            initialEpisodeUrl = destination.episodeUrl,
        )
    }
    DisposableEffect(model) {
        onDispose { model.dispose() }
    }
    EpisodePlayerScreen(model = model, modifier = modifier.fillMaxSize())
}

@Composable
private fun UpdatesDestination(
    model: UpdatesScreenModel,
    navigator: Navigator,
    carrierFlow: MutableStateFlow<BrowseCarrier>,
    modifier: Modifier = Modifier,
) {
    UpdatesScreen(
        model = model,
        carrierFlow = carrierFlow,
        onCarrierChange = { carrierFlow.value = it },
        onOpenItem = { sourceId, entryUrl, itemUrl ->
            navigator.push(
                when (carrierFlow.value) {
                    BrowseCarrier.SHOWS ->
                        Destination.EpisodePlayer(sourceId, entryUrl, itemUrl)
                    BrowseCarrier.MANGA ->
                        Destination.MangaReader(sourceId, entryUrl, itemUrl)
                    BrowseCarrier.BOOKS ->
                        Destination.Reader(sourceId, entryUrl, itemUrl)
                },
            )
        },
        onOpenEntry = { sourceId, entryUrl, entryTitle ->
            navigator.push(
                Destination.Details(sourceId, entryUrl, entryTitle, carrierFlow.value),
            )
        },
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun LibraryDestination(
    graph: MikoUiGraph,
    navigator: Navigator,
    carrierFlow: MutableStateFlow<BrowseCarrier>,
    modifier: Modifier = Modifier,
) {
    val model = remember { LibraryScreenModel(graph, carrierFlow) }
    DisposableEffect(model) {
        onDispose { model.dispose() }
    }
    LibraryScreen(
        model = model,
        carrierFlow = carrierFlow,
        onCarrierChange = { carrierFlow.value = it },
        onEntryClick = { entry ->
            navigator.push(
                Destination.Details(
                    entry.sourceId,
                    entry.url,
                    entry.title,
                    carrierFlow.value,
                ),
            )
        },
        modifier = modifier.fillMaxSize(),
    )
}
