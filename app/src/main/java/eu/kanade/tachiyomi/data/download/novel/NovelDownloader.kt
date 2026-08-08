package eu.kanade.tachiyomi.data.download.novel

import android.content.Context
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelDownloader(
    private val context: Context,
    private val provider: NovelDownloadProvider,
    private val cache: NovelDownloadCache,
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val getCollections: GetNovelCollections = Injekt.get(),
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

    fun queueChapters(novel: Novel, chapters: List<NovelChapter>, autoStart: Boolean) {
        if (chapters.isEmpty()) return

        val source = sourceManager.get(novel.source) as? NovelHttpSource ?: return
        val wasEmpty = queueState.value.isEmpty()
        val chaptersToQueue = chapters.asSequence()
            .filter { provider.findChapterDir(it.name, it.scanlator, novel.title, source) == null }
            .sortedByDescending { it.sourceOrder }
            .filter { chapter -> queueState.value.none { it.chapter.id == chapter.id } }
            .map { NovelDownload(source, novel, it) }
            .toList()

        if (chaptersToQueue.isNotEmpty()) {
            addAllToQueue(chaptersToQueue)

            if (autoStart && wasEmpty) {
                NovelDownloadJob.start(context)
            }
        }
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

            cache.addChapter(chapterDirname, novelDir, download.novel)

            DiskUtil.createNoMediaFile(novelDir, context)

            download.status = NovelDownload.State.DOWNLOADED
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
