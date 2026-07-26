package eu.kanade.tachiyomi.ui.collection.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.collection.AnimeCollectionScreen
import eu.kanade.presentation.collection.components.CollectionCreateDialog
import eu.kanade.presentation.collection.components.CollectionDeleteDialog
import eu.kanade.presentation.collection.components.CollectionRenameDialog
import eu.kanade.presentation.components.TabContent
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun Screen.animeCollectionTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { AnimeCollectionScreenModel() }

    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_anime,
        searchEnabled = false,
        content = { contentPadding, _ ->
            if (state is AnimeCollectionScreenState.Loading) {
                LoadingScreen()
            } else {
                val successState = state as AnimeCollectionScreenState.Success

                AnimeCollectionScreen(
                    state = successState,
                    onClickCreate = { screenModel.showDialog(AnimeCollectionDialog.Create) },
                    onClickRename = { screenModel.showDialog(AnimeCollectionDialog.Rename(it)) },
                    onClickHide = screenModel::hideCollection,
                    onClickDelete = { screenModel.showDialog(AnimeCollectionDialog.Delete(it)) },
                    onChangeOrder = screenModel::changeOrder,
                )

                when (val dialog = successState.dialog) {
                    null -> {}
                    AnimeCollectionDialog.Create -> {
                        CollectionCreateDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onCreate = screenModel::createCollection,
                            collections = successState.collections.fastMap { it.name }.toImmutableList(),
                        )
                    }
                    is AnimeCollectionDialog.Rename -> {
                        CollectionRenameDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onRename = { screenModel.renameCollection(dialog.collection, it) },
                            collections = successState.collections.fastMap { it.name }.toImmutableList(),
                            collection = dialog.collection.name,
                        )
                    }
                    is AnimeCollectionDialog.Delete -> {
                        CollectionDeleteDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onDelete = { screenModel.deleteCollection(dialog.collection.id) },
                            collection = dialog.collection.name,
                        )
                    }
                }
            }
        },
        navigateUp = navigator::pop,
    )
}
