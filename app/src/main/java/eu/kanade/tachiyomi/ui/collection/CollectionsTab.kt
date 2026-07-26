package eu.kanade.tachiyomi.ui.collection

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.collection.anime.AnimeCollectionEvent
import eu.kanade.tachiyomi.ui.collection.anime.AnimeCollectionScreenModel
import eu.kanade.tachiyomi.ui.collection.anime.animeCollectionTab
import eu.kanade.tachiyomi.ui.collection.manga.MangaCollectionEvent
import eu.kanade.tachiyomi.ui.collection.manga.MangaCollectionScreenModel
import eu.kanade.tachiyomi.ui.collection.manga.mangaCollectionTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

data object CollectionsTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                index = 7u,
                title = stringResource(AYMR.strings.general_collections),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    private val switchToMangaCollectionTabChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showMangaCollection() {
        switchToMangaCollectionTabChannel.trySend(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current

        val animeCollectionScreenModel = rememberScreenModel { AnimeCollectionScreenModel() }
        val mangaCollectionScreenModel = rememberScreenModel { MangaCollectionScreenModel() }

        val tabs = persistentListOf(
            animeCollectionTab(),
            mangaCollectionTab(),
        )

        val state = rememberPagerState { tabs.size }

        TabbedScreen(
            titleRes = AYMR.strings.general_collections,
            tabs = tabs,
            state = state,
        )
        LaunchedEffect(Unit) {
            switchToMangaCollectionTabChannel.receiveAsFlow()
                .collectLatest { state.scrollToPage(1) }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }

        LaunchedEffect(Unit) {
            mangaCollectionScreenModel.events.collectLatest { event ->
                if (event is MangaCollectionEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
            animeCollectionScreenModel.events.collectLatest { event ->
                if (event is AnimeCollectionEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
