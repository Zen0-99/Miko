package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.collection.visualName
import tachiyomi.domain.collection.model.Collection
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Returns a string of collections name for settings subtitle
 */
@ReadOnlyComposable
@Composable
fun getCollectionsLabel(
    allCollections: List<Collection>,
    included: Set<String>,
    excluded: Set<String>,
): String {
    val context = LocalContext.current

    val includedCollections = included
        .mapNotNull { id -> allCollections.find { it.id == id.toLong() } }
        .sortedBy { it.order }
    val excludedCollections = excluded
        .mapNotNull { id -> allCollections.find { it.id == id.toLong() } }
        .sortedBy { it.order }
    val allExcluded = excludedCollections.size == allCollections.size

    val includedItemsText = when {
        // Some selected, but not all
        includedCollections.isNotEmpty() &&
            includedCollections.size != allCollections.size ->
            includedCollections.joinToString {
                it.visualName(
                    context,
                )
            }
        // All explicitly selected
        includedCollections.size == allCollections.size -> stringResource(MR.strings.all)
        allExcluded -> stringResource(MR.strings.none)
        else -> stringResource(MR.strings.all)
    }
    val excludedItemsText = when {
        excludedCollections.isEmpty() -> stringResource(MR.strings.none)
        allExcluded -> stringResource(MR.strings.all)
        else -> excludedCollections.joinToString { it.visualName(context) }
    }
    return stringResource(MR.strings.include, includedItemsText) + "\n" +
        stringResource(MR.strings.exclude, excludedItemsText)
}
