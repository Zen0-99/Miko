package miko.ios

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import data.History
import data.Mangas
import dataanime.Animehistory
import dataanime.Animes
import datanovel.Novelhistory
import datanovel.Novels
import eu.kanade.tachiyomi.core.network.platformHttpClient
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import mihon.domain.extensionrepo.service.ExtensionRepoService
import mihon.domain.extensionrepo.service.IosExtensionRepoService
import miko.jssource.IosJsRuntimeFactory
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
import tachiyomi.data.extension.novel.DefaultNovelPluginStorage
import tachiyomi.data.extension.novel.IosNovelPluginKeyValueStore
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore
import tachiyomi.data.extension.novel.NovelPluginRepositoryImpl
import tachiyomi.data.extension.novel.NovelPluginStorage
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.anime.IosAnimeDatabaseHandler
import tachiyomi.data.handlers.manga.IosMangaDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.IosNovelDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.data.source.novel.NovelStubSourceRepositoryImpl
import tachiyomi.db.achievement.AchievementsDatabase
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository
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

    val extensionRepoService: ExtensionRepoService = IosExtensionRepoService()

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

    private val json = Json { ignoreUnknownKeys = true }

    val jsRuntimeFactory = IosJsRuntimeFactory()

    val novelSourceManager: NovelSourceManager = IosNovelSourceManager(
        pluginRepository = novelPluginRepository,
        pluginStorage = novelPluginStorage,
        sourceRepository = novelStubSourceRepository,
        runtimeFactory = jsRuntimeFactory,
        httpClient = httpClient,
        json = json,
    )

    private fun newDriver(
        schema: app.cash.sqldelight.db.SqlSchema<app.cash.sqldelight.db.QueryResult.Value<Unit>>,
        fileName: String,
    ): NativeSqliteDriver {
        val driver = NativeSqliteDriver(schema, "$databasesDir/$fileName")
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        driver.execute(null, "PRAGMA journal_mode = WAL", 0)
        driver.execute(null, "PRAGMA synchronous = NORMAL", 0)
        return driver
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
