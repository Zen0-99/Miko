package miko.ui

import kotlinx.serialization.json.Json
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.CreateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.DeleteMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.ReplaceMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.CreateNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.DeleteNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.ReplaceNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.UpdateNovelExtensionRepo
import mihon.domain.extensionrepo.service.ExtensionRepoService
import mihon.domain.upcoming.manga.interactor.GetUpcomingManga
import mihon.domain.upcoming.novel.interactor.GetUpcomingNovel
import mihon.domain.upcoming.shows.interactor.GetUpcomingAnime
import miko.ui.carrier.BrowseCarrier
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.NovelCarrierBinding
import miko.ui.catalog.CatalogRowResolver
import miko.ui.catalog.CatalogRowStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.extension.novel.NovelPluginInstallerFacade
import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.entries.shows.interactor.GetLibraryAnime
import tachiyomi.domain.extension.novel.api.JsPluginApiFacade
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.novel.interactor.GetNovelHistory
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.history.shows.interactor.GetAnimeHistory
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates
import tachiyomi.domain.updates.novel.interactor.GetNovelUpdates
import tachiyomi.domain.updates.shows.interactor.GetAnimeUpdates

/**
 * The dependency surface shared screens consume. Each platform's composition
 * root (ios-app's AppGraph today; Android's AppModule at convergence) builds
 * one of these — screens/models never see platform wiring.
 */
class MikoUiGraph(
    val novelSourceManager: NovelSourceManager,
    val mangaSourceManager: MangaSourceManager,
    val animeSourceManager: AnimeSourceManager,
    val preferenceStore: PreferenceStore,
    val mangaBinding: CarrierBinding,
    val animeBinding: CarrierBinding,
    val getLibraryNovels: GetLibraryNovels,
    val getLibraryManga: GetLibraryManga,
    val getLibraryAnime: GetLibraryAnime,
    val getNovel: GetNovel,
    val getNovelChaptersByNovelId: GetNovelChaptersByNovelId,
    val networkToLocalNovel: NetworkToLocalNovel,
    val novelRepository: NovelRepository,
    val novelChapterRepository: NovelChapterRepository,
    val novelHistoryRepository: NovelHistoryRepository,
    val novelCollectionRepository: NovelCollectionRepository,
    val getNovelUpdates: GetNovelUpdates,
    val getMangaUpdates: GetMangaUpdates,
    val getAnimeUpdates: GetAnimeUpdates,
    val getUpcomingNovel: GetUpcomingNovel,
    val getUpcomingManga: GetUpcomingManga,
    val getUpcomingAnime: GetUpcomingAnime,
    val getNovelHistory: GetNovelHistory,
    val getMangaHistory: GetMangaHistory,
    val getAnimeHistory: GetAnimeHistory,
    val extensionRepoService: ExtensionRepoService,
    val jsPluginApi: JsPluginApiFacade,
    val novelPluginRepository: NovelPluginRepository,
    val novelPluginInstaller: NovelPluginInstallerFacade,
    val getAnimeExtensionRepo: GetAnimeExtensionRepo,
    val getMangaExtensionRepo: GetMangaExtensionRepo,
    val getNovelExtensionRepo: GetNovelExtensionRepo,
    val createAnimeExtensionRepo: CreateAnimeExtensionRepo,
    val createMangaExtensionRepo: CreateMangaExtensionRepo,
    val createNovelExtensionRepo: CreateNovelExtensionRepo,
    val deleteAnimeExtensionRepo: DeleteAnimeExtensionRepo,
    val deleteMangaExtensionRepo: DeleteMangaExtensionRepo,
    val deleteNovelExtensionRepo: DeleteNovelExtensionRepo,
    val replaceAnimeExtensionRepo: ReplaceAnimeExtensionRepo,
    val replaceMangaExtensionRepo: ReplaceMangaExtensionRepo,
    val replaceNovelExtensionRepo: ReplaceNovelExtensionRepo,
    val updateAnimeExtensionRepo: UpdateAnimeExtensionRepo,
    val updateMangaExtensionRepo: UpdateMangaExtensionRepo,
    val updateNovelExtensionRepo: UpdateNovelExtensionRepo,
) {

    /** Home catalog rows — persisted spec store + resolver, built in-module
     *  like [novelBinding] (pref store + interactors are already here).
     *  `ignoreUnknownKeys` tolerates spec schema drift across versions. */
    val catalogRowStore: CatalogRowStore by lazy {
        CatalogRowStore(preferenceStore, Json { ignoreUnknownKeys = true })
    }
    val catalogRowResolver: CatalogRowResolver by lazy { CatalogRowResolver(this) }

    /** Books carrier — built in-module; manga/anime inject theirs (they
     * wrap platform-actual source contracts commonMain can't see). */
    val novelBinding: CarrierBinding = NovelCarrierBinding(
        sourceManager = novelSourceManager,
        getNovel = getNovel,
        networkToLocalNovel = networkToLocalNovel,
        novelRepository = novelRepository,
        chapterRepository = novelChapterRepository,
        historyRepository = novelHistoryRepository,
        collectionRepository = novelCollectionRepository,
        preferenceStore = preferenceStore,
    )

    fun binding(carrier: BrowseCarrier): CarrierBinding = when (carrier) {
        BrowseCarrier.SHOWS -> animeBinding
        BrowseCarrier.MANGA -> mangaBinding
        BrowseCarrier.BOOKS -> novelBinding
    }
}
