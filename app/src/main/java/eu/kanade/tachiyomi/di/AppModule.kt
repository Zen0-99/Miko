package eu.kanade.tachiyomi.di

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import data.History
import data.Mangas
import dataanime.Animehistory
import dataanime.Animes
import datanovel.Novelhistory
import datanovel.Novels
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager
import eu.kanade.tachiyomi.source.manga.AndroidMangaSourceManager
import eu.kanade.tachiyomi.source.novel.AndroidNovelSourceManager
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.JsNovelPluginManager
import eu.kanade.tachiyomi.extension.novel.NovelPluginSourceFactory
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginApi
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginApiFacade
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginIndexFetcher
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginIndexParser
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginRepoProvider
import eu.kanade.tachiyomi.extension.novel.api.NovelPluginRepoProviderImpl
import eu.kanade.tachiyomi.extension.novel.api.NetworkNovelPluginIndexFetcher
import eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntimeFactory
import eu.kanade.tachiyomi.extension.novel.runtime.NovelJsSourceFactory
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginAssetBindings
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginRuntimeOverrides
import eu.kanade.tachiyomi.extension.novel.runtime.NovelDomainAliasResolver
import eu.kanade.tachiyomi.extension.novel.NetworkNovelPluginDownloader
import tachiyomi.data.extension.novel.NovelPluginStorage
import tachiyomi.data.extension.novel.AndroidNovelPluginKeyValueStore
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore
import tachiyomi.data.extension.novel.NovelPluginDownloader
import tachiyomi.data.extension.novel.NovelPluginInstaller
import tachiyomi.data.extension.novel.NovelPluginInstallerFacade
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.map
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode.Charset
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.NovelUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.novel.data.NovelDatabase
import tachiyomi.source.local.entries.anime.LocalAnimeFetchTypeManager
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import tachiyomi.source.local.io.manga.LocalMangaSourceFileSystem
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AppModule(val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

        val sqlDriverManga = AndroidSqliteDriver(
            schema = Database.Schema,
            context = app,
            name = "tachiyomi.db",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        val sqlDriverAnime = AndroidSqliteDriver(
            schema = AnimeDatabase.Schema,
            context = app,
            name = "tachiyomi.animedb",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(AnimeDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        addSingletonFactory {
            Database(
                driver = sqlDriverManga,
                historyAdapter = History.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                mangasAdapter = Mangas.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                ),
            )
        }

        addSingletonFactory {
            AnimeDatabase(
                driver = sqlDriverAnime,
                animehistoryAdapter = Animehistory.Adapter(
                    last_seenAdapter = DateColumnAdapter,
                ),
                animesAdapter = Animes.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                    fetch_typeAdapter = FetchTypeColumnAdapter,
                ),
            )
        }

        addSingletonFactory<MangaDatabaseHandler> {
            AndroidMangaDatabaseHandler(
                get(),
                sqlDriverManga,
            )
        }

        addSingletonFactory<AnimeDatabaseHandler> {
            AndroidAnimeDatabaseHandler(
                get(),
                sqlDriverAnime,
            )
        }

        val sqlDriverNovel = AndroidSqliteDriver(
            schema = NovelDatabase.Schema,
            context = app,
            name = "tachiyomi.noveldb",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(NovelDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        addSingletonFactory {
            NovelDatabase(
                driver = sqlDriverNovel,
                novelhistoryAdapter = Novelhistory.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                novelsAdapter = Novels.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = NovelUpdateStrategyColumnAdapter,
                ),
            )
        }

        addSingletonFactory<NovelDatabaseHandler> {
            AndroidNovelDatabaseHandler(
                get(),
                sqlDriverNovel,
            )
        }

        val sqlDriverAchievements = AndroidSqliteDriver(
            schema = tachiyomi.db.achievement.AchievementsDatabase.Schema,
            context = app,
            name = "tachiyomi.achievementsdb",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(tachiyomi.db.achievement.AchievementsDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        addSingletonFactory {
            tachiyomi.data.achievement.database.AchievementsDatabase(
                driver = sqlDriverAchievements,
            )
        }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        addSingletonFactory {
            XML {
                defaultPolicy {
                    ignoreUnknownChildren()
                }
                autoPolymorphic = true
                xmlDeclMode = Charset
                indent = 2
                xmlVersion = XmlVersion.XML10
            }
        }
        addSingletonFactory<ProtoBuf> {
            ProtoBuf
        }

        addSingletonFactory { ChapterCache(app, get()) }

        addSingletonFactory { MangaCoverCache(app) }
        addSingletonFactory { AnimeCoverCache(app) }
        addSingletonFactory { AnimeBackgroundCache(app) }
        addSingletonFactory { NovelCoverCache(app) }

        addSingletonFactory { NetworkHelper(app, get()) }
        addSingletonFactory { JavaScriptEngine(app) }

        addSingletonFactory<MangaSourceManager> { AndroidMangaSourceManager(app, get(), get()) }
        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(app, get(), get()) }

        // --- JS novel plugin runtime wiring ---
        // Storage for plugin scripts and custom assets
        addSingletonFactory {
            tachiyomi.data.extension.novel.NovelPluginStorage(
                java.io.File(app.filesDir, "novel_plugins"),
            )
        }
        // Key-value store for plugin settings (SharedPreferences-backed)
        addSingletonFactory<tachiyomi.data.extension.novel.NovelPluginKeyValueStore> {
            tachiyomi.data.extension.novel.AndroidNovelPluginKeyValueStore(app)
        }
        // Network downloader for plugin scripts
        addSingletonFactory<tachiyomi.data.extension.novel.NovelPluginDownloader> {
            eu.kanade.tachiyomi.extension.novel.NetworkNovelPluginDownloader(get<eu.kanade.tachiyomi.network.NetworkHelper>().client)
        }
        // Installer that downloads + verifies + stores plugin scripts
        addSingletonFactory<tachiyomi.data.extension.novel.NovelPluginInstallerFacade> {
            tachiyomi.data.extension.novel.NovelPluginInstaller(get(), get(), get())
        }
        // Asset bindings for custom JS/CSS injection
        addSingletonFactory {
            eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginAssetBindings(get())
        }
        // Plugin index fetcher + parser (for browsing available plugins)
        addSingletonFactory<eu.kanade.tachiyomi.extension.novel.api.NovelPluginIndexFetcher> {
            eu.kanade.tachiyomi.extension.novel.api.NetworkNovelPluginIndexFetcher(
                get<eu.kanade.tachiyomi.network.NetworkHelper>().client,
            )
        }
        addSingletonFactory {
            eu.kanade.tachiyomi.extension.novel.api.NovelPluginIndexParser(get())
        }
        // Repo provider — bridges JS plugin API to the shared extension repo table
        addSingletonFactory<eu.kanade.tachiyomi.extension.novel.api.NovelPluginRepoProvider> {
            eu.kanade.tachiyomi.extension.novel.api.NovelPluginRepoProviderImpl(get())
        }
        // Plugin API facade — fetches available plugins from all repos
        addSingletonFactory<eu.kanade.tachiyomi.extension.novel.api.NovelPluginApiFacade> {
            eu.kanade.tachiyomi.extension.novel.api.NovelPluginApi(get(), get(), get())
        }
        // Runtime overrides (domain aliases, script patches)
        addSingletonFactory {
            eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginRuntimeOverrides.fromJson(get(), null)
        }
        // Domain alias resolver (uses runtime overrides)
        addSingletonFactory {
            eu.kanade.tachiyomi.extension.novel.runtime.NovelDomainAliasResolver(get())
        }
        // J2V8 runtime factory
        addSingletonFactory {
            eu.kanade.tachiyomi.extension.novel.runtime.NovelJsRuntimeFactory(
                context = app,
                networkHelper = get(),
                keyValueStore = get(),
                json = get(),
                domainAliasResolver = get(),
            )
        }
        // Source factory — creates NovelJsSource instances from installed plugins
        addSingletonFactory<eu.kanade.tachiyomi.extension.novel.NovelPluginSourceFactory> {
            eu.kanade.tachiyomi.extension.novel.runtime.NovelJsSourceFactory(
                runtimeFactory = get(),
                pluginStorage = get(),
                json = get(),
                runtimeOverrides = get(),
                keyValueStore = get(),
                assetBindings = get(),
            )
        }
        // JS plugin manager — orchestrates install/uninstall/refresh
        addSingletonFactory { eu.kanade.tachiyomi.extension.novel.JsNovelPluginManager(get(), get(), get()) }

        // Source manager — now wired with JS plugin sources flow
        addSingletonFactory<NovelSourceManager> {
            val jsPluginManager = get<eu.kanade.tachiyomi.extension.novel.JsNovelPluginManager>()
            val sourceFactory = get<eu.kanade.tachiyomi.extension.novel.NovelPluginSourceFactory>()
            val jsSourcesFlow = jsPluginManager.installedPluginsFlow.map { plugins ->
                plugins.mapNotNull { plugin -> sourceFactory.create(plugin) }
            }
            AndroidNovelSourceManager(app, get(), get(), kotlinx.coroutines.Dispatchers.IO, jsSourcesFlow)
        }

        addSingletonFactory { MangaExtensionManager(app) }
        addSingletonFactory { AnimeExtensionManager(app) }
        addSingletonFactory { NovelExtensionManager(app) }

        addSingletonFactory { MangaDownloadProvider(app) }
        addSingletonFactory { MangaDownloadManager(app) }
        addSingletonFactory { MangaDownloadCache(app) }

        addSingletonFactory { AnimeDownloadProvider(app) }
        addSingletonFactory { AnimeDownloadManager(app) }
        addSingletonFactory { AnimeDownloadCache(app) }

        addSingletonFactory { NovelDownloadProvider(app) }
        addSingletonFactory { NovelDownloadManager(app) }
        addSingletonFactory { NovelDownloadCache(app) }

        addSingletonFactory { TrackerManager(app) }
        addSingletonFactory { DelayedAnimeTrackingStore(app) }
        addSingletonFactory { DelayedMangaTrackingStore(app) }

        // Achievement system repositories
        addSingletonFactory<tachiyomi.domain.achievement.repository.AchievementRepository> {
            tachiyomi.data.achievement.repository.AchievementRepositoryImpl(get())
        }
        addSingletonFactory<tachiyomi.domain.achievement.repository.ActivityDataRepository> {
            tachiyomi.data.achievement.ActivityDataRepositoryImpl(get())
        }
        addSingletonFactory<tachiyomi.domain.achievement.repository.UserProfileRepository> {
            tachiyomi.data.achievement.UserProfileRepositoryImpl(get())
        }
        // Entry repositories (needed by achievement handlers)
        addSingletonFactory<tachiyomi.domain.entries.manga.repository.MangaRepository> {
            tachiyomi.data.entries.manga.MangaRepositoryImpl(get())
        }
        addSingletonFactory<tachiyomi.domain.entries.anime.repository.AnimeRepository> {
            tachiyomi.data.entries.anime.AnimeRepositoryImpl(get())
        }
        addSingletonFactory<tachiyomi.domain.entries.novel.repository.NovelRepository> {
            tachiyomi.data.entries.novel.NovelRepositoryImpl(get())
        }

        // Achievement system managers and handlers
        addSingletonFactory<tachiyomi.data.achievement.localization.AchievementTextResolver> {
            eu.kanade.tachiyomi.data.achievement.localization.AchievementTextResolverImpl(app)
        }
        addSingletonFactory { tachiyomi.data.achievement.loader.AchievementLoader(app, get(), get(), get()) }
        addSingletonFactory { tachiyomi.data.achievement.handler.PointsManager(get()) }
        addSingletonFactory { tachiyomi.data.achievement.UserProfileManager(get()) }
        addSingletonFactory {
            tachiyomi.data.achievement.UnlockableManager(
                app.getSharedPreferences("achievement_unlockables", Context.MODE_PRIVATE),
                get(),
            )
        }
        addSingletonFactory { tachiyomi.data.achievement.handler.AchievementEventBus() }
        addSingletonFactory { tachiyomi.data.achievement.handler.FeatureUsageCollector(get()) }
        addSingletonFactory { tachiyomi.data.achievement.handler.SessionManager(get(), get()) }
        addSingletonFactory { tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker(get(), get(), get()) }
        addSingletonFactory { tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker(get()) }
        addSingletonFactory { tachiyomi.data.achievement.handler.checkers.TimeBasedAchievementChecker(get(), get()) }
        addSingletonFactory { tachiyomi.data.achievement.handler.checkers.FeatureBasedAchievementChecker(get(), get()) }
        addSingletonFactory { tachiyomi.data.achievement.handler.AchievementRuleRegistry(get(), get(), get()) }
        // RuleContextImpl is not registered as a singleton because it requires
        // runtime data (allProgress, allAchievementsMap) that is not available at DI time.
        // It is created on-demand by AchievementHandler.
        addSingletonFactory {
            tachiyomi.data.achievement.handler.AchievementCalculator(
                repository = get(),
                mangaHandler = get(),
                animeHandler = get(),
                novelHandler = get(),
                diversityChecker = get(),
                streakChecker = get(),
                achievementsDatabase = get(),
                ruleRegistry = get(),
                featureCollector = get(),
                pointsManager = get(),
                mangaRepository = get(),
                animeRepository = get(),
                novelRepository = get(),
                unlockableManager = get(),
                userProfileManager = get(),
                activityDataRepository = get(),
            )
        }
        addSingletonFactory {
            tachiyomi.data.achievement.handler.AchievementHandler(
                eventBus = get(),
                repository = get(),
                diversityChecker = get(),
                streakChecker = get(),
                timeBasedChecker = get(),
                featureBasedChecker = get(),
                featureCollector = get(),
                pointsManager = get(),
                unlockableManager = get(),
                mangaHandler = get(),
                animeHandler = get(),
                novelHandler = get(),
                mangaRepository = get(),
                animeRepository = get(),
                novelRepository = get(),
                userProfileManager = get(),
                activityDataRepository = get(),
                ruleRegistry = get(),
            )
        }
        // Force eager init of AchievementsDatabase
        get<tachiyomi.data.achievement.database.AchievementsDatabase>()

        addSingletonFactory { ImageSaver(app) }

        addSingletonFactory { AndroidStorageFolderProvider(app) }

        addSingletonFactory { LocalMangaSourceFileSystem(get()) }
        addSingletonFactory { LocalMangaCoverManager(app, get()) }

        addSingletonFactory { LocalAnimeSourceFileSystem(get()) }
        addSingletonFactory { LocalAnimeBackgroundManager(app, get()) }
        addSingletonFactory { LocalAnimeCoverManager(app, get()) }
        addSingletonFactory { LocalAnimeFetchTypeManager(app, get()) }
        addSingletonFactory { LocalEpisodeThumbnailManager(app, get()) }

        addSingletonFactory { StorageManager(app, get()) }

        addSingletonFactory { ExternalIntents() }

        addSingletonFactory { eu.kanade.domain.savedsearches.manga.MangaFilterSerializer() }
        addSingletonFactory { eu.kanade.domain.savedsearches.anime.AnimeFilterSerializer() }
        addSingletonFactory { eu.kanade.domain.savedsearches.novel.NovelFilterSerializer() }

        addSingletonFactory { TorrentServerApi(get(), get()) }
        addSingletonFactory { TorrentServerUtils(get(), get()) }

        // Suggestions pipeline
        addSingletonFactory { eu.kanade.tachiyomi.data.suggestions.SuggestionCoordinator(get()) }
        addSingletonFactory { eu.kanade.tachiyomi.data.suggestions.anime.AnimeSearchFallbackEngine() }
        addSingletonFactory { eu.kanade.tachiyomi.data.suggestions.manga.MangaSearchFallbackEngine() }
        addSingletonFactory { eu.kanade.tachiyomi.data.suggestions.novel.NovelSearchFallbackEngine() }
        addSingletonFactory { eu.kanade.tachiyomi.data.suggestions.novel.NovelRelatedSuggestionCoordinator() }

        // Asynchronously init expensive components for a faster cold start
        ContextCompat.getMainExecutor(app).execute {
            get<NetworkHelper>()

            get<MangaSourceManager>()
            get<AnimeSourceManager>()

            get<Database>()
            get<AnimeDatabase>()

            get<MangaDownloadManager>()
            get<AnimeDownloadManager>()
        }
    }
}
