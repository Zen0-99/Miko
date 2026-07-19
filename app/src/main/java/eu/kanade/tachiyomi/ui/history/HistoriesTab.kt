package eu.kanade.tachiyomi.ui.history

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.history.anime.AnimeHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.anime.animeHistoryTab
import eu.kanade.tachiyomi.ui.history.anime.resumeLastEpisodeSeenEvent
import eu.kanade.tachiyomi.ui.history.manga.MangaHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.manga.mangaHistoryTab
import eu.kanade.tachiyomi.ui.history.novel.NovelHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.novel.novelHistoryTab
import eu.kanade.tachiyomi.ui.history.novel.resumeLastNovelChapterReadEvent
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object HistoriesTab : Tab {

    private val uiPreferences: UiPreferences = Injekt.get()

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(MR.strings.history),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        resumeLastEpisodeSeenEvent.send(Unit)
        resumeLastNovelChapterReadEvent.send(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val contentMode by uiPreferences.contentMode().collectAsState()

        // Hoist only the current mode's screen model for search-bar sharing.
        val (tab, searchQuery, onChangeSearchQuery) = when (contentMode) {
            ContentMode.ANIME -> {
                val screenModel = rememberScreenModel { AnimeHistoryScreenModel() }
                val query by screenModel.query.collectAsState()
                Triple(animeHistoryTab(context, fromMore = false), query, screenModel::search)
            }
            ContentMode.MANGA -> {
                val screenModel = rememberScreenModel { MangaHistoryScreenModel() }
                val query by screenModel.query.collectAsState()
                Triple(mangaHistoryTab(context, fromMore = false), query, screenModel::search)
            }
            ContentMode.NOVEL -> {
                val screenModel = rememberScreenModel { NovelHistoryScreenModel() }
                val query by screenModel.query.collectAsState()
                Triple(novelHistoryTab(context, fromMore = false), query, screenModel::search)
            }
        }

        TabbedScreen(
            titleRes = MR.strings.label_recent_manga,
            tabs = persistentListOf(tab),
            // Single-tab mode: TabbedScreen picks the first non-null query.
            animeSearchQuery = searchQuery,
            onChangeAnimeSearchQuery = onChangeSearchQuery,
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
