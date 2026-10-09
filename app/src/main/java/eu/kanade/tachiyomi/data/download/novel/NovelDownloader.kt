package eu.kanade.tachiyomi.data.download.novel

import android.content.Context
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelDownloader(
    private val context: Context,
    private val provider: NovelDownloadProvider,
    private val cache: NovelDownloadCache,
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val getCollections: GetNovelCollections = Injekt.get(),
    private val canonRouter: eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter = Injekt.get(),
) {

    private val store = NovelDownloadStore(context)

    private val _queueState = MutableStateFlow<List<NovelDownload>>(emptyList())
    val queueState = _queueState.asStateFlow()

    private val notifier by lazy { NovelDownloadNotifier(context) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloaderJob: Job? = null

    val isRunning: Boolean
        get() = downloaderJob?.isActive ?: false

    @Volatile
    var isPaused: Boolean = false

    init {
        launchIO {
            val chapters = async { store.restore() }
            addAllToQueue(chapters.await())
        }
    }

    fun start(): Boolean {
        if (isRunning || queueState.value.isEmpty()) {
            return false
        }

        val pending = queueState.value.filter { it.status != NovelDownload.State.DOWNLOADED }
        pending.forEach { if (it.status != NovelDownload.State.QUEUE) it.status = NovelDownload.State.QUEUE }

        isPaused = false

        launchDownloaderJob()

        return pending.isNotEmpty()
    }

    fun stop(reason: String? = null) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == NovelDownload.State.DOWNLOADING }
            .forEach { it.status = NovelDownload.State.ERROR }

        if (reason != null) {
            notifier.onWarning(reason)
            return
        }

        if (isPaused && queueState.value.isNotEmpty()) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        isPaused = false

        NovelDownloadJob.stop(context)
    }

    fun pause() {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == NovelDownload.State.DOWNLOADING }
            .forEach { it.status = NovelDownload.State.QUEUE }
        isPaused = true
    }

    fun clearQueue() {
        cancelDownloaderJob()
        internalClearQueue()
        notifier.dismissProgress()
    }

    private fun launchDownloaderJob() {
        if (isRunning) return

        downloaderJob = scope.launch {
            val activeDownloadsFlow = queueState.transformLatest { queue ->
                while (true) {
                    val activeDownloads = queue.asSequence()
                        .filter {
                            it.status.value <= NovelDownload.State.DOWNLOADING.value
                        }
                        .groupBy { it.source }
                        .toList().take(5)
                        .map { (_, downloads) -> downloads.first() }
                    emit(activeDownloads)

                    if (activeDownloads.isEmpty()) break
                    val activeDownloadsErroredFlow =
                        combine(activeDownloads.map(NovelDownload::statusFlow)) { states ->
                            states.contains(NovelDownload.State.ERROR)
                        }.filter { it }
                    activeDownloadsErroredFlow.first()
                }
            }.distinctUntilChanged()

            supervisorScope {
                val downloadJobs = mutableMapOf<NovelDownload, Job>()

                activeDownloadsFlow.collectLatest { activeDownloads ->
                    val downloadJobsToStop = downloadJobs.filter { it.key !in activeDownloads }
                    downloadJobsToStop.forEach { (download, job) ->
                        job.cancel()
                        downloadJobs.remove(download)
                    }

                    val downloadsToStart = activeDownloads.filter { it !in downloadJobs }
                    downloadsToStart.forEach { download ->
                        downloadJobs[download] = launchDownloadJob(download)
                    }
                }
            }
        }
    }

    private fun CoroutineScope.launchDownloadJob(download: NovelDownload) = launchIO {
        try {
            // Show progress notification when a download starts
            notifier.onProgressChange(download)

            downloadChapter(download)

            if (download.status == NovelDownload.State.DOWNLOADED) {
                removeFromQueue(download)
            }
            if (areAllDownloadsFinished()) {
                stop()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
            notifier.onError(e.message)
            stop()
        }
    }

    private fun cancelDownloaderJob() {
        downloaderJob?.cancel()
        downloaderJob = null
    }

    suspend fun queueChapters(
        novel: Novel,
        chapters: List<NovelChapter>,
        autoStart: Boolean,
        forcedCarrierPkg: String? = null,
    ) {
        if (chapters.isEmpty()) return

        // Canon chapters carry source id 0 (the local stub) — each one
        // routes to a carrier source via the chapter router instead.
        val source = sourceManager.get(novel.source) as? eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
        val wasEmpty = queueState.value.isEmpty()
        // (No asSequence — resolveDownload suspends, which Sequence
        //  lambdas can't.)
        val unrouted = mutableListOf<NovelChapter>()
        val chaptersToQueue = chapters
            .filter { chapter -> queueState.value.none { it.chapter.id == chapter.id } }
            .mapNotNull { ch -> resolveDownload(novel, ch, source, forcedCarrierPkg, unrouted) }
            .sortedByDescending { it.chapter.sourceOrder }

        // Canon chapters with no serving carrier would otherwise vanish
        // silently — the tap must explain itself.
        if (unrouted.isNotEmpty()) {
            notifier.onError(
                context.stringResource(MR.strings.download_notifier_no_carrier),
                novelTitle = novel.title,
                novelId = novel.id,
            )
        }

        if (chaptersToQueue.isNotEmpty()) {
            addAllToQueue(chaptersToQueue)

            if (autoStart && wasEmpty) {
                NovelDownloadJob.start(context)
            }
        }
    }

    /**
     * Maps a chapter to the [NovelDownload] it would create — canon rows
     * resolve to their serving carrier (preferred pick → ranking) and
     * carry the carrier's real chapter url so `getChapterText` works
     * unchanged. Null when already downloaded or unresolvable.
     */
    private suspend fun resolveDownload(
        novel: Novel,
        chapter: NovelChapter,
        novelSource: eu.kanade.tachiyomi.novelsource.NovelCatalogueSource?,
        forcedCarrierPkg: String?,
        unrouted: MutableList<NovelChapter>? = null,
    ): NovelDownload? {
        // Audio-scheme chapters (abs://track/…) are remote media, not text
        // — they must never enter the download queue (its getChapterText
        // call would throw or write a dead .txt). This guard runs before
        // every routing branch; an audio download path is the v1.5 note
        // (RESEARCH §4.5).
        if (eu.kanade.tachiyomi.source.novel.AudioChapterSchemes.isAudioChapterUrl(chapter.url)) {
            return null
        }
        if (eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter.isCanonUrl(chapter.url)) {
            val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
                .workIdFromNovelUrl(novel.url) ?: return null
            val seq = eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter
                .seqFromCanonUrl(workId, chapter.url) ?: return null
            val (carrier, candidate) = canonRouter
                .novelDownloadTarget(
                    workId,
                    seq,
                    forcedCarrierPkg,
                    chapter.chapterNumber.takeIf { it >= 0.0 },
                )
                ?: run {
                    unrouted?.add(chapter)
                    return null
                }
            if (provider.findChapterDir(chapter.name, chapter.scanlator, novel.title, carrier) != null) {
                return null
            }
            return NovelDownload(carrier, novel, chapter.copy(url = candidate.chapterUrl))
        }
        // `carrier:{id}:{url}` rows route to their recorded source — an
        // uninstalled carrier is unrouted, not silently queueable.
        if (eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter.isCarrierUrl(chapter.url)) {
            val carrierId = eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter
                .carrierSourceId(chapter.url) ?: return null
            val carrier = sourceManager.get(carrierId)
                as? eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
                ?: run {
                    unrouted?.add(chapter)
                    return null
                }
            if (provider.findChapterDir(chapter.name, chapter.scanlator, novel.title, carrier) != null) {
                return null
            }
            return NovelDownload(
                carrier,
                novel,
                chapter.copy(
                    url = eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter
                        .carrierRawUrl(chapter.url),
                ),
            )
        }
        if (novelSource == null) return null
        if (provider.findChapterDir(chapter.name, chapter.scanlator, novel.title, novelSource) != null) {
            return null
        }
        return NovelDownload(novelSource, novel, chapter)
    }

    private suspend fun downloadChapter(download: NovelDownload) {
        val novelDir = provider.getNovelDir(download.novel.title, download.source)

        val availSpace = DiskUtil.getAvailableStorageSpace(novelDir)
        if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
            download.status = NovelDownload.State.ERROR
            notifier.onError(
                "Insufficient space",
                download.chapter.name,
                download.novel.title,
                download.novel.id,
            )
            return
        }

        val chapterDirname = provider.getChapterDirName(download.chapter.name, download.chapter.scanlator)
        val tmpFile = novelDir.createFile(chapterDirname + TMP_DIR_SUFFIX)!!

        try {
            download.status = NovelDownload.State.DOWNLOADING

            val chapterText = withIOContext {
                download.source.getChapterText(download.chapter.toSNovelChapter())
            }

            if (chapterText.isBlank()) {
                throw Exception("Chapter text is empty")
            }

            download.text = chapterText

            tmpFile.openOutputStream().use { output ->
                output.write(chapterText.toByteArray(Charsets.UTF_8))
            }

            val finalFile = novelDir.findFile(chapterDirname)
            finalFile?.delete()
            tmpFile.renameTo(chapterDirname)

            // The file is in place — the download is complete. Bookkeeping
            // failures must not downgrade it to ERROR.
            download.status = NovelDownload.State.DOWNLOADED
            try {
                cache.addChapter(chapterDirname, novelDir, download.novel)
                DiskUtil.createNoMediaFile(novelDir, context)
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            logcat(LogPriority.ERROR, error)
            download.status = NovelDownload.State.ERROR
            notifier.onError(error.message, download.chapter.name, download.novel.title, download.novel.id)
            tmpFile.delete()
        }
    }

    private fun NovelChapter.toSNovelChapter(): SNovelChapter {
        return SNovelChapter.create().apply {
            url = this@toSNovelChapter.url
            name = this@toSNovelChapter.name
        }
    }

    fun removeFromQueue(chapters: List<NovelChapter>) {
        val downloads = queueState.value.filter { it.chapter.id in chapters.map { c -> c.id } }
        _queueState.value = queueState.value - downloads.toSet()
        store.removeAll(downloads)
    }

    fun removeFromQueue(chapter: NovelChapter) {
        removeFromQueue(listOf(chapter))
    }

    fun removeFromQueue(novel: Novel) {
        val downloads = queueState.value.filter { it.novel.id == novel.id }
        _queueState.value = queueState.value - downloads.toSet()
        store.removeAll(downloads)
    }

    private fun removeFromQueue(download: NovelDownload) {
        _queueState.value = queueState.value - download
        store.remove(download)
    }

    fun updateQueue(downloads: List<NovelDownload>) {
        _queueState.value = downloads
        store.clear()
        store.addAll(downloads)
    }

    private fun addAllToQueue(downloads: List<NovelDownload>) {
        // Set status to QUEUE immediately so the UI shows the spinner
        // for all queued chapters, not just the one actively downloading.
        downloads.forEach { it.status = NovelDownload.State.QUEUE }
        _queueState.value = queueState.value + downloads
        store.addAll(downloads)
    }

    private fun internalClearQueue() {
        _queueState.value = emptyList()
        store.clear()
    }

    private fun areAllDownloadsFinished(): Boolean {
        return queueState.value.none { it.status.value <= NovelDownload.State.DOWNLOADING.value }
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val MIN_DISK_SPACE = 50 * 1024 * 1024L
    }
}
