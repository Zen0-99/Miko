package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.PocketBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import logcat.LogPriority
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import tachiyomi.core.common.util.system.logcat
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Marker interface for the install root directory. Lets unit tests inject
 * a temporary folder without standing up a full Android `Context`.
 *
 * In production, this is `Context.getFilesDir()`. In tests, it's a
 * `@TempDir`-backed File.
 */
fun interface EngineFilesDir {
    fun get(): File
}

/**
 * Just the bit of the Pocket engine that the installer needs. Extracting an
 * interface here lets unit tests run without instantiating the engine (which
 * transitively needs an Android `Context` we can't mint in a JVM test).
 */
fun interface NativeEngineHandle {
    /** Drop any cached native handle (mmap'd model bytes etc.). Idempotent. */
    fun release()
}

/**
 * Abstraction over the HTTP fetch step. Production wires this to
 * `HttpURLConnection`; unit tests substitute a synchronous in-memory
 * fetcher so they don't need to stand up a real TCP server.
 */
fun interface HttpFetcher {
    /**
     * Open an input stream for [url]. Implementations are responsible for
     * following redirects, applying timeouts, and throwing IOException on
     * non-2xx response codes. The caller closes the stream.
     *
     * `fun interface`: this is the sole abstract method ([openRange] has
     * a default body), so SAM conversion lets test fakes pass a
     * `(String) -> InputStream` lambda directly to constructors that
     * take an `HttpFetcher`.
     */
    @Throws(IOException::class)
    fun open(url: String): java.io.InputStream

    /**
     * Open an input stream for [url] starting at byte offset [fromBytes].
     * Used by the installer to resume a partial download after a network
     * abort. The returned [RangeResult] tells the caller whether the
     * server honored the Range header (HTTP 206 — caller continues from
     * the existing partial file) or replied with the full body (HTTP 200
     * — caller discards the partial file and starts over).
     *
     * Servers that don't speak Range/206 should still return a working
     * stream of the full body, so this method must never throw just
     * because resume isn't supported.
     */
    @Throws(IOException::class)
    fun openRange(url: String, fromBytes: Long): RangeResult {
        // Default implementation: ignore the offset, do a full GET. Lets
        // tests + simple stubs satisfy the interface without implementing
        // Range. Production [UrlHttpFetcher] overrides.
        return RangeResult(stream = open(url), startedAtBytes = 0L)
    }
}

/**
 * Result of an [HttpFetcher.openRange] call.
 *
 * @property stream Body stream. Body bytes start at offset [startedAtBytes]
 *           in the logical file — i.e. for a 206 response with
 *           `Content-Range: bytes 100-/300`, [startedAtBytes] is 100 and
 *           the stream emits bytes 100..299.
 * @property startedAtBytes Offset at which the body starts. Equals the
 *           caller's requested `fromBytes` on resume (HTTP 206); equals 0
 *           when the server returned the full body (HTTP 200) and the
 *           caller must discard any partial file and re-download from
 *           scratch.
 */
data class RangeResult(
    val stream: java.io.InputStream,
    val startedAtBytes: Long,
)

/** Production implementation: stream from a remote URL via `HttpURLConnection`. */
object UrlHttpFetcher : HttpFetcher {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    override fun open(url: String): java.io.InputStream {
        return openInternal(url, fromBytes = 0L).first
    }

    override fun openRange(url: String, fromBytes: Long): RangeResult {
        val (stream, startedAt) = openInternal(url, fromBytes)
        return RangeResult(stream = stream, startedAtBytes = startedAt)
    }

    /**
     * Single connection-open path shared by [open] + [openRange]. Returns
     * the body stream + the byte offset at which the body actually starts
     * (always 0 from [open]; might be 0 or `fromBytes` from [openRange]
     * depending on whether the server honored the Range header).
     */
    private fun openInternal(url: String, fromBytes: Long): Pair<java.io.InputStream, Long> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
            if (fromBytes > 0L) {
                // Open-ended range — GitHub releases (S3-backed) responds
                // with 206 + Content-Range; servers that don't support
                // ranges respond with 200 + full body.
                setRequestProperty("Range", "bytes=$fromBytes-")
            }
        }
        conn.connect()
        val code = conn.responseCode
        // 206 = Range honored; 200 = ignored (or no range requested); other 2xx
        // are valid full-body responses. Anything non-2xx is a hard fail.
        if (code !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP $code fetching $url")
        }
        val startedAt = if (code == 206 && fromBytes > 0L) fromBytes else 0L
        val raw = conn.inputStream
        val wrapped: java.io.InputStream = object : java.io.FilterInputStream(raw) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    conn.disconnect()
                }
            }
        }
        return wrapped to startedAt
    }
}

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   UI: engine install card in the TTS settings screen (plan 04-06)
//     │
//     │  .install("pocket-tts-en-v2026_04") { progress -> ... }
//     ▼
//   PocketEngineInstaller.install(name)
//     │
//     ├── PocketEngineCatalog.POCKET_TTS_EN + sources()
//     │     ──► ordered List<EngineArchiveSource> (Miko primary first once
//     │         populated, Marmalade v21 fallback always present — D-03;
//     │         each source carries its own sha256 pin)
//     │
//     ├── _state[name].value = Downloading(0, source.sizeBytes, "archive")
//     │
//     ├── Per source, in order:
//     │      ├── HTTP GET via HttpFetcher → <name>.archive.tmp
//     │      │     (Range-resume when a partial .archive.tmp is on disk)
//     │      ├── update sha256 incrementally
//     │      ├── emit Downloading progress every ~1% / ~256 KB
//     │      └── reject on sha256 mismatch with source.sha256
//     │      ├── Extracting → tar.bz2 → <name>.tmp/ scratch dir
//     │      │     (archiveRoot strip + canonical-path zip-slip check)
//     │      ├── atomic rename <name>.tmp → engines/<name>
//     │      ├── write .install_meta.json (the sha of the source that worked)
//     │      └── verifyLayout → Installed
//     │     On any failure: clean partials, try the NEXT source.
//     │
//     └── Failed only when every source has been tried and failed.
//
//   On any failure:
//     ├── _state[name].value = Failed(reason)
//     ├── delete scratch dir (and the partial archive when moving to the
//     │   next source or when the sha proved the bytes bad — a kept partial
//     │   from the final failed source stays so a later retry can resume)
//     └── return Result.failure(IOException(reason))
//
//   UI: subscribes via .state("pocket-tts-en-v2026_04")
// -----------------------------------------------------------------------------

