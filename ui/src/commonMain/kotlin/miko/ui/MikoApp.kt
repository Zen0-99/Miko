package miko.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    var browseMode by remember { mutableStateOf(BrowseMode.BROWSE) }

    MikoTheme {
        Scaffold(
            topBar = {
                if (navigator.canPop) {
                    TopAppBar(
                        title = { Text(screenTitle(navigator.current)) },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = { Text(screenTitle(navigator.current)) },
                        actions = {
                            if (navigator.current == Destination.Browse) {
                                BrowseOverflowMenu(
                                    onOpenExtensionRepos = {
                                        navigator.push(Destination.ExtensionRepos)
                                    },
                                )
                            }
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
                    graph = graph,
                    navigator = navigator,
                    browseMode = browseMode,
                    onBrowseModeChange = { browseMode = it },
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
 * Browse tab content — BROWSE|EXTENSIONS pill over sources/extensions lists,
 * mirroring the Android BrowseTab structure. Both models are created eagerly
 * like Android's `rememberScreenModel` calls so switching the pill doesn't
 * re-fetch.
 */
@Composable
private fun BrowseDestination(
    graph: MikoUiGraph,
    navigator: Navigator,
    browseMode: BrowseMode,
    onBrowseModeChange: (BrowseMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourcesModel = remember { SourcesScreenModel(graph.novelSourceManager) }
    val extensionsModel = remember {
        ExtensionsScreenModel(
            pluginRepository = graph.novelPluginRepository,
            jsPluginApi = graph.jsPluginApi,
            installer = graph.novelPluginInstaller,
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            sourcesModel.dispose()
            extensionsModel.dispose()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        BrowseModePill(browseMode = browseMode, onModeChange = onBrowseModeChange)
        when (browseMode) {
            BrowseMode.BROWSE -> SourcesScreen(
                model = sourcesModel,
                onSourceClick = { sourceId -> navigator.push(Destination.SourceFeed(sourceId)) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            BrowseMode.EXTENSIONS -> ExtensionsScreen(
                model = extensionsModel,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
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
