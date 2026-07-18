package eu.kanade.domain.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.home.hub.HomeHubTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import tachiyomi.i18n.aniyomi.AYMR

/**
 * Determines which tab is moved to the More screen, keeping the bottom nav at 5 items.
 *
 * [LibraryTab] and [HomeHubTab] are always visible. One of Updates/History/Browse is moved to More.
 * The legacy MOVE_MANGA_TO_MORE and MOVE_NOVEL_TO_MORE values were removed when the
 * three per-type library tabs were collapsed into a single mode-aware [LibraryTab].
 * Users with those stored values will get [MOVE_HISTORY_TO_MORE] (the default) via
 * the [tachiyomi.core.common.preference.getEnum] fallback.
 */
enum class NavStyle(
    val titleRes: StringResource,
    val moreTab: Tab,
) {
    MOVE_UPDATES_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_updates, moreTab = UpdatesTab),
    MOVE_HISTORY_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_history, moreTab = HistoriesTab),
    MOVE_BROWSE_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_browse, moreTab = BrowseTab),
    ;

    val moreIcon: ImageVector
        @Composable
        get() = when (this) {
            MOVE_UPDATES_TO_MORE -> ImageVector.vectorResource(id = R.drawable.ic_updates_outline_24dp)
            MOVE_HISTORY_TO_MORE -> Icons.Outlined.History
            MOVE_BROWSE_TO_MORE -> Icons.Outlined.Explore
        }

    val tabs: List<Tab>
        get() {
            return mutableListOf(
                HomeHubTab,
                LibraryTab,
                UpdatesTab,
                HistoriesTab,
                BrowseTab,
                MoreTab,
            ).apply { remove(this@NavStyle.moreTab) }
        }
}
