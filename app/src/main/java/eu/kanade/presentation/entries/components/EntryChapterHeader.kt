package eu.kanade.presentation.entries.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA

/**
 * Chapter/episode count header with a filter icon on the right and optional reload dropdown.
 * Uses the entry's accent color for the icons.
 *
 * Layout: [Count Text (left)] --- [Reload Icon (left)] [Filter Icon (right, aligned with download icons)]
 *
 * @param itemCountText the formatted count text (e.g. "12 chapters" or "24 episodes")
 * @param onClick called when the filter icon is tapped (opens filter sheet)
 * @param accentColor the entry's cover-derived accent color, or null for default
 * @param onFetchNew optional callback to fetch new items only
 * @param onFetchAll optional callback to fetch all items
 */
@Composable
fun EntryChapterHeader(
    itemCountText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    onFetchNew: (() -> Unit)? = null,
    onFetchAll: (() -> Unit)? = null,
) {
    var reloadMenuExpanded by remember { mutableStateOf(false) }
    val showReload = onFetchNew != null || onFetchAll != null
    val iconTint = accentColor ?: MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Chapter/episode count (left)
        Text(
            text = itemCountText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // Right side: reload icon (left) + filter icon (right, aligned with download icons)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Reload icon with dropdown menu (left of filter so filter aligns with download icons)
            if (showReload) {
                Box {
                    IconButton(onClick = { reloadMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = iconTint,
                        )
                    }
                    DropdownMenu(
                        expanded = reloadMenuExpanded,
                        onDismissRequest = { reloadMenuExpanded = false },
                    ) {
                        if (onFetchNew != null) {
                            DropdownMenuItem(
                                text = { Text("Fetch new chapters") },
                                onClick = {
                                    onFetchNew()
                                    reloadMenuExpanded = false
                                },
                            )
                        }
                        if (onFetchAll != null) {
                            DropdownMenuItem(
                                text = { Text("Fetch all chapters") },
                                onClick = {
                                    onFetchAll()
                                    reloadMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // Filter icon (rightmost, aligned with download icons in list items)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
