package eu.kanade.presentation.library.displayoptions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import tachiyomi.domain.library.model.LibraryCollectionDisplay
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle

@Composable
fun CategoriesTab(
    libraryPreferences: LibraryPreferences,
    modifier: Modifier = Modifier,
    currentDisplayMode: LibraryDisplayMode = LibraryDisplayMode.CompactGrid,
) {
    val collectionDisplayMode by libraryPreferences.collectionDisplayMode().collectAsStateWithLifecycle()
    val showCollectionTabs by libraryPreferences.collectionTabs().collectAsStateWithLifecycle()

    // Section 1: Collection display mode (tabbed vs continuous)
    HeadingItem(MR.strings.collection_display_mode)
    RadioItem(
        label = stringResource(MR.strings.collection_display_tabbed),
        selected = collectionDisplayMode == LibraryCollectionDisplay.TABBED,
        onClick = {
            libraryPreferences.collectionDisplayMode().set(LibraryCollectionDisplay.TABBED)
        },
    )
    RadioItem(
        label = stringResource(MR.strings.collection_display_continuous),
        selected = collectionDisplayMode == LibraryCollectionDisplay.CONTINUOUS,
        onClick = {
            libraryPreferences.collectionDisplayMode().set(LibraryCollectionDisplay.CONTINUOUS)
        },
    )

    // Section 2: Common settings — "show number of items" applies to both modes
    HeadingItem(MR.strings.tabs_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_number_of_items),
        pref = libraryPreferences.collectionNumberOfItems(),
    )

    // "Show author" only relevant in List or ComfortableGrid display mode
    if (currentDisplayMode == LibraryDisplayMode.List ||
        currentDisplayMode == LibraryDisplayMode.ComfortableGrid
    ) {
        CheckboxItem(
            label = stringResource(MR.strings.action_display_show_list_author),
            pref = libraryPreferences.showListAuthor(),
        )
    }

    // "Show status" only relevant in List display mode
    if (currentDisplayMode == LibraryDisplayMode.List) {
        CheckboxItem(
            label = stringResource(MR.strings.action_display_show_list_status),
            pref = libraryPreferences.showListStatus(),
        )
    }

    // Section 3: Tabbed-only settings
    if (collectionDisplayMode == LibraryCollectionDisplay.TABBED) {
        CheckboxItem(
            label = stringResource(MR.strings.action_display_show_tabs),
            pref = libraryPreferences.collectionTabs(),
        )
        // "Show library title" is only meaningful when tabs are off
        if (!showCollectionTabs) {
            CheckboxItem(
                label = stringResource(MR.strings.action_display_show_library_title),
                pref = libraryPreferences.showLibraryTitle(),
            )
        }
    }
}
