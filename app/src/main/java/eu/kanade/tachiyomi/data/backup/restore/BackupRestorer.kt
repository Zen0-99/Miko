package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCollection
import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelLink
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.AchievementRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeCollectionsRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.CustomButtonRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionsRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaCollectionsRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelCollectionsRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.NovelLinksRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.library.EntryRef
import eu.kanade.tachiyomi.data.library.FailedEntry
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgress
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.library.model.EntryKind
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
import java.util.concurrent.atomic.AtomicInteger

class BackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,
    private val isSync: Boolean,

    private val animeCollectionsRestorer: AnimeCollectionsRestorer = AnimeCollectionsRestorer(),
    private val mangaCollectionsRestorer: MangaCollectionsRestorer = MangaCollectionsRestorer(),
    private val novelCollectionsRestorer: NovelCollectionsRestorer = NovelCollectionsRestorer(),
    private val preferenceRestorer: PreferenceRestorer = PreferenceRestorer(context),
    private val animeExtensionRepoRestorer: AnimeExtensionRepoRestorer = AnimeExtensionRepoRestorer(),
    private val mangaExtensionRepoRestorer: MangaExtensionRepoRestorer = MangaExtensionRepoRestorer(),
    private val customButtonRestorer: CustomButtonRestorer = CustomButtonRestorer(),
    private val animeRestorer: AnimeRestorer = AnimeRestorer(),
    private val mangaRestorer: MangaRestorer = MangaRestorer(),
    private val novelRestorer: NovelRestorer = NovelRestorer(),
    private val novelLinksRestorer: NovelLinksRestorer = NovelLinksRestorer(),
    private val extensionsRestorer: ExtensionsRestorer = ExtensionsRestorer(context),
    private val achievementRestorer: AchievementRestorer = AchievementRestorer(),
) {

    private var restoreAmount = 0
    private var restoreProgress = 0
    private val errors = mutableListOf<Pair<Date, String>>()

    // Per-mode progress for the in-app fetching overlay
    private val animeRestoreProgress = AtomicInteger(0)
    private val mangaRestoreProgress = AtomicInteger(0)
    private val novelRestoreProgress = AtomicInteger(0)
    private val animeRestoreFailures = java.util.concurrent.CopyOnWriteArrayList<FailedEntry>()
    private val mangaRestoreFailures = java.util.concurrent.CopyOnWriteArrayList<FailedEntry>()
    private val novelRestoreFailures = java.util.concurrent.CopyOnWriteArrayList<FailedEntry>()

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
        novelLinksRestorer.sourceIdMap = novelSourceIdMap

        if (options.libraryEntries) {
            restoreAmount += backup.backupManga.size + backup.backupAnime.size + backup.backupNovels.size
            // Start per-mode progress on the in-app fetching overlay
            if (backup.backupAnime.isNotEmpty()) {
                LibraryUpdateProgressBus.startRun(total = backup.backupAnime.size, source = "Anime")
            }
            if (backup.backupManga.isNotEmpty()) {
                LibraryUpdateProgressBus.startRun(total = backup.backupManga.size, source = "Manga")
            }
            if (backup.backupNovels.isNotEmpty()) {
                LibraryUpdateProgressBus.startRun(total = backup.backupNovels.size, source = "Novel")
            }
        }
        if (options.collections) {
            restoreAmount += 3 // +3 for anime, manga, and novel collections
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
            if (options.collections) {
                restoreCollections(
                    backupAnimeCollections = backup.backupAnimeCollections,
                    backupMangaCollections = backup.backupCollections,
                    backupNovelCollections = backup.backupNovelCollection,
                )
            }
            if (options.appSettings) {
                restoreAppPreferences(backup.backupPreferences, backup.backupCollections.takeIf { options.collections })
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup.backupSourcePreferences)
            }
            if (options.libraryEntries) {
                restoreAnime(backup.backupAnime, if (options.collections) backup.backupAnimeCollections else emptyList())
                restoreManga(backup.backupManga, if (options.collections) backup.backupCollections else emptyList())
                restoreNovel(backup.backupNovels, if (options.collections) backup.backupNovelCollection else emptyList())
                // Restore novel source links after novels are inserted
                restoreNovelLinks(backup.backupNovelLinks)
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

            // Always restore achievements/stats if present in the backup.
            restoreAchievements(
                backup.backupAchievements,
                backup.backupUserProfile,
                backup.backupActivityLog,
                backup.backupStats,
            )

            // TODO: optionally trigger online library + tracker update
        }
    }

    private fun CoroutineScope.restoreCollections(
        backupAnimeCollections: List<BackupCollection>,
        backupMangaCollections: List<BackupCollection>,
        backupNovelCollections: List<BackupCollection>,
    ) = launch {
        ensureActive()
        animeCollectionsRestorer(backupAnimeCollections)
        mangaCollectionsRestorer(backupMangaCollections)
        novelCollectionsRestorer(backupNovelCollections)

        restoreProgress += 1
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.collections),
            restoreProgress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreAnime(
        backupAnimes: List<BackupAnime>,
        backupAnimeCollections: List<BackupCollection>,
    ) = launch {
        val total = backupAnimes.size
        animeRestorer.sortByNew(backupAnimes)
            .forEach {
                ensureActive()

                val seasons = backupAnimes.filter { s -> s.parentId == it.id }
                val entryRef = EntryRef(
                    id = it.url.hashCode().toLong(),
                    title = it.title,
                    sourceId = it.source,
                    kind = EntryKind.ANIME,
                )
                try {
                    animeRestorer.restore(it, backupAnimeCollections, seasons)
                } catch (e: Exception) {
                    val sourceName = animeSourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                    animeRestoreFailures.add(
                        FailedEntry(entry = entryRef, reason = e.message ?: "Unknown error", sourceName = sourceName),
                    )
                }

                restoreProgress += 1
                val processed = animeRestoreProgress.incrementAndGet()
                notifier.showRestoreProgress(it.title, restoreProgress, restoreAmount, isSync)
                LibraryUpdateProgressBus.updateProgress(
                    processed = processed,
                    currentlyUpdating = listOf(entryRef),
                    failedSoFar = animeRestoreFailures.toList(),
                    totalEntries = total,
                    source = "Anime",
                )
            }
        LibraryUpdateProgressBus.completeRun(failed = animeRestoreFailures.toList(), source = "Anime")
    }

    private fun CoroutineScope.restoreManga(
        backupMangas: List<BackupManga>,
        backupMangaCollections: List<BackupCollection>,
    ) = launch {
        val total = backupMangas.size
        mangaRestorer.sortByNew(backupMangas)
            .forEach {
                ensureActive()

                val entryRef = EntryRef(
                    id = it.url.hashCode().toLong(),
                    title = it.title,
                    sourceId = it.source,
                    kind = EntryKind.MANGA,
                )
                try {
                    mangaRestorer.restore(it, backupMangaCollections)
                } catch (e: Exception) {
                    val sourceName = mangaSourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                    mangaRestoreFailures.add(
                        FailedEntry(entry = entryRef, reason = e.message ?: "Unknown error", sourceName = sourceName),
                    )
                }

                restoreProgress += 1
                val processed = mangaRestoreProgress.incrementAndGet()
                notifier.showRestoreProgress(it.title, restoreProgress, restoreAmount, isSync)
                LibraryUpdateProgressBus.updateProgress(
                    processed = processed,
                    currentlyUpdating = listOf(entryRef),
                    failedSoFar = mangaRestoreFailures.toList(),
                    totalEntries = total,
                    source = "Manga",
                )
            }
        LibraryUpdateProgressBus.completeRun(failed = mangaRestoreFailures.toList(), source = "Manga")
    }

    private fun CoroutineScope.restoreNovel(
        backupNovels: List<BackupNovel>,
        backupNovelCollections: List<BackupCollection>,
    ) = launch {
        val total = backupNovels.size
        novelRestorer.sortByNew(backupNovels)
            .forEach {
                ensureActive()

                val entryRef = EntryRef(
                    id = it.url.hashCode().toLong(),
                    title = it.title,
                    sourceId = it.source,
                    kind = EntryKind.NOVEL,
                )
                try {
                    novelRestorer.restore(it, backupNovelCollections)
                } catch (e: Exception) {
                    val sourceName = novelSourceMapping[it.source] ?: it.source.toString()
                    errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                    novelRestoreFailures.add(
                        FailedEntry(entry = entryRef, reason = e.message ?: "Unknown error", sourceName = sourceName),
                    )
                }

                restoreProgress += 1
                val processed = novelRestoreProgress.incrementAndGet()
                notifier.showRestoreProgress(it.title, restoreProgress, restoreAmount, isSync)
                LibraryUpdateProgressBus.updateProgress(
                    processed = processed,
                    currentlyUpdating = listOf(entryRef),
                    failedSoFar = novelRestoreFailures.toList(),
                    totalEntries = total,
                    source = "Novel",
                )
            }
        LibraryUpdateProgressBus.completeRun(failed = novelRestoreFailures.toList(), source = "Novel")
    }

    private fun CoroutineScope.restoreNovelLinks(
        backupLinks: List<BackupNovelLink>,
    ) = launch {
        if (backupLinks.isEmpty()) return@launch
        ensureActive()
        try {
            novelLinksRestorer.restore(backupLinks)
        } catch (e: Exception) {
            errors.add(Date() to "Novel links restore: ${e.message}")
        }
    }

    private fun CoroutineScope.restoreAppPreferences(
        preferences: List<BackupPreference>,
        collections: List<BackupCollection>?,
    ) = launch {
        ensureActive()
        preferenceRestorer.restoreApp(
            preferences,
            collections,
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

    private fun CoroutineScope.restoreAchievements(
        backupAchievements: List<eu.kanade.tachiyomi.data.backup.models.BackupAchievement>,
        backupUserProfile: eu.kanade.tachiyomi.data.backup.models.BackupUserProfile?,
        backupActivityLog: List<eu.kanade.tachiyomi.data.backup.models.BackupDayActivity>,
        backupStats: eu.kanade.tachiyomi.data.backup.models.BackupStats?,
    ) = launch {
        if (backupAchievements.isEmpty() && backupUserProfile == null && backupActivityLog.isEmpty() && backupStats == null) {
            return@launch
        }
        ensureActive()
        try {
            achievementRestorer.restoreAchievements(
                backupAchievements,
                backupUserProfile,
                backupActivityLog,
                backupStats,
            )
        } catch (e: Exception) {
            errors.add(Date() to "Achievement restore: ${e.message}")
        }
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
