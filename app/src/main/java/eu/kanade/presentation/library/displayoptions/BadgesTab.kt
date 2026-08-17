package eu.kanade.presentation.library.displayoptions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BadgesTab(
    libraryPreferences: LibraryPreferences,
    modifier: Modifier = Modifier,
) {
    // Section 1: Cover badges — small indicators on the cover art
    HeadingItem(MR.strings.overlay_cover_badges)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_download_badge),
        pref = libraryPreferences.downloadBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_unread_badge),
        pref = libraryPreferences.unreadBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_local_badge),
        pref = libraryPreferences.localBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_language_badge),
        pref = libraryPreferences.languageBadge(),
    )

    // Section 2: Actions — interactive overlays on the cover
    HeadingItem(MR.strings.overlay_actions)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_reading_number),
        pref = libraryPreferences.showReadingNumber(),
    )
    CheckboxItem(
        label = stringResource(AYMR.strings.action_display_show_continue_reading_button),
        pref = libraryPreferences.showContinueViewingButton(),
    )
}
