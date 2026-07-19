package eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifNovelSourcesLoaded
import eu.kanade.presentation.browse.novel.GlobalNovelSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class GlobalNovelSearchScreen(
    val searchQuery: String = "",
    private val extensionFilter: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val sourcesLoaded = ifNovelSourcesLoaded()
        Log.d("NovelSearch", "[GlobalNovelSearchScreen] Content() - searchQuery='$searchQuery', extensionFilter='$extensionFilter', sourcesLoaded=$sourcesLoaded")
        if (!sourcesLoaded) {
            Log.w("NovelSearch", "[GlobalNovelSearchScreen] Content() - novel sources NOT loaded, showing LoadingScreen (screen appears empty/stuck)")
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            GlobalNovelSearchScreenModel(
                initialQuery = searchQuery,
                initialExtensionFilter = extensionFilter,
            )
        }
        val state by screenModel.state.collectAsState()
        var showSingleLoadingScreen by remember {
            mutableStateOf(
                searchQuery.isNotEmpty() && !extensionFilter.isNullOrEmpty() && state.total == 1,
            )
        }
        Log.d("NovelSearch", "[GlobalNovelSearchScreen] Content() - state.total=${state.total}, showSingleLoadingScreen=$showSingleLoadingScreen, items=${state.items.size}")

        if (showSingleLoadingScreen) {
            Log.d("NovelSearch", "[GlobalNovelSearchScreen] Content() - showing single loading screen (extensionFilter set with single source)")
            LoadingScreen()

            LaunchedEffect(state.items) {
                when (val result = state.items.values.singleOrNull()) {
                    NovelSearchItemResult.Loading -> return@LaunchedEffect
                    is NovelSearchItemResult.Success -> {
                        val novel = result.result.singleOrNull()
                        if (novel != null) {
                            Log.d("NovelSearch", "[GlobalNovelSearchScreen] Content() - single result success, navigating to NovelScreen(id=${novel.id}, title='${novel.title}') via navigator.replace")
                            navigator.replace(eu.kanade.tachiyomi.ui.entries.novel.NovelScreen(novel.id, true))
                            showSingleLoadingScreen = false
                        } else {
                            Log.d("NovelSearch", "[GlobalNovelSearchScreen] Content() - single result success but result list empty, falling back to search screen")
                            showSingleLoadingScreen = false
                        }
                    }
                    is NovelSearchItemResult.Error -> {
                        Log.e("NovelSearch", "[GlobalNovelSearchScreen] Content() - single result ERROR: ${result.throwable.message}", result.throwable)
                        showSingleLoadingScreen = false
                    }
                    null -> {
                        Log.w("NovelSearch", "[GlobalNovelSearchScreen] Content() - state.items.values.singleOrNull() is null (items=${state.items.size}), falling back")
                        showSingleLoadingScreen = false
                    }
                }
            }
        } else {
            GlobalNovelSearchScreen(
                state = state,
                navigateUp = {
                    Log.d("NovelSearch", "[GlobalNovelSearchScreen] Content() - navigateUp (navigator.pop) called")
                    Log.d("NovelSearch", "[GlobalNovelSearchScreen] navigateUp stacktrace:", Throwable("navigateUp caller"))
                    navigator.pop()
                },
                onChangeSearchQuery = screenModel::updateSearchQuery,
                onSearch = { screenModel.search() },
                getNovel = { screenModel.getNovel(it) },
                onChangeSearchFilter = screenModel::setSourceFilter,
                onToggleResults = screenModel::toggleFilterResults,
                onClickSource = {
                    Log.d("NovelSearch", "[GlobalNovelSearchScreen] Content() - onClickSource: ${it.name} (id=${it.id}), pushing BrowseNovelSourceScreen with query='${state.searchQuery}'")
                    navigator.push(BrowseNovelSourceScreen(it.id, state.searchQuery))
                },
                onClickItem = {
                    Log.d("NovelSearch", "[GlobalNovelSearchScreen] Content() - onClickItem: '${it.title}' (id=${it.id}), pushing NovelScreen")
                    navigator.push(eu.kanade.tachiyomi.ui.entries.novel.NovelScreen(it.id, true))
                },
                onLongClickItem = {
                    Log.d("NovelSearch", "[GlobalNovelSearchScreen] Content() - onLongClickItem: '${it.title}' (id=${it.id}), pushing NovelScreen")
                    navigator.push(eu.kanade.tachiyomi.ui.entries.novel.NovelScreen(it.id, true))
                },
            )
        }
    }
}
