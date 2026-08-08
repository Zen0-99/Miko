package eu.kanade.tachiyomi.ui.browse.manga.migration.all

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.manga.MigrateMangaAllContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.manga.model.Manga

class MigrateMangaAllScreen(
    private val mangaIds: List<Long>,
    private val extensionName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateMangaAllScreenModel(mangaIds) }
        val state by screenModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        val dialogScreenModel = rememberScreenModel { MigrateMangaDialogScreenModel() }

        MigrateMangaAllContent(
            title = extensionName,
            state = state,
            navigateUp = navigator::pop,
            onSkip = screenModel::skip,
            onMigrateNow = { oldManga: Manga, newManga: Manga ->
                scope.launchIO {
                    dialogScreenModel.migrateManga(
                        oldManga = oldManga,
                        newManga = newManga,
                        replace = true,
                        flags = dialogScreenModel.migrateFlags.get(),
                    )
                    screenModel.markMigrated(oldManga.id)
                }
            },
            onSearchManually = { mangaId ->
                navigator.push(MigrateMangaSearchScreen(mangaId))
            },
            onClickOldManga = { mangaId ->
                navigator.push(MangaScreen(mangaId))
            },
            onClickRecommendedManga = { mangaId ->
                navigator.push(MangaScreen(mangaId, true))
            },
        )
    }
}
