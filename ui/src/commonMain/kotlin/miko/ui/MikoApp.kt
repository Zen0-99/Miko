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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import miko.ui.details.NovelDetailsScreen
import miko.ui.details.NovelDetailsScreenModel
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
import miko.ui.repos.ExtensionReposScreen
import miko.ui.repos.ExtensionReposScreenModel
import miko.ui.search.GlobalNovelSearchScreen
import miko.ui.search.GlobalNovelSearchScreenModel
import miko.ui.sources.SourcesScreen
import miko.ui.sources.SourcesScreenModel
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
    LaunchedEffect(navigator.current) {
        if (navigator.current != Destination.Browse) browseSearchQuery = null
    }

    // The global-search screen's model lives at app scope so the top bar's
    // search field can submit into it (Android embeds the toolbar in the
    // screen's own Scaffold; the shared shell hoists it instead).
    val globalSearchDest = navigator.current as? Destination.GlobalSearch
    val globalSearchModel = remember(globalSearchDest?.query) {
        globalSearchDest?.let { GlobalNovelSearchScreenModel(graph.novelSourceManager, it.query) }
    }
    DisposableEffect(globalSearchModel) {
        onDispose { globalSearchModel?.dispose() }
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
    val sourcesModel = remember { SourcesScreenModel(graph.novelSourceManager, browseCatalog) }
    val extensionsModel = remember {
        ExtensionsScreenModel(
            catalog = browseCatalog,
            installer = graph.novelPluginInstaller,
        )
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
                        canSearch = current == Destination.Browse,
                        searchQuery = browseSearchQuery,
                        onSearchQueryChange = { browseSearchQuery = it },
                        onSearch = { query ->
                            if (browseMode == BrowseMode.BROWSE) {
                                // Android pushes GlobalNovelSearchScreen and
                                // closes the browse search.
                                navigator.push(Destination.GlobalSearch(query))
                                browseSearchQuery = null
                            }
                        },
                        searchPlaceholderText = if (current == Destination.Browse) {
                            stringResource(
                                if (browseMode == BrowseMode.EXTENSIONS) {
                                    MR.strings.search_hint_extensions
                                } else {
                                    MR.strings.search_hint_browse
                                },
                            )
                        } else {
                            null
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
                    NavigationBar {
                        NavigationBarItem(
                            selected = navigator.current == Destination.Browse,
                            onClick = { navigator.replace(Destination.Browse) },
                            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            label = { Text(stringResource(MR.strings.browse)) },
                        )
                        NavigationBarItem(
                            selected = navigator.current == Destination.Library,
                            onClick = { navigator.replace(Destination.Library) },
                            icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                            label = { Text(stringResource(MR.strings.label_library)) },
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
                    modifier = Modifier.padding(padding),
                )
                is Destination.SourceFeed -> SourceFeedDestination(
                    graph = graph,
                    navigator = navigator,
                    sourceId = destination.sourceId,
                    modifier = Modifier.padding(padding),
                )
                is Destination.Details -> DetailsDestination(
                    graph = graph,
                    sourceId = destination.sourceId,
                    entryKey = destination.entryKey,
                    modifier = Modifier.padding(padding),
                )
                Destination.ExtensionRepos -> ExtensionReposDestination(
                    graph = graph,
                    modifier = Modifier.padding(padding),
                )
                is Destination.GlobalSearch -> GlobalSearchDestination(
                    model = globalSearchModel,
                    navigator = navigator,
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
    Destination.ExtensionRepos -> stringResource(MR.strings.label_extension_repos)
    is Destination.SourceFeed -> stringResource(MR.strings.sources)
    is Destination.GlobalSearch -> stringResource(MR.strings.action_search_hint)
    is Destination.Details -> ""
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
            onSourceClick = { sourceId -> navigator.push(Destination.SourceFeed(sourceId)) },
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
    model: GlobalNovelSearchScreenModel?,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    if (model != null) {
        GlobalNovelSearchScreen(
            model = model,
            onSourceClick = { sourceId -> navigator.push(Destination.SourceFeed(sourceId)) },
            onNovelClick = { sourceId, novelUrl ->
                navigator.push(Destination.Details(sourceId, novelUrl))
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SourceFeedDestination(
    graph: MikoUiGraph,
    navigator: Navigator,
    sourceId: Long,
    modifier: Modifier = Modifier,
) {
    val model = remember(sourceId) {
        SourceFeedScreenModel(graph.novelSourceManager, sourceId)
    }
    DisposableEffect(model) {
        onDispose { model.dispose() }
    }
    SourceFeedScreen(
        model = model,
        onNovelClick = { novel ->
            navigator.push(Destination.Details(sourceId, novel.url))
        },
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun DetailsDestination(
    graph: MikoUiGraph,
    sourceId: Long,
    entryKey: String,
    modifier: Modifier = Modifier,
) {
    val model = remember(sourceId, entryKey) {
        NovelDetailsScreenModel(graph, sourceId, entryKey)
    }
    DisposableEffect(model) {
        onDispose { model.dispose() }
    }
    NovelDetailsScreen(
        model = model,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun LibraryDestination(
    graph: MikoUiGraph,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val model = remember { LibraryScreenModel(graph.getLibraryNovels) }
    DisposableEffect(model) {
        onDispose { model.dispose() }
    }
    LibraryScreen(
        model = model,
        onNovelClick = { entry ->
            navigator.push(Destination.Details(entry.novel.source, entry.novel.url))
        },
        modifier = modifier.fillMaxSize(),
    )
}
