package eu.kanade.presentation.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** One row in the sub-category context menu — a subtype label, whether
 *  it's the active filter, and the action that applies it. */
data class SubtypeMenuOption(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

/** Display label for a subtype selection — "all types" is the unfettered
 *  default; the rest name the pinned sub-category. */
fun subtypeLabelFor(subtype: tachiyomi.domain.library.model.LibrarySubtype): String =
    when (subtype) {
        tachiyomi.domain.library.model.LibrarySubtype.ALL -> "All types"
        tachiyomi.domain.library.model.LibrarySubtype.MANGA -> "Manga"
        tachiyomi.domain.library.model.LibrarySubtype.WEBTOON -> "Webtoons"
        tachiyomi.domain.library.model.LibrarySubtype.BOOK -> "Books"
        tachiyomi.domain.library.model.LibrarySubtype.NOVEL -> "Novels"
        tachiyomi.domain.library.model.LibrarySubtype.AUDIOBOOK -> "Audiobooks"
        tachiyomi.domain.library.model.LibrarySubtype.SERIES -> "Series"
        tachiyomi.domain.library.model.LibrarySubtype.MOVIE -> "Movies"
    }

/** Menu options for a mode's subtype choices (ALL + the mode's pair). */
fun subtypeOptionsFor(
    choices: List<tachiyomi.domain.library.model.LibrarySubtype>,
    current: tachiyomi.domain.library.model.LibrarySubtype,
    onSelect: (tachiyomi.domain.library.model.LibrarySubtype) -> Unit,
): List<SubtypeMenuOption> = choices.map { choice ->
    SubtypeMenuOption(
        label = subtypeLabelFor(choice),
        selected = choice == current,
        onSelect = { onSelect(choice) },
    )
}

/**
 * Collection header row matching the old repo's LibraryHeaderHolder layout:
 * - Title (18sp, semi-bold) on the left with item count in parentheses
 * - Optional subtype sub-line under the title — taps open a context menu
 *   that filters the whole mode (all collections) by entry subtype
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
    subtypeLabel: String? = null,
    subtypeOptions: List<SubtypeMenuOption> = emptyList(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        // Top-aligned: the sort chip sits on the title line even when the
        // subtype sub-line adds a second row beneath it.
        verticalAlignment = Alignment.Top,
        horizontalArrangement = if (showTitle) Arrangement.SpaceBetween else Arrangement.End,
    ) {
        // Title + item count on the left, subtype selector underneath.
        // The whole block — name included — opens the subtype menu.
        if (showTitle) {
            var subtypeMenuExpanded by remember { mutableStateOf(false) }
            // Click target is deliberately oversized — the entire title
            // region (name, count, sub-line, and the empty space to the
            // sort chip) opens the subtype menu.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = subtypeLabel != null) {
                        subtypeMenuExpanded = true
                    },
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    if (subtypeLabel != null) {
                        SubtypeSelectorLabel(label = subtypeLabel)
                    }
                }
                if (subtypeLabel != null) {
                    SubtypeDropdownMenu(
                        expanded = subtypeMenuExpanded,
                        onDismiss = { subtypeMenuExpanded = false },
                        options = subtypeOptions,
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
                        imageVector = if (sortDescending) {
                            Icons.Outlined.ArrowDownward
                        } else {
                            Icons.Outlined.ArrowUpward
                        },
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

/** The subtype sub-line label — name + caret; click handling lives on the
 *  enclosing block (the whole collection header/title opens the menu). */
@Composable
internal fun SubtypeSelectorLabel(label: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.Outlined.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Standalone subtype selector for surfaces with no collection header —
 * the empty-library branch uses it so a subtype pick that filters to
 * zero entries doesn't strand the only way back (in the normal path the
 * selector lives on the header, which vanishes with the collection).
 */
@Composable
fun SubtypeSelectorRow(
    label: String,
    options: List<SubtypeMenuOption>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SubtypeSelectorLabel(label = label)
        SubtypeDropdownMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            options = options,
        )
    }
}

/** The subtype context menu — expansion state is owned by the caller so
 *  any part of the header (title, count, sub-line) can open it. */
@Composable
internal fun SubtypeDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<SubtypeMenuOption>,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = option.label,
                        fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (option.selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                },
                onClick = {
                    onDismiss()
                    option.onSelect()
                },
            )
        }
    }
}
