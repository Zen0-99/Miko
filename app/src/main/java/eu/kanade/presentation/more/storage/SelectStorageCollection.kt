package eu.kanade.presentation.more.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import tachiyomi.domain.collection.model.Collection
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.SelectItem
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SelectStorageCollection(
    selectedCollection: Collection,
    collections: List<Collection>,
    modifier: Modifier = Modifier,
    onCollectionSelected: (Collection) -> Unit,
) {
    val all = stringResource(AYMR.strings.label_all)
    val default = stringResource(MR.strings.label_default)
    val mappedCollections = remember(collections) {
        collections.map {
            when (it.id) {
                -1L -> it.copy(name = all)
                Collection.UNCATEGORIZED_ID -> it.copy(name = default)
                else -> it
            }
        }.toTypedArray()
    }

    SelectItem(
        modifier = modifier,
        label = stringResource(AYMR.strings.label_collection),
        selectedIndex = mappedCollections.indexOfFirst { it.id == selectedCollection.id },
        options = mappedCollections,
        onSelect = { index ->
            onCollectionSelected(mappedCollections[index])
        },
        toString = { it.name },
    )
}
