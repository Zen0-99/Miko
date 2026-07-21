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
import androidx.compose.material.icons.outlined.HourglassEmpty
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
import androidx.compose.ui.unit.sp
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA

/**
 * Chapter/episode count header with optional interval badge inline and a filter icon on the right.
 * The refresh/fetch action has been moved to the top bar.
 *
 * Layout: [Count Text - Interval Badge (left)] --- [Filter Icon (right, aligned with download icons)]
 *
 * @param itemCountText the formatted count text (e.g. "12 chapters" or "24 episodes")
 * @param onClick called when the filter icon is tapped (opens filter sheet)
 * @param accentColor the entry's cover-derived accent color, or null for default
 * @param intervalDays optional smart update interval in days, shown inline after the count
 */
@Composable
fun EntryChapterHeader(
    itemCountText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    onFetchNew: (() -> Unit)? = null,
    onFetchAll: (() -> Unit)? = null,
    intervalDays: Int? = null,
) {
    val iconTint = accentColor ?: MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Chapter/episode count + interval badge (left)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = itemCountText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // Always show the interval badge — "N/A" when interval can't be calculated
            val label = if (intervalDays == null || intervalDays == 0) "N/A" else intervalDays.toString()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.titleMedium,
                    color = iconTint,
                )
                Icon(
                    imageVector = Icons.Outlined.HourglassEmpty,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
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
