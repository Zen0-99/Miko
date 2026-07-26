package eu.kanade.tachiyomi.ui.browse.novel.migration.all

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.novel.MigrateNovelAllContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelSearchScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.novel.model.Novel

class MigrateNovelAllScreen(
    private val novelIds: List<Long>,
    private val extensionName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateNovelAllScreenModel(novelIds) }
        val state by screenModel.state.collectAsState()
        val scope = rememberCoroutineScope()

        val dialogScreenModel = rememberScreenModel { MigrateNovelDialogScreenModel() }

        MigrateNovelAllContent(
            title = extensionName,
            state = state,
            navigateUp = navigator::pop,
            onSkip = screenModel::skip,
            onMigrateNow = { oldNovel: Novel, newNovel: Novel ->
                scope.launchIO {
                    dialogScreenModel.migrateNovel(
                        oldNovel = oldNovel,
                        newNovel = newNovel,
                        replace = true,
                        flags = dialogScreenModel.migrateFlags.get(),
                    )
                    screenModel.markMigrated(oldNovel.id)
                }
            },
            onSearchManually = { novelId ->
                navigator.push(MigrateNovelSearchScreen(novelId))
            },
            onClickOldNovel = { novelId ->
                navigator.push(NovelScreen(novelId))
            },
            onClickRecommendedNovel = { novelId ->
                navigator.push(NovelScreen(novelId, true))
            },
        )
    }
}
