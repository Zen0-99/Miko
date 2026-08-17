package eu.kanade.tachiyomi.ui.updates

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
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
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.history.anime.animeHistoryTab
import eu.kanade.tachiyomi.ui.history.manga.mangaHistoryTab
import eu.kanade.tachiyomi.ui.history.novel.novelHistoryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.stats.anime.animeStatsTab
import eu.kanade.tachiyomi.ui.stats.manga.mangaStatsTab
import eu.kanade.tachiyomi.ui.stats.novel.novelStatsTab
import eu.kanade.tachiyomi.ui.updates.anime.animeUpdatesTab
import eu.kanade.tachiyomi.ui.updates.fetching.fetchingTab
import eu.kanade.tachiyomi.ui.updates.manga.mangaUpdatesTab
import eu.kanade.tachiyomi.ui.updates.novel.novelUpdatesTab
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgress
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import mihon.feature.upcoming.anime.UpcomingAnimeScreenContent
import mihon.feature.upcoming.anime.UpcomingAnimeScreenModel
import mihon.feature.upcoming.manga.UpcomingMangaScreenContent
import mihon.feature.upcoming.manga.UpcomingMangaScreenModel
import mihon.feature.upcoming.novel.UpcomingNovelScreenContent
import mihon.feature.upcoming.novel.UpcomingNovelScreenModel
import tachiyomi.i18n.MR
import tachiyomi.domain.library.interactor.GetFailedFetches
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object UpdatesTab : Tab {

    private val uiPreferences: UiPreferences = Injekt.get()

    /** Set by the fetching overlay's "view failures" button to request
     *  navigation to the Fetching sub-tab on next composition. */
    @Volatile
    var navigateToFetchingRequested: Boolean = false

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

        // Fetching tab is conditional: only show when there are failed fetches
        // OR a library update run is currently in progress.
        val getFailedFetches = remember { Injekt.get<GetFailedFetches>() }
        val failedFetches by getFailedFetches.subscribe().collectAsState(initial = emptyList())
        val progressState by LibraryUpdateProgressBus.state.collectAsState()
        val showFetchingTab = failedFetches.isNotEmpty() ||
            progressState is LibraryUpdateProgress.Running ||
            progressState is LibraryUpdateProgress.Completed

        // Build 5 sub-tabs: Updates, History, Downloads, Calendar, Stats
        // Override titleRes so tabs show their function name, not the content mode
        val updatesTab = when (contentMode) {
            ContentMode.ANIME -> animeUpdatesTab(context, fromMore = false)
            ContentMode.MANGA -> mangaUpdatesTab(context, fromMore = false)
            ContentMode.NOVEL -> novelUpdatesTab(context, fromMore = false)
        }.copy(titleRes = MR.strings.label_recent_updates)

        val historyTab = when (contentMode) {
            ContentMode.ANIME -> animeHistoryTab(context, fromMore = false)
            ContentMode.MANGA -> mangaHistoryTab(context, fromMore = false)
            ContentMode.NOVEL -> novelHistoryTab(context, fromMore = false)
        }.copy(titleRes = MR.strings.history)

        val downloadsTab = when (contentMode) {
            ContentMode.ANIME -> animeDownloadTab(nestedScrollConnection)
            ContentMode.MANGA -> mangaDownloadTab(nestedScrollConnection)
            ContentMode.NOVEL -> novelDownloadTab(nestedScrollConnection)
        }.copy(titleRes = MR.strings.label_downloads, navigateUp = null)

        val statsTab = when (contentMode) {
            ContentMode.ANIME -> animeStatsTab()
            ContentMode.MANGA -> mangaStatsTab()
            ContentMode.NOVEL -> novelStatsTab()
        }.copy(titleRes = MR.strings.label_stats_short, navigateUp = null)

        // Calendar tab — embeds the Upcoming screen content directly
        val calendarTab = when (contentMode) {
            ContentMode.ANIME -> {
                val screenModel = rememberScreenModel { UpcomingAnimeScreenModel() }
                val state by screenModel.state.collectAsState()
                TabContent(
                    titleRes = MR.strings.label_calendar,
                    content = { contentPadding, _ ->
                        UpcomingAnimeScreenContent(
                            state = state,
                            setSelectedYearMonth = screenModel::setSelectedYearMonth,
                            onClickUpcoming = { navigator.push(AnimeScreen(it.id)) },
                            modifier = Modifier.padding(contentPadding),
                        )
                    },
                )
            }
            ContentMode.MANGA -> {
                val screenModel = rememberScreenModel { UpcomingMangaScreenModel() }
                val state by screenModel.state.collectAsState()
                TabContent(
                    titleRes = MR.strings.label_calendar,
                    content = { contentPadding, _ ->
                        UpcomingMangaScreenContent(
                            state = state,
                            setSelectedYearMonth = screenModel::setSelectedYearMonth,
                            onClickUpcoming = { navigator.push(MangaScreen(it.id)) },
                            modifier = Modifier.padding(contentPadding),
                        )
                    },
                )
            }
            ContentMode.NOVEL -> {
                val screenModel = rememberScreenModel { UpcomingNovelScreenModel() }
                val state by screenModel.state.collectAsState()
                TabContent(
                    titleRes = MR.strings.label_calendar,
                    content = { contentPadding, _ ->
                        UpcomingNovelScreenContent(
                            state = state,
                            setSelectedYearMonth = screenModel::setSelectedYearMonth,
                            onClickUpcoming = { navigator.push(NovelScreen(it.id)) },
                            modifier = Modifier.padding(contentPadding),
                        )
                    },
                )
            }
        }

        val fetchingTabContent = if (showFetchingTab) fetchingTab(context, contentMode) else null

        val tabs = buildList {
            add(updatesTab)
            add(calendarTab)
            add(downloadsTab)
            add(statsTab)
            add(historyTab)
            if (fetchingTabContent != null) add(fetchingTabContent)
        }.toPersistentList()

        // Pager state — start on the Fetching tab if requested by the overlay
        val pagerState = rememberPagerState(
            initialPage = if (navigateToFetchingRequested && fetchingTabContent != null) {
                tabs.lastIndex // Fetching tab is always last
            } else {
                0
            },
        ) { tabs.size }

        // Consume the navigation request after the pager state is created
        LaunchedEffect(Unit) {
            if (navigateToFetchingRequested) {
                navigateToFetchingRequested = false
            }
        }

        TabbedScreen(
            titleRes = MR.strings.label_recent_updates,
            tabs = tabs,
            state = pagerState,
            scrollable = true,
            onClickSettings = { navigator.push(SettingsScreen()) },
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
