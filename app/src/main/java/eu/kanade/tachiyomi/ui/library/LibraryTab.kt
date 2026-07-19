package eu.kanade.tachiyomi.ui.library

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.library.novel.NovelLibraryTab
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Unified, mode-aware Library tab. Replaces the three separate Anime/Manga/Novel library
 * tabs in the bottom nav. Reads the global [UiPreferences.contentMode] and delegates
 * [Content] to the appropriate per-type library tab.
 *
 * The mode is switched via the [eu.kanade.presentation.home.ModeCarouselTitle] in the
 * HomeScreen top bar, not via separate nav items.
 */
data object LibraryTab : Tab {

    private val uiPreferences: UiPreferences = Injekt.get()

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                index = 0u,
                title = stringResource(MR.strings.label_library),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        when (uiPreferences.contentMode().get()) {
            ContentMode.ANIME -> AnimeLibraryTab.onReselect(navigator)
            ContentMode.MANGA -> MangaLibraryTab.onReselect(navigator)
            ContentMode.NOVEL -> NovelLibraryTab.onReselect(navigator)
        }
    }

    @Composable
    override fun Content() {
        val contentMode by uiPreferences.contentMode().collectAsState()
        when (contentMode) {
            ContentMode.ANIME -> AnimeLibraryTab.Content()
            ContentMode.MANGA -> MangaLibraryTab.Content()
            ContentMode.NOVEL -> NovelLibraryTab.Content()
        }
    }

    /**
     * Forward a search query to the per-type library tab that matches the current mode.
     * Called by [eu.kanade.tachiyomi.ui.home.HomeScreen.search].
     */
    suspend fun search(query: String) {
        when (uiPreferences.contentMode().get()) {
            ContentMode.ANIME -> AnimeLibraryTab.search(query)
            ContentMode.MANGA -> MangaLibraryTab.search(query)
            ContentMode.NOVEL -> NovelLibraryTab.search(query)
        }
    }
}
