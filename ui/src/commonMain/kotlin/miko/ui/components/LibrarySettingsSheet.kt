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
import miko.ui.library.LibraryScreenModel
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Android's library settings dialog (Display / Sort tabs): display mode
 * radio list plus the library sort rows whose trailing arrow shows the
 * active direction. Writes pref_display_mode_library and the carrier's
 * library sorting pref through the model setters.
 */
@Composable
fun LibrarySettingsSheet(
    displayMode: LibraryDisplayMode,
    sort: LibraryScreenModel.LibrarySort,
    onDismissRequest: () -> Unit,
    onDisplayMode: (LibraryDisplayMode) -> Unit,
    onSort: (LibraryScreenModel.LibrarySort) -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        TabRow(selectedTabIndex = page) {
            listOf(
                MR.strings.action_display,
                MR.strings.action_sort,
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
                0 -> listOf(
                    MR.strings.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
                    MR.strings.action_display_grid to LibraryDisplayMode.CompactGrid,
                    MR.strings.action_display_list to LibraryDisplayMode.List,
                ).forEach { (res, mode) ->
                    LibraryRadioRow(
                        label = stringResource(res),
                        selected = displayMode == mode,
                        onClick = { onDisplayMode(mode) },
                    )
                }
                else -> listOf(
                    MR.strings.action_sort_alpha to LibraryScreenModel.SortType.ALPHABETICAL,
                    MR.strings.action_sort_last_read to LibraryScreenModel.SortType.LAST_READ,
                    MR.strings.action_sort_last_manga_update to LibraryScreenModel.SortType.LAST_UPDATE,
                    MR.strings.action_sort_unread_count to LibraryScreenModel.SortType.UNREAD_COUNT,
                    MR.strings.action_sort_total to LibraryScreenModel.SortType.TOTAL_CHAPTERS,
                    MR.strings.action_sort_latest_chapter to LibraryScreenModel.SortType.LATEST_CHAPTER,
                    MR.strings.action_sort_chapter_fetch_date to LibraryScreenModel.SortType.CHAPTER_FETCH_DATE,
                    MR.strings.action_sort_date_added to LibraryScreenModel.SortType.DATE_ADDED,
                ).forEach { (res, type) ->
                    // Android's SortItem — re-tapping the active type flips
                    // direction; picking a new type defaults to ascending.
                    LibrarySortRow(
                        label = stringResource(res),
                        sortDescending = if (sort.type == type) {
                            !sort.ascending
                        } else {
                            null
                        },
                        onClick = {
                            if (sort.type == type) {
                                onSort(sort.copy(ascending = !sort.ascending))
                            } else {
                                onSort(LibraryScreenModel.LibrarySort(type))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRowBase(
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
private fun LibraryRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    LibraryRowBase(label = label, onClick = onClick) {
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun LibrarySortRow(
    label: String,
    sortDescending: Boolean?,
    onClick: () -> Unit,
) {
    LibraryRowBase(label = label, onClick = onClick) {
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