/**
 * Persistent install lifecycle state for an engine bundle.
 *
 * State transitions are linear-with-loopback:
 *
 * ```
 *   NotInstalled ──► Downloading ──► Extracting ──► Installed
 *                          │                            │
 *                          ▼                            ▼
 *                       Failed                       uninstall()
 *                          │                            │
 *                          └──► NotInstalled ◄──────────┘
 *
 *   Corrupt: discovered out-of-band by verify() — Installed devolves
 *   to Corrupt when files have gone missing under the running app.
 * ```
 */
sealed class InstallState {
    object NotInstalled : InstallState()

    /**
     * Engine is mid-download. The UI polls/observes this for the progress
     * bar. One emission per ~1% of the archive or per ~256 KB transferred,
     * whichever is coarser, so the Flow isn't flooded.
     */
    data class Downloading(
        val bytesFetched: Long,
        val totalBytes: Long,
        val currentFile: String,
    ) : InstallState()

    /**
     * Archive is downloaded + sha256-verified and is now being decompressed
     * + untarred into the scratch directory. [bytesExtracted] is the
     * cumulative size of files written to disk during the unpack so far;
     * [totalBytes] is the *estimated* unpacked size from
     * `PocketEngineDescriptor.installedSizeBytes`. The estimate is cheap and
     * accurate (we control the bundle layout); UI bars divide the two for a
     * determinate progress fraction.
     */
    data class Extracting(
        val bytesExtracted: Long,
        val totalBytes: Long,
    ) : InstallState()

    /** Engine is ready for use — `PocketEngineCatalog.isInstalled()` will return true. */
    object Installed : InstallState()

    /** Install attempt failed mid-flight. UI shows a Retry affordance with the reason. */
    data class Failed(val reason: String) : InstallState()

    /**
     * Files are *present on disk* but the post-install sanity check
     * couldn't find the required top-level files. Surfaced by
     * [PocketEngineInstaller.verify] and treated by the UI the same as
     * NotInstalled (offer reinstall).
     */
    object Corrupt : InstallState()

    /**
     * Files are present on disk and pass the layout check, but the
     * `.install_meta.json` left by [PocketEngineInstaller.install] records an
     * archive SHA-256 that doesn't match the archive the catalog would
     * install today (the first of [PocketEngineCatalog.sources]) — the
     * bundle has been re-published (different URL or different contents)
     * since the last install. UI treats this as a soft prompt to update;
     * the existing files still work in the interim. The two SHA-256s are
     * carried for diagnostic logging.
     *
     * Older installs may carry no `.install_meta.json` — in that case the
     * meta is bootstrapped from the current catalog and [installedSha256]
     * is not surfaced as a mismatch (see checkInstallMeta).
     */
    data class Outdated(
        val installedSha256: String?,
        val expectedSha256: String,
    ) : InstallState()
}

