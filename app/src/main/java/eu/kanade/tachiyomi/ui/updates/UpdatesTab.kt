package eu.kanade.tachiyomi.ui.updates

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.download.anime.animeDownloadTab
import eu.kanade.tachiyomi.ui.download.manga.mangaDownloadTab
import eu.kanade.tachiyomi.ui.download.novel.novelDownloadTab
import eu.kanade.tachiyomi.ui.history.anime.animeHistoryTab
import eu.kanade.tachiyomi.ui.history.manga.mangaHistoryTab
import eu.kanade.tachiyomi.ui.history.novel.novelHistoryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.stats.anime.animeStatsTab
import eu.kanade.tachiyomi.ui.stats.manga.mangaStatsTab
import eu.kanade.tachiyomi.ui.updates.anime.animeUpdatesTab
import eu.kanade.tachiyomi.ui.updates.manga.mangaUpdatesTab
import eu.kanade.tachiyomi.ui.updates.novel.novelUpdatesTab
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object UpdatesTab : Tab {

    private val uiPreferences: UiPreferences = Injekt.get()

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                index = 1u,
                title = stringResource(MR.strings.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        // Could scroll to top of updates list
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val contentMode by uiPreferences.contentMode().collectAsState()
        val nestedScrollConnection = remember { object : NestedScrollConnection {} }

        // Build 4 sub-tabs: Updates, History, Downloads, Stats
        val updatesTab = when (contentMode) {
            ContentMode.ANIME -> animeUpdatesTab(context, fromMore = false)
            ContentMode.MANGA -> mangaUpdatesTab(context, fromMore = false)
            ContentMode.NOVEL -> novelUpdatesTab(context, fromMore = false)
        }

        val historyTab = when (contentMode) {
            ContentMode.ANIME -> animeHistoryTab(context, fromMore = false)
            ContentMode.MANGA -> mangaHistoryTab(context, fromMore = false)
            ContentMode.NOVEL -> novelHistoryTab(context, fromMore = false)
        }

        val downloadsTab = when (contentMode) {
            ContentMode.ANIME -> animeDownloadTab(nestedScrollConnection)
            ContentMode.MANGA -> mangaDownloadTab(nestedScrollConnection)
            ContentMode.NOVEL -> novelDownloadTab(nestedScrollConnection)
        }

        val statsTab = when (contentMode) {
            ContentMode.ANIME -> animeStatsTab()
            ContentMode.MANGA -> mangaStatsTab()
            ContentMode.NOVEL -> mangaStatsTab() // No novel stats yet, reuse manga
        }

        TabbedScreen(
            titleRes = MR.strings.label_recent_updates,
            tabs = persistentListOf(updatesTab, historyTab, downloadsTab, statsTab),
            onClickSettings = { navigator.push(SettingsScreen()) },
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
