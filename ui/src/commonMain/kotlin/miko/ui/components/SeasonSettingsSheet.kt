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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import miko.domain.shows.SeasonDisplayMode
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Port of Android's `SeasonSettingsDialog` — the Filter / Sort / Display
 * tabbed sheet the season-list filter icon opens on structured
 * (FetchType.Seasons) shows. Every row writes the matching seasonFlags
 * bit through the model's setters.
 */
@Composable
fun SeasonSettingsSheet(
    downloadedFilter: TriState,
    unseenFilter: TriState,
    startedFilter: TriState,
    completedFilter: TriState,
    bookmarkedFilter: TriState,
    fillermarkedFilter: TriState,
    sortingMode: Long,
    sortDescending: Boolean,
    displayGridMode: SeasonDisplayMode,
    displayGridSize: Int,
    overlayDownloaded: Boolean,
    overlayUnseen: Boolean,
    overlayLocal: Boolean,
    overlayLang: Boolean,
    overlayContinue: Boolean,
    displayMode: Long,
    onDismissRequest: () -> Unit,
    onDownloadedFilter: (TriState) -> Unit,
    onUnseenFilter: (TriState) -> Unit,
    onStartedFilter: (TriState) -> Unit,
    onCompletedFilter: (TriState) -> Unit,
    onBookmarkedFilter: (TriState) -> Unit,
    onFillermarkedFilter: (TriState) -> Unit,
    onSortMode: (Long) -> Unit,
    onDisplayGridMode: (SeasonDisplayMode) -> Unit,
    onDisplayGridSize: (Int) -> Unit,
    onOverlayDownloaded: (Boolean) -> Unit,
    onOverlayUnseen: (Boolean) -> Unit,
    onOverlayLocal: (Boolean) -> Unit,
    onOverlayLang: (Boolean) -> Unit,
    onOverlayContinue: (Boolean) -> Unit,
    onDisplayMode: (Long) -> Unit,
    /** Android's tab overflow — SetAsDefaultDialog's applyToExisting. */
    onSetAsDefault: ((Boolean) -> Unit)? = null,
) {
    var page by remember { mutableIntStateOf(0) }
    var defaultDialogOpen by remember { mutableStateOf(false) }
    if (defaultDialogOpen) {
        SeasonSetAsDefaultDialog(
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
            if (onSetAsDefault != null) {
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
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        MR.strings.set_chapter_settings_as_default,
                                    ),
                                )
                            },
                            onClick = {
                                overflowOpen = false
                                defaultDialogOpen = true
                            },
                        )
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
                    SeasonTriStateRow(
                        label = stringResource(MR.strings.label_downloaded),
                        state = downloadedFilter,
                        onClick = { onDownloadedFilter(downloadedFilter.seasonNext()) },
                    )
                    SeasonTriStateRow(
                        label = stringResource(AYMR.strings.action_filter_unseen),
                        state = unseenFilter,
                        onClick = { onUnseenFilter(unseenFilter.seasonNext()) },
                    )
                    SeasonTriStateRow(
                        label = stringResource(MR.strings.label_started),
                        state = startedFilter,
                        onClick = { onStartedFilter(startedFilter.seasonNext()) },
                    )
                    SeasonTriStateRow(
                        label = stringResource(MR.strings.completed),
                        state = completedFilter,
                        onClick = { onCompletedFilter(completedFilter.seasonNext()) },
                    )
                    SeasonTriStateRow(
                        label = stringResource(MR.strings.action_filter_bookmarked),
                        state = bookmarkedFilter,
                        onClick = { onBookmarkedFilter(bookmarkedFilter.seasonNext()) },
                    )
                    SeasonTriStateRow(
                        label = stringResource(AYMR.strings.action_filter_fillermarked),
                        state = fillermarkedFilter,
                        onClick = { onFillermarkedFilter(fillermarkedFilter.seasonNext()) },
                    )
                }
                1 -> {
                    // Android's SeasonSortPage — the row's arrow shows
                    // direction only on the active mode; re-picking the
                    // active mode flips it (the interactor owns the flip).
                    listOf(
                        MR.strings.sort_by_source to Anime.SEASON_SORT_SOURCE,
                        AYMR.strings.sort_by_season_number to Anime.SEASON_SORT_SEASON,
                        MR.strings.sort_by_upload_date to Anime.SEASON_SORT_UPLOAD,
                        MR.strings.action_sort_alpha to Anime.SEASON_SORT_ALPHABET,
                        AYMR.strings.action_sort_unseen_count to Anime.SEASON_SORT_COUNT,
                        AYMR.strings.action_sort_last_seen to Anime.SEASON_SORT_LAST_SEEN,
                        AYMR.strings.action_sort_episode_fetch_date to Anime.SEASON_SORT_FETCHED,
                    ).forEach { (res, mode) ->
                        SeasonSortRow(
                            label = stringResource(res),
                            sortDescending = sortDescending.takeIf { sortingMode == mode },
                            onClick = { onSortMode(mode) },
                        )
                    }
                }
                else -> {
                    SeasonHeadingRow(stringResource(MR.strings.action_display_mode))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            MR.strings.action_display_grid to SeasonDisplayMode.CompactGrid,
                            MR.strings.action_display_comfortable_grid to
                                SeasonDisplayMode.ComfortableGrid,
                            MR.strings.action_display_cover_only_grid to
                                SeasonDisplayMode.CoverOnlyGrid,
                            MR.strings.action_display_list to SeasonDisplayMode.List,
                        ).forEach { (res, mode) ->
                            FilterChip(
                                selected = displayGridMode == mode,
                                onClick = { onDisplayGridMode(mode) },
                                label = { Text(stringResource(res)) },
                            )
                        }
                    }
                    // Android's SliderItem — 0 reads as "auto"; the label
                    // swaps columns↔rows between grid and list modes.
                    val sizeLabel = if (displayGridMode == SeasonDisplayMode.List) {
                        AYMR.strings.pref_library_rows
                    } else {
                        MR.strings.pref_library_columns
                    }
                    SeasonSliderRow(
                        label = stringResource(sizeLabel),
                        value = displayGridSize,
                        valueRange = 0f..10f,
                        valueText = if (displayGridSize > 0) {
                            displayGridSize.toString()
                        } else {
                            stringResource(MR.strings.label_auto)
                        },
                        onChange = onDisplayGridSize,
                    )
                    SeasonHeadingRow(stringResource(MR.strings.overlay_header))
                    SeasonCheckRow(
                        label = stringResource(
                            AYMR.strings.action_display_download_badge_anime,
                        ),
                        checked = overlayDownloaded,
                        onClick = { onOverlayDownloaded(!overlayDownloaded) },
                    )
                    SeasonCheckRow(
                        label = stringResource(AYMR.strings.action_display_unseen_badge),
                        checked = overlayUnseen,
                        onClick = { onOverlayUnseen(!overlayUnseen) },
                    )
                    SeasonCheckRow(
                        label = stringResource(MR.strings.action_display_local_badge),
                        checked = overlayLocal,
                        onClick = { onOverlayLocal(!overlayLocal) },
                    )
                    SeasonCheckRow(
                        label = stringResource(MR.strings.action_display_language_badge),
                        checked = overlayLang,
                        onClick = { onOverlayLang(!overlayLang) },
                    )
                    SeasonCheckRow(
                        label = stringResource(
                            AYMR.strings.action_display_show_continue_watching_button,
                        ),
                        checked = overlayContinue,
                        onClick = { onOverlayContinue(!overlayContinue) },
                    )
                    SeasonHeadingRow(stringResource(AYMR.strings.action_display_grid_mode))
                    SeasonRadioRow(
                        label = stringResource(MR.strings.show_title),
                        selected = displayMode == Anime.SEASON_DISPLAY_MODE_SOURCE,
                        onClick = { onDisplayMode(Anime.SEASON_DISPLAY_MODE_SOURCE) },
                    )
                    SeasonRadioRow(
                        label = stringResource(AYMR.strings.show_season_number),
                        selected = displayMode == Anime.SEASON_DISPLAY_MODE_NUMBER,
                        onClick = { onDisplayMode(Anime.SEASON_DISPLAY_MODE_NUMBER) },
                    )
                }
            }
        }
    }
}

