package miko.ios

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.SynchronousFlag
import data.History
import data.Mangas
import dataanime.Animehistory
import dataanime.Animes
import datanovel.Novelhistory
import datanovel.Novels
import eu.kanade.tachiyomi.core.network.platformHttpClient
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import mihon.data.extensionrepo.KtorRepoHttpFetcher
import mihon.data.repository.anime.AnimeExtensionRepoRepositoryImpl
import mihon.data.repository.manga.MangaExtensionRepoRepositoryImpl
import mihon.data.repository.novel.NovelExtensionRepoRepositoryImpl
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.manga.interactor.CreateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.DeleteMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.ReplaceMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import mihon.domain.extensionrepo.novel.interactor.CreateNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.DeleteNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.ReplaceNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.UpdateNovelExtensionRepo
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository
import mihon.domain.extensionrepo.service.ExtensionRepoService
import mihon.domain.extensionrepo.service.IosExtensionRepoService
import mihon.domain.extensionrepo.service.RepoHttpFetcher
import miko.jssource.IosJsRuntimeFactory
import miko.ui.MikoUiGraph
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import tachiyomi.core.common.preference.IosPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.NovelUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.entries.novel.NovelRepositoryImpl
import tachiyomi.data.extension.novel.DefaultNovelPluginStorage
import tachiyomi.data.extension.novel.IosNovelPluginKeyValueStore
import tachiyomi.data.extension.novel.KtorJsIndexFetcher
import tachiyomi.data.extension.novel.KtorNovelPluginDownloader
import tachiyomi.data.extension.novel.NovelPluginInstaller
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore
import tachiyomi.data.extension.novel.NovelPluginRepositoryImpl
import tachiyomi.data.extension.novel.NovelPluginStorage
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.anime.IosAnimeDatabaseHandler
import tachiyomi.data.handlers.manga.IosMangaDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.IosNovelDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.data.items.chapter.NovelChapterRepositoryImpl
import tachiyomi.data.source.novel.NovelStubSourceRepositoryImpl
import tachiyomi.db.achievement.AchievementsDatabase
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.extension.novel.api.CombinedJsPluginRepoProvider
import tachiyomi.domain.extension.novel.api.JsPluginApi
import tachiyomi.domain.extension.novel.api.JsPluginIndexFetcher
import tachiyomi.domain.extension.novel.api.JsPluginIndexParser
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService
import tachiyomi.domain.source.novel.repository.NovelStubSourceRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.novel.data.NovelDatabase

/**
 * Manual composition root for the iOS shell (Phase 27 decision D2 — Injekt is
 * JVM-only; the iOS graph is small enough to compose by hand).
 *
 * Databases live under `<Application Support>/databases/` in the app sandbox,
 * created eagerly on first access. Drivers get the same pragmas as Android's
 * `onOpen` callback: foreign keys, WAL, NORMAL synchronous.
 */
class AppGraph {

    val httpClient: HttpClient = platformHttpClient()

    val preferenceStore: PreferenceStore = IosPreferenceStore()

    val novelPluginStorage: NovelPluginStorage =
        DefaultNovelPluginStorage("$filesDir/plugins")

    val novelPluginKeyValueStore: NovelPluginKeyValueStore =
        IosNovelPluginKeyValueStore()

    private val json = Json { ignoreUnknownKeys = true }

    val repoHttpFetcher: RepoHttpFetcher = KtorRepoHttpFetcher(httpClient)

    val jsPluginIndexParser = JsPluginIndexParser(json)

    val jsPluginIndexFetcher: JsPluginIndexFetcher = KtorJsIndexFetcher(repoHttpFetcher)

    val extensionRepoService: ExtensionRepoService = IosExtensionRepoService(
        fetcher = repoHttpFetcher,
        indexFetcher = jsPluginIndexFetcher,
        parser = jsPluginIndexParser,
        json = json,
    )

    val releaseService: ReleaseService = object : ReleaseService {
        override suspend fun latest(arguments: GetApplicationRelease.Arguments): Release? = null
    }

    private val mangaDriver = newDriver(Database.Schema, "tachiyomi.db")
    private val animeDriver = newDriver(AnimeDatabase.Schema, "tachiyomi.animedb")
    private val novelDriver = newDriver(NovelDatabase.Schema, "tachiyomi.noveldb")
    private val achievementsDriver =
        newDriver(AchievementsDatabase.Schema, "tachiyomi.achievementsdb")

