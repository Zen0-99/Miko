package eu.kanade.presentation.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import eu.kanade.presentation.collection.components.CollectionFloatingActionButton
import eu.kanade.presentation.collection.components.CollectionListItem
import eu.kanade.tachiyomi.ui.collection.anime.AnimeCollectionScreenState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.collection.model.Collection
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun AnimeCollectionScreen(
    state: AnimeCollectionScreenState.Success,
    onClickCreate: () -> Unit,
    onClickRename: (Collection) -> Unit,
    onClickHide: (Collection) -> Unit,
    onClickDelete: (Collection) -> Unit,
    onChangeOrder: (Collection, Int) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        floatingActionButton = {
            CollectionFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = onClickCreate,
            )
        },
    ) { paddingValues ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.information_empty_collection,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        CollectionContent(
            collections = state.collections,
            lazyListState = lazyListState,
            paddingValues = paddingValues,
            onClickRename = onClickRename,
            onClickHide = onClickHide,
            onClickDelete = onClickDelete,
            onChangeOrder = onChangeOrder,
        )
    }
}

@Composable
private fun CollectionContent(
    collections: List<Collection>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickRename: (Collection) -> Unit,
    onClickHide: (Collection) -> Unit,
    onClickDelete: (Collection) -> Unit,
    onChangeOrder: (Collection, Int) -> Unit,
) {
    val collectionsState = remember { collections.toMutableStateList() }
    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        val item = collectionsState.removeAt(from.index)
        collectionsState.add(to.index, item)
        onChangeOrder(item, to.index)
    }

    LaunchedEffect(collections) {
        if (!reorderableState.isAnyItemDragging) {
            collectionsState.clear()
            collectionsState.addAll(collections)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = PaddingValues(MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = collectionsState,
            key = { collection -> collection.key },
        ) { collection ->
            ReorderableItem(reorderableState, collection.key) {
                CollectionListItem(
                    modifier = Modifier.animateItem(),
                    collection = collection,
                    onRename = { onClickRename(collection) },
                    onHide = { onClickHide(collection) },
                    onDelete = { onClickDelete(collection) },
                )
            }
        }
    }
}

private val Collection.key inline get() = "collection-$id"
