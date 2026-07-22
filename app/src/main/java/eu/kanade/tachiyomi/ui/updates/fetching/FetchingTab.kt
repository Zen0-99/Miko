package eu.kanade.tachiyomi.ui.updates.fetching

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.updates.fetching.FetchingScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelSearchScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.library.model.EntryKind
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.fetchingTab(
    @Suppress("UNUSED_PARAMETER") context: Context,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { FetchingScreenModel() }
    val state by screenModel.state.collectAsState()
    val dialog by screenModel.dialog.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_fetching_tab,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(AYMR.strings.action_clear_failed_fetches),
                icon = Icons.Outlined.DeleteSweep,
                onClick = { screenModel.setDialog(FetchingScreenModel.Dialog.ClearAllConfirmation) },
            ),
        ),
        content = { contentPadding, _ ->
            FetchingScreen(
                state = state,
                dialog = dialog,
                onMigrate = { entry ->
                    val screen = when (entry.entryKind) {
                        EntryKind.MANGA -> MigrateMangaSearchScreen(entry.entryId)
                        EntryKind.ANIME -> MigrateAnimeSearchScreen(entry.entryId)
                        EntryKind.NOVEL -> MigrateNovelSearchScreen(entry.entryId)
                    }
                    navigator.push(screen)
                },
                onDismissEntry = screenModel::deleteById,
                onDismissGroup = screenModel::deleteByReason,
                onClearAll = screenModel::clearAll,
                onPause = screenModel::pause,
                onResume = { screenModel.resume(context) },
                onCancel = screenModel::cancel,
                modifier = Modifier.padding(contentPadding),
            )
        },
    )
}
