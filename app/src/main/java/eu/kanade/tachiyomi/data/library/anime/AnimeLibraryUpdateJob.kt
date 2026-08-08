package eu.kanade.tachiyomi.data.library.anime

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.library.FailedFetchStore
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.NoEpisodesException
import tachiyomi.domain.items.season.interactor.GetAnimeSeasonsByParentId
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.source.anime.model.AnimeSourceNotInstalledException
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AnimeLibraryUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: AnimeSourceManager = Injekt.get()
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val downloadManager: AnimeDownloadManager = Injekt.get()
    private val coverCache: AnimeCoverCache = Injekt.get()
    private val backgroundCache: AnimeBackgroundCache = Injekt.get()
    private val getLibraryAnime: GetLibraryAnime = Injekt.get()
    private val getAnime: GetAnime = Injekt.get()
    private val updateAnime: UpdateAnime = Injekt.get()
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get()
    private val animeFetchInterval: AnimeFetchInterval = Injekt.get()
    private val filterEpisodesForDownload: FilterEpisodesForDownload = Injekt.get()
    private val getAnimeSeasonsByParentId: GetAnimeSeasonsByParentId = Injekt.get()

    private val notifier = AnimeLibraryUpdateNotifier(context)

    private var animeToUpdate: List<LibraryAnime> = mutableListOf()

    override suspend fun doWork(): Result {
        if (tags.contains(WORK_NAME_AUTO)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val preferences = Injekt.get<LibraryPreferences>()
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                if ((DEVICE_ONLY_ON_WIFI in restrictions) && !context.isConnectedToWifi()) {
                    return Result.retry()
                }
            }
        }

        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground job" }
        }

        libraryPreferences.lastUpdatedTimestamp().set(Instant.now().toEpochMilli())

        val collectionId = inputData.getLong(KEY_COLLECTION, -1L)
        val isResume = inputData.getBoolean(KEY_RESUME, false)
        addAnimeToQueue(collectionId)

        return withIOContext {
            try {
                updateEpisodeList(isResume = isResume)
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Assume success although cancelled
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifier = AnimeLibraryUpdateNotifier(context)
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },

        )
    }

    /**
     * Adds list of anime to be updated.
     *
     * @param collectionId the ID of the collection to update, or -1 if no collection specified.
     */
    private suspend fun addAnimeToQueue(collectionId: Long) {
        val libraryAnime = getLibraryAnime.await()

        val listToUpdate = if (collectionId != -1L) {
            libraryAnime.filter { it.collection == collectionId }
        } else {
            val collectionsToUpdate = libraryPreferences.animeUpdateCollections().get().map { it.toLong() }
            val includedAnime = if (collectionsToUpdate.isNotEmpty()) {
                libraryAnime.filter { it.collection in collectionsToUpdate }
            } else {
                libraryAnime
            }

            val collectionsToExclude = libraryPreferences.animeUpdateCollectionsExclude().get().map { it.toLong() }
            val excludedAnimeIds = if (collectionsToExclude.isNotEmpty()) {
                libraryAnime.filter { it.collection in collectionsToExclude }.map { it.anime.id }
            } else {
                emptyList()
            }

            includedAnime
                .filterNot { it.anime.id in excludedAnimeIds }
                .distinctBy { it.anime.id }
        }

        val includeSeasons = libraryPreferences.updateSeasonOnLibraryUpdate().get()
        val lastToUpdateWithSeasons = listToUpdate.flatMap { libAnime ->
            when (libAnime.anime.fetchType) {
                FetchType.Seasons -> {
                    if (includeSeasons) {
                        val seasons = getAnimeSeasonsByParentId.await(libAnime.anime.id)
                        seasons
                            .filter { s ->
                                s.anime.fetchType == FetchType.Episodes && !s.anime.favorite
                            }
                            .map { it.toLibraryAnime() }
                    } else {
                        emptyList()
                    }
                }
                FetchType.Episodes -> listOf(libAnime)
            }
        }

        val restrictions = libraryPreferences.autoUpdateItemRestrictions().get()
        val skippedUpdates = mutableListOf<Pair<Anime, String?>>()
        val (_, fetchWindowUpperBound) = animeFetchInterval.getWindow(ZonedDateTime.now())

        animeToUpdate = lastToUpdateWithSeasons
            .filter {
                when {
                    it.anime.updateStrategy != AnimeUpdateStrategy.ALWAYS_UPDATE -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_always_update),
                        )
                        false
                    }

                    ENTRY_NON_COMPLETED in restrictions && it.anime.status.toInt() == SAnime.COMPLETED -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_completed),
                        )
                        false
                    }

                    ENTRY_HAS_UNVIEWED in restrictions && it.unseenCount != 0L -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_caught_up),
                        )
                        false
                    }

                    ENTRY_NON_VIEWED in restrictions && it.totalCount > 0L && !it.hasStarted -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_started),
                        )
                        false
                    }

                    ENTRY_OUTSIDE_RELEASE_PERIOD in restrictions && it.anime.nextUpdate > fetchWindowUpperBound -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_in_release_period),
                        )
                        false
                    }
                    else -> true
                }
            }
            .sortedBy { it.anime.title }

        notifier.showQueueSizeWarningNotificationIfNeeded(animeToUpdate)

        if (skippedUpdates.isNotEmpty()) {
            // TODO: surface skipped reasons to user?
            logcat {
                skippedUpdates
                    .groupBy { it.second }
                    .map { (reason, entries) -> "$reason: [${entries.map { it.first.title }.sorted().joinToString()}]" }
                    .joinToString()
            }
        }
    }

    /**
     * Method that updates anime in [animeToUpdate]. It's called in a background thread, so it's safe
     * to do heavy operations or network calls here.
     * For each anime it calls [updateAnime] and updates the notification showing the current
     * progress.
     *
     * @return an observable delivering the progress of each update.
     */
    private suspend fun updateEpisodeList(isResume: Boolean = false) {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingAnime = CopyOnWriteArrayList<Anime>()
        val newUpdates = CopyOnWriteArrayList<Pair<Anime, Array<Episode>>>()
        val failedUpdates = CopyOnWriteArrayList<Pair<Anime, String?>>()
        val hasDownloads = AtomicBoolean(false)
        val fetchWindow = animeFetchInterval.getWindow(ZonedDateTime.now())

        // Publish initial running state to the in-app progress bus
        LibraryUpdateProgressBus.startRun(total = animeToUpdate.size, source = "Anime", isResume = isResume)
        if (isResume) {
            progressCount.set(LibraryUpdateProgressBus.checkpoint.size)
        }

        coroutineScope {
            animeToUpdate.groupBy { it.anime.source }.values
                .map { animeInSource ->
                    async {
                        semaphore.withPermit {
                            animeInSource.forEach { libraryAnime ->
                                val anime = libraryAnime.anime
                                ensureActive()

                                // Cooperative pause/cancel check
                                if (LibraryUpdateProgressBus.isCancelRequested()) {
                                    throw CancellationException("User cancelled library update")
                                }
                                if (LibraryUpdateProgressBus.isPauseRequested()) {
                                    throw CancellationException("User paused library update")
                                }

                                // Don't continue to update if anime is not in library
                                if (anime.parentId == null && getAnime.await(anime.id)?.favorite != true) {
                                    return@forEach
                                }

                                // Skip entries already processed in a prior (paused) run
                                if (anime.id in LibraryUpdateProgressBus.checkpoint) {
                                    progressCount.incrementAndGet()
                                    return@forEach
                                }

                                val publishState: () -> Unit = {
                                    LibraryUpdateProgressBus.updateProgress(
                                        processed = progressCount.get(),
                                        currentlyUpdating = currentlyUpdatingAnime.map {
                                            eu.kanade.tachiyomi.data.library.EntryRef(
                                                id = it.id,
                                                title = it.title,
                                                sourceId = it.source,
                                                kind = tachiyomi.domain.library.model.EntryKind.ANIME,
                                            )
                                        },
                                        failedSoFar = failedUpdates.map { (a, reason) ->
                                            eu.kanade.tachiyomi.data.library.FailedEntry(
                                                entry = eu.kanade.tachiyomi.data.library.EntryRef(
                                                    id = a.id,
                                                    title = a.title,
                                                    sourceId = a.source,
                                                    kind = tachiyomi.domain.library.model.EntryKind.ANIME,
                                                ),
                                                reason = reason ?: context.stringResource(MR.strings.unknown_error),
                                                sourceName = sourceManager.getOrStub(a.source).name,
                                            )
                                        },
                                        totalEntries = animeToUpdate.size,
                                        source = "Anime",
                                    )
                                }

                                withUpdateNotification(
                                    currentlyUpdatingAnime,
                                    progressCount,
                                    anime,
                                    publishState,
                                ) {
                                    try {
                                        val newEpisodes = updateAnime(anime, fetchWindow)
                                            .sortedByDescending { it.sourceOrder }

                                        if (newEpisodes.isNotEmpty()) {
                                            val episodesToDownload = filterEpisodesForDownload.await(anime, newEpisodes)

                                            if (episodesToDownload.isNotEmpty()) {
                                                hasDownloads.set(true)
                                            }

                                            libraryPreferences.newAnimeUpdatesCount()
                                                .getAndSet { it + newEpisodes.size }

                                            // Convert to the anime that contains new episodes
                                            newUpdates.add(anime to newEpisodes.toTypedArray())
                                        }
                                    } catch (e: Throwable) {
                                        val errorMessage = when (e) {
                                            is NoEpisodesException -> context.stringResource(
                                                AYMR.strings.no_episodes_error,
                                            )
                                            // failedUpdates will already have the source, don't need to copy it into the message
                                            is AnimeSourceNotInstalledException -> context.stringResource(
                                                MR.strings.loader_not_implemented_error,
                                            )
                                            else -> e.message
                                        }
                                        failedUpdates.add(anime to errorMessage)
                                    }
                                }

                                // Mark processed for resume checkpoint
                                LibraryUpdateProgressBus.markProcessed(anime.id)
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateNotifications(newUpdates)
            if (hasDownloads.get()) {
                downloadManager.startDownloads()
            }
        }

        // Publish completion + persist failures to the FailedFetch table
        val failedEntries = failedUpdates.map { (a, reason) ->
            eu.kanade.tachiyomi.data.library.FailedEntry(
                entry = eu.kanade.tachiyomi.data.library.EntryRef(
                    id = a.id,
                    title = a.title,
                    sourceId = a.source,
                    kind = tachiyomi.domain.library.model.EntryKind.ANIME,
                ),
                reason = reason ?: context.stringResource(MR.strings.unknown_error),
                sourceName = sourceManager.getOrStub(a.source).name,
            )
        }
        LibraryUpdateProgressBus.completeRun(failed = failedEntries, source = "Anime")
        if (failedEntries.isNotEmpty()) {
            FailedFetchStore.insert(failedEntries)
            val errorFile = writeErrorFile(failedUpdates)
            notifier.showUpdateErrorNotification(
                failedUpdates.size,
                errorFile.getUriCompat(context),
            )
        }
    }

    private fun downloadEpisodes(anime: Anime, episodes: List<Episode>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadEpisodes(anime, episodes, false)
    }

    /**
     * Updates the episodes for the given anime and adds them to the database.
     *
     * @param anime the anime to update.
     * @return a pair of the inserted and removed episodes.
     */
    private suspend fun updateAnime(anime: Anime, fetchWindow: Pair<Long, Long>): List<Episode> {
        val source = sourceManager.getOrStub(anime.source)

        // Update anime metadata if needed
        if (libraryPreferences.autoUpdateMetadata().get()) {
            val networkAnime = source.getAnimeDetails(anime.toSAnime())
            updateAnime.awaitUpdateFromSource(anime, networkAnime, manualFetch = false, coverCache, backgroundCache)
        }

        val episodes = source.getEpisodeList(anime.toSAnime())

        // Get anime from database to account for if it was removed during the update and
        // to get latest data so it doesn't get overwritten later on
        val dbAnime = getAnime.await(anime.id)?.takeIf { it.parentId != null || it.favorite } ?: return emptyList()

        return syncEpisodesWithSource.await(episodes, dbAnime, source, false, fetchWindow)
    }

    private suspend fun withUpdateNotification(
        updatingAnime: CopyOnWriteArrayList<Anime>,
        completed: AtomicInteger,
        anime: Anime,
        publishState: () -> Unit,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingAnime.add(anime)
        notifier.showProgressNotification(
            updatingAnime,
            completed.get(),
            animeToUpdate.size,
        )
        // Publish to the in-app overlay immediately so the entry appears as
        // "currently updating" while it's being fetched — not only after it
        // completes. Without this, parallel entries that start during a slow
        // fetch are invisible to the overlay until one of them finishes.
        publishState()

        block()

        ensureActive()

        updatingAnime.remove(anime)
        completed.getAndIncrement()
        notifier.showProgressNotification(
            updatingAnime,
            completed.get(),
            animeToUpdate.size,
        )
        publishState()
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(errors: List<Pair<Anime, String?>>): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("aniyomi_update_errors.txt")
                file.bufferedWriter().use { out ->
                    out.write(
                        context.stringResource(MR.strings.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n",
                    )
                    // Error file format:
                    // ! Error
                    //   # Source
                    //     - Anime
                    errors.groupBy({ it.second }, { it.first }).forEach { (error, animes) ->
                        out.write("\n! ${error}\n")
                        animes.groupBy { it.source }.forEach { (srcId, animes) ->
                            val source = sourceManager.getOrStub(srcId)
                            out.write("  # $source\n")
                            animes.forEach {
                                out.write("    - ${it.title}\n")
                            }
                        }
                    }
                }
                return file
            }
        } catch (_: Exception) {}
        return File("")
    }

    companion object {
        private const val TAG = "AnimeLibraryUpdate"
        private const val WORK_NAME_AUTO = "AnimeLibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "AnimeLibraryUpdate-manual"

        private const val ERROR_LOG_HELP_URL = "https://aniyomi.org/docs/guides/troubleshooting/"

        private const val ANIME_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        /**
         * Key for collection to update.
         */
        private const val KEY_COLLECTION = "animeCollection"
        private const val KEY_RESUME = "animeResume"

        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }

        fun setupTask(
            context: Context,
            prefInterval: Int? = null,
        ) {
            val preferences = Injekt.get<LibraryPreferences>()
            val interval = prefInterval ?: preferences.autoUpdateInterval().get()
            if (interval > 0) {
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                val networkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }
                val networkRequestBuilder = NetworkRequest.Builder()
                if (DEVICE_ONLY_ON_WIFI in restrictions) {
                    networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                }
                if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
                val constraints = Constraints.Builder()
                    // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
                    .setRequiredNetworkRequest(networkRequestBuilder.build(), networkType)
                    .setRequiresCharging(DEVICE_CHARGING in restrictions)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<AnimeLibraryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }
        fun startNow(
            context: Context,
            collection: Collection? = null,
            resumeFromCheckpoint: Boolean = false,
        ): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }

            val inputData = workDataOf(
                KEY_COLLECTION to collection?.id,
                KEY_RESUME to resumeFromCheckpoint,
            )
            val request = OneTimeWorkRequestBuilder<AnimeLibraryUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .setInputData(inputData)
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)

                    // Re-enqueue cancelled scheduled work
                    if (it.tags.contains(WORK_NAME_AUTO)) {
                        setupTask(context)
                    }
                }
        }
    }
}