    val mangaDatabase: Database = Database(
        driver = mangaDriver,
        historyAdapter = History.Adapter(
            last_readAdapter = DateColumnAdapter,
        ),
        mangasAdapter = Mangas.Adapter(
            genreAdapter = StringListColumnAdapter,
            update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
        ),
    )

    val animeDatabase: AnimeDatabase = AnimeDatabase(
        driver = animeDriver,
        animehistoryAdapter = Animehistory.Adapter(
            last_seenAdapter = DateColumnAdapter,
        ),
        animesAdapter = Animes.Adapter(
            genreAdapter = StringListColumnAdapter,
            update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
            fetch_typeAdapter = FetchTypeColumnAdapter,
        ),
    )

    val novelDatabase: NovelDatabase = NovelDatabase(
        driver = novelDriver,
        novelhistoryAdapter = Novelhistory.Adapter(
            last_readAdapter = DateColumnAdapter,
        ),
        novelsAdapter = Novels.Adapter(
            genreAdapter = StringListColumnAdapter,
            update_strategyAdapter = NovelUpdateStrategyColumnAdapter,
        ),
    )

    val achievementsDatabase: AchievementsDatabase = AchievementsDatabase(
        driver = achievementsDriver,
    )

    val mangaHandler: MangaDatabaseHandler = IosMangaDatabaseHandler(mangaDatabase)
    val animeHandler: AnimeDatabaseHandler = IosAnimeDatabaseHandler(animeDatabase)
    val novelHandler: NovelDatabaseHandler = IosNovelDatabaseHandler(novelDatabase)

    val novelPluginRepository: NovelPluginRepository =
        NovelPluginRepositoryImpl(novelHandler)

    val novelStubSourceRepository: NovelStubSourceRepository =
        NovelStubSourceRepositoryImpl(novelHandler)

    val novelExtensionRepoRepository: NovelExtensionRepoRepository =
        NovelExtensionRepoRepositoryImpl(novelHandler)

    val mangaExtensionRepoRepository: MangaExtensionRepoRepository =
        MangaExtensionRepoRepositoryImpl(mangaHandler)

    val animeExtensionRepoRepository: AnimeExtensionRepoRepository =
        AnimeExtensionRepoRepositoryImpl(animeHandler)

    val novelPluginInstaller = NovelPluginInstaller(
        downloader = KtorNovelPluginDownloader(httpClient),
        repository = novelPluginRepository,
        storage = novelPluginStorage,
    )

    val jsPluginApi = JsPluginApi(
        repoProvider = CombinedJsPluginRepoProvider(
            novelRepoRepository = novelExtensionRepoRepository,
            mangaRepoRepository = mangaExtensionRepoRepository,
            animeRepoRepository = animeExtensionRepoRepository,
        ),
        fetcher = jsPluginIndexFetcher,
        parser = jsPluginIndexParser,
        // APK rows aren't installable on iOS — exclude them from the list.
        includeKotlinExtensions = false,
        // …but the APK index still feeds the repo's Not-Installed rows.
        extensionRepoService = extensionRepoService,
    )

    val createAnimeExtensionRepo = CreateAnimeExtensionRepo(animeExtensionRepoRepository, extensionRepoService)
    val createMangaExtensionRepo = CreateMangaExtensionRepo(mangaExtensionRepoRepository, extensionRepoService)
    val createNovelExtensionRepo = CreateNovelExtensionRepo(novelExtensionRepoRepository, extensionRepoService)

    val deleteAnimeExtensionRepo = DeleteAnimeExtensionRepo(animeExtensionRepoRepository)
    val deleteMangaExtensionRepo = DeleteMangaExtensionRepo(mangaExtensionRepoRepository)
    val deleteNovelExtensionRepo = DeleteNovelExtensionRepo(novelExtensionRepoRepository)

    val getAnimeExtensionRepo = GetAnimeExtensionRepo(animeExtensionRepoRepository)
    val getMangaExtensionRepo = GetMangaExtensionRepo(mangaExtensionRepoRepository)
    val getNovelExtensionRepo = GetNovelExtensionRepo(novelExtensionRepoRepository)

    val replaceAnimeExtensionRepo = ReplaceAnimeExtensionRepo(animeExtensionRepoRepository)
    val replaceMangaExtensionRepo = ReplaceMangaExtensionRepo(mangaExtensionRepoRepository)
    val replaceNovelExtensionRepo = ReplaceNovelExtensionRepo(novelExtensionRepoRepository)

