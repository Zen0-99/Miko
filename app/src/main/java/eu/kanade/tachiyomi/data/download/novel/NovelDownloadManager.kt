package eu.kanade.tachiyomi.data.download.novel

import android.content.Context
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.tts.render.ChapterAudioRenderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class NovelDownloadManager(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val provider: NovelDownloadProvider = Injekt.get(),
    private val cache: NovelDownloadCache = Injekt.get(),
    private val getCollections: GetNovelCollections = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    private val workCarriers: eu.kanade.tachiyomi.data.mikodb.WorkCarriers by injectLazy()

    private val downloader = NovelDownloader(context, provider, cache)

    val isRunning: Boolean
        get() = downloader.isRunning

    private val pendingDeleter = NovelDownloadPendingDeleter(context)

    // Lazy to avoid resolving the render manager at construction time; it is
    // only needed on the delete/rename paths.
    private val renderManager: ChapterAudioRenderManager by injectLazy()

    val queueState
        get() = downloader.queueState

    fun downloaderStart() = downloader.start()
    fun downloaderStop(reason: String? = null) = downloader.stop(reason)

    val isDownloaderRunning
        get() = NovelDownloadJob.isRunningFlow(context)

    fun startDownloads() {
        if (downloader.isRunning) return

        if (NovelDownloadJob.isRunning(context)) {
            downloader.start()
        } else {
            NovelDownloadJob.start(context)
        }
    }

    fun pauseDownloads() {
        downloader.pause()
        downloader.stop()
    }

    fun clearQueue() {
        downloader.clearQueue()
        downloader.stop()
    }

    fun getQueuedDownloadOrNull(chapterId: Long): NovelDownload? {
        return queueState.value.find { it.chapter.id == chapterId }
    }

    fun startDownloadNow(chapterId: Long) {
        val existingDownload = getQueuedDownloadOrNull(chapterId)
        val toAdd = existingDownload ?: runBlocking { NovelDownload.fromChapterId(chapterId) } ?: return
        queueState.value.toMutableList().apply {
            existingDownload?.let { remove(it) }
            add(0, toAdd)
            reorderQueue(this)
        }
        startDownloads()
    }

    fun reorderQueue(downloads: List<NovelDownload>) {
        downloader.updateQueue(downloads)
    }

    suspend fun downloadChapters(
        novel: Novel,
        chapters: List<NovelChapter>,
        autoStart: Boolean = true,
        forcedCarrierPkg: String? = null,
    ) {
        downloader.queueChapters(novel, chapters, autoStart, forcedCarrierPkg)
    }

    /**
     * The carrier source holding a canon chapter's downloaded text, if
     * any — canon rows have no single home source, so the check scans
     * every bound carrier's download dir.
     */
    suspend fun canonDownloadSource(
        chapterName: String,
        chapterScanlator: String?,
        novel: Novel,
    ): NovelSource? {
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromNovelUrl(novel.url) ?: return null
        val sourceIds = runCatching {
            workCarriers.forWork(workId, eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_BOOKS)
                .carriers
        }.getOrNull()?.flatMap { it.sourceIds } ?: return null
        return sourceIds.firstNotNullOfOrNull { id ->
            sourceManager.get(id)
                ?.takeIf {
                    cache.isChapterDownloaded(chapterName, chapterScanlator, novel.title, id, true)
                }
        }
    }

    /** Serving source for a `carrier:{id}:{url}` chapter row, when the
     *  recorded carrier is still installed. */
    fun carrierDownloadSource(chapterUrl: String): NovelSource? =
        eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter
            .carrierSourceId(chapterUrl)
            ?.let { sourceManager.get(it) }

    fun addDownloadsToStartOfQueue(downloads: List<NovelDownload>) {
        if (downloads.isEmpty()) return
        queueState.value.toMutableList().apply {
            addAll(0, downloads)
            reorderQueue(this)
        }
        if (!NovelDownloadJob.isRunning(context)) startDownloads()
    }

    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        novelTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean {
        return cache.isChapterDownloaded(
            chapterName,
            chapterScanlator,
            novelTitle,
            sourceId,
            skipCache,
        )
    }

    fun getDownloadCount(): Int {
        return cache.getTotalDownloadCount()
    }

    fun getDownloadCount(novel: Novel): Int {
        return cache.getDownloadCount(novel)
    }

    fun getDownloadSize(): Long {
        return cache.getTotalDownloadSize()
    }

    fun getDownloadSize(novel: Novel): Long {
        return cache.getDownloadSize(novel)
    }

    fun cancelQueuedDownloads(downloads: List<NovelDownload>) {
        removeFromDownloadQueue(downloads.map { it.chapter })
    }

    /**
     * Deletes the chapters' downloaded content (and any rendered audio).
     * Suspending so callers can refresh row state the moment the files
     * are actually gone — `statusFlow` only tracks queued downloads, so
     * deletions emit nothing on their own. Returns the chapters that
     * were actually deleted (bookmark/collection filters apply).
     */
    suspend fun deleteChapters(
        chapters: List<NovelChapter>,
        novel: Novel,
        source: NovelSource,
    ): List<NovelChapter> = withContext(Dispatchers.IO) {
        val filteredChapters = getChaptersToDelete(chapters, novel)
        if (filteredChapters.isEmpty()) {
            return@withContext emptyList()
        }

        removeFromDownloadQueue(filteredChapters)

        // Canon rows download under whichever carrier served them —
        // scan every bound carrier's dir, not just the entry's own
        // (stub) source dir.
        val sourcesToScan = if (
            eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
                .workIdFromNovelUrl(novel.url) != null
        ) {
            // Carrier-tagged rows carry their serving source on the
            // url — include them so an unbound carrier's dirs still
            // get cleaned.
            (carrierSources(novel) + filteredChapters.mapNotNull { carrierDownloadSource(it.url) })
                .distinct()
                .ifEmpty { listOf(source) }
        } else {
            listOf(source)
        }
        sourcesToScan.forEach { src ->
            val (novelDir, chapterDirs) = provider.findChapterDirs(filteredChapters, novel, src)
            chapterDirs.forEach { it.delete() }

            // Rendered-audio cascade (05-02, D-18): cancel any queued/running
            // render FIRST so the engine can't resurrect the .m4a after it
            // is deleted here; runs before the empty-novel-dir check so the
            // audio/ files don't block auto-cleanup.
            filteredChapters.forEach { chapter ->
                renderManager.cancelRender(chapter.id)
                provider.deleteAudioFile(chapter.name, chapter.scanlator, novel.title, src)
            }
            // Drop the audio/ dir itself once empty — a leftover empty dir
            // would still make novelDir.listFiles() non-empty below.
            provider.findAudioDir(novel.title, src)
                ?.takeIf { it.listFiles().isNullOrEmpty() }
                ?.delete()

            if (novelDir?.listFiles()?.isEmpty() == true) {
                deleteNovel(novel, src, removeQueued = false)
            }
        }
        cache.removeChapters(filteredChapters, novel)
        filteredChapters
    }

    /** Installed sources behind this work's bound carriers — where canon
     *  chapter downloads physically live. */
    private suspend fun carrierSources(novel: Novel): List<NovelSource> {
        val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
            .workIdFromNovelUrl(novel.url) ?: return emptyList()
        val sourceIds = runCatching {
            workCarriers.forWork(workId, eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_BOOKS)
                .carriers
        }.getOrNull()?.flatMap { it.sourceIds }.orEmpty()
        return sourceIds.mapNotNull { sourceManager.get(it) }
    }

    fun deleteNovel(novel: Novel, source: NovelSource, removeQueued: Boolean = true) {
        launchIO {
            if (removeQueued) {
                downloader.removeFromQueue(novel)
            }
            provider.findNovelDir(novel.title, source)?.delete()
            cache.removeNovel(novel)

            val sourceDir = provider.findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
                cache.removeSource(source)
            }
        }
    }

    private fun removeFromDownloadQueue(chapters: List<NovelChapter>) {
        val wasRunning = downloader.isRunning
        if (wasRunning) {
            downloader.pause()
        }

        downloader.removeFromQueue(chapters)

        if (wasRunning) {
            if (queueState.value.isEmpty()) {
                downloader.stop()
            } else if (queueState.value.isNotEmpty()) {
                downloader.start()
            }
        }
    }

    suspend fun enqueueChaptersToDelete(chapters: List<NovelChapter>, novel: Novel) {
        pendingDeleter.addChapters(getChaptersToDelete(chapters, novel), novel)
    }

    fun deletePendingChapters() {
        launchIO {
            val pendingChapters = pendingDeleter.getPendingChapters()
            for ((novel, chapters) in pendingChapters) {
                val source = sourceManager.get(novel.source) ?: continue
                deleteChapters(chapters, novel, source)
            }
        }
    }

    fun renameSource(oldSource: NovelSource, newSource: NovelSource) {
        val oldFolder = provider.findSourceDir(oldSource) ?: return
        val newName = provider.getSourceDirName(newSource)

        if (oldFolder.name == newName) return

        if (!oldFolder.renameTo(newName)) {
            logcat { "Failed to rename source download folder: ${oldFolder.name}" }
        }
    }

    suspend fun renameChapter(
        source: NovelSource,
        novel: Novel,
        oldChapter: NovelChapter,
        newChapter: NovelChapter,
    ) {
        val oldNames = provider.getValidChapterDirNames(oldChapter.name, oldChapter.scanlator)
        val novelDir = provider.getNovelDir(novel.title, source)

        // Rename the rendered `.m4a` alongside the chapter dir so the audio
        // stays keyed to the chapter's new name (05-02, D-18). Runs before the
        // early-return guards below — rendered audio can outlive a deleted or
        // partially-present chapter dir.
        provider.findAudioFile(
            oldChapter.name,
            oldChapter.scanlator,
            novel.title,
            source,
        )?.renameTo(
            provider.getAudioFileName(newChapter.name, newChapter.scanlator),
        )

        val oldDownload = oldNames.asSequence()
            .mapNotNull { novelDir.findFile(it) }
            .firstOrNull() ?: return

        var newName = provider.getChapterDirName(newChapter.name, newChapter.scanlator)

        if (oldDownload.name == newName) return

        if (oldDownload.renameTo(newName)) {
            cache.removeChapter(oldChapter, novel)
            cache.addChapter(newName, novelDir, novel)
        }
    }

    private suspend fun getChaptersToDelete(chapters: List<NovelChapter>, novel: Novel): List<NovelChapter> {
        val collectionsToExclude = downloadPreferences.removeExcludeCollections().get().map(
            String::toLong,
        )

        val collectionsForNovel = getCollections.await(novel.id)
            .map { it.id }
            .ifEmpty { listOf(0) }
        val filteredCollectionNovel = if (collectionsForNovel.intersect(collectionsToExclude).isNotEmpty()) {
            chapters.filterNot { it.read }
        } else {
            chapters
        }

        return if (!downloadPreferences.removeBookmarkedChapters().get()) {
            filteredCollectionNovel.filterNot { it.bookmark }
        } else {
            filteredCollectionNovel
        }
    }

    fun statusFlow(): Flow<NovelDownload> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.statusFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == NovelDownload.State.DOWNLOADING }.asFlow(),
            )
        }

    fun progressFlow(): Flow<NovelDownload> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.progressFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == NovelDownload.State.DOWNLOADING }
                    .asFlow(),
            )
        }
}
