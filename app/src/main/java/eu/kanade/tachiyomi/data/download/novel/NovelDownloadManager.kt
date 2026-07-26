package eu.kanade.tachiyomi.data.download.novel

import android.content.Context
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.novelsource.NovelSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
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

class NovelDownloadManager(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val provider: NovelDownloadProvider = Injekt.get(),
    private val cache: NovelDownloadCache = Injekt.get(),
    private val getCollections: GetNovelCollections = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    private val downloader = NovelDownloader(context, provider, cache)

    val isRunning: Boolean
        get() = downloader.isRunning

    private val pendingDeleter = NovelDownloadPendingDeleter(context)

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

    fun downloadChapters(novel: Novel, chapters: List<NovelChapter>, autoStart: Boolean = true) {
        downloader.queueChapters(novel, chapters, autoStart)
    }

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

    fun deleteChapters(chapters: List<NovelChapter>, novel: Novel, source: NovelSource) {
        launchIO {
            val filteredChapters = getChaptersToDelete(chapters, novel)
            if (filteredChapters.isEmpty()) {
                return@launchIO
            }

            removeFromDownloadQueue(filteredChapters)

            val (novelDir, chapterDirs) = provider.findChapterDirs(filteredChapters, novel, source)
            chapterDirs.forEach { it.delete() }
            cache.removeChapters(filteredChapters, novel)

            if (novelDir?.listFiles()?.isEmpty() == true) {
                deleteNovel(novel, source, removeQueued = false)
            }
        }
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
        val pendingChapters = pendingDeleter.getPendingChapters()
        for ((novel, chapters) in pendingChapters) {
            val source = sourceManager.get(novel.source) ?: continue
            deleteChapters(chapters, novel, source)
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
