package eu.kanade.tachiyomi.ui.history.shows

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.collection.components.ChangeCollectionDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.entries.shows.DuplicateShowsDialog
import eu.kanade.presentation.history.HistoryDeleteAllDialog
import eu.kanade.presentation.history.HistoryDeleteDialog
import eu.kanade.presentation.history.shows.ShowsHistoryScreen
import eu.kanade.tachiyomi.metadata.stream.StreamResolver
import eu.kanade.tachiyomi.ui.browse.shows.migration.search.MigrateShowsDialog
import eu.kanade.tachiyomi.ui.browse.shows.migration.search.MigrateShowsDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.shows.migration.shows.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.collection.CollectionsTab
import eu.kanade.tachiyomi.ui.entries.shows.ShowsScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.injectLazy

val resumeLastEpisodeSeenEvent = Channel<Unit>()

@Composable
fun Screen.animeHistoryTab(
    context: Context,
    fromMore: Boolean,
): TabContent {
    val snackbarHostState = SnackbarHostState()

    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { ShowsHistoryScreenModel() }
    val state by screenModel.state.collectAsState()
    val searchQuery by screenModel.query.collectAsState()

    suspend fun openEpisode(context: Context, episode: Episode?) {
        val playerPreferences: PlayerPreferences by injectLazy()
        val streamResolver: StreamResolver by injectLazy()
        val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
        if (episode != null) {
            // Cinemeta entries need the mapped source episode — the stub
            // source has no hosters. Unmapped entries open the detail where
            // the source picker can run.
            val playable = streamResolver.resolvePlayableByIds(episode.animeId, episode.id)
            if (playable != null) {
                MainActivity.startPlayerActivity(context, playable.first.id, playable.second.id, extPlayer)
            } else {
                navigator.push(ShowsScreen(episode.animeId))
            }
        } else {
            snackbarHostState.showSnackbar(context.stringResource(AYMR.strings.no_next_episode))
        }
    }

    val scope = rememberCoroutineScope()
    val navigateUp: (() -> Unit)? = if (fromMore) {
        {
            if (navigator.lastItem == HomeScreen) {
                scope.launch { HomeScreen.openTab(HomeScreen.Tab.Library()) }
            } else {
                navigator.pop()
            }
        }
    } else {
        null
    }

    return TabContent(
        titleRes = AYMR.strings.carrier_shows_history,
        searchEnabled = true,
        searchAvailable = true,
        searchPlaceholderText = MR.strings.search_hint_history,
        searchQuery = searchQuery,
        onSearchQueryChange = { screenModel.search(it) },
        content = { contentPadding, _ ->
            ShowsHistoryScreen(
                state = state,
                searchQuery = searchQuery,
                snackbarHostState = snackbarHostState,
                onClickCover = { navigator.push(ShowsScreen(it)) },
                onClickResume = screenModel::getNextEpisodeForAnime,
                onDialogChange = screenModel::setDialog,
                onClickFavorite = screenModel::addFavorite,
            )

            val onDismissRequest = { screenModel.setDialog(null) }
            when (val dialog = state.dialog) {
                is ShowsHistoryScreenModel.Dialog.Delete -> {
                    HistoryDeleteDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = { all ->
                            if (all) {
                                screenModel.removeAllFromHistory(dialog.history.animeId)
                            } else {
                                screenModel.removeFromHistory(dialog.history)
                            }
                        },
                        isManga = false,
                    )
                }
                is ShowsHistoryScreenModel.Dialog.DeleteAll -> {
                    HistoryDeleteAllDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = screenModel::removeAllHistory,
                    )
                }
                is ShowsHistoryScreenModel.Dialog.DuplicateAnime -> {
                    DuplicateShowsDialog(
                        onDismissRequest = onDismissRequest,
                        onConfirm = {
                            screenModel.addFavorite(dialog.anime)
                        },
                        onOpenAnime = { navigator.push(ShowsScreen(dialog.duplicate.id)) },
                        onMigrate = {
                            screenModel.showMigrateDialog(dialog.anime, dialog.duplicate)
                        },
                    )
                }
                is ShowsHistoryScreenModel.Dialog.ChangeCollection -> {
                    ChangeCollectionDialog(
                        initialSelection = dialog.initialSelection,
                        onDismissRequest = onDismissRequest,
                        onEditCollections = { navigator.push(CollectionsTab) },
                        onConfirm = { include, _ ->
                            screenModel.moveAnimeToCollectionsAndAddToLibrary(dialog.anime, include)
                        },
                    )
                }
                is ShowsHistoryScreenModel.Dialog.Migrate -> {
                    MigrateShowsDialog(
                        oldAnime = dialog.oldAnime,
                        newAnime = dialog.newAnime,
                        screenModel = MigrateShowsDialogScreenModel(),
                        onDismissRequest = onDismissRequest,
                        onClickTitle = { navigator.push(ShowsScreen(dialog.oldAnime.id)) },
                        onClickSeasons = {
                            navigator.push(MigrateSeasonSelectScreen(dialog.oldAnime, dialog.newAnime))
                        },
                        onPopScreen = { navigator.replace(ShowsScreen(dialog.oldAnime.id)) },
                    )
                }
                null -> {}
            }

            LaunchedEffect(state.list) {
                if (state.list != null) {
                    (context as? MainActivity)?.ready = true
                }
            }

            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { e ->
                    when (e) {
                        ShowsHistoryScreenModel.Event.InternalError ->
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                        ShowsHistoryScreenModel.Event.HistoryCleared ->
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                        is ShowsHistoryScreenModel.Event.OpenEpisode -> openEpisode(context, e.episode)
                    }
                }
            }

            LaunchedEffect(Unit) {
                resumeLastEpisodeSeenEvent.receiveAsFlow().collectLatest {
                    openEpisode(context, screenModel.getNextEpisode())
                }
            }
        },
        actions =
        persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.pref_clear_history),
                icon = Icons.Outlined.DeleteSweep,
                onClick = { screenModel.setDialog(ShowsHistoryScreenModel.Dialog.DeleteAll) },
            ),
        ),
        navigateUp = navigateUp,
    )
}
