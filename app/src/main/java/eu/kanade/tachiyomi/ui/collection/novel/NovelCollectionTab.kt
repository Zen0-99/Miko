package eu.kanade.tachiyomi.ui.collection.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.collection.NovelCollectionScreen
import eu.kanade.presentation.collection.components.CollectionCreateDialog
import eu.kanade.presentation.collection.components.CollectionDeleteDialog
import eu.kanade.presentation.collection.components.CollectionRenameDialog
import eu.kanade.presentation.components.TabContent
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun Screen.novelCollectionTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { NovelCollectionScreenModel() }

    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_novel,
        searchEnabled = false,
        content = { contentPadding, _ ->
            if (state is NovelCollectionScreenState.Loading) {
                LoadingScreen()
            } else {
                val successState = state as NovelCollectionScreenState.Success

                NovelCollectionScreen(
                    state = successState,
                    onClickCreate = { screenModel.showDialog(NovelCollectionDialog.Create) },
                    onClickRename = { screenModel.showDialog(NovelCollectionDialog.Rename(it)) },
                    onClickHide = screenModel::hideCollection,
                    onClickDelete = { screenModel.showDialog(NovelCollectionDialog.Delete(it)) },
                    onChangeOrder = screenModel::changeOrder,
                )

                when (val dialog = successState.dialog) {
                    null -> {}
                    NovelCollectionDialog.Create -> {
                        CollectionCreateDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onCreate = screenModel::createCollection,
                            collections = successState.collections.fastMap { it.name }.toImmutableList(),
                        )
                    }
                    is NovelCollectionDialog.Rename -> {
                        CollectionRenameDialog(
                            onDismissRequest = screenModel::dismissDialog,
                            onRename = { screenModel.renameCollection(dialog.collection, it) },
                            collections = successState.collections.fastMap { it.name }.toImmutableList(),
                            collection = dialog.collection.name,
                        )
                    }
                    is NovelCollectionDialog.Delete -> {
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