/**
 * Engine-as-plugin installer for the Pocket TTS bundle.
 *
 * Owns the lifecycle of `${filesDir}/engines/<engine>/` directories — the
 * UI never touches those paths directly. The contract with the pocket
 * engine is: once [install] completes successfully,
 * `PocketEngineCatalog.isInstalled(filesDir)` returns true and the engine
 * can load `engines/<name>/bundle.json`.
 *
 * Implementation invariants:
 *
 *  1. **Atomic installs.** The archive is downloaded to
 *     `<name>.archive.tmp`, extracted into a scratch directory
 *     (`<name>.tmp`), and the scratch dir is renamed to the final
 *     location on success. The app never observes a partial engine
 *         directory. (This is the fix for the `NeuralVoiceManager`
 *     anti-pattern, which extracted straight into the final dir.)
 *  2. **Hash-verified archive.** Each source's archive SHA-256 is checked
 *     against its own pin (D-03) — integrity never depends on which host
 *     served the bytes. Mismatch fails that source's attempt and deletes
 *     the bad bytes.
 *  3. **Zip-slip protection.** Each archive entry's normalized path is
 *     checked to make sure it stays inside the scratch directory.
 *  4. **Dual-source fallback (D-03).** Sources are tried in catalog order;
 *     a network/sha/extract failure on one source cleans its partials and
 *     falls through to the next. `Failed` only when all sources exhaust.
 *  5. **Single concurrent install per engine.** [install] and [uninstall]
 *     serialize on a per-engine [Mutex].
 *  6. **No network use outside install/verify.** The single
 *     `<uses-permission android:name="android.permission.INTERNET" />`
 *     in the manifest documents that boundary.
 *
 * Ported from marmalade-tts-android `install/EngineInstaller.kt` minus
 * `seedFromAssets` (Miko never bakes a ~97 MB engine into the APK) and the
 * Kitten/Kokoro layout branches — this installer only ever serves
 * `pocket-tts-en-v2026_04`. `org.json` was swapped for
 * kotlinx-serialization so [verify] stays JVM-testable.
 */
