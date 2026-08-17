package eu.kanade.presentation.library.filter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Persistent filter sheet anchored at the bottom of the library screen, above the nav bar.
 *
 * - COLLAPSED: shows quick filter chips row + filter button
 * - EXPANDED: also shows action buttons (Group By, Display, Expand/Collapse collections)
 *
 * @param visibility Current visibility state
 * @param filters Ordered list of filter chip data
 * @param bottomOffset Padding from the bottom (nav bar height)
 * @param onFilterButtonClick Called when the filter icon is tapped (opens full filter sheet)
 * @param onGroupByClick Called when the Group By action button is tapped
 * @param onDisplayClick Called when the Display action button is tapped
 * @param onExpandCollapseClick Called when Expand/Collapse collections button is tapped
 * @param onClearFilters Called when long-pressing the filter icon
 * @param showExpandCollapse Whether to show the expand/collapse collections button
 * @param allExpanded Whether all collections are expanded (for button icon direction)
 */
@Composable
fun PersistentFilterSheet(
    visibility: FilterSheetVisibility,
    filters: List<FilterChipData>,
    bottomOffset: androidx.compose.ui.unit.Dp,
    onFilterButtonClick: () -> Unit,
    onGroupByClick: () -> Unit = {},
    onDisplayClick: () -> Unit,
    onExpandCollapseClick: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    showExpandCollapse: Boolean = false,
    allExpanded: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val isExpanded = visibility == FilterSheetVisibility.EXPANDED
    val hasActiveFilters = filters.any { it.state != TriState.DISABLED }

    // Sort filters: active first, then inactive, respecting filter order
    val orderedFilters = remember(filters) {
        filters.partition { it.state != TriState.DISABLED }
            .toList()
            .flatten()
    }

    // Animate the entire sheet sliding in/out from the bottom
    AnimatedVisibility(
        visible = visibility != FilterSheetVisibility.HIDDEN,
        enter = slideInVertically(
            animationSpec = tween(250),
            initialOffsetY = { it },
        ) + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(
            animationSpec = tween(250),
            targetOffsetY = { it },
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                )
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(bottom = bottomOffset),
        ) {
        // Quick filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Filter icon button
            FilterIconButton(
                onClick = onFilterButtonClick,
                onLongClick = onClearFilters,
                hasActiveFilters = hasActiveFilters,
            )

            // Filter chips
            orderedFilters.forEach { filter ->
                FilterTagChip(
                    label = stringResource(filter.labelRes),
                    state = filter.state,
                    enabled = filter.enabled,
                    onToggle = filter.onToggle,
                )
            }

            // Clear filters button (inline, only when active)
            if (hasActiveFilters) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(MR.strings.clear_filters),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onClearFilters)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        // Action buttons (only when expanded)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                FilterMenuButton(
                    icon = Icons.Outlined.Label,
                    text = stringResource(MR.strings.group_library_by),
                    onClick = onGroupByClick,
                )
                FilterMenuButton(
                    icon = Icons.Outlined.Tune,
                    text = stringResource(MR.strings.action_display),
                    onClick = onDisplayClick,
                )
                if (showExpandCollapse && allExpanded != null) {
                    FilterMenuButton(
                        icon = if (allExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        text = stringResource(
                            if (allExpanded) MR.strings.collapse_all_categories else MR.strings.expand_all_categories,
                        ),
                        onClick = onExpandCollapseClick,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun FilterIconButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    hasActiveFilters: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (hasActiveFilters) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FilterList,
            contentDescription = stringResource(MR.strings.action_filter),
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun FilterMenuButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
