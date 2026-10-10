package eu.kanade.presentation.entries.components

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

/**
 * Horizontal scrollable pill row for chapter groups parsed from chapter names
 * ("Volume 1", "Book 3", "Season 2"…) — the manga/novel counterpart of the
 * anime season pills. Hidden by the caller when there is only one group.
 * The selected pill is scrolled into view when it isn't already — a
 * progress-derived default (e.g. Volume 72 of 72) must not force the user
 * to swipe the row themselves.
 *
 * @param groups ordered labels to render
 * @param selectedGroup the currently selected label
 * @param completedGroups labels whose chapters are all read — pill gets a check
 * @param onGroupSelected callback when a pill is tapped
 * @param onGroupLongPressed callback when a pill is held — toggles the whole
 * group's read state
 * @param accentColor the per-entry accent color from the detail view theme
 */
@Composable
fun ChapterGroupPillRow(
    groups: List<String>,
    selectedGroup: String,
    completedGroups: Set<String> = emptySet(),
    onGroupSelected: (String) -> Unit,
    onGroupLongPressed: (String) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
) {
    if (groups.size <= 1) return

    val listState = rememberLazyListState()
    // Bring the selected pill into view only when it isn't fully visible —
    // tap selections are already on screen, so this fires on progress/
    // fallback selection (initial open, group auto-advance), not taps.
    LaunchedEffect(selectedGroup, groups) {
        val index = groups.indexOf(selectedGroup)
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
        // bottom padding; bottom is roomier so the card carousel doesn't
        // sit flush against the pills.
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 14.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(groups, key = { index, _ -> index }) { _, group ->
            val isSelected = group == selectedGroup

            val selectedBg = accentColor ?: MaterialTheme.colorScheme.primary
            val isComplete = group in completedGroups
            // Unselected pills sit on the entry accent darkened into a
            // surface (chapter-card logic); fully-read groups dim a notch
            // further, same as read chapter cards.
            val unselectedBg = accentTintedSurface(
                accent = selectedBg,
                background = MaterialTheme.colorScheme.background,
                dimmed = isComplete,
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
                        onClick = { onGroupSelected(group) },
                        onLongClick = { onGroupLongPressed(group) },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isComplete) {
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
                        } else if (isComplete) {
                            // Read group: the accent text darkened to
                            // match the dimmed surface — "consumed" look.
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
