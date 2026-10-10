package miko.ui

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
import tachiyomi.data.extension.novel.NovelPluginInstallerFacade
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.extension.novel.api.JsPluginApiFacade
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager

/**
 * The dependency surface shared screens consume. Each platform's composition
 * root (ios-app's AppGraph today; Android's AppModule at convergence) builds
 * one of these — screens/models never see platform wiring.
 */
class MikoUiGraph(
    val novelSourceManager: NovelSourceManager,
    val getLibraryNovels: GetLibraryNovels,
    val getNovel: GetNovel,
    val getNovelChaptersByNovelId: GetNovelChaptersByNovelId,
    val networkToLocalNovel: NetworkToLocalNovel,
    val novelRepository: NovelRepository,
    val novelChapterRepository: NovelChapterRepository,
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
)
