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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import miko.ui.library.LibraryScreenModel
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
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
    filters: LibraryScreenModel.LibraryFilters,
    badges: LibraryScreenModel.LibraryBadges,
    onDismissRequest: () -> Unit,
    onDisplayMode: (LibraryDisplayMode) -> Unit,
    onSort: (LibraryScreenModel.LibrarySort) -> Unit,
    onToggleFilter: (LibraryScreenModel.LibraryFilterId) -> Unit,
    onToggleBadge: (LibraryScreenModel.LibraryBadgeId) -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        TabRow(selectedTabIndex = page) {
            listOf(
                MR.strings.action_filter,
                MR.strings.action_display,
                MR.strings.badges,
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
                    Triple(
                        MR.strings.label_downloaded_only,
                        LibraryScreenModel.LibraryFilterId.DOWNLOADED,
                        filters.downloaded,
                    ),
                    Triple(
                        MR.strings.action_filter_unread,
                        LibraryScreenModel.LibraryFilterId.UNREAD,
                        filters.unread,
                    ),
                    Triple(
                        MR.strings.label_started,
                        LibraryScreenModel.LibraryFilterId.STARTED,
                        filters.started,
                    ),
                    Triple(
                        MR.strings.action_filter_bookmarked,
                        LibraryScreenModel.LibraryFilterId.BOOKMARKED,
                        filters.bookmarked,
                    ),
                    Triple(
                        MR.strings.completed,
                        LibraryScreenModel.LibraryFilterId.COMPLETED,
                        filters.completed,
                    ),
                ).forEach { (res, id, state) ->
                    // Android's TriStateItem — tap cycles off → include
                    // → exclude → off.
                    LibraryTriStateRow(
                        label = stringResource(res),
                        state = state,
                        onClick = { onToggleFilter(id) },
                    )
                }
                1 -> listOf(
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
                2 -> {
                    // Android's BadgesTab — "Cover badges" heading with the
                    // four display_*_badge checkboxes.
                    SheetHeading(stringResource(MR.strings.overlay_cover_badges))
                    listOf(
                        Triple(
                            MR.strings.action_display_download_badge,
                            LibraryScreenModel.LibraryBadgeId.DOWNLOADED,
                            badges.downloaded,
                        ),
                        Triple(
                            MR.strings.action_display_unread_badge,
                            LibraryScreenModel.LibraryBadgeId.UNREAD,
                            badges.unread,
                        ),
                        Triple(
                            MR.strings.action_display_local_badge,
                            LibraryScreenModel.LibraryBadgeId.LOCAL,
                            badges.local,
                        ),
                        Triple(
                            MR.strings.action_display_language_badge,
                            LibraryScreenModel.LibraryBadgeId.LANGUAGE,
                            badges.language,
                        ),
                    ).forEach { (res, id, checked) ->
                        LabeledCheckbox(
                            label = stringResource(res),
                            checked = checked,
                            onCheckedChange = { onToggleBadge(id) },
                        )
                    }
                    // Android's "Actions" section — the continue-viewing
                    // cover button (reading-order badge stays deferred
                    // with mikoDB infra).
                    SheetHeading(stringResource(MR.strings.overlay_actions))
                    LabeledCheckbox(
                        label = stringResource(
                            AYMR.strings.action_display_show_continue_reading_button,
                        ),
                        checked = badges.continueButton,
                        onCheckedChange = {
                            onToggleBadge(LibraryScreenModel.LibraryBadgeId.CONTINUE_BUTTON)
                        },
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

/**
 * Android's TriStateItem — DISABLED shows an empty box, ENABLED_IS a
 * check, ENABLED_NOT a cross; tap cycles through the three states.
 */
@Composable
private fun LibraryTriStateRow(
    label: String,
    state: TriState,
    onClick: () -> Unit,
) {
    LibraryRowBase(label = label, onClick = onClick) {
        when (state) {
            TriState.ENABLED_IS -> Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            TriState.ENABLED_NOT -> Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            TriState.DISABLED -> Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

/** Android's HeadingItem — bold titleSmall section header in the sheet. */
@Composable
private fun SheetHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
    )
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
