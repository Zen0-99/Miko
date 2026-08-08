package eu.kanade.tachiyomi.ui.browse.anime.migration.all

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.MigrateAnimeAllContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeSearchScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.anime.model.Anime

class MigrateAnimeAllScreen(
    private val animeIds: List<Long>,
    private val extensionName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateAnimeAllScreenModel(animeIds) }
        val state by screenModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        val dialogScreenModel = rememberScreenModel { MigrateAnimeDialogScreenModel() }

        MigrateAnimeAllContent(
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
                navigator.push(MigrateAnimeSearchScreen(animeId))
            },
            onClickOldAnime = { animeId ->
                navigator.push(AnimeScreen(animeId))
            },
            onClickRecommendedAnime = { animeId ->
                navigator.push(AnimeScreen(animeId, true))
            },
        )
    }
}
