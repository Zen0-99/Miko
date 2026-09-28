package eu.kanade.tachiyomi.ui.browse.shows.migration.all

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.shows.MigrateShowsAllContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.shows.migration.search.MigrateShowsDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.shows.migration.search.MigrateShowsSearchScreen
import eu.kanade.tachiyomi.ui.entries.shows.ShowsScreen
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.shows.model.Anime

class MigrateShowsAllScreen(
    private val animeIds: List<Long>,
    private val extensionName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateShowsAllScreenModel(animeIds) }
        val state by screenModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        val dialogScreenModel = rememberScreenModel { MigrateShowsDialogScreenModel() }

        MigrateShowsAllContent(
            title = extensionName,
            state = state,
            navigateUp = navigator::pop,
            onSkip = screenModel::skip,
            onMigrateNow = { oldAnime: Anime, newAnime: Anime ->
                scope.launchIO {
                    dialogScreenModel.migrateAnime(
                        oldAnime = oldAnime,
                        newAnime = newAnime,
                        replace = true,
                        flags = dialogScreenModel.migrateFlags.get(),
                    )
                    screenModel.markMigrated(oldAnime.id)
                }
            },
            onSearchManually = { animeId ->
                navigator.push(MigrateShowsSearchScreen(animeId))
            },
            onClickOldAnime = { animeId ->
                navigator.push(ShowsScreen(animeId))
            },
            onClickRecommendedAnime = { animeId ->
                navigator.push(ShowsScreen(animeId, true))
            },
        )
    }
}
