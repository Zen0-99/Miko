package miko.ui.download

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import miko.ui.carrier.BrowseCarrier
import kotlin.coroutines.coroutineContext

/**
 * What an item's download looks like once resolved — the binding maps its
 * fetch result into files-to-pull (manga page images) or literal text
 * (novel chapter bodies). Shows return null: hoster streams are often HLS
 * playlists that cannot be a single file download.
 */
data class ItemPayload(
    val files: List<Pair<String, String>> = emptyList(),
    val text: Pair<String, String>? = null,
)

enum class DownloadState { QUEUED, DOWNLOADING, DOWNLOADED, ERROR }

/** One row in the queue screen — display metadata for a queued task. */
data class QueueRow(
    val key: String,
    val carrier: BrowseCarrier,
    val sourceName: String,
    val entryTitle: String,
    val itemName: String,
)

/**
 * Android's `DownloadManager` + `DownloadCache` flattened: a sequential
 * worker writing payloads into `<downloads>/<carrier>/<source>/<entry>/<item>/`
 * with a `.done` marker. The filesystem is the source of truth — queue
 * state lives in memory like upstream's downloader.
 */
object DownloadManager {

    private const val DONE_MARKER = ".miko_done"
    private const val MAX_NAME = 80

    private val client = HttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    /** Pending + failed queue rows for the Download Queue screen. */
    private val _queueRows = MutableStateFlow<List<QueueRow>>(emptyList())
    val queueRows: StateFlow<List<QueueRow>> = _queueRows.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    /** Tick emitted whenever an entry dir's downloaded set may have changed. */
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 64)

    /** Emits the affected entry-dir path string. */
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    private data class QueuedTask(
        val key: String,
        val run: suspend () -> Unit,
    )

    private val queue = Channel<QueuedTask>(Channel.UNLIMITED)
    private val jobs = mutableMapOf<String, Job>()

    /** Keys cancelled while still buffered — skipped when dequeued. */
    private val cancelled = mutableSetOf<String>()

    init {
        scope.launch {
            for (task in queue) {
                if (task.key in cancelled) {
                    cancelled -= task.key
                    continue
                }
                val job = launch { task.run() }
                jobs[task.key] = job
                job.join()
                jobs.remove(task.key)
            }
        }
    }

    // ---- paths -------------------------------------------------------------

    fun sanitizeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(MAX_NAME)
            .ifBlank { "item" }

    fun entryDir(carrier: BrowseCarrier, sourceName: String, entryTitle: String): Path =
        Path(
            downloadsRoot(),
            "downloads",
            carrier.name.lowercase(),
            sanitizeName(sourceName),
            sanitizeName(entryTitle),
        )

    fun itemDir(
        carrier: BrowseCarrier,
        sourceName: String,
        entryTitle: String,
        itemName: String,
    ): Path = Path(entryDir(carrier, sourceName, entryTitle).toString(), sanitizeName(itemName))

    fun isDownloaded(dir: Path): Boolean =
        SystemFileSystem.exists(Path(dir.toString(), DONE_MARKER))

    /** Names of downloaded item dirs under the entry — Android's DownloadCache scan. */
    fun downloadedItemNames(
        carrier: BrowseCarrier,
        sourceName: String,
        entryTitle: String,
    ): Set<String> {
        val dir = entryDir(carrier, sourceName, entryTitle)
        if (!SystemFileSystem.exists(dir)) return emptySet()
        return runCatching {
            SystemFileSystem.list(dir)
                .filter { SystemFileSystem.exists(Path(it.toString(), DONE_MARKER)) }
                .mapTo(HashSet()) { it.name }
        }.getOrDefault(emptySet())
    }

    // ---- queue -------------------------------------------------------------

    /**
     * Queue an item download. [payload] is resolved lazily on the worker so
     * page lists fetch at run time, not enqueue time. [key] identifies the
     * row (item url); [entryPath] is the parent dir for change events.
     */
    fun enqueue(
        carrier: BrowseCarrier,
        sourceName: String,
        entryTitle: String,
        itemName: String,
        key: String,
        payload: suspend () -> ItemPayload?,
    ) {
        if (_states.value[key] == DownloadState.QUEUED ||
            _states.value[key] == DownloadState.DOWNLOADING
        ) {
            return
        }
        val dir = itemDir(carrier, sourceName, entryTitle, itemName)
        val entryPath = entryDir(carrier, sourceName, entryTitle).toString()
        _states.value = _states.value + (key to DownloadState.QUEUED)
        _queueRows.value = _queueRows.value.filterNot { it.key == key } +
            QueueRow(key, carrier, sourceName, entryTitle, itemName)
        queue.trySend(
            QueuedTask(key) {
                _states.value = _states.value + (key to DownloadState.DOWNLOADING)
                try {
                    val body = payload()
                    val total = (body?.files?.size ?: 0) + (if (body?.text != null) 1 else 0)
                    if (body == null || total == 0) {
                        // Carrier can't file-download (shows) or fetch failed
                        // upstream — don't leave a false .done marker.
                        _states.value = _states.value - key
                        _queueRows.value = _queueRows.value.filterNot { it.key == key }
                        return@QueuedTask
                    }
                    SystemFileSystem.createDirectories(dir)
                    var done = 0
                    for ((name, url) in body.files) {
                        coroutineContext.ensureActive()
                        fetchToFile(url, Path(dir.toString(), name))
                        done++
                        _progress.value = _progress.value + (key to done / total.toFloat())
                    }
                    body?.text?.let { (name, text) ->
                        SystemFileSystem.sink(Path(dir.toString(), name))
                            .buffered()
                            .use { it.write(text.encodeToByteArray()) }
                        done++
                        _progress.value = _progress.value + (key to done / total.toFloat())
                    }
                    SystemFileSystem.sink(Path(dir.toString(), DONE_MARKER))
                        .buffered()
                        .use { it.write(ByteArray(0)) }
                    _states.value = _states.value + (key to DownloadState.DOWNLOADED)
                    _queueRows.value = _queueRows.value.filterNot { it.key == key }
                    _changes.tryEmit(entryPath)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _states.value = _states.value + (key to DownloadState.ERROR)
                }
            },
        )
    }

    fun cancel(key: String) {
        // Channel can't be drained — queued-but-not-started tasks skip via
        // the cancelled set; running ones get their job cancelled.
        if (jobs[key] == null) cancelled += key
        jobs.remove(key)?.cancel()
        _states.value = _states.value - key
        _progress.value = _progress.value - key
        _queueRows.value = _queueRows.value.filterNot { it.key == key }
    }

    /** Cancel every pending/failed task — the queue screen's clear action. */
    fun clearQueue() {
        _queueRows.value.forEach { cancel(it.key) }
    }

    /** Delete an item's download dir — Android's per-item delete. */
    fun delete(
        carrier: BrowseCarrier,
        sourceName: String,
        entryTitle: String,
        itemName: String,
    ) {
        val dir = itemDir(carrier, sourceName, entryTitle, itemName)
        deleteRecursively(dir)
        _changes.tryEmit(entryDir(carrier, sourceName, entryTitle).toString())
    }

    /** Android's deleteManga/deleteEntry — drops the whole entry dir. */
    fun deleteEntry(carrier: BrowseCarrier, sourceName: String, entryTitle: String) {
        deleteRecursively(entryDir(carrier, sourceName, entryTitle))
        _changes.tryEmit(entryDir(carrier, sourceName, entryTitle).toString())
    }

    /** kotlinx-io deletes only empty dirs — children first, then the dir. */
    private fun deleteRecursively(dir: Path) {
        if (!SystemFileSystem.exists(dir)) return
        val meta = runCatching { SystemFileSystem.metadataOrNull(dir) }.getOrNull()
        if (meta?.isDirectory == true) {
            SystemFileSystem.list(dir).forEach { deleteRecursively(it) }
        }
        runCatching { SystemFileSystem.delete(dir) }
    }

    // ---- io ----------------------------------------------------------------

    private suspend fun fetchToFile(url: String, path: Path) {
        val bytes = client.get(url).bodyAsBytes()
        SystemFileSystem.sink(path).buffered().use { it.write(bytes) }
    }

    /** Read a downloaded file — the offline reader path. */
    fun readFile(dir: Path, name: String): ByteArray? {
        val path = Path(dir.toString(), name)
        if (!SystemFileSystem.exists(path)) return null
        return runCatching {
            SystemFileSystem.source(path).buffered().use { it.readByteArray() }
        }.getOrNull()
    }

    /** `miko-file://` scheme marks a downloaded page path for the reader. */
    const val FILE_SCHEME = "miko-file://"

    /** Downloaded page files in index order — (index, miko-file:// path). */
    fun downloadedPages(dir: Path): List<Pair<Int, String>> =
        runCatching {
            SystemFileSystem.list(dir)
                .filter { it.name.endsWith(".img") }
                .sortedBy { it.name }
                .mapIndexed { i, p -> i to FILE_SCHEME + p.toString() }
        }.getOrDefault(emptyList())
}

/** Platform documents root for the downloads tree. */
expect fun downloadsRoot(): String

/**
 * Composition-time init hook — Android's root needs a Context, so the app
 * root seeds it once; iOS resolves its Documents dir without one.
 */
@androidx.compose.runtime.Composable
expect fun downloadsInitHook()