class PocketEngineInstaller(
    private val filesDir: EngineFilesDir,
    private val engineHandle: NativeEngineHandle,
    private val httpFetcher: HttpFetcher,
) {

    /**
     * Per-engine state flows. Created lazily on first observation so the
     * flow is hot once the UI subscribes.
     */
    private val states: MutableMap<String, MutableStateFlow<InstallState>> = mutableMapOf()
    private val statesLock = Any()

    /**
     * Per-engine lock serializing operations that mutate `engines/<name>` and
     * `engines/<name>.tmp` — namely [installViaDescriptor].
     */
    private val installMutexes = mutableMapOf<String, Mutex>()
    private fun installMutex(engineName: String): Mutex = synchronized(installMutexes) {
        installMutexes.getOrPut(engineName) { Mutex() }
    }

    /**
     * Returns a hot [Flow] of [InstallState] for [engineName]. The initial
     * value is computed eagerly by inspecting the on-disk engine directory:
     *
     *  - directory absent → [InstallState.NotInstalled]
     *  - directory present → [InstallState.Installed]
     *
     * [verify] refines the initial guess asynchronously to Corrupt/Outdated.
     * Multiple subscribers share the same StateFlow.
     */
    fun state(engineName: String): Flow<InstallState> = stateFlow(engineName).asStateFlow()

    /**
     * Install [engineName] from the catalog.
     *
     * Downloads the engine's tar.bz2 archive — trying
     * [PocketEngineCatalog.sources] in order (Miko primary first once
     * populated, Marmalade v21 fallback last — D-03) — verifies each
     * source's SHA-256 against its own pin, decompresses + untars it into
     * the engine directory via a scratch dir + atomic rename. Reports
     * progress through [onProgress] and updates the per-engine state flow.
     *
     * Re-running install on an already-installed engine is idempotent:
     * the existing engine directory is removed first so the new install
     * starts clean.
     *
     * @return Result.success(Unit) on success, Result.failure(IOException)
     *   on any download / verification / I/O error across *all* sources.
     *   The state flow has already been updated to Failed before the
     *   Result returns.
     */
    suspend fun install(
        engineName: String,
        onProgress: (InstallState.Downloading) -> Unit = {},
    ): Result<Unit> {
        if (engineName != PocketEngineCatalog.ENGINE_NAME) {
            return failed(engineName, "Unknown engine: $engineName")
        }
        return installViaDescriptor(
            PocketEngineCatalog.POCKET_TTS_EN,
            PocketEngineCatalog.sources(),
            onProgress,
        )
    }

    /**
     * Test-friendly install that operates against caller-supplied
     * [sources] instead of reading [PocketEngineCatalog.sources]. Used by
     * `PocketEngineInstallerTest` to drive the installer against in-memory
     * fakes without polluting the production catalog — the production
     * constants stay immutable.
     *
     * Production code paths must go through [install] — the catalog is
     * the source of truth for what's installable.
     *
     * Per-source loop (D-03): on `IOException`/sha-mismatch from one source
     * the `.archive.tmp` partial and scratch dir are deleted (a partial's
     * bytes can't carry over to a source pinned to a different sha256) and
     * the next source is tried. When all sources are exhausted the partial
     * is kept unless the last failure proved it corrupt (sha mismatch) —
     * the next [install] call then Range-resumes it.
     */
    internal suspend fun installViaDescriptor(
        descriptor: PocketEngineDescriptor,
        sources: List<EngineArchiveSource>,
        onProgress: (InstallState.Downloading) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        installMutex(descriptor.name).withLock {
            val engineName = descriptor.name
            val sf = stateFlow(engineName)
            val finalDir = engineDirFor(engineName)
            val scratchDir = scratchDirFor(engineName)
            val archiveTmp = archiveTmpFor(engineName)

            // Clean up any leftover scratch dir, but KEEP the partial
            // archive — [downloadArchive] knows how to resume from it via
            // HTTP Range requests, and the post-download SHA-256 check
            // guarantees a partial that doesn't match the expected hash is
            // rejected. Reusing the bytes is a big win when the 97 MB
            // Pocket bundle aborts mid-download.
            //
            // A pre-existing finalDir (reinstall/update) is deliberately
            // NOT touched here: the currently-working engine must survive a
            // failed download or extraction. It's deleted at the last
            // moment, right before the atomic rename. The cost is transient
            // double disk usage during an update; the win is that an
            // aborted update on flaky wifi never leaves the user with no
            // engine at all.
            if (scratchDir.exists()) scratchDir.deleteRecursively()
            archiveTmp.parentFile?.mkdirs()

            var lastError: Throwable? = null
            for ((index, source) in sources.withIndex()) {
                scratchDir.mkdirs()
                try {
                    // 1. Download the archive while computing SHA-256 and
                    // emitting throttled progress updates.
                    sf.value = InstallState.Downloading(
                        bytesFetched = 0L,
                        totalBytes = source.sizeBytes,
                        currentFile = ARCHIVE_PROGRESS_LABEL,
                    )

                    logcat(LogPriority.DEBUG) {
                        "Downloading ${source.url} → ${archiveTmp.absolutePath} " +
                            "(source ${index + 1}/${sources.size})"
                    }
                    val fetchedSha = downloadArchive(source.url, archiveTmp, source.sizeBytes) { fetched ->
                        val update = InstallState.Downloading(
                            bytesFetched = fetched,
                            totalBytes = source.sizeBytes,
                            currentFile = ARCHIVE_PROGRESS_LABEL,
                        )
                        sf.value = update
                        onProgress(update)
                    }

                    // 2. Verify archive hash against THIS source's pin (D-03).
                    if (!fetchedSha.equals(source.sha256, ignoreCase = true)) {
                        throw IOException(
                            "SHA-256 mismatch for archive $engineName from ${source.url}: " +
                                "expected ${source.sha256}, got $fetchedSha",
                        )
                    }

                    // 3. Extract into the scratch dir. Emit byte-level
                    // progress against the descriptor's declared installed
                    // size — the UI shows a determinate bar for the unpack
                    // phase instead of a stuck indeterminate spinner.
                    val totalUnpackedBytes = descriptor.installedSizeBytes
                    sf.value = InstallState.Extracting(
                        bytesExtracted = 0L,
                        totalBytes = totalUnpackedBytes,
                    )
                    extractArchive(archiveTmp, scratchDir, source.archiveRoot) { bytes ->
                        sf.value = InstallState.Extracting(
                            bytesExtracted = bytes,
                            totalBytes = totalUnpackedBytes,
                        )
                    }

                    // 4. Delete the archive scratch file — it's served its purpose.
                    archiveTmp.delete()

                    // 5. Swap the new bundle in. Only now — with the download
                    // and extraction fully succeeded — does a reinstall/update
                    // drop the old working engine. Release the native handle
                    // first, otherwise the engine could be holding open
                    // mmap'd model bytes.
                    if (finalDir.exists()) {
                        try {
                            engineHandle.release()
                        } catch (_: Throwable) {
                            // Best effort — release() should be idempotent and
                            // exception-free, but a faulty native build shouldn't
                            // block a reinstall.
                        }
                        finalDir.deleteRecursively()
                    }
                    // Atomic rename. Scratch dir and final dir share the same
                    // parent (${filesDir}/engines/), so rename is just an inode
                    // flip — no cross-filesystem fallback needed.
                    if (!scratchDir.renameTo(finalDir)) {
                        throw IOException(
                            "Could not rename ${scratchDir.absolutePath} to ${finalDir.absolutePath}",
                        )
                    }

                    // 6. Stamp the install meta. Records which source produced
                    // this on-disk bundle so [verifyLayout] can detect when the
                    // catalog has since changed and surface InstallState.Outdated.
                    // Written AFTER the atomic rename so a partial install never
                    // leaves a stale meta behind.
                    writeInstallMeta(finalDir, source)

                    // 7. Post-install sanity check. The archive sha already
                    // proved the bytes are correct, so this just confirms the
                    // extraction produced the expected top-level layout —
                    // defensive against a malformed bundle slipping through.
                    val verified = verifyLayout(finalDir, expectedSource = source)
                    if (verified is InstallState.Corrupt) {
                        // Extracted shape doesn't match expectations. Tear down
                        // so a retry has a clean slate.
                        finalDir.deleteRecursively()
                        throw IOException(
                            "Post-install verification failed for $engineName: " +
                                "extracted layout missing required files",
                        )
                    }

                    sf.value = InstallState.Installed
                    return@withContext Result.success(Unit)
                } catch (t: Throwable) {
                    lastError = t
                    logcat(LogPriority.WARN, t) {
                        "Install of $engineName from ${source.url} failed " +
                            "(source ${index + 1}/${sources.size})"
                    }
                    val hasNext = index + 1 < sources.size
                    val shaMismatch = t is IOException &&
                        t.message?.contains("mismatch", ignoreCase = true) == true
                    // Tear down the partial scratch dir (post-extract state is
                    // unrecoverable). The partial archive is deleted when the
                    // next source can't reuse its bytes (moving on) or when
                    // the sha proved them bad; on the final source's non-sha
                    // failure it's kept so a later install() can Range-resume
                    // it (see [downloadArchive] — existing bytes are re-hashed
                    // into the digest, so corruption can't slip through).
                    if (archiveTmp.exists() && (hasNext || shaMismatch)) archiveTmp.delete()
                    if (scratchDir.exists()) scratchDir.deleteRecursively()
                }
            }

            val reason = lastError?.let {
                "all ${sources.size} sources failed — last: ${it.message ?: it::class.java.simpleName}"
            } ?: "no download sources for $engineName"
            sf.value = InstallState.Failed(reason)
            Result.failure(
                if (lastError is IOException) lastError else IOException(reason, lastError),
            )
        } // installMutex.withLock
    }

    /**
     * Remove [engineName] from disk. Releases the engine's native handle
     * first so we never delete files that are still mmap'd. Deletes the
     * whole engine directory recursively — including `voice_cache/` —
     * so stale `.emb` voice embeddings can't survive a bundle swap.
     *
     * Idempotent — calling uninstall on an engine that isn't installed is
     * a successful no-op.
     */
    suspend fun uninstall(engineName: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (engineName != PocketEngineCatalog.ENGINE_NAME) {
            return@withContext Result.failure(IOException("Unknown engine: $engineName"))
        }

        try {
            // Release first — deleting an mmap'd file can leak the mapping
            // on some Android versions, even though the file system entry
            // disappears immediately. The injected NativeEngineHandle
            // releases the loaded engine; release() is idempotent when the
            // engine isn't currently loaded.
            engineHandle.release()

            val dir = engineDirFor(engineName)
            if (dir.exists() && !dir.deleteRecursively()) {
                throw IOException("Could not delete ${dir.absolutePath}")
            }
            // Also clean any stale scratch dir + partial archive lying around.
            val scratch = scratchDirFor(engineName)
            if (scratch.exists()) scratch.deleteRecursively()
            val archiveTmp = archiveTmpFor(engineName)
            if (archiveTmp.exists()) archiveTmp.delete()

            stateFlow(engineName).value = InstallState.NotInstalled
            Result.success(Unit)
        } catch (t: Throwable) {
            logcat(LogPriority.WARN, t) { "Uninstall of $engineName failed" }
            Result.failure(if (t is IOException) t else IOException(t))
        }
    }

    /**
     * Inspect the on-disk engine bundle and return the matching state.
     * Cheap — does not re-hash files, only checks presence of the well-
     * known top-level layout. Used by the UI to populate the install card
     * on first composition.
     */
    suspend fun verify(engineName: String): InstallState {
        if (engineName != PocketEngineCatalog.ENGINE_NAME) return InstallState.NotInstalled
        // The expected sha is the source install() would try first today —
        // the populated Miko primary, else the fallback. When the Miko
        // mirror goes live, installs stamped with the fallback sha surface
        // as Outdated, which is exactly the "catalog changed server-side"
        // update prompt this check exists for.
        val expectedSource = PocketEngineCatalog.sources().first()
        return verifyDescriptor(PocketEngineCatalog.POCKET_TTS_EN, expectedSource)
    }

    /**
     * Test-friendly verify that operates against a caller-supplied expected
     * [expectedSource] instead of reading [PocketEngineCatalog.sources].
     * Production code paths must go through [verify].
     */
    internal suspend fun verifyDescriptor(
        descriptor: PocketEngineDescriptor,
        expectedSource: EngineArchiveSource,
    ): InstallState = withContext(Dispatchers.IO) {
        val engineName = descriptor.name
        val dir = engineDirFor(engineName)
        val computed = if (!dir.isDirectory) {
            InstallState.NotInstalled
        } else {
            verifyLayout(dir, expectedSource)
        }
        stateFlow(engineName).value = computed
        computed
    }

    // -- internals ---------------------------------------------------------

    private fun stateFlow(engineName: String): MutableStateFlow<InstallState> {
        synchronized(statesLock) {
            states[engineName]?.let { return it }
            // Initialise from disk so observers see a meaningful starting state.
            // verify() will refine it asynchronously, but for the synchronous
            // path used here we just check the engine directory exists.
            val initial = if (engineDirFor(engineName).isDirectory) {
                InstallState.Installed
            } else {
                InstallState.NotInstalled
            }
            val sf = MutableStateFlow<InstallState>(initial)
            states[engineName] = sf
            return sf
        }
    }

    private fun engineDirFor(engineName: String): File =
        File(filesDir.get(), "engines/$engineName")

    private fun scratchDirFor(engineName: String): File =
        File(filesDir.get(), "engines/$engineName.tmp")

    private fun archiveTmpFor(engineName: String): File =
        File(filesDir.get(), "engines/$engineName.archive.tmp")

    /**
     * Stream the archive at [url] to [target] via [httpFetcher], computing
     * SHA-256 incrementally and emitting throttled byte-count updates via
     * [onProgress].
     *
     * **Resume support.** If [target] already exists from a prior aborted
     * download, the request includes a `Range: bytes=N-` header and the
     * remote bytes are appended. The existing partial file is re-hashed
     * into the running SHA-256 first so the final digest still matches
     * the full archive. If the server doesn't honor Range (HTTP 200
     * response with the full body), the partial is discarded and the
     * download restarts from scratch — same final outcome, just without
     * the resume win.
     *
     * Progress is throttled to one emission per ~1% of [totalBytes] (or per
     * ~256 KB, whichever is coarser) so the StateFlow consumer isn't
     * flooded with thousands of updates per second on fast connections.
     *
     * @return hex-encoded SHA-256 of the downloaded bytes (lowercase).
     */
    private fun downloadArchive(
        url: String,
        target: File,
        totalBytes: Long,
        onProgress: (Long) -> Unit,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val emitThreshold = maxOf(totalBytes / 100L, PROGRESS_MIN_BYTES)
        // Look for a partial download from a previous attempt. If the file
        // exists and is non-empty we'll try to resume; the server may decline.
        val partialBytes = if (target.isFile) target.length() else 0L
        // A previous attempt may have downloaded the WHOLE archive and then
        // failed later (e.g. disk full during extraction — the catch keeps
        // the archive). Requesting `Range: bytes=<totalBytes>-` gets HTTP 416
        // from any spec-compliant server, which would brick every retry.
        // Skip the network entirely and just hash what's on disk: the SHA
        // check upstream accepts good bytes, and wipes bad/oversized ones so
        // the next attempt starts clean.
        if (totalBytes > 0L && partialBytes >= totalBytes) {
            logcat(LogPriority.DEBUG) {
                "Archive already fully on disk ($partialBytes bytes); skipping download"
            }
            target.inputStream().use { existing ->
                val rb = ByteArray(BUFFER_SIZE)
                while (true) {
                    val n = existing.read(rb)
                    if (n == -1) break
                    digest.update(rb, 0, n)
                }
            }
            onProgress(partialBytes)
            return digest.digest().toHex()
        }
        // Ask the server for the remaining bytes. The fetcher returns
        // `startedAtBytes` so we can tell whether the resume was honored
        // (206) or the server gave us the whole body (200).
        val rangeResult = httpFetcher.openRange(url, partialBytes)
        val rangeStart = rangeResult.startedAtBytes
        rangeResult.stream.use { input ->
            // Decide write mode based on whether the server honored Range.
            //   resumeOk = server responded 206 starting at our offset
            //   else     = server ignored Range; restart from zero
            val resumeOk = partialBytes > 0L && rangeStart == partialBytes
            if (resumeOk) {
                // Re-hash the existing partial bytes into the running digest.
                // Streaming through the same buffer keeps memory bounded; this
                // is one extra disk read of the partial file (typically tens
                // of MB at most), no extra network use.
                target.inputStream().use { existing ->
                    val rb = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = existing.read(rb)
                        if (n == -1) break
                        digest.update(rb, 0, n)
                    }
                }
                logcat(LogPriority.DEBUG) {
                    "Resuming download of $url from $partialBytes / $totalBytes bytes"
                }
            } else if (partialBytes > 0L) {
                // Range not honored — discard partial bytes; the server is
                // sending us the full body and writing into a non-empty file
                // would corrupt the archive.
                logcat(LogPriority.DEBUG) { "Server ignored Range for $url; restarting download" }
                target.delete()
            }
            var bytesSoFar = if (resumeOk) partialBytes else 0L
            var bytesSinceEmit = 0L
            // Append in resume mode, truncate-and-write otherwise.
            java.io.FileOutputStream(target, /* append = */ resumeOk).use { output ->
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buf)
                    if (read == -1) break
                    output.write(buf, 0, read)
                    digest.update(buf, 0, read)
                    bytesSoFar += read
                    bytesSinceEmit += read
                    if (bytesSinceEmit >= emitThreshold) {
                        onProgress(bytesSoFar)
                        bytesSinceEmit = 0L
                    }
                }
            }
            // Final emission so the bar reaches 100% before we flip to Extracting.
            onProgress(bytesSoFar)
        }
        return digest.digest().toHex()
    }

    /**
     * Extract a tar archive [archiveFile] into [destDir]. Strips the leading
     * [archiveRoot] prefix from each entry path (so an archive containing
     * `pocket-tts-en/bundle.json` produces `${destDir}/bundle.json`).
     *
     * Skips directory entries (parent dirs are mkdirs'd implicitly).
     * Rejects entries whose normalized path would escape [destDir]
     * (zip-slip protection). Compression format is sniffed from the magic
     * bytes: gzip and bzip2 are both accepted.
     */
    private fun extractArchive(
        archiveFile: File,
        destDir: File,
        archiveRoot: String,
        onProgress: (bytesExtracted: Long) -> Unit = {},
    ) {
        val destCanonical = destDir.canonicalPath
        // Throttle progress emissions to ~every 1 MB so the StateFlow isn't
        // flooded during the unpack of a 200+ MB engine bundle.
        var bytesWritten = 0L
        var bytesSinceLastEmit = 0L
        val emitThreshold = 1024L * 1024L
        BufferedInputStream(archiveFile.inputStream()).use { fileIn ->
            // Detect compression format by magic bytes:
            //   gzip:  0x1F 0x8B  (java.util.zip — native zlib, ~5× faster)
            //   bzip2: 'B' 'Z'    (Apache Commons — pure Java, slow)
            fileIn.mark(2)
            val b0 = fileIn.read()
            val b1 = fileIn.read()
            fileIn.reset()
            val decompressed: java.io.InputStream = when {
                b0 == 0x1F && b1 == 0x8B -> GZIPInputStream(fileIn)
                b0 == 'B'.code && b1 == 'Z'.code -> BZip2CompressorInputStream(fileIn)
                else -> throw IOException(
                    "Unrecognised archive format (first two bytes: " +
                        "0x${b0.toString(16)} 0x${b1.toString(16)}) — expected gzip or bzip2",
                )
            }
            decompressed.use { compIn ->
                TarArchiveInputStream(compIn).use { tarIn ->
                    while (true) {
                        val entry = tarIn.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val rawName = entry.name
                        // Strip the wrapper directory if it matches; otherwise
                        // keep the entry path as-is. Empty archiveRoot means
                        // "no stripping".
                        val relPath = when {
                            archiveRoot.isEmpty() -> rawName
                            rawName.startsWith(archiveRoot) -> rawName.removePrefix(archiveRoot)
                            else -> rawName
                        }
                        if (relPath.isEmpty()) continue

                        val outFile = File(destDir, relPath)
                        val outCanonical = outFile.canonicalPath
                        // Zip-slip: refuse any entry whose canonical path
                        // lands outside destDir. catches `../../etc/passwd`-
                        // style escapes in adversarial archives.
                        if (!outCanonical.startsWith(destCanonical + File.separator) &&
                            outCanonical != destCanonical
                        ) {
                            throw IOException(
                                "Tar entry escapes destination directory: $rawName " +
                                    "→ $outCanonical (dest=$destCanonical)",
                            )
                        }
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out ->
                            val buf = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val read = tarIn.read(buf)
                                if (read == -1) break
                                out.write(buf, 0, read)
                                bytesWritten += read
                                bytesSinceLastEmit += read
                                if (bytesSinceLastEmit >= emitThreshold) {
                                    onProgress(bytesWritten)
                                    bytesSinceLastEmit = 0L
                                }
                            }
                        }
                    }
                }
            }
        }
        // Final emission so the UI bar lands at the actual extracted total
        // before we transition to Installed (or, when the bundle's actual
        // unpacked size differs slightly from the descriptor's declared
        // installedSizeBytes, so the bar doesn't freeze short of 100%).
        onProgress(bytesWritten)
    }

    /**
     * Post-extract structural check. The meta-sha drift check runs first so
     * a catalog update surfaces as Outdated even when the layout is intact;
     * then the pocket layout check runs.
     */
    private fun verifyLayout(dir: File, expectedSource: EngineArchiveSource): InstallState {
        val outdated = checkInstallMeta(dir, expectedSource)
        if (outdated != null) return outdated
        return verifyPocketLayout(dir)
    }

    /**
     * Pocket TTS layout: 5 ONNX graphs + tokenizer.model +
     * bos_before_voice.npy + bundle.json + voices/ subdir with the six
     * commercial-safe base WAVs. Voice cloning lands files under
     * `voice_cache/`/`cloned_voices/` later; their absence at install time
     * is fine, and Miko's extra `vctk_*` WAVs are optional extras — present
     * in the Miko bundle, absent from the Marmalade fallback, required
     * neither way.
     */
    private fun verifyPocketLayout(dir: File): InstallState {
        // Always-present, variant-independent files. The 5 ONNX filenames are
        // variant-specific (declared in bundle.json's `onnx_files` block) and
        // checked separately below.
        val requiredFiles = listOf(
            "tokenizer.model",
            "bos_before_voice.npy",
            "bundle.json",
        )
        for (name in requiredFiles) {
            val f = File(dir, name)
            if (!f.isFile || f.length() == 0L) return InstallState.Corrupt
        }
        // Read the onnx filenames from bundle.json. Parse failure ⇒ corrupt.
        val bundleSpec = runCatching {
            PocketBundle.load(File(dir, "bundle.json"))
        }.getOrNull() ?: return InstallState.Corrupt
        val onnxFiles = bundleSpec.onnxFiles
        val onnxNames = listOf(
            onnxFiles.textConditioner,
            onnxFiles.mimiEncoder,
            onnxFiles.mimiDecoder,
            onnxFiles.flowLmMain,
            onnxFiles.flowLmFlow,
        )
        for (name in onnxNames) {
            val f = File(dir, name)
            if (!f.isFile || f.length() == 0L) return InstallState.Corrupt
        }
        // At least the flow_lm_main file should be appreciably-sized — guards
        // against a truncated extraction where the headers landed but the body
        // didn't (the sherpa MIN_MODEL_BYTES analogue). flow_lm_main is the
        // largest of the five graphs across all variants.
        val mainOnnx = File(dir, onnxFiles.flowLmMain)
        if (mainOnnx.length() < MIN_MODEL_BYTES) return InstallState.Corrupt

        val voicesDir = File(dir, "voices")
        if (!voicesDir.isDirectory) return InstallState.Corrupt
        // Check the six commercial-safe Kyutai predefined names match what the
        // voice catalog promises (PocketVoiceCatalog.baseVoices is the source
        // of truth — the six hardcoded names here mirror it). The upstream
        // `cosette` (Expresso) and `jean` (EARS) voices are CC-BY-NC-4.0 and
        // are deliberately absent. Miko's `vctk_*` extras are not required.
        val expectedVoices = listOf("alba", "azelma", "eponine", "fantine", "javert", "marius")
        for (name in expectedVoices) {
            val wav = File(voicesDir, "$name.wav")
            if (!wav.isFile || wav.length() == 0L) return InstallState.Corrupt
        }
        return InstallState.Installed
    }

    /**
     * Convenience to mark a state-flow as Failed and produce the matching
     * Result.failure in one place.
     */
    private fun failed(engineName: String, reason: String): Result<Unit> {
        stateFlow(engineName).value = InstallState.Failed(reason)
        return Result.failure(IOException(reason))
    }

    /**
     * Write `.install_meta.json` to the engine directory recording which
     * source produced the on-disk bundle. Called once after the atomic
     * rename, so a partial install never leaves a stale meta file.
     *
     * kotlinx-serialization instead of org.json so [verify] stays
     * JVM-testable (org.json lives in android.jar, not on the unit-test
     * classpath) — the same port choice PocketBundle made in plan 04-01.
     *
     * Failure to write is logged but non-fatal: the engine still works,
     * the only consequence is that the next [verifyLayout] will see a
     * missing meta and bootstrap it.
     */
    private fun writeInstallMeta(dir: File, source: EngineArchiveSource) {
        val meta = buildJsonObject {
            put("archive_sha256", source.sha256)
            put("archive_url", source.url)
        }
        try {
            File(dir, INSTALL_META_FILENAME).writeText(meta.toString())
        } catch (t: Throwable) {
            logcat(LogPriority.WARN, t) { "Failed to write install meta for ${dir.name}" }
        }
    }

    /**
     * Compare the on-disk install meta to the catalog's current expected
     * source. Returns an [InstallState.Outdated] when the recorded
     * `archive_sha256` differs from the expected source's sha. Returns
     * `null` when the install matches (caller proceeds with the structural
     * layout check).
     *
     * If the meta file is missing entirely (install predates meta-writing,
     * or the write failed), we bootstrap it from the expected source and
     * return `null`. The assumption is "the user installed this from the
     * current catalog at some point and the contents on disk reflect
     * whatever the catalog said *then*"; without a recorded sha we can't
     * prove otherwise, and flagging every pre-feature install as outdated
     * fires false positives.
     *
     * Cost of this bootstrap: if the catalog changed since the user's last
     * install, they won't see an "Update available" prompt and must go
     * through Uninstall + Install once to migrate. From the next install
     * forward, the meta is recorded correctly and future catalog updates
     * surface as Outdated normally.
     */
    private fun checkInstallMeta(dir: File, expectedSource: EngineArchiveSource): InstallState? {
        val metaFile = File(dir, INSTALL_META_FILENAME)
        val expected = expectedSource.sha256
        if (!metaFile.isFile) {
            writeInstallMeta(dir, expectedSource)
            return null
        }
        val recorded = runCatching {
            (Json.parseToJsonElement(metaFile.readText()) as JsonObject)
                .getValue("archive_sha256").jsonPrimitive.content
        }.getOrNull()
        if (recorded == null || recorded != expected) {
            return InstallState.Outdated(installedSha256 = recorded, expectedSha256 = expected)
        }
        return null
    }

    companion object {
        // 32 KB chunks are a good balance for HTTPS over a typical mobile
        // connection — small enough to keep the progress UI responsive,
        // large enough that the SHA-256 digest call dominates over loop
        // overhead.
        private const val BUFFER_SIZE = 32 * 1024

        // Minimum emit interval for download progress (256 KB). Throttles
        // the StateFlow at the high end when 1% of total is < 256 KB.
        private const val PROGRESS_MIN_BYTES: Long = 256L * 1024L

        // Sanity floor for the post-install layout check. Tuned to catch
        // truncated extractions without pinning to the exact upstream
        // numbers (which would force a code change every bundle bump).
        private const val MIN_MODEL_BYTES: Long = 1L * 1024L * 1024L

        // String shown in the per-engine progress UI while the archive is
        // downloading. A single archive download is the whole download phase.
        private const val ARCHIVE_PROGRESS_LABEL: String = "archive"

        // Filename recording which catalog archive produced the on-disk
        // bundle. Written once at install time, read by every verify pass
        // to detect catalog updates that don't change the install layout.
        // The leading dot keeps the file out of casual `ls`, signalling
        // "internal bookkeeping, not part of the model bundle."
        private const val INSTALL_META_FILENAME: String = ".install_meta.json"
    }
}

/** Lowercase hex encoding for SHA-256 output. */
private fun ByteArray.toHex(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        sb.append(HEX_CHARS[v ushr 4])
        sb.append(HEX_CHARS[v and 0x0F])
    }
    return sb.toString()
}

private val HEX_CHARS = "0123456789abcdef".toCharArray()
