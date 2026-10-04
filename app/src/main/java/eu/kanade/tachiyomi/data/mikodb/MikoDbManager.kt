package eu.kanade.tachiyomi.data.mikodb

import android.content.Context
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream

/**
 * Syncs the MikoDB identity artifacts + binding metadata from extension repos.
 *
 * A repo that ships a `miko.json` sidecar (see MikoDB repo) gets its declared
 * artifacts (`db` = anime map, `mangaDb` = manga/novel map) downloaded to
 * local storage whenever the sha256 changes. Miko only ever talks to the
 * repo host (GitHub) — no extra backends.
 */
class MikoDbManager(
    private val context: Context,
) {

    @Serializable
    data class MikoJson(
        val db: DbInfo? = null,
        val mangaDb: DbInfo? = null,
        val sources: Map<String, SourceMeta> = emptyMap(),
        @SerialName("manga_sources")
        val mangaSources: Map<String, SourceMeta> = emptyMap(),
        @SerialName("novel_sources")
        val novelSources: Map<String, SourceMeta> = emptyMap(),
    )

    @Serializable
    data class DbInfo(
        val version: String,
        val sha256: String,
        val rows: Int = 0,
        val url: String,
        /** Preserved raw upstream dump (manga map only) — rebuild input if MangaBaka dies. */
        val sourceDump: ArtifactRef? = null,
    )

    @Serializable
    data class ArtifactRef(
        val sha256: String,
        val url: String,
    )

    @Serializable
    data class SourceMeta(
        val name: String = "",
        val tier: String = "",
        val idType: String = "",
        val numbering: String = "",
        val baseUrl: String = "",
        /** MangaBaka/manga-map provider ids this source serves (golden-source joins). */
        val providers: List<String> = emptyList(),
        val lang: String = "",
    )

    private val networkHelper: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val json: Json by injectLazy()

    private val dbDir get() = File(context.filesDir, "mikodb")
    private val mikoJsonFile get() = File(dbDir, "miko.json")

    private fun dbFile(artifact: String) = File(dbDir, artifactFileName(artifact))
    private fun shaPref(artifact: String) = preferenceStore.getString(
        if (artifact == "anime") "mikodb_sha256" else "mikodb_sha256_$artifact",
        "",
    )

    @Volatile private var animeBridge: AnimeIdBridge? = null

    @Volatile private var sourcesCache: Map<String, SourceMeta>? = null

    private val syncMutex = Mutex()

    /** Listeners fired when a named artifact is replaced on disk (close open handles). */
    private val swapListeners = CopyOnWriteArrayList<(String) -> Unit>()

    fun onArtifactSwapped(listener: (String) -> Unit) {
        swapListeners += listener
    }

    /**
     * Best-effort sync for a repo base URL. Silent no-op when the repo has no
     * miko.json sidecar, the sha matches the stored artifact, or anything fails.
     */
    suspend fun trySync(repoBaseUrl: String) = withIOContext {
        syncMutex.withLock {
            val base = repoBaseUrl.trim().trimEnd('/')
                .removeSuffix("/index.min.json").removeSuffix("/index.pb").removeSuffix("/index.pb.gz")
            val miko = try {
                with(json) {
                    networkHelper.client.newCall(GET("$base/miko.json"))
                        .awaitSuccess()
                        .parseAs<MikoJson>()
                }
            } catch (_: Exception) {
                return@withLock // not a MikoDB repo — normal extension repo
            }

            // Cache the sidecar for source metadata regardless of db freshness
            try {
                mikoJsonFile.parentFile?.mkdirs()
                mikoJsonFile.writeText(json.encodeToString(miko))
            } catch (
                _: Exception,
            ) {}
            sourcesCache = miko.sources + miko.mangaSources + miko.novelSources

            for ((artifact, info) in declaredArtifacts(miko)) {
                if (info.sha256 == shaPref(artifact).get() && dbFile(artifact).exists()) continue
                try {
                    downloadArtifact(artifact, info)
                    shaPref(artifact).set(info.sha256)
                    logcat(LogPriority.INFO) {
                        "MikoDB artifact updated: $artifact ${info.version} (${info.rows} rows)"
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "MikoDB artifact download failed from ${info.url}" }
                }
            }
        }
    }

    /**
     * The ~170 MB manga/novel map is opt-in — identity normally resolves
     * per-work through `works_cache` + the Worker. This pref gates the
     * full offline download.
     */
    private val basePreferences: eu.kanade.domain.base.BasePreferences by injectLazy()

    private fun declaredArtifacts(miko: MikoJson): Map<String, DbInfo> = buildMap {
        miko.db?.let { put("anime", it) }
        if (basePreferences.offlineMangaMap().get()) {
            miko.mangaDb?.let { put("manga", it) }
        }
    }

    private suspend fun downloadArtifact(artifact: String, info: DbInfo) {
        val body = networkHelper.client.newCall(GET(info.url)).awaitSuccess().body
        dbDir.mkdirs()

        val gzTmp = File(dbDir, "$artifact-artifact.dl.gz")
        gzTmp.outputStream().use { body.byteStream().copyTo(it) }

        val digest = sha256Hex(gzTmp)
        if (info.sha256.isNotBlank() && !digest.equals(info.sha256, ignoreCase = true)) {
            gzTmp.delete()
            throw IllegalStateException("MikoDB artifact sha256 mismatch: got $digest, want ${info.sha256}")
        }

        val outTmp = File(dbDir, "${artifactFileName(artifact)}.dl")
        if (info.url.endsWith(".gz")) {
            GZIPInputStream(gzTmp.inputStream()).use { g -> outTmp.outputStream().use(g::copyTo) }
        } else {
            gzTmp.renameTo(outTmp)
        }
        gzTmp.delete()

        if (outTmp.length() == 0L) {
            outTmp.delete()
            throw IllegalStateException("MikoDB artifact decompressed to empty file")
        }
        if (artifact == "anime") {
            animeBridge?.close()
            animeBridge = null
        }
        outTmp.renameTo(dbFile(artifact))
        if (artifact == "manga") {
            // The bridge's fullDb() holds an open handle — post-swap it
            // would keep serving the unlinked old map (stale titles,
            // missing alt-titles → identity-gate rejects everywhere).
            Injekt.get<MangaNovelIdBridge>().invalidateFullDb()
        }
        swapListeners.forEach { it(artifact) }
    }

    private fun sha256Hex(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Per-extension binding metadata from the last synced miko.json. */
    fun sourceMeta(pkgName: String): SourceMeta? {
        sourcesCache?.let { return it[pkgName] }
        val miko = try {
            mikoJsonFile.takeIf { it.exists() }?.let { json.decodeFromString<MikoJson>(it.readText()) }
        } catch (_: Exception) {
            null
        }
        sourcesCache = miko?.let { it.sources + it.mangaSources + it.novelSources }
        return sourcesCache?.get(pkgName)
    }

    /** All tiered source bindings from the last synced miko.json. */
    fun allSourceMeta(): Map<String, SourceMeta> {
        sourceMeta("") // warm/ensure sourcesCache is populated
        return sourcesCache.orEmpty()
    }

    /** Open (and cache) the local anime identity DB. Null until a MikoDB repo has synced. */
    fun bridge(): AnimeIdBridge? {
        animeBridge?.let { return it }
        return synchronized(this) {
            animeBridge ?: AnimeIdBridge.open(dbFile("anime"))?.also { animeBridge = it }
        }
    }

    /** Local file for the manga/novel map — may not exist until downloaded. */
    fun mangaMapFile(): File = dbFile("manga")

    companion object {
        fun artifactFileName(artifact: String) = when (artifact) {
            "manga" -> "miko-manga-map.db"
            else -> "miko-anime-map.db"
        }
    }
}
