package eu.kanade.presentation.entries.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Horizontal scrollable pill row for chapter groups parsed from chapter names
 * ("Volume 1", "Book 3", "Season 2"…) — the manga/novel counterpart of the
 * anime season pills. Hidden by the caller when there is only one group.
 *
 * @param groups ordered labels to render
 * @param selectedGroup the currently selected label
 * @param completedGroups labels whose chapters are all read — pill gets a check
 * @param onGroupSelected callback when a pill is tapped
 * @param accentColor the per-entry accent color from the detail view theme
 */
@Composable
fun ChapterGroupPillRow(
    groups: List<String>,
    selectedGroup: String,
    completedGroups: Set<String> = emptySet(),
    onGroupSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
) {
    if (groups.size <= 1) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        groups.forEach { group ->
            val isSelected = group == selectedGroup

            val selectedBg = accentColor ?: MaterialTheme.colorScheme.primary
            // Readable text via the theme-transition machinery — animates
            // with the accent fadethrough instead of snapping mid-swap.
            val selectedText = rememberOnAccentColor(accentColor)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) {
                            selectedBg
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .clickable { onGroupSelected(group) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (group in completedGroups) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = if (isSelected) {
                                selectedText
                            } else {
                                accentColor ?: MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = group,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            selectedText
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
