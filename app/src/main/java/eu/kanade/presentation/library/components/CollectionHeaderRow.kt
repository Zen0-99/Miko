package eu.kanade.presentation.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Collection header row matching the old repo's LibraryHeaderHolder layout:
 * - Title (18sp, semi-bold) on the left with item count in parentheses
 * - Sort chip (label + direction arrow) on the right, inline with title
 *
 * Used by:
 * - Continuous mode (header for each collection section)
 * - Tabbed mode when "show collection tabs" is off (header for the current collection)
 */
@Composable
fun CollectionHeaderRow(
    title: String,
    itemCount: Int?,
    sortLabel: String?,
    sortDescending: Boolean?,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (showTitle) Arrangement.SpaceBetween else Arrangement.End,
    ) {
        // Title + item count on the left
        if (showTitle) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (itemCount != null) {
                    Text(
                        text = "($itemCount)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Sort chip on the right — direction arrow on the left of the label
        if (sortLabel != null) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onSortClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (sortDescending != null) {
                    Icon(
                        imageVector = if (sortDescending) Icons.Outlined.ArrowDownward
                                      else Icons.Outlined.ArrowUpward,
                        contentDescription = stringResource(MR.strings.action_sort),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = sortLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