private fun TriState.seasonNext() = when (this) {
    TriState.DISABLED -> TriState.ENABLED_IS
    TriState.ENABLED_IS -> TriState.ENABLED_NOT
    TriState.ENABLED_NOT -> TriState.DISABLED
}

@Composable
private fun SeasonRowBase(
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
private fun SeasonHeadingRow(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SeasonCheckRow(label: String, checked: Boolean, onClick: () -> Unit) {
    SeasonRowBase(label = label, onClick = onClick) {
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SeasonRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    SeasonRowBase(label = label, onClick = onClick) {
        RadioButton(selected = selected, onClick = null)
    }
}

/** Android's `TriStateItem` — leading state icon, cycles IS → NOT → off. */
@Composable
private fun SeasonTriStateRow(label: String, state: TriState, onClick: () -> Unit) {
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

/** Android's `SortItem` — the row's arrow shows direction only when active. */
@Composable
private fun SeasonSortRow(
    label: String,
    sortDescending: Boolean?,
    onClick: () -> Unit,
) {
    SeasonRowBase(label = label, onClick = onClick) {
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

/** Android's `SliderItem` — label + slider + live value text. */
@Composable
private fun SeasonSliderRow(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = valueRange,
            steps = 9,
        )
    }
}

/** Android's `SetAsDefaultDialog` — confirm + apply-to-library checkbox. */
@Composable
private fun SeasonSetAsDefaultDialog(
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
