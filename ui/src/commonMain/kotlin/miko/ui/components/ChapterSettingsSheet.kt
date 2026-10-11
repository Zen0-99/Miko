package miko.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import miko.ui.carrier.BrowseCarrier
import miko.ui.carrier.ChapterSortMode
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
    unreadOnly: Boolean,
    bookmarkedOnly: Boolean,
    sortMode: ChapterSortMode,
    sortDescending: Boolean,
    displayByNumber: Boolean,
    onDismissRequest: () -> Unit,
    onToggleUnread: () -> Unit,
    onToggleBookmarked: () -> Unit,
    onSortMode: (ChapterSortMode) -> Unit,
    onToggleSortDirection: () -> Unit,
    onDisplayByNumber: (Boolean) -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        TabRow(selectedTabIndex = page) {
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> {
                    SheetCheckRow(
                        label = stringResource(MR.strings.action_filter_unread),
                        checked = unreadOnly,
                        onClick = onToggleUnread,
                    )
                    SheetCheckRow(
                        label = stringResource(MR.strings.action_filter_bookmarked),
                        checked = bookmarkedOnly,
                        onClick = onToggleBookmarked,
                    )
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

@Composable
private fun SheetRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    SheetRowBase(label = label, onClick = onClick) {
        RadioButton(selected = selected, onClick = null)
    }
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
