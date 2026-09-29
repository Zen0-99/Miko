package eu.kanade.presentation.entries.novel.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.entries.components.ExpandableEntryDescription

/**
 * Deprecated — use [eu.kanade.presentation.entries.components.ExpandableEntryDescription] instead.
 * Kept as a thin delegate for backward compatibility.
 */
@Composable
fun ExpandableNovelDescription(
    defaultExpandState: Boolean,
    description: String?,
    tagsProvider: () -> List<String>?,
    accentColor: Color?,
    onTagSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpandableEntryDescription(
        defaultExpandState = defaultExpandState,
        description = description,
        tagsProvider = tagsProvider,
        accentColor = accentColor,
        onTagSearch = onTagSearch,
        modifier = modifier,
    )
}
