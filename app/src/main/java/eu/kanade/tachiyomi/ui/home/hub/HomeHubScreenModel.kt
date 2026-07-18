package eu.kanade.tachiyomi.ui.home.hub

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.ui.UiPreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.novel.interactor.GetNovelHistory
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.novel.LibraryNovel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Minimal Home Hub ScreenModel — combines history and recently added
 * library items across all three media types.
 */
class HomeHubScreenModel(
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val getAnimeHistory: GetAnimeHistory = Injekt.get(),
    private val getMangaHistory: GetMangaHistory = Injekt.get(),
    private val getNovelHistory: GetNovelHistory = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getLibraryNovels: GetLibraryNovels = Injekt.get(),
) : StateScreenModel<HomeHubState>(HomeHubState()) {

    init {
        screenModelScope.launchIO {
            // combine() supports max 5 flows — nest history and library combines
            val historyFlow = combine(
                getAnimeHistory.subscribe(""),
                getMangaHistory.subscribe(""),
                getNovelHistory.subscribe(""),
            ) { animeHistory, mangaHistory, novelHistory ->
                Triple(animeHistory, mangaHistory, novelHistory)
            }
            val libraryFlow = combine(
                getLibraryAnime.subscribe(),
                getLibraryManga.subscribe(),
                getLibraryNovels.subscribe(),
            ) { animeLib, mangaLib, novelLib ->
                Triple(animeLib, mangaLib, novelLib)
            }
            combine(historyFlow, libraryFlow) { (animeHistory, mangaHistory, novelHistory), (animeLib, mangaLib, novelLib) ->
                HomeHubData(animeHistory, mangaHistory, novelHistory, animeLib, mangaLib, novelLib)
            }.collectLatest { data ->
                mutableState.update {
                    HomeHubState(
                        isLoading = false,
                        recentAnime = data.animeHistory.take(10),
                        recentManga = data.mangaHistory.take(10),
                        recentNovels = data.novelHistory.take(10),
                        recentlyAddedAnime = data.animeLib
                            .sortedByDescending { it.anime.dateAdded }
                            .take(10)
                            .map { it.toRecentlyAdded(HomeHubMediaType.ANIME) },
                        recentlyAddedManga = data.mangaLib
                            .sortedByDescending { it.manga.dateAdded }
                            .take(10)
                            .map { it.toRecentlyAdded(HomeHubMediaType.MANGA) },
                        recentlyAddedNovels = data.novelLib
                            .sortedByDescending { it.novel.dateAdded }
                            .take(10)
                            .map { it.toRecentlyAdded(HomeHubMediaType.NOVEL) },
                    )
                }
            }
        }
    }

    private data class HomeHubData(
        val animeHistory: List<tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations>,
        val mangaHistory: List<tachiyomi.domain.history.manga.model.MangaHistoryWithRelations>,
        val novelHistory: List<tachiyomi.domain.history.novel.model.NovelHistoryWithRelations>,
        val animeLib: List<LibraryAnime>,
        val mangaLib: List<LibraryManga>,
        val novelLib: List<LibraryNovel>,
    )
}

private fun LibraryAnime.toRecentlyAdded(type: HomeHubMediaType) = RecentlyAddedItem(
    id = anime.id,
    title = anime.title,
    coverData = anime,
    sourceId = anime.source,
    url = anime.url,
    mediaType = type,
)

private fun LibraryManga.toRecentlyAdded(type: HomeHubMediaType) = RecentlyAddedItem(
    id = manga.id,
    title = manga.title,
    coverData = manga,
    sourceId = manga.source,
    url = manga.url,
    mediaType = type,
)

private fun LibraryNovel.toRecentlyAdded(type: HomeHubMediaType) = RecentlyAddedItem(
    id = novel.id,
    title = novel.title,
    coverData = novel,
    sourceId = novel.source,
    url = novel.url,
    mediaType = type,
)
