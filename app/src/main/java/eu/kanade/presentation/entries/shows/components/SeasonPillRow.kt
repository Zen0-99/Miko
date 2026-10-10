package eu.kanade.presentation.entries.shows.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.accentTintedSurface
import eu.kanade.presentation.entries.components.rememberOnAccentColor

/**
 * NuvioMobile-style horizontal scrollable season pills.
 *
 * Shows one pill per season. The selected season is filled; others are
 * subtle. Season 0 is labeled "Specials". The selected pill scrolls into
 * view when it isn't already — a progress-derived default (e.g. the final
 * season of a fully-watched show) must not sit off the right edge.
 *
 * @param seasons list of season numbers (e.g. [0, 1, 2])
 * @param selectedSeason the currently selected season number
 * @param watchedSeasons seasons whose episodes are all seen — pill gets a check
 * @param onSeasonSelected callback when a pill is tapped
 * @param onSeasonLongPressed callback when a pill is held — toggles the whole
 * season's seen state
 * @param accentColor the per-anime accent color from the detail view theme
 */
@Composable
fun SeasonPillRow(
    seasons: List<Int>,
    selectedSeason: Int,
    watchedSeasons: Set<Int> = emptySet(),
    onSeasonSelected: (Int) -> Unit,
    onSeasonLongPressed: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
) {
    if (seasons.size <= 1) return

    val sortedSeasons = seasons.sortedWith(compareBy { if (it == 0) Int.MAX_VALUE else it })

    val listState = rememberLazyListState()
    // Bring the selected pill into view only when it isn't fully visible —
    // tap selections are already on screen, so this fires on progress/
    // fallback selection (initial open, season auto-advance), not taps.
    LaunchedEffect(selectedSeason, sortedSeasons) {
        val index = sortedSeasons.indexOf(selectedSeason)
        if (index < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val pill = info.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyVisible = pill != null &&
            pill.offset >= info.viewportStartOffset &&
            pill.offset + pill.size <= info.viewportEndOffset
        if (!fullyVisible) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        // Top kept tight — the count header above already carries 12dp of
        // bottom padding; bottom is roomier so the episode cards don't
        // sit flush against the pills.
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 14.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(sortedSeasons, key = { index, _ -> index }) { _, season ->
            val isSelected = season == selectedSeason
            val label = if (season == 0) "Specials" else "Season $season"

            val selectedBg = accentColor ?: MaterialTheme.colorScheme.primary
            val isWatched = season in watchedSeasons
            // Unselected pills sit on the entry accent darkened into a
            // surface (chapter-card logic); fully-watched seasons dim a
            // notch further, same as read chapter cards.
            val unselectedBg = accentTintedSurface(
                accent = selectedBg,
                background = MaterialTheme.colorScheme.background,
                dimmed = isWatched,
            )
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
                            unselectedBg
                        },
                    )
                    .combinedClickable(
                        onClick = { onSeasonSelected(season) },
                        onLongClick = { onSeasonLongPressed(season) },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isWatched) {
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
                        } else if (isWatched) {
                            // Watched: the accent text darkened to match
                            // the dimmed surface — reads as "consumed".
                            selectedBg.copy(alpha = 0.72f)
                        } else {
                            selectedBg
                        },
                    )
                }
            }
        }
    }
}
