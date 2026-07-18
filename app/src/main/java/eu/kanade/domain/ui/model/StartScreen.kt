package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.home.hub.HomeHubTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

/**
 * The screen to show on app launch.
 *
 * ANIME and MANGA both open [LibraryTab] but imply a different default [ContentMode].
 * The content mode is set by [eu.kanade.tachiyomi.ui.home.HomeScreen] on startup based
 * on the chosen start screen.
 */
enum class StartScreen(val titleRes: StringResource, val tab: Tab, val contentMode: ContentMode? = null) {
    HOME(AYMR.strings.pref_start_screen_home, HomeHubTab),
    ANIME(AYMR.strings.label_anime, LibraryTab, ContentMode.ANIME),
    MANGA(AYMR.strings.manga, LibraryTab, ContentMode.MANGA),
    UPDATES(MR.strings.label_recent_updates, UpdatesTab),
    HISTORY(MR.strings.label_recent_manga, HistoriesTab),
    BROWSE(MR.strings.browse, BrowseTab),
}
