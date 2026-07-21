package eu.kanade.tachiyomi.data.library.novel

import android.content.Context
import android.content.pm.ServiceInfo
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
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelUpdateStrategy
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
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.NovelFetchInterval
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapterUpdate
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NovelLibraryUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: NovelSourceManager = Injekt.get()
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val getLibraryNovels: GetLibraryNovels = Injekt.get()
    private val getNovel: GetNovel = Injekt.get()
    private val novelChapterRepository: NovelChapterRepository = Injekt.get()
    private val novelFetchInterval: NovelFetchInterval = Injekt.get()

    private var novelsToUpdate: List<LibraryNovel> = mutableListOf()

    override suspend fun doWork(): Result {
        if (tags.contains(WORK_NAME_AUTO)) {
            // Find a running manual worker. If exists, try again later
            if (context.workManager.isRunning(WORK_NAME_MANUAL)) {
                return Result.retry()
            }
        }

        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground job" }
        }

        libraryPreferences.lastUpdatedTimestamp().set(Instant.now().toEpochMilli())

        val categoryId = inputData.getLong(KEY_CATEGORY, -1L)
        addNovelsToQueue(categoryId)

        return withIOContext {
            try {
                updateChapterList()
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifier = NovelLibraryUpdateNotifier(context)
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
     * Adds list of novels to be updated.
     *
     * @param categoryId the ID of the category to update, or -1 if no category specified.
     */
    private suspend fun addNovelsToQueue(categoryId: Long) {
        val libraryNovels = getLibraryNovels.await()

        val listToUpdate = if (categoryId != -1L) {
            libraryNovels.filter { it.category == categoryId }
        } else {
            val categoriesToUpdate = libraryPreferences.novelUpdateCategories().get().map { it.toLong() }
            val includedNovels = if (categoriesToUpdate.isNotEmpty()) {
                libraryNovels.filter { it.category in categoriesToUpdate }
            } else {
                libraryNovels
            }

            val categoriesToExclude = libraryPreferences.novelUpdateCategoriesExclude().get().map { it.toLong() }
            val excludedNovelIds = if (categoriesToExclude.isNotEmpty()) {
                libraryNovels.filter { it.category in categoriesToExclude }.map { it.novel.id }
            } else {
                emptyList()
            }

            includedNovels
                .filterNot { it.novel.id in excludedNovelIds }
                .distinctBy { it.novel.id }
        }

        val restrictions = libraryPreferences.autoUpdateItemRestrictions().get()
        val skippedUpdates = mutableListOf<Pair<Novel, String?>>()

        novelsToUpdate = listToUpdate
            .filter {
                when {
                    it.novel.updateStrategy != NovelUpdateStrategy.ALWAYS_UPDATE -> {
                        skippedUpdates.add(
                            it.novel to context.stringResource(MR.strings.skipped_reason_not_always_update),
                        )
                        false
                    }

                    ENTRY_HAS_UNVIEWED in restrictions && it.unreadCount != 0L -> {
                        skippedUpdates.add(
                            it.novel to context.stringResource(MR.strings.skipped_reason_not_caught_up),
                        )
                        false
                    }

                    ENTRY_NON_VIEWED in restrictions && it.totalChapters > 0L && !it.hasStarted -> {
                        skippedUpdates.add(
                            it.novel to context.stringResource(MR.strings.skipped_reason_not_started),
                        )
                        false
                    }

                    else -> true
                }
            }
            .sortedBy { it.novel.title }

        if (skippedUpdates.isNotEmpty()) {
            logcat {
                skippedUpdates
                    .groupBy { it.second }
                    .map { (reason, entries) -> "$reason: [${entries.map { it.first.title }.sorted().joinToString()}]" }
                    .joinToString()
            }
        }
    }

    /**
     * Method that updates novels in [novelsToUpdate]. It's called in a background thread, so it's safe
     * to do heavy operations or network calls here.
     */
    private suspend fun updateChapterList() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingNovels = CopyOnWriteArrayList<Novel>()
        val newUpdates = CopyOnWriteArrayList<Pair<Novel, List<NovelChapter>>>()
        val failedUpdates = CopyOnWriteArrayList<Pair<Novel, String?>>()

        coroutineScope {
            novelsToUpdate.groupBy { it.novel.source }.values
                .map { novelsInSource ->
                    async {
                        semaphore.withPermit {
                            novelsInSource.forEach { libraryNovel ->
                                val novel = libraryNovel.novel
                                ensureActive()

                                // Don't continue to update if novel is not in library
                                if (getNovel.await(novel.id)?.favorite != true) {
                                    return@forEach
                                }

                                withUpdateNotification(
                                    currentlyUpdatingNovels,
                                    progressCount,
                                    novel,
                                ) {
                                    try {
                                        val newChapters = updateNovel(novel)
                                            .sortedByDescending { it.sourceOrder }

                                        if (newChapters.isNotEmpty()) {
                                            libraryPreferences.newNovelUpdatesCount()
                                                .getAndSet { it + newChapters.size }
                                            newUpdates.add(novel to newChapters)
                                        }
                                    } catch (e: Throwable) {
                                        val errorMessage = e.message
                                        failedUpdates.add(novel to errorMessage)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        val notifier = NovelLibraryUpdateNotifier(context)
        notifier.cancelProgressNotification()

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateNotifications(newUpdates)
        }

        if (failedUpdates.isNotEmpty()) {
            logcat(LogPriority.ERROR) {
                "Failed updates: ${failedUpdates.joinToString { "${it.first.title}: ${it.second}" }}"
            }
        }
    }

    /**
     * Updates the chapters for the given novel and adds them to the database.
     *
     * @param novel the novel to update.
     * @return a list of newly added chapters.
     */
    private suspend fun updateNovel(novel: Novel): List<NovelChapter> {
        val source = sourceManager.getOrStub(novel.source) as? NovelCatalogueSource
            ?: return emptyList()

        // Update novel metadata if needed
        if (libraryPreferences.autoUpdateMetadata().get()) {
            try {
                val networkNovel = source.getNovelDetails(novel.toSNovel())
                val title = if (networkNovel.title.isEmpty() || novel.favorite) null else networkNovel.title
                val author = if (novel.favorite) null else networkNovel.author
                val artist = if (novel.favorite) null else networkNovel.artist
                val description = if (novel.favorite) null else networkNovel.description
                val genres = if (novel.favorite) null else networkNovel.getGenres()
                val thumbnailUrl = if (novel.favorite) null else networkNovel.thumbnail_url
                val status = if (novel.favorite) null else networkNovel.status.toLong()

                val update = NovelUpdate(
                    id = novel.id,
                    title = title,
                    author = author,
                    artist = artist,
                    description = description,
                    genre = genres,
                    thumbnailUrl = thumbnailUrl,
                    status = status,
                )
                val novelRepository = Injekt.get<tachiyomi.domain.entries.novel.repository.NovelRepository>()
                novelRepository.updateNovel(update)
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Metadata update failed for novel ${novel.id}" }
            }
        }

        val remoteChapters = source.getChapterList(novel.toSNovel())

        // Get novel from database to account for if it was removed during the update
        val dbNovel = getNovel.await(novel.id)?.takeIf { it.favorite } ?: return emptyList()

        val localChapters = novelChapterRepository.getNovelChaptersByNovelId(dbNovel.id)

        val newChapters = remoteChapters.mapIndexed { i, sNovelChapter ->
            NovelChapter.create().copy(
                novelId = dbNovel.id,
                url = sNovelChapter.url,
                name = sNovelChapter.name,
                chapterNumber = sNovelChapter.chapter_number.toDouble(),
                sourceOrder = i.toLong(),
                dateFetch = sNovelChapter.date_upload,
                dateUpload = sNovelChapter.date_upload,
            )
        }

        // Merge remote with local (match by URL)
        val localMap = localChapters.associateBy { it.url }
        val mergedChapters = newChapters.map { remote ->
            val local = localMap[remote.url]
            if (local != null) {
                local.copyFrom(remote)
            } else {
                remote
            }
        }

        val toAdd = mergedChapters.filter { it.id == -1L }
        val toUpdate = mergedChapters
            .filter { it.id != -1L }
            .filter { mc -> localChapters.any { it.id == mc.id && it != mc } }

        if (toAdd.isNotEmpty()) {
            novelChapterRepository.addAllNovelChapters(toAdd)
        }

        if (toUpdate.isNotEmpty()) {
            val updateInteractor = Injekt.get<tachiyomi.domain.items.chapter.interactor.UpdateNovelChapter>()
            updateInteractor.awaitAll(
                toUpdate.map {
                    NovelChapterUpdate(
                        id = it.id,
                        name = it.name,
                        url = it.url,
                        chapterNumber = it.chapterNumber,
                        dateFetch = it.dateFetch,
                        dateUpload = it.dateUpload,
                        sourceOrder = it.sourceOrder,
                    )
                },
            )
        }

        // Update novel's lastUpdate and nextUpdate timestamps
        val now = Instant.now().toEpochMilli()
        val interval = novelFetchInterval.calculateInterval(newChapters)
        val nextUpdate = if (interval != null && interval > 0) {
            now + (interval.toLong() * 24 * 60 * 60 * 1000)
        } else {
            now + 7 * 24 * 60 * 60 * 1000 // default 7 days
        }
        val novelRepository = Injekt.get<tachiyomi.domain.entries.novel.repository.NovelRepository>()
        novelRepository.updateNovel(
            NovelUpdate(
                id = dbNovel.id,
                lastUpdate = now,
                nextUpdate = nextUpdate,
            ),
        )

        return toAdd
    }

    private suspend fun withUpdateNotification(
        updatingNovels: CopyOnWriteArrayList<Novel>,
        progressCount: AtomicInteger,
        novel: Novel,
        block: suspend () -> Unit,
    ) {
        updatingNovels.add(novel)
        val notifier = NovelLibraryUpdateNotifier(context)
        notifier.showProgressNotification(
            updatingNovels,
            progressCount.incrementAndGet(),
            novelsToUpdate.size,
        )
        try {
            block()
        } finally {
            updatingNovels.remove(novel)
        }
    }

    companion object {
        private const val TAG = "NovelLibraryUpdateJob"
        private const val WORK_NAME_MANUAL = "NovelLibraryUpdate-$TAG-manual"
        private const val WORK_NAME_AUTO = "NovelLibraryUpdate-$TAG-auto"
        private const val KEY_CATEGORY = "category"

        fun startNow(context: Context, category: Category? = null): Boolean {
            val wm = androidx.work.WorkManager.getInstance(context)
            val workQuery = androidx.work.WorkQuery.Builder
                .fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            if (wm.getWorkInfos(workQuery).get().isNotEmpty()) {
                return false
            }

            val request = androidx.work.OneTimeWorkRequestBuilder<NovelLibraryUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .setInputData(
                    workDataOf(
                        KEY_CATEGORY to (category?.id ?: -1L),
                    ),
                )
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        fun setupTask(context: Context, interval: Int = 0) {
            val preferences = Injekt.get<LibraryPreferences>()
            val autoUpdateInterval = if (interval == 0) preferences.autoUpdateInterval().get() else interval
            if (autoUpdateInterval == 0) {
                androidx.work.WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_AUTO)
                return
            }

            val restrictions = preferences.autoUpdateDeviceRestrictions().get()
            val networkType = when {
                LibraryPreferences.DEVICE_ONLY_ON_WIFI in restrictions -> NetworkType.UNMETERED
                LibraryPreferences.DEVICE_NETWORK_NOT_METERED in restrictions -> NetworkType.UNMETERED
                else -> NetworkType.CONNECTED
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<NovelLibraryUpdateJob>(
                autoUpdateInterval.toLong(),
                TimeUnit.HOURS,
                6,
                TimeUnit.HOURS,
            )
                .addTag(TAG)
                .addTag(WORK_NAME_AUTO)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_AUTO,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
