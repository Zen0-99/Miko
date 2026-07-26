package eu.kanade.tachiyomi.ui.browse.novel.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.novel.NovelPluginDetailsScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.novel.migration.all.MigrateNovelAllScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelSearchScreen
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.screens.LoadingScreen

data class NovelPluginDetailsScreen(
    private val pluginId: String,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel {
            NovelPluginDetailsScreenModel(
                pluginId = pluginId,
            )
        }
        val state by screenModel.state.collectAsState()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow

        NovelPluginDetailsScreen(
            navigateUp = navigator::pop,
            state = state,
            onClickUninstall = screenModel::uninstallPlugin,
            onClickToggle = screenModel::toggleSource,
            onClickMigrate = { novelId -> navigator.push(MigrateNovelSearchScreen(novelId)) },
            onClickMigrateAll = {
                val novelIds = state.migrateItems.map { it.novel.id }
                if (novelIds.isNotEmpty()) {
                    navigator.push(
                        MigrateNovelAllScreen(
                            novelIds = novelIds,
                            extensionName = state.plugin?.name ?: "",
                        ),
                    )
                }
            },
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is NovelPluginDetailsEvent.Uninstalled) {
                    navigator.pop()
                }
            }
        }
    }
}
