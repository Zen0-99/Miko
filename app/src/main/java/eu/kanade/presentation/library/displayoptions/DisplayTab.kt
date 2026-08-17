package eu.kanade.presentation.library.displayoptions

import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.IconItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle

/**
 * Library type for selecting the correct column preferences.
 */
enum class LibraryType {
    MANGA,
    ANIME,
    NOVEL,
}

private val displayModes = listOf(
    MR.strings.display_mode_compact to LibraryDisplayMode.CompactGrid,
    MR.strings.display_mode_comfortable to LibraryDisplayMode.ComfortableGrid,
    MR.strings.display_mode_cover_only to LibraryDisplayMode.CoverOnlyGrid,
    MR.strings.action_display_list to LibraryDisplayMode.List,
)

@Composable
fun DisplayTab(
    libraryPreferences: LibraryPreferences,
    libraryType: LibraryType,
    onClickReadingOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayMode by libraryPreferences.displayMode().collectAsStateWithLifecycle()

    SettingsChipRow(MR.strings.action_display_mode) {
        displayModes.map { (titleRes, mode) ->
            FilterChip(
                selected = displayMode == mode,
                onClick = { libraryPreferences.displayMode().set(mode) },
                label = { Text(stringResource(titleRes)) },
            )
        }
    }

    val configuration = LocalConfiguration.current
    val columnPreference: Preference<Int> = remember(libraryType, configuration.orientation) {
        val portrait = when (libraryType) {
            LibraryType.MANGA -> libraryPreferences.mangaPortraitColumns()
            LibraryType.ANIME -> libraryPreferences.animePortraitColumns()
            LibraryType.NOVEL -> libraryPreferences.novelPortraitColumns()
        }
        val landscape = when (libraryType) {
            LibraryType.MANGA -> libraryPreferences.mangaLandscapeColumns()
            LibraryType.ANIME -> libraryPreferences.animeLandscapeColumns()
            LibraryType.NOVEL -> libraryPreferences.novelLandscapeColumns()
        }
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) landscape else portrait
    }

    val columns by columnPreference.collectAsStateWithLifecycle()
    if (displayMode == LibraryDisplayMode.List) {
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(AYMR.strings.pref_library_rows),
            valueText = if (columns > 0) columns.toString()
                        else stringResource(MR.strings.label_auto),
            onChange = columnPreference::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    } else {
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(MR.strings.pref_library_columns),
            valueText = if (columns > 0) columns.toString()
                        else stringResource(MR.strings.label_auto),
            onChange = columnPreference::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    HeadingItem(AYMR.strings.reading_order_list)
    IconItem(
        label = stringResource(AYMR.strings.reading_order_list),
        icon = Icons.AutoMirrored.Outlined.List,
        onClick = onClickReadingOrders,
    )
}
