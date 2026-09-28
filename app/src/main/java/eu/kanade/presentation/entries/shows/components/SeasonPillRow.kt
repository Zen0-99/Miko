package eu.kanade.presentation.entries.shows.components

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
import eu.kanade.presentation.entries.components.rememberOnAccentColor

/**
 * NuvioMobile-style horizontal scrollable season pills.
 *
 * Shows one pill per season. The selected season is filled; others are
 * subtle. Season 0 is labeled "Specials".
 *
 * @param seasons list of season numbers (e.g. [0, 1, 2])
 * @param selectedSeason the currently selected season number
 * @param watchedSeasons seasons whose episodes are all seen — pill gets a check
 * @param onSeasonSelected callback when a pill is tapped
 * @param accentColor the per-anime accent color from the detail view theme
 */
@Composable
fun SeasonPillRow(
    seasons: List<Int>,
    selectedSeason: Int,
    watchedSeasons: Set<Int> = emptySet(),
    onSeasonSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
) {
    if (seasons.size <= 1) return

    val sortedSeasons = seasons.sortedWith(compareBy { if (it == 0) Int.MAX_VALUE else it })

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sortedSeasons.forEach { season ->
            val isSelected = season == selectedSeason
            val label = if (season == 0) "Specials" else "Season $season"

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
                    .clickable { onSeasonSelected(season) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (season in watchedSeasons) {
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
                        text = label,
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
