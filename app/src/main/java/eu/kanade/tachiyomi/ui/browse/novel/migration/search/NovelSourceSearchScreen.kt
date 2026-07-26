package eu.kanade.tachiyomi.ui.browse.novel.migration.search

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifNovelSourcesLoaded
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreenModel
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen

data class NovelSourceSearchScreen(
    private val oldNovel: Novel,
    private val sourceId: Long,
    private val query: String?,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifNovelSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        val screenModel = rememberScreenModel { BrowseNovelSourceScreenModel(sourceId, query) }
        val state by screenModel.state.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    searchQuery = state.toolbarQuery ?: "",
                    onChangeSearchQuery = screenModel::setToolbarQuery,
                    onClickCloseSearch = navigator::pop,
                    onSearch = screenModel::search,
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {},
            snackbarHost = { AchievementStyledSnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val openMigrateDialog: (Novel) -> Unit = {
                screenModel.setDialog(BrowseNovelSourceScreenModel.Dialog.Migrate(newNovel = it, oldNovel = oldNovel))
            }
            // TODO: BrowseNovelSourceContent when novel browse grid/list components are created
            Text(
                text = "Novel source search - TODO",
                modifier = Modifier.padding(paddingValues),
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is BrowseNovelSourceScreenModel.Dialog.Migrate -> {
                MigrateNovelDialog(
                    oldNovel = oldNovel,
                    newNovel = dialog.newNovel,
                    screenModel = rememberScreenModel { MigrateNovelDialogScreenModel() },
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(NovelScreen(dialog.newNovel.id)) },
                    onPopScreen = {
                        scope.launch {
                            navigator.popUntilRoot()
                            HomeScreen.openTab(HomeScreen.Tab.Browse())
                            navigator.push(NovelScreen(dialog.newNovel.id))
                        }
                    },
                )
            }
            else -> {}
        }
    }
}
