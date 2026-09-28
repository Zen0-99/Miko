package eu.kanade.tachiyomi.ui.browse.shows.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.shows.MigrateShowsSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.shows.migration.shows.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.entries.shows.ShowsScreen

class MigrateShowsSearchScreen(private val animeId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { MigrateShowsSearchScreenModel(animeId = animeId) }
        val state by screenModel.state.collectAsState()

        val dialogScreenModel = rememberScreenModel {
            ShowsMigrateSearchScreenDialogScreenModel(
                animeId = animeId,
            )
        }
        val dialogState by dialogScreenModel.state.collectAsState()

        MigrateShowsSearchScreen(
            state = state,
            fromSourceId = dialogState.anime?.source,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            getAnime = { screenModel.getAnime(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = {
                navigator.push(
                    ShowsSourceSearchScreen(dialogState.anime!!, it.id, state.searchQuery),
                )
            },
            onClickItem = {
                dialogScreenModel.setDialog(
                    (ShowsMigrateSearchScreenDialogScreenModel.Dialog.Migrate(it)),
                )
            },
            onLongClickItem = { navigator.push(ShowsScreen(it.id, true)) },
        )

        when (val dialog = dialogState.dialog) {
            is ShowsMigrateSearchScreenDialogScreenModel.Dialog.Migrate -> {
                MigrateShowsDialog(
                    oldAnime = dialogState.anime!!,
                    newAnime = dialog.anime,
                    screenModel = rememberScreenModel { MigrateShowsDialogScreenModel() },
                    onDismissRequest = { dialogScreenModel.setDialog(null) },
                    onClickTitle = {
                        navigator.push(ShowsScreen(dialog.anime.id, true))
                    },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(dialogState.anime!!, dialog.anime)) },
                    onPopScreen = {
                        if (navigator.lastItem is ShowsScreen) {
                            val lastItem = navigator.lastItem
                            navigator.popUntil { navigator.items.contains(lastItem) }
                            navigator.push(ShowsScreen(dialog.anime.id))
                        } else {
                            navigator.replace(ShowsScreen(dialog.anime.id))
                        }
                    },
                )
            }
            else -> {}
        }
    }
}
