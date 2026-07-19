package eu.kanade.presentation.entries.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.preference.TriState
import eu.kanade.domain.entries.novel.model.downloadedFilter
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun NovelChapterSettingsDialog(
    onDismissRequest: () -> Unit,
    novel: Novel,
    onUnreadFilterChanged: (TriState) -> Unit,
    onBookmarkedFilterChanged: (TriState) -> Unit,
    onDownloadedFilterChanged: (TriState) -> Unit,
    onSortModeChanged: (Long) -> Unit,
    onDisplayModeChanged: (Long) -> Unit,
    onSetAsDefault: (applyToExisting: Boolean) -> Unit,
    onResetToDefault: () -> Unit,
    accentColor: Color? = null,
) {
    var showSetAsDefaultDialog by rememberSaveable { mutableStateOf(false) }
    if (showSetAsDefaultDialog) {
        SetAsDefaultDialog(
            onDismissRequest = { showSetAsDefaultDialog = false },
            onConfirmed = onSetAsDefault,
        )
    }

    // Apply the novel's accent color as the primary color so the tab
    // indicator, selected tab text, sort arrows, active tri-state
    // checkboxes and selected radio buttons match the novel theme —
    // same approach as NovelReaderSettingsDialog.
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    val accentedScheme = MaterialTheme.colorScheme.copy(primary = accent)
    MaterialTheme(colorScheme = accentedScheme) {
        TabbedDialog(
            onDismissRequest = onDismissRequest,
            tabTitles = persistentListOf(
                stringResource(MR.strings.action_filter),
                stringResource(MR.strings.action_sort),
                stringResource(MR.strings.action_display),
            ),
            tabOverflowMenuContent = { closeMenu ->
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.set_chapter_settings_as_default)) },
                    onClick = {
                        showSetAsDefaultDialog = true
                        closeMenu()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_reset)) },
                    onClick = {
                        onResetToDefault()
                        closeMenu()
                    },
                )
            },
        ) { page ->
            Column(
                modifier = Modifier
                    .padding(vertical = TabbedDialogPaddings.Vertical)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (page) {
                    0 -> {
                        FilterPage(
                            downloadedFilter = novel.downloadedFilter,
                            onDownloadedFilterChanged = onDownloadedFilterChanged,
                            unreadFilter = novel.unreadFilter,
                            onUnreadFilterChanged = onUnreadFilterChanged,
                            bookmarkedFilter = novel.bookmarkedFilter,
                            onBookmarkedFilterChanged = onBookmarkedFilterChanged,
                        )
                    }
                    1 -> {
                        SortPage(
                            sortingMode = novel.sorting,
                            sortDescending = novel.sortDescending(),
                            onItemSelected = onSortModeChanged,
                        )
                    }
                    2 -> {
                        DisplayPage(
                            displayMode = novel.displayMode,
                            onItemSelected = onDisplayModeChanged,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    downloadedFilter: TriState,
    onDownloadedFilterChanged: (TriState) -> Unit,
    unreadFilter: TriState,
    onUnreadFilterChanged: (TriState) -> Unit,
    bookmarkedFilter: TriState,
    onBookmarkedFilterChanged: (TriState) -> Unit,
) {
    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = downloadedFilter,
        onClick = {
            val next = when (downloadedFilter) {
                TriState.DISABLED -> TriState.ENABLED_IS
                TriState.ENABLED_IS -> TriState.ENABLED_NOT
                TriState.ENABLED_NOT -> TriState.DISABLED
            }
            onDownloadedFilterChanged(next)
        },
    )
    TriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = unreadFilter,
        onClick = {
            val next = when (unreadFilter) {
                TriState.DISABLED -> TriState.ENABLED_IS
                TriState.ENABLED_IS -> TriState.ENABLED_NOT
                TriState.ENABLED_NOT -> TriState.DISABLED
            }
            onUnreadFilterChanged(next)
        },
    )
    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = bookmarkedFilter,
        onClick = {
            val next = when (bookmarkedFilter) {
                TriState.DISABLED -> TriState.ENABLED_IS
                TriState.ENABLED_IS -> TriState.ENABLED_NOT
                TriState.ENABLED_NOT -> TriState.DISABLED
            }
            onBookmarkedFilterChanged(next)
        },
    )
}

@Composable
private fun ColumnScope.SortPage(
    sortingMode: Long,
    sortDescending: Boolean,
    onItemSelected: (Long) -> Unit,
) {
    listOf(
        MR.strings.sort_by_source to Novel.CHAPTER_SORTING_SOURCE,
        MR.strings.sort_by_number to Novel.CHAPTER_SORTING_NUMBER,
        MR.strings.sort_by_upload_date to Novel.CHAPTER_SORTING_UPLOAD_DATE,
        MR.strings.action_sort_alpha to Novel.CHAPTER_SORTING_ALPHABET,
    ).map { (titleRes, mode) ->
        SortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = { onItemSelected(mode) },
        )
    }
}

@Composable
private fun ColumnScope.DisplayPage(
    displayMode: Long,
    onItemSelected: (Long) -> Unit,
) {
    listOf(
        MR.strings.show_title to Novel.CHAPTER_DISPLAY_NAME,
        MR.strings.show_chapter_number to Novel.CHAPTER_DISPLAY_NUMBER,
    ).map { (titleRes, mode) ->
        RadioItem(
            label = stringResource(titleRes),
            selected = displayMode == mode,
            onClick = { onItemSelected(mode) },
        )
    }
}

@Composable
private fun SetAsDefaultDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: (optionalChecked: Boolean) -> Unit,
) {
    var optionalChecked by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.chapter_settings)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(MR.strings.confirm_set_chapter_settings))

                LabeledCheckbox(
                    label = stringResource(AYMR.strings.also_set_chapter_settings_for_library),
                    checked = optionalChecked,
                    onCheckedChange = { optionalChecked = it },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmed(optionalChecked)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
