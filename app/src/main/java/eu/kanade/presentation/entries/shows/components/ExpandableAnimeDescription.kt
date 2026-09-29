package eu.kanade.presentation.entries.shows.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.kanade.presentation.entries.components.ExpandableEntryDescription

/**
 * Thin delegate to [ExpandableEntryDescription] kept for the shows entry
 * screen's call-site signature.
 */
@Composable
fun ExpandableAnimeDescription(
    defaultExpandState: Boolean,
    description: String?,
    tagsProvider: () -> List<String>?,
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
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
