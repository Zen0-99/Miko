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
import eu.kanade.tachiyomi.data.library.FailedFetchStore
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelUpdateStrategy
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.maybeShowDnsToast
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.model.Collection
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
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.ZonedDateTime
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

        val collectionId = inputData.getLong(KEY_COLLECTION, -1L)
        addNovelsToQueue(collectionId)

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
     * @param collectionId the ID of the collection to update, or -1 if no collection specified.
     */
    private suspend fun addNovelsToQueue(collectionId: Long) {
        val libraryNovels = getLibraryNovels.await()
        android.util.Log.i("NovelUpdateJob", "addNovelsToQueue: ${libraryNovels.size} library novels, collectionId=$collectionId")

        val listToUpdate = if (collectionId != -1L) {
            libraryNovels.filter { it.collection == collectionId }
        } else {
            val collectionsToUpdate = libraryPreferences.novelUpdateCollections().get().map { it.toLong() }
            val includedNovels = if (collectionsToUpdate.isNotEmpty()) {
                libraryNovels.filter { it.collection in collectionsToUpdate }
            } else {
                libraryNovels
            }

            val collectionsToExclude = libraryPreferences.novelUpdateCollectionsExclude().get().map { it.toLong() }
            val excludedNovelIds = if (collectionsToExclude.isNotEmpty()) {
                libraryNovels.filter { it.collection in collectionsToExclude }.map { it.novel.id }
            } else {
                emptyList()
            }

            android.util.Log.i("NovelUpdateJob", "  Collections to update: $collectionsToUpdate")
            android.util.Log.i("NovelUpdateJob", "  Collections to exclude: $collectionsToExclude")
            android.util.Log.i("NovelUpdateJob", "  After collection filter: ${includedNovels.size} included, ${excludedNovelIds.size} excluded")

            includedNovels
                .filterNot { it.novel.id in excludedNovelIds }
                .distinctBy { it.novel.id }
        }

        val restrictions = libraryPreferences.autoUpdateItemRestrictions().get()
        val skippedUpdates = mutableListOf<Pair<Novel, String?>>()
        // Compute fetch window for ENTRY_OUTSIDE_RELEASE_PERIOD check
        val now = ZonedDateTime.now()
        val today = now.toLocalDate().atStartOfDay(now.zone)
        val fetchWindowUpperBound = today.plusDays(7).toEpochSecond() * 1000 - 1

        android.util.Log.i("NovelUpdateJob", "  Restrictions: $restrictions")
        android.util.Log.i("NovelUpdateJob", "  Fetch window upper bound: $fetchWindowUpperBound")

        novelsToUpdate = listToUpdate
            .filter {
                when {
                    it.novel.updateStrategy != NovelUpdateStrategy.ALWAYS_UPDATE -> {
                        android.util.Log.i("NovelUpdateJob", "  SKIP '${it.novel.title}': updateStrategy=${it.novel.updateStrategy}")
                        skippedUpdates.add(
                            it.novel to context.stringResource(MR.strings.skipped_reason_not_always_update),
                        )
                        false
                    }

                    ENTRY_NON_COMPLETED in restrictions && it.novel.status == SNovel.COMPLETED.toLong() -> {
                        android.util.Log.i("NovelUpdateJob", "  SKIP '${it.novel.title}': COMPLETED")
                        skippedUpdates.add(
                            it.novel to context.stringResource(MR.strings.skipped_reason_completed),
                        )
                        false
                    }

                    ENTRY_HAS_UNVIEWED in restrictions && it.unreadCount != 0L -> {
                        android.util.Log.i("NovelUpdateJob", "  SKIP '${it.novel.title}': has unviewed (unreadCount=${it.unreadCount})")
                        skippedUpdates.add(
                            it.novel to context.stringResource(MR.strings.skipped_reason_not_caught_up),
                        )
                        false
                    }

                    ENTRY_NON_VIEWED in restrictions && it.totalChapters > 0L && !it.hasStarted -> {
                        android.util.Log.i("NovelUpdateJob", "  SKIP '${it.novel.title}': not started (totalChapters=${it.totalChapters})")
                        skippedUpdates.add(
                            it.novel to context.stringResource(MR.strings.skipped_reason_not_started),
                        )
                        false
                    }

                    ENTRY_OUTSIDE_RELEASE_PERIOD in restrictions && it.novel.nextUpdate > fetchWindowUpperBound -> {
                        android.util.Log.i("NovelUpdateJob", "  SKIP '${it.novel.title}': outside release period (nextUpdate=${it.novel.nextUpdate})")
                        skippedUpdates.add(
                            it.novel to context.stringResource(MR.strings.skipped_reason_not_in_release_period),
                        )
                        false
                    }

                    else -> {
                        android.util.Log.i("NovelUpdateJob", "  INCLUDE '${it.novel.title}': status=${it.novel.status}, unreadCount=${it.unreadCount}, hasStarted=${it.hasStarted}")
                        true
                    }
                }
            }
            .sortedBy { it.novel.title }

        android.util.Log.i("NovelUpdateJob", "  Final: ${novelsToUpdate.size} novels to update out of ${libraryNovels.size} library novels")

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

        // Publish initial running state to the in-app progress bus
        LibraryUpdateProgressBus.startRun(total = novelsToUpdate.size, source = "Novel")

        try {
            coroutineScope {
                novelsToUpdate.groupBy { it.novel.source }.values
                    .map { novelsInSource ->
                        async {
                            semaphore.withPermit {
                                novelsInSource.forEach { libraryNovel ->
                                    val novel = libraryNovel.novel
                                    ensureActive()

                                    // Cooperative cancel check
                                    if (LibraryUpdateProgressBus.isCancelRequested("Novel")) {
                                        throw CancellationException("User cancelled library update")
                                    }

                                    // Don't continue to update if novel is not in library
                                    if (getNovel.await(novel.id)?.favorite != true) {
                                        return@forEach
                                    }

                                    val publishState: () -> Unit = {
                                        LibraryUpdateProgressBus.updateProgress(
                                            processed = progressCount.get(),
                                            currentlyUpdating = currentlyUpdatingNovels.map {
                                                eu.kanade.tachiyomi.data.library.EntryRef(
                                                    id = it.id,
                                                    title = it.title,
                                                    sourceId = it.source,
                                                    kind = tachiyomi.domain.library.model.EntryKind.NOVEL,
                                                )
                                            },
                                            failedSoFar = failedUpdates.map { (n, reason) ->
                                                eu.kanade.tachiyomi.data.library.FailedEntry(
                                                    entry = eu.kanade.tachiyomi.data.library.EntryRef(
                                                        id = n.id,
                                                        title = n.title,
                                                        sourceId = n.source,
                                                        kind = tachiyomi.domain.library.model.EntryKind.NOVEL,
                                                    ),
                                                    reason = reason ?: context.stringResource(MR.strings.unknown_error),
                                                    sourceName = sourceManager.getOrStub(n.source).name,
                                                )
                                            },
                                            totalEntries = novelsToUpdate.size,
                                            source = "Novel",
                                        )
                                    }

                                    withUpdateNotification(
                                        currentlyUpdatingNovels,
                                        progressCount,
                                        novel,
                                        publishState,
                                    ) {
                                        try {
                                            // 1-minute hard timeout per novel — uses
                                            // CompletableDeferred race so blocking I/O
                                            // can't overrun the timeout.
                                            val newChapters = hardTimeoutNovel(PER_NOVEL_TIMEOUT_MS) {
                                                updateNovel(novel)
                                            }?.sortedByDescending { it.sourceOrder }

                                            if (newChapters != null && newChapters.isNotEmpty()) {
                                                libraryPreferences.newNovelUpdatesCount()
                                                    .getAndSet { it + newChapters.size }
                                                newUpdates.add(novel to newChapters)
                                            } else if (newChapters == null) {
                                                // Timed out
                                                failedUpdates.add(
                                                    novel to "Timeout after ${PER_NOVEL_TIMEOUT_MS / 1000}s",
                                                )
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
        } finally {
            // Ensure completeRun is ALWAYS called, even if the coroutineScope
            // throws (e.g., due to cancellation or an uncaught exception).
            // Without this, the overlay would stay stuck at "X/Y" forever.
            val notifier = NovelLibraryUpdateNotifier(context)
            notifier.cancelProgressNotification()

            if (newUpdates.isNotEmpty()) {
                notifier.showUpdateNotifications(newUpdates)
            }

            // Publish completion + persist failures to the FailedFetch table
            val failedEntries = failedUpdates.map { (n, reason) ->
                eu.kanade.tachiyomi.data.library.FailedEntry(
                    entry = eu.kanade.tachiyomi.data.library.EntryRef(
                        id = n.id,
                        title = n.title,
                        sourceId = n.source,
                        kind = tachiyomi.domain.library.model.EntryKind.NOVEL,
                    ),
                    reason = reason ?: context.stringResource(MR.strings.unknown_error),
                    sourceName = sourceManager.getOrStub(n.source).name,
                )
            }
            LibraryUpdateProgressBus.completeRun(failed = failedEntries, source = "Novel")
            if (failedEntries.isNotEmpty()) {
                FailedFetchStore.insert(failedEntries)
                logcat(LogPriority.ERROR) {
                    "Failed updates: ${failedUpdates.joinToString { "${it.first.title}: ${it.second}" }}"
                }
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

        // Cover art checker: check if the cover cache file is missing.
        // This runs regardless of autoUpdateMetadata because missing covers
        // (e.g., after a backup restore) should always be fetched.
        val coverCache = Injekt.get<eu.kanade.tachiyomi.data.cache.NovelCoverCache>()
        val coverFileExists = coverCache.getCoverFile(novel.thumbnailUrl)?.exists() == true
        val hasLocalThumbnailUrl = !novel.thumbnailUrl.isNullOrEmpty()
        android.util.Log.d("NovelCoverCheck", "novel=${novel.title} id=${novel.id} thumbnailUrl=${novel.thumbnailUrl} coverFileExists=$coverFileExists hasLocalThumbnailUrl=$hasLocalThumbnailUrl autoUpdateMetadata=${libraryPreferences.autoUpdateMetadata().get()}")

        // Update novel metadata if needed
        if (libraryPreferences.autoUpdateMetadata().get()) {
            try {
                val networkNovel = source.getNovelDetails(novel.toSNovel())
                val title = if (networkNovel.title.isEmpty() || novel.favorite) null else networkNovel.title
                val author = if (novel.favorite) null else networkNovel.author
                val artist = if (novel.favorite) null else networkNovel.artist
                val description = if (novel.favorite) null else networkNovel.description
                val genres = if (novel.favorite) null else networkNovel.getGenres()
                // For favorited novels, only update thumbnailUrl if cover is missing.
                // Use remote thumbnail_url if available, otherwise keep local.
                val hasRemoteThumbnailUrl = !networkNovel.thumbnail_url.isNullOrEmpty()
                val thumbnailUrl = if (novel.favorite) {
                    if (!coverFileExists) {
                        if (hasRemoteThumbnailUrl) networkNovel.thumbnail_url
                        else if (hasLocalThumbnailUrl) null // keep local, just force cover refresh
                        else null
                    } else {
                        null // cover exists, don't overwrite
                    }
                } else {
                    networkNovel.thumbnail_url
                }
                // Force coverLastModified update when cover is missing and we
                // have any thumbnailUrl (local or remote)
                val coverLastModified = if (!coverFileExists && (hasLocalThumbnailUrl || hasRemoteThumbnailUrl)) {
                    coverCache.deleteFromCache(novel, false)
                    java.time.Instant.now().toEpochMilli()
                } else {
                    null
                }
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
                    coverLastModified = coverLastModified,
                )
                val novelRepository = Injekt.get<tachiyomi.domain.entries.novel.repository.NovelRepository>()
                novelRepository.updateNovel(update)
            } catch (e: Throwable) {
                android.util.Log.e("NovelCoverCheck", "getNovelDetails FAILED for novel=${novel.title}: ${e::class.simpleName}: ${e.message}")
                logcat(LogPriority.ERROR, e) { "Metadata update failed for novel ${novel.id}" }
                context.maybeShowDnsToast(e, novel.thumbnailUrl)
                // getNovelDetails failed (e.g. Anna's Archive may not support it).
                // Still try to fix missing covers using the local thumbnailUrl.
                if (!coverFileExists && hasLocalThumbnailUrl) {
                    try {
                        coverCache.deleteFromCache(novel, false)
                        val novelRepository = Injekt.get<tachiyomi.domain.entries.novel.repository.NovelRepository>()
                        novelRepository.updateNovel(
                            NovelUpdate(
                                id = novel.id,
                                coverLastModified = java.time.Instant.now().toEpochMilli(),
                            ),
                        )
                        android.util.Log.d("NovelCoverCheck", "Cover refresh triggered (catch block) for novel=${novel.title}")
                    } catch (e2: Throwable) {
                        android.util.Log.e("NovelCoverCheck", "Cover refresh FAILED for novel=${novel.title}: ${e2::class.simpleName}: ${e2.message}")
                        logcat(LogPriority.ERROR, e2) { "Cover refresh failed for novel ${novel.id}" }
                    }
                }
            }
        } else if (!coverFileExists && hasLocalThumbnailUrl) {
            // autoUpdateMetadata is off, but cover is missing. The stored
            // thumbnailUrl may be stale. Call getNovelDetails to get a fresh
            // URL (some sources change CDN hosts), then update both
            // thumbnailUrl and coverLastModified — same as the detail view.
            android.util.Log.d("NovelCoverCheck", "autoUpdateMetadata off, fetching fresh details for cover for novel=${novel.title}")
            try {
                val networkNovel = source.getNovelDetails(novel.toSNovel())
                val updateNovelInteractor = Injekt.get<eu.kanade.domain.entries.novel.interactor.UpdateNovel>()
                updateNovelInteractor.awaitUpdateFromSource(
                    localNovel = novel,
                    remoteTitle = networkNovel.title,
                    remoteAuthor = networkNovel.author,
                    remoteArtist = networkNovel.artist,
                    remoteDescription = networkNovel.description,
                    remoteGenre = networkNovel.getGenres(),
                    remoteThumbnailUrl = networkNovel.thumbnail_url,
                    remoteStatus = networkNovel.status.toLong(),
                    remoteUpdateStrategy = networkNovel.update_strategy,
                    manualFetch = false,
                )
                android.util.Log.d("NovelCoverCheck", "Cover refresh via getNovelDetails for novel=${novel.title}")
            } catch (e: Throwable) {
                // getNovelDetails failed — fall back to just updating
                // coverLastModified with the existing URL
                android.util.Log.e("NovelCoverCheck", "getNovelDetails failed for cover refresh, falling back: novel=${novel.title}: ${e::class.simpleName}: ${e.message}")
                context.maybeShowDnsToast(e, novel.thumbnailUrl)
                try {
                    coverCache.deleteFromCache(novel, false)
                    val novelRepository = Injekt.get<tachiyomi.domain.entries.novel.repository.NovelRepository>()
                    novelRepository.updateNovel(
                        NovelUpdate(
                            id = novel.id,
                            coverLastModified = java.time.Instant.now().toEpochMilli(),
                        ),
                    )
                } catch (e2: Throwable) {
                    android.util.Log.e("NovelCoverCheck", "Cover refresh fallback FAILED for novel=${novel.title}: ${e2::class.simpleName}: ${e2.message}")
                }
            }
        }

        // Cooperative cancel check before the heavy network call
        if (LibraryUpdateProgressBus.isCancelRequested("Novel")) {
            throw CancellationException("User cancelled library update")
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
        publishState: () -> Unit,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingNovels.add(novel)
        val notifier = NovelLibraryUpdateNotifier(context)
        notifier.showProgressNotification(
            updatingNovels,
            progressCount.get(),
            novelsToUpdate.size,
        )
        // Publish to the in-app overlay immediately so the entry appears as
        // "currently updating" while it's being fetched — not only after it
        // completes. Without this, parallel entries that start during a slow
        // fetch are invisible to the overlay until one of them finishes.
        publishState()

        block()

        ensureActive()

        updatingNovels.remove(novel)
        progressCount.getAndIncrement()
        notifier.showProgressNotification(
            updatingNovels,
            progressCount.get(),
            novelsToUpdate.size,
        )
        publishState()
    }

    companion object {
        private const val TAG = "NovelLibraryUpdateJob"
        private const val WORK_NAME_MANUAL = "NovelLibraryUpdate-$TAG-manual"
        private const val WORK_NAME_AUTO = "NovelLibraryUpdate-$TAG-auto"
        private const val KEY_COLLECTION = "collection"
        // 1-minute hard timeout per novel fetch
        private const val PER_NOVEL_TIMEOUT_MS = 60_000L

        /**
         * Hard timeout that works even with blocking I/O. The blocking I/O runs
         * in a fire-and-forget coroutine; we race `await()` against
         * `withTimeoutOrNull`. When the timeout fires, we return null and the
         * background I/O is discarded.
         */
        private suspend fun <T> hardTimeoutNovel(timeoutMs: Long, block: suspend () -> T): T? {
            val result = CompletableDeferred<T?>()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    result.complete(block())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    result.complete(null)
                }
            }
            return withTimeoutOrNull(timeoutMs) {
                result.await()
            }
        }

        fun startNow(
            context: Context,
            collection: Collection? = null,
        ): Boolean {
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
                        KEY_COLLECTION to (collection?.id ?: -1L),
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

        /**
         * Stops a running novel library update job by cancelling the WorkManager
         * work. This interrupts the coroutine, which triggers the try/finally
         * in [updateChapterList] to call [LibraryUpdateProgressBus.completeRun].
         */
        fun stop(context: Context) {
            val wm = androidx.work.WorkManager.getInstance(context)
            val workQuery = androidx.work.WorkQuery.Builder
                .fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                .forEach {
                    wm.cancelWorkById(it.id)
                    if (it.tags.contains(WORK_NAME_AUTO)) {
                        setupTask(context)
                    }
                }
        }
    }
}

