package eu.kanade.presentation.library.novel

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.ui.library.novel.NovelLibrarySettingsScreenModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.novel.model.NovelLibrarySort
import tachiyomi.domain.library.novel.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.BaseSortItem
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun NovelLibrarySettingsDialog(
    onDismissRequest: () -> Unit,
    screenModel: NovelLibrarySettingsScreenModel,
    collection: Collection?,
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.action_sort),
            stringResource(MR.strings.action_display),
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> FilterPage(screenModel = screenModel)
                1 -> SortPage(collection = collection, screenModel = screenModel)
                2 -> DisplayPage(screenModel = screenModel)
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    screenModel: NovelLibrarySettingsScreenModel,
) {
    val filterDownloaded by screenModel.libraryPreferences.filterDownloadedNovel().collectAsStateWithLifecycle()
    val downloadedOnly by screenModel.preferences.downloadedOnly().collectAsStateWithLifecycle()

    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = if (downloadedOnly) {
            TriState.ENABLED_IS
        } else {
            filterDownloaded
        },
        enabled = !downloadedOnly,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterDownloadedNovel) },
    )
    val filterUnread by screenModel.libraryPreferences.filterUnreadNovel().collectAsStateWithLifecycle()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = filterUnread,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterUnreadNovel) },
    )
    val filterStarted by screenModel.libraryPreferences.filterStartedNovel().collectAsStateWithLifecycle()
    TriStateItem(
        label = stringResource(MR.strings.label_started),
        state = filterStarted,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterStartedNovel) },
    )
    val filterBookmarked by screenModel.libraryPreferences.filterBookmarkedNovel().collectAsStateWithLifecycle()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = filterBookmarked,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterBookmarkedNovel) },
    )
    val filterCompleted by screenModel.libraryPreferences.filterCompletedNovel().collectAsStateWithLifecycle()
    TriStateItem(
        label = stringResource(MR.strings.completed),
        state = filterCompleted,
        onClick = { screenModel.toggleFilter(LibraryPreferences::filterCompletedNovel) },
    )
}

@Composable
private fun ColumnScope.SortPage(
    collection: Collection?,
    screenModel: NovelLibrarySettingsScreenModel,
) {
    val sortingMode = collection?.sort?.type ?: NovelLibrarySort.Type.Alphabetical
    val sortDescending = !(collection?.sort?.isAscending ?: true)

    val options = remember {
        listOfNotNull(
            MR.strings.action_sort_alpha to NovelLibrarySort.Type.Alphabetical,
            MR.strings.action_sort_total to NovelLibrarySort.Type.TotalChapters,
            MR.strings.action_sort_last_read to NovelLibrarySort.Type.LastRead,
            AYMR.strings.action_sort_last_manga_update to NovelLibrarySort.Type.LastUpdate,
            MR.strings.action_sort_unread_count to NovelLibrarySort.Type.UnreadCount,
            MR.strings.action_sort_latest_chapter to NovelLibrarySort.Type.LatestChapter,
            MR.strings.action_sort_chapter_fetch_date to NovelLibrarySort.Type.ChapterFetchDate,
            MR.strings.action_sort_date_added to NovelLibrarySort.Type.DateAdded,
            AYMR.strings.action_sort_custom_order to NovelLibrarySort.Type.CustomOrder,
            MR.strings.action_sort_random to NovelLibrarySort.Type.Random,
        )
    }

    options.map { (titleRes, mode) ->
        if (mode == NovelLibrarySort.Type.Random) {
            BaseSortItem(
                label = stringResource(titleRes),
                icon = Icons.Default.Refresh
                    .takeIf { sortingMode == NovelLibrarySort.Type.Random },
                onClick = {
                    screenModel.setSort(collection, mode, NovelLibrarySort.Direction.Ascending)
                },
            )
            return@map
        }
        SortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = {
                val isTogglingDirection = sortingMode == mode
                val direction = when {
                    isTogglingDirection -> if (sortDescending) {
                        NovelLibrarySort.Direction.Ascending
                    } else {
                        NovelLibrarySort.Direction.Descending
                    }
                    else -> if (sortDescending) {
                        NovelLibrarySort.Direction.Descending
                    } else {
                        NovelLibrarySort.Direction.Ascending
                    }
                }
                screenModel.setSort(collection, mode, direction)
            },
        )
    }
}

private val displayModes = listOf(
    MR.strings.action_display_grid to LibraryDisplayMode.CompactGrid,
    MR.strings.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
    MR.strings.action_display_cover_only_grid to LibraryDisplayMode.CoverOnlyGrid,
    MR.strings.action_display_list to LibraryDisplayMode.List,
)

@Composable
private fun ColumnScope.DisplayPage(
    screenModel: NovelLibrarySettingsScreenModel,
) {
    val displayMode by screenModel.libraryPreferences.displayMode().collectAsStateWithLifecycle()
    SettingsChipRow(MR.strings.action_display_mode) {
        displayModes.map { (titleRes, mode) ->
            FilterChip(
                selected = displayMode == mode,
                onClick = { screenModel.setDisplayMode(mode) },
                label = { Text(stringResource(titleRes)) },
            )
        }
    }

    val configuration = LocalConfiguration.current
    val columnPreference = remember {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            screenModel.libraryPreferences.novelLandscapeColumns()
        } else {
            screenModel.libraryPreferences.novelPortraitColumns()
        }
    }

    val columns by columnPreference.collectAsStateWithLifecycle()
    if (displayMode == LibraryDisplayMode.List) {
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(AYMR.strings.pref_library_rows),
            valueText = if (columns > 0) {
                columns.toString()
            } else {
                stringResource(MR.strings.label_auto)
            },
            onChange = columnPreference::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    } else {
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(MR.strings.pref_library_columns),
            valueText = if (columns > 0) {
                columns.toString()
            } else {
                stringResource(MR.strings.label_auto)
            },
            onChange = columnPreference::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    HeadingItem(MR.strings.overlay_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_download_badge),
        pref = screenModel.libraryPreferences.downloadBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_unread_badge),
        pref = screenModel.libraryPreferences.unreadBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_local_badge),
        pref = screenModel.libraryPreferences.localBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_language_badge),
        pref = screenModel.libraryPreferences.languageBadge(),
    )
    CheckboxItem(
        label = stringResource(AYMR.strings.action_display_show_continue_reading_button),
        pref = screenModel.libraryPreferences.showContinueViewingButton(),
    )

    HeadingItem(MR.strings.tabs_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_tabs),
        pref = screenModel.libraryPreferences.collectionTabs(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_number_of_items),
        pref = screenModel.libraryPreferences.collectionNumberOfItems(),
    )
}