    val updateAnimeExtensionRepo = UpdateAnimeExtensionRepo(animeExtensionRepoRepository, extensionRepoService)
    val updateMangaExtensionRepo = UpdateMangaExtensionRepo(mangaExtensionRepoRepository, extensionRepoService)
    val updateNovelExtensionRepo = UpdateNovelExtensionRepo(novelExtensionRepoRepository, extensionRepoService)

    val jsRuntimeFactory = IosJsRuntimeFactory()

    val novelSourceManager: NovelSourceManager = IosNovelSourceManager(
        pluginRepository = novelPluginRepository,
        pluginStorage = novelPluginStorage,
        sourceRepository = novelStubSourceRepository,
        runtimeFactory = jsRuntimeFactory,
        httpClient = httpClient,
        json = json,
    )

    val novelRepository: NovelRepository = NovelRepositoryImpl(novelHandler)

    val novelChapterRepository: NovelChapterRepository =
        NovelChapterRepositoryImpl(novelHandler)

    val getNovel = GetNovel(novelRepository)

    val getLibraryNovels = GetLibraryNovels(novelRepository)

    val getNovelChaptersByNovelId = GetNovelChaptersByNovelId(novelChapterRepository)

    val networkToLocalNovel = NetworkToLocalNovel(novelRepository)

    val uiGraph = MikoUiGraph(
        novelSourceManager = novelSourceManager,
        getLibraryNovels = getLibraryNovels,
        getNovel = getNovel,
        getNovelChaptersByNovelId = getNovelChaptersByNovelId,
        networkToLocalNovel = networkToLocalNovel,
        novelRepository = novelRepository,
        novelChapterRepository = novelChapterRepository,
        extensionRepoService = extensionRepoService,
        jsPluginApi = jsPluginApi,
        novelPluginRepository = novelPluginRepository,
        novelPluginInstaller = novelPluginInstaller,
        getAnimeExtensionRepo = getAnimeExtensionRepo,
        getMangaExtensionRepo = getMangaExtensionRepo,
        getNovelExtensionRepo = getNovelExtensionRepo,
        createAnimeExtensionRepo = createAnimeExtensionRepo,
        createMangaExtensionRepo = createMangaExtensionRepo,
        createNovelExtensionRepo = createNovelExtensionRepo,
        deleteAnimeExtensionRepo = deleteAnimeExtensionRepo,
        deleteMangaExtensionRepo = deleteMangaExtensionRepo,
        deleteNovelExtensionRepo = deleteNovelExtensionRepo,
        replaceAnimeExtensionRepo = replaceAnimeExtensionRepo,
        replaceMangaExtensionRepo = replaceMangaExtensionRepo,
        replaceNovelExtensionRepo = replaceNovelExtensionRepo,
        updateAnimeExtensionRepo = updateAnimeExtensionRepo,
        updateMangaExtensionRepo = updateMangaExtensionRepo,
        updateNovelExtensionRepo = updateNovelExtensionRepo,
    )

    private fun newDriver(
        schema: app.cash.sqldelight.db.SqlSchema<app.cash.sqldelight.db.QueryResult.Value<Unit>>,
        fileName: String,
    ): NativeSqliteDriver {
        // sqliter rejects path separators in `name` — the directory goes
        // through Extended.basePath. Journal mode defaults to WAL; foreign
        // keys + synchronous via config so every pooled connection gets them.
        return NativeSqliteDriver(
            schema = schema,
            name = fileName,
            onConfiguration = { configuration ->
                configuration.copy(
                    extendedConfig = DatabaseConfiguration.Extended(
                        basePath = databasesDir,
                        foreignKeyConstraints = true,
                        synchronousFlag = SynchronousFlag.NORMAL,
                    ),
                )
            },
        )
    }

    companion object {
        private val appSupportDir: String by lazy {
            val url = NSFileManager.defaultManager.URLsForDirectory(
                NSApplicationSupportDirectory,
                NSUserDomainMask,
            ).firstOrNull() as? NSURL
            url?.path ?: error("could not resolve NSApplicationSupportDirectory")
        }

        private val databasesDir: String by lazy {
            val dir = "$appSupportDir/databases"
            NSFileManager.defaultManager.createDirectoryAtPath(
                dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            dir
        }

        private val filesDir: String by lazy {
            val dir = "$appSupportDir/files"
            NSFileManager.defaultManager.createDirectoryAtPath(
                dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            dir
        }
    }
}
