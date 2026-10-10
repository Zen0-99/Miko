package miko.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import miko.ui.details.NovelDetailsScreen
import miko.ui.details.NovelDetailsScreenModel
import miko.ui.feed.SourceFeedScreen
import miko.ui.feed.SourceFeedScreenModel
import miko.ui.library.LibraryScreen
import miko.ui.library.LibraryScreenModel
import miko.ui.navigation.Destination
import miko.ui.navigation.Navigator
import miko.ui.navigation.rememberNavigator
import miko.ui.sources.SourcesScreen
import miko.ui.sources.SourcesScreenModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Shared app shell: hand-rolled navigation host + bottom tabs (Browse /
 * Library) at the root. Platform entry points (ios-app's MainViewController,
 * the Android shell at convergence) call this with their [MikoUiGraph].
 */
@Composable
fun MikoApp(graph: MikoUiGraph) {
    val navigator = rememberNavigator(Destination.Browse)

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
                Destination.Browse -> SourcesDestination(
                    graph = graph,
                    navigator = navigator,
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
            }
        }
    }
}

@Composable
private fun screenTitle(destination: Destination): String = when (destination) {
    is Destination.SourceFeed -> stringResource(MR.strings.sources)
    is Destination.Details -> ""
    else -> ""
}

@Composable
private fun SourcesDestination(
    graph: MikoUiGraph,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val model = remember { SourcesScreenModel(graph.novelSourceManager) }
    DisposableEffect(model) {
        onDispose { model.dispose() }
    }
    SourcesScreen(
        model = model,
        onSourceClick = { sourceId -> navigator.push(Destination.SourceFeed(sourceId)) },
        modifier = modifier.fillMaxSize(),
    )
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
