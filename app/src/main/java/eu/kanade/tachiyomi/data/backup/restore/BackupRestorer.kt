package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.CustomButtonRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionsRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
    private val isSync: Boolean,

    private val animeCategoriesRestorer: AnimeCategoriesRestorer = AnimeCategoriesRestorer(),
    private val mangaCategoriesRestorer: MangaCategoriesRestorer = MangaCategoriesRestorer(),
    private val novelCategoriesRestorer: NovelCategoriesRestorer = NovelCategoriesRestorer(),
    private val preferenceRestorer: PreferenceRestorer = PreferenceRestorer(context),
    private val animeExtensionRepoRestorer: AnimeExtensionRepoRestorer = AnimeExtensionRepoRestorer(),
    private val mangaExtensionRepoRestorer: MangaExtensionRepoRestorer = MangaExtensionRepoRestorer(),
    private val customButtonRestorer: CustomButtonRestorer = CustomButtonRestorer(),
    private val animeRestorer: AnimeRestorer = AnimeRestorer(),
    private val mangaRestorer: MangaRestorer = MangaRestorer(),
    private val novelRestorer: NovelRestorer = NovelRestorer(),
    private val extensionsRestorer: ExtensionsRestorer = ExtensionsRestorer(context),
) {

    private var restoreAmount = 0
    private var restoreProgress = 0
    private val errors = mutableListOf<Pair<Date, String>>()

    /**
     * Mapping of source ID to source name from backup data
     */
    private var animeSourceMapping: Map<Long, String> = emptyMap()
    private var mangaSourceMapping: Map<Long, String> = emptyMap()
    private var novelSourceMapping: Map<Long, String> = emptyMap()

    /**
     * Mapping from backup source IDs to installed source IDs, built by [SourceIdMapper].
     * Used to remap source IDs when the backup comes from a different app (e.g. Tadami/Hayai
     * with JS-based source IDs) so that library entries link to the correct installed extensions.
     */
    private var animeSourceIdMap: Map<Long, Long> = emptyMap()
    private var mangaSourceIdMap: Map<Long, Long> = emptyMap()
    private var novelSourceIdMap: Map<Long, Long> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        restoreFromFile(uri, options)

        val time = System.currentTimeMillis() - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(
            time,
            errors.size,
            logFile.parent,
            logFile.name,
            isSync,
        )
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val backup = BackupDecoder(context).decode(uri)

        // Store source mapping for error messages
        val backupAnimeMaps = backup.backupAnimeSources
        animeSourceMapping = backupAnimeMaps.associate { it.sourceId to it.name }
        val backupMangaMaps = backup.backupSources
        mangaSourceMapping = backupMangaMaps.associate { it.sourceId to it.name }
        val backupNovelMaps = backup.backupNovelSources
        novelSourceMapping = backupNovelMaps.associate { it.sourceId to it.name }

        // Build source ID mappings for cross-format backup compatibility.
        // This remaps source IDs from the backup to match installed extension source IDs
        // (e.g. Tadami/Hayai JS-based IDs → Miko APK-based IDs) by matching on baseUrl or name.
        val sourceIdMapper = SourceIdMapper()
        mangaSourceIdMap = sourceIdMapper.buildMangaMapping(backupMangaMaps, Injekt.get<MangaSourceManager>())
        animeSourceIdMap = sourceIdMapper.buildAnimeMapping(backupAnimeMaps, Injekt.get<AnimeSourceManager>())
        novelSourceIdMap = sourceIdMapper.buildNovelMapping(backupNovelMaps, Injekt.get<NovelSourceManager>())

        // Pass mappings to restorers so they can remap source IDs during restore
        mangaRestorer.sourceIdMap = mangaSourceIdMap
        animeRestorer.sourceIdMap = animeSourceIdMap
        novelRestorer.sourceIdMap = novelSourceIdMap

        if (options.libraryEntries) {
            restoreAmount += backup.backupManga.size + backup.backupAnime.size + backup.backupNovels.size
        }
        if (options.categories) {
            restoreAmount += 3 // +3 for anime, manga, and novel categories
        }
        if (options.appSettings) {
            restoreAmount += 1
        }
        if (options.extensionRepoSettings) {
            restoreAmount += backup.backupAnimeExtensionRepo.size + backup.backupMangaExtensionRepo.size
        }
        if (options.customButtons) {
            restoreAmount += 1
        }
        if (options.sourceSettings) {
            restoreAmount += 1
        }
        if (options.extensions) {
            restoreAmount += 1
        }

        coroutineScope {
            if (options.categories) {
                restoreCategories(
                    backupAnimeCategories = backup.backupAnimeCategories,
                    backupMangaCategories = backup.backupCategories,
                    backupNovelCategories = backup.backupNovelCategory,
                )
            }
            if (options.appSettings) {
                restoreAppPreferences(backup.backupPreferences, backup.backupCategories.takeIf { options.categories })
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup.backupSourcePreferences)
            }
            if (options.libraryEntries) {
                restoreAnime(backup.backupAnime, if (options.categories) backup.backupAnimeCategories else emptyList())
                restoreManga(backup.backupManga, if (options.categories) backup.backupCategories else emptyList())
                restoreNovel(backup.backupNovels, if (options.categories) backup.backupNovelCategory else emptyList())
            }
            if (options.extensionRepoSettings) {
                restoreExtensionRepos(backup.backupAnimeExtensionRepo, backup.backupMangaExtensionRepo)
            }
            if (options.customButtons) {
                restoreCustomButtons(backup.backupCustomButton)
            }
            if (options.extensions) {
                restoreExtensions(backup.backupExtensions)
            }

            // TODO: optionally trigger online library + tracker update
        }
    }

    private fun CoroutineScope.restoreCategories(
        backupAnimeCategories: List<BackupCategory>,
        backupMangaCategories: List<BackupCategory>,
        backupNovelCategories: List<BackupCategory>,
    ) = launch {
        ensureActive()
        animeCategoriesRestorer(backupAnimeCategories)
        mangaCategoriesRestorer(backupMangaCategories)
        novelCategoriesRestorer(backupNovelCategories)

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.categories),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreAnime(
        backupAnimes: List<BackupAnime>,
        backupAnimeCategories: List<BackupCategory>,
    ) = launch {
        animeRestorer.sortByNew(backupAnimes)
            .forEach {
                ensureActive()

                val seasons = backupAnimes.filter { s -> s.parentId == it.id }
                try {
                    animeRestorer.restore(it, backupAnimeCategories, seasons)
                } catch (e: Exception) {
                    val sourceName = animeSourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                }

                restoreProgress += 1
                notifier.showRestoreProgress(it.title, restoreProgress, restoreAmount, isSync)
            }
    }

    private fun CoroutineScope.restoreManga(
        backupMangas: List<BackupManga>,
        backupMangaCategories: List<BackupCategory>,
    ) = launch {
        mangaRestorer.sortByNew(backupMangas)
            .forEach {
                ensureActive()

                try {
                    mangaRestorer.restore(it, backupMangaCategories)
                } catch (e: Exception) {
                    val sourceName = mangaSourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                }

                restoreProgress += 1
                notifier.showRestoreProgress(it.title, restoreProgress, restoreAmount, isSync)
            }
    }

    private fun CoroutineScope.restoreNovel(
        backupNovels: List<BackupNovel>,
        backupNovelCategories: List<BackupCategory>,
    ) = launch {
        novelRestorer.sortByNew(backupNovels)
            .forEach {
                ensureActive()

                try {
                    novelRestorer.restore(it, backupNovelCategories)
                } catch (e: Exception) {
                    val sourceName = novelSourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                }

                restoreProgress += 1
                notifier.showRestoreProgress(it.title, restoreProgress, restoreAmount, isSync)
            }
    }

    private fun CoroutineScope.restoreAppPreferences(
        preferences: List<BackupPreference>,
        categories: List<BackupCategory>?,
    ) = launch {
        ensureActive()
        preferenceRestorer.restoreApp(
            preferences,
            categories,
        )

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.app_settings),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreSourcePreferences(preferences: List<BackupSourcePreferences>) = launch {
        ensureActive()
        preferenceRestorer.restoreSource(preferences)

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreExtensionRepos(
        backupAnimeExtensionRepo: List<BackupExtensionRepos>,
        backupMangaExtensionRepo: List<BackupExtensionRepos>,
    ) = launch {
        backupAnimeExtensionRepo
            .forEach {
                ensureActive()

                try {
                    animeExtensionRepoRestorer(it)
                } catch (e: Exception) {
                    errors.add(Date() to "Error Adding Anime Repo: ${it.name} : ${e.message}")
                }

                restoreProgress += 1
                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionRepo_settings),
                    restoreProgress,
                    restoreAmount,
                    isSync,
                )
            }

        backupMangaExtensionRepo
            .forEach {
                ensureActive()

                try {
                    mangaExtensionRepoRestorer(it)
                } catch (e: Exception) {
                    errors.add(Date() to "Error Adding Manga Repo: ${it.name} : ${e.message}")
                }

                restoreProgress += 1
                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionRepo_settings),
                    restoreProgress,
                    restoreAmount,
                    isSync,
                )
            }
    }

    private fun CoroutineScope.restoreCustomButtons(customButtons: List<BackupCustomButtons>) = launch {
        ensureActive()
        customButtonRestorer(customButtons)

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(AYMR.strings.custom_button_settings),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreExtensions(extensions: List<BackupExtension>) = launch {
        ensureActive()
        extensionsRestorer.restoreExtensions(extensions)

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("aniyomi_restore_error.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (e: Exception) {
            // Empty
        }
        return File("")
    }
}
