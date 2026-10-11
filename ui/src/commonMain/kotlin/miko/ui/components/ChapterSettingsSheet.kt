package miko.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DisabledByDefault
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import miko.ui.carrier.BrowseCarrier
import miko.ui.carrier.ChapterSortMode
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Port of Android's `ChapterSettingsDialog` — a tabbed sheet with the
 * Filter / Sort / Display pages the entry toolbar's filter icon opens.
 * Every row writes the matching chapterFlags/episodeFlags bit through the
 * model's setters.
 */
@Composable
fun ChapterSettingsSheet(
    carrier: BrowseCarrier,
    unreadFilter: TriState,
    bookmarkedFilter: TriState,
    fillermarkedFilter: TriState,
    sortMode: ChapterSortMode,
    sortDescending: Boolean,
    displayByNumber: Boolean,
    onDismissRequest: () -> Unit,
    onCycleUnread: () -> Unit,
    onCycleBookmarked: () -> Unit,
    onCycleFillermarked: () -> Unit,
    onSortMode: (ChapterSortMode) -> Unit,
    onToggleSortDirection: () -> Unit,
    onDisplayByNumber: (Boolean) -> Unit,
    /** Episode previews/summaries (shows) — null callbacks hide the rows. */
    showPreviews: Boolean = true,
    showSummaries: Boolean = true,
    onShowPreviews: ((Boolean) -> Unit)? = null,
    onShowSummaries: ((Boolean) -> Unit)? = null,
    /** Scanlator exclusion row (manga) — null hides it like Android. */
    scanlatorFilterActive: Boolean = false,
    onScanlatorFilterClick: (() -> Unit)? = null,
    /** Android's tab overflow — SetAsDefaultDialog + reset. */
    onSetAsDefault: ((Boolean) -> Unit)? = null,
    onResetToDefault: (() -> Unit)? = null,
) {
    var page by remember { mutableIntStateOf(0) }
    var defaultDialogOpen by remember { mutableStateOf(false) }
    if (defaultDialogOpen) {
        SetAsDefaultDialog(
            onDismissRequest = { defaultDialogOpen = false },
            onConfirm = { applyToExisting ->
                defaultDialogOpen = false
                onSetAsDefault?.invoke(applyToExisting)
            },
        )
    }
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TabRow(
                selectedTabIndex = page,
                modifier = Modifier.weight(1f),
            ) {
                listOf(
                    MR.strings.action_filter,
                    MR.strings.action_sort,
                    MR.strings.action_display,
                ).forEachIndexed { index, res ->
                    Tab(
                        selected = page == index,
                        onClick = { page = index },
                        text = { Text(stringResource(res)) },
                    )
                }
            }
            if (onSetAsDefault != null || onResetToDefault != null) {
                var overflowOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = null,
                        )
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                    ) {
                        if (onSetAsDefault != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            MR.strings
                                                .set_chapter_settings_as_default,
                                        ),
                                    )
                                },
                                onClick = {
                                    overflowOpen = false
                                    defaultDialogOpen = true
                                },
                            )
                        }
                        if (onResetToDefault != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(MR.strings.action_reset))
                                },
                                onClick = {
                                    overflowOpen = false
                                    onResetToDefault()
                                },
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> {
                    // Android's TriStateItems — DISABLED → IS → NOT cycle.
                    val unreadRes = if (carrier == BrowseCarrier.SHOWS) {
                        AYMR.strings.action_filter_unseen
                    } else {
                        MR.strings.action_filter_unread
                    }
                    SheetTriStateRow(
                        label = stringResource(unreadRes),
                        state = unreadFilter,
                        onClick = onCycleUnread,
                    )
                    SheetTriStateRow(
                        label = stringResource(MR.strings.action_filter_bookmarked),
                        state = bookmarkedFilter,
                        onClick = onCycleBookmarked,
                    )
                    if (carrier == BrowseCarrier.SHOWS) {
                        SheetTriStateRow(
                            label = stringResource(AYMR.strings.action_filter_fillermarked),
                            state = fillermarkedFilter,
                            onClick = onCycleFillermarked,
                        )
                    }
                    // Android's scanlator-filter row at the bottom of the
                    // Filter tab — primary-tinted while exclusions apply.
                    if (onScanlatorFilterClick != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onScanlatorFilterClick)
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = stringResource(MR.strings.exclude_scanlators),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (scanlatorFilterActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
                1 -> {
                    val numberRes = if (carrier == BrowseCarrier.SHOWS) {
                        AYMR.strings.sort_by_episode_number
                    } else {
                        MR.strings.sort_by_number
                    }
                    listOf(
                        MR.strings.sort_by_source to ChapterSortMode.SOURCE,
                        numberRes to ChapterSortMode.NUMBER,
                        MR.strings.sort_by_upload_date to ChapterSortMode.UPLOAD_DATE,
                        MR.strings.action_sort_alpha to ChapterSortMode.ALPHABET,
                    ).forEach { (res, mode) ->
                        SheetSortRow(
                            label = stringResource(res),
                            sortDescending = sortDescending.takeIf { sortMode == mode },
                            onClick = {
                                if (sortMode == mode) {
                                    onToggleSortDirection()
                                } else {
                                    onSortMode(mode)
                                }
                            },
                        )
                    }
                }
                else -> {
                    val numberRes = if (carrier == BrowseCarrier.SHOWS) {
                        AYMR.strings.show_episode_number
                    } else {
                        MR.strings.show_chapter_number
                    }
                    SheetRadioRow(
                        label = stringResource(MR.strings.show_title),
                        selected = !displayByNumber,
                        onClick = { onDisplayByNumber(false) },
                    )
                    SheetRadioRow(
                        label = stringResource(numberRes),
                        selected = displayByNumber,
                        onClick = { onDisplayByNumber(true) },
                    )
                    if (onShowPreviews != null) {
                        SheetCheckRow(
                            label = stringResource(AYMR.strings.show_episode_previews),
                            checked = showPreviews,
                            onClick = { onShowPreviews(!showPreviews) },
                        )
                    }
                    if (onShowSummaries != null) {
                        SheetCheckRow(
                            label = stringResource(AYMR.strings.show_episode_summaries),
                            checked = showSummaries,
                            onClick = { onShowSummaries(!showSummaries) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRowBase(
    label: String,
    onClick: () -> Unit,
    widget: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        widget()
    }
}

@Composable
private fun SheetCheckRow(label: String, checked: Boolean, onClick: () -> Unit) {
    SheetRowBase(label = label, onClick = onClick) {
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/**
 * Android's `TriStateItem` — a leading state icon (blank / checked /
 * disabled-by-default, primary-tinted when engaged) instead of a trailing
 * checkbox. The click handler cycles DISABLED → IS → NOT.
 */
@Composable
private fun SheetTriStateRow(label: String, state: TriState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(
            imageVector = when (state) {
                TriState.DISABLED -> Icons.Rounded.CheckBoxOutlineBlank
                TriState.ENABLED_IS -> Icons.Rounded.CheckBox
                TriState.ENABLED_NOT -> Icons.Rounded.DisabledByDefault
            },
            contentDescription = null,
            tint = if (state == TriState.DISABLED) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SheetRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    SheetRowBase(label = label, onClick = onClick) {
        RadioButton(selected = selected, onClick = null)
    }
}

/**
 * Android's `SetAsDefaultDialog` — confirm saving the entry's current
 * flags as the default, with the optional apply-to-library checkbox.
 */
@Composable
private fun SetAsDefaultDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (applyToExisting: Boolean) -> Unit,
) {
    var applyToExisting by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.chapter_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(MR.strings.confirm_set_chapter_settings))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { applyToExisting = !applyToExisting },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = applyToExisting,
                        onCheckedChange = { applyToExisting = it },
                    )
                    Text(
                        text = stringResource(
                            MR.strings.also_set_chapter_settings_for_library,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(applyToExisting) }) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

/** Android's `SortItem` — the row's arrow shows direction only when active. */
@Composable
private fun SheetSortRow(
    label: String,
    sortDescending: Boolean?,
    onClick: () -> Unit,
) {
    SheetRowBase(label = label, onClick = onClick) {
        when (sortDescending) {
            true -> Icon(
                imageVector = Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            false -> Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            null -> Spacer(modifier = Modifier.size(24.dp))
        }
    }
}
