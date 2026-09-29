package eu.kanade.tachiyomi.ui.download.novel

import android.view.MenuItem
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.download.novel.model.NovelDownload
import eu.kanade.tachiyomi.databinding.DownloadListBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelDownloadQueueScreenModel(
    private val downloadManager: NovelDownloadManager = Injekt.get(),
) : ScreenModel {

    private val _state = MutableStateFlow(emptyList<NovelDownloadHeaderItem>())
    val state = _state.asStateFlow()

    lateinit var controllerBinding: DownloadListBinding

    var adapter: NovelDownloadAdapter? = null

    private val progressJobs = mutableMapOf<NovelDownload, Job>()

    val listener = object : NovelDownloadAdapter.DownloadItemListener {
        override fun onItemReleased(position: Int) {
            val adapter = adapter ?: return
            val downloads = adapter.headerItems.flatMap { header ->
                adapter.getSectionItems(header).map { item ->
                    (item as NovelDownloadItem).download
                }
            }
            reorder(downloads)
        }

        override fun onMenuItemClick(position: Int, menuItem: MenuItem) {
            val item = adapter?.getItem(position) ?: return
            if (item is NovelDownloadItem) {
                when (menuItem.itemId) {
                    R.id.move_to_top, R.id.move_to_bottom -> {
                        val headerItems = adapter?.headerItems ?: return
                        val newDownloads = mutableListOf<NovelDownload>()
                        headerItems.forEach { headerItem ->
                            headerItem as NovelDownloadHeaderItem
                            if (headerItem == item.header) {
                                headerItem.removeSubItem(item)
                                if (menuItem.itemId == R.id.move_to_top) {
                                    headerItem.addSubItem(0, item)
                                } else {
                                    headerItem.addSubItem(item)
                                }
                            }
                            newDownloads.addAll(headerItem.subItems.map { it.download })
                        }
                        reorder(newDownloads)
                    }
                    R.id.move_to_top_series, R.id.move_to_bottom_series -> {
                        val (selectedSeries, otherSeries) = adapter?.currentItems
                            ?.filterIsInstance<NovelDownloadItem>()
                            ?.map(NovelDownloadItem::download)
                            ?.partition { item.download.novel.id == it.novel.id }
                            ?: Pair(emptyList(), emptyList())
                        if (menuItem.itemId == R.id.move_to_top_series) {
                            reorder(selectedSeries + otherSeries)
                        } else {
                            reorder(otherSeries + selectedSeries)
                        }
                    }
                    R.id.cancel_download -> {
                        cancel(listOf(item.download))
                    }
                    R.id.cancel_series -> {
                        val allDownloadsForSeries = adapter?.currentItems
                            ?.filterIsInstance<NovelDownloadItem>()
                            ?.filter { item.download.novel.id == it.download.novel.id }
                            ?.map(NovelDownloadItem::download)
                        if (!allDownloadsForSeries.isNullOrEmpty()) {
                            cancel(allDownloadsForSeries)
                        }
                    }
                }
            }
        }
    }

    init {
        screenModelScope.launch {
            downloadManager.queueState
                .map { downloads ->
                    downloads
                        .groupBy { it.source }
                        .map { entry ->
                            NovelDownloadHeaderItem(entry.key.id, entry.key.name, entry.value.size).apply {
                                addSubItems(0, entry.value.map { NovelDownloadItem(it, this) })
                            }
                        }
                }
                .collect { newList -> _state.update { newList } }
        }
    }

    override fun onDispose() {
        for (job in progressJobs.values) {
            job.cancel()
        }
        progressJobs.clear()
        adapter = null
    }

    val isDownloaderRunning = downloadManager.isDownloaderRunning
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun getDownloadStatusFlow() = downloadManager.statusFlow()
    fun getDownloadProgressFlow() = downloadManager.progressFlow()

    fun startDownloads() {
        downloadManager.startDownloads()
    }

    fun pauseDownloads() {
        downloadManager.pauseDownloads()
    }

    fun clearQueue() {
        downloadManager.clearQueue()
    }

    fun reorder(downloads: List<NovelDownload>) {
        downloadManager.reorderQueue(downloads)
    }

    fun cancel(downloads: List<NovelDownload>) {
        downloadManager.cancelQueuedDownloads(downloads)
    }

    fun <R : Comparable<R>> reorderQueue(
        selector: (NovelDownloadItem) -> R,
        reverse: Boolean = false,
    ) {
        val adapter = adapter ?: return
        val newDownloads = mutableListOf<NovelDownload>()
        adapter.headerItems.forEach { headerItem ->
            headerItem as NovelDownloadHeaderItem
            headerItem.subItems = headerItem.subItems.sortedBy(selector).toMutableList().apply {
                if (reverse) {
                    reverse()
                }
            }
            newDownloads.addAll(headerItem.subItems.map { it.download })
        }
        reorder(newDownloads)
    }

    fun onStatusChange(download: NovelDownload) {
        when (download.status) {
            NovelDownload.State.DOWNLOADING -> {
                launchProgressJob(download)
                onUpdateProgress(download)
            }
            NovelDownload.State.DOWNLOADED -> {
                cancelProgressJob(download)
                onUpdateProgress(download)
            }
            NovelDownload.State.ERROR -> cancelProgressJob(download)
            else -> {
                /* unused */
            }
        }
    }

    private fun launchProgressJob(download: NovelDownload) {
        val job = screenModelScope.launch {
            download.progressFlow
                .distinctUntilChanged()
                .debounce(50)
                .collectLatest {
                    onUpdateProgress(download)
                }
        }

        progressJobs.remove(download)?.cancel()
        progressJobs[download] = job
    }

    private fun cancelProgressJob(download: NovelDownload) {
        progressJobs.remove(download)?.cancel()
    }

    fun onUpdateProgress(download: NovelDownload) {
        getHolder(download)?.notifyProgress()
    }

    private fun getHolder(download: NovelDownload): NovelDownloadHolder? {
        return controllerBinding.root.findViewHolderForItemId(download.chapter.id) as? NovelDownloadHolder
    }
}
