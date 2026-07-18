package eu.kanade.tachiyomi.extension.novel.js

import android.content.Context
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelUpdateStrategy
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Adapter that wraps a JS novel plugin and exposes it as a [NovelCatalogueSource].
 *
 * The adapter loads the plugin script into a [NovelJsRuntime], then delegates
 * source operations (popular, latest, search, details, chapters, chapter text)
 * to the plugin's JS methods via JSON serialization.
 *
 * LNReader plugin method mapping:
 * - `popularNovels(page)` → getPopularNovels
 * - `searchNovels(page, query)` → getSearchNovels
 * - `latestNovels(page)` → getLatestUpdates (if supported)
 * - `novelDetails(novelUrl)` → getNovelDetails
 * - `novelChapters(novelUrl)` → getChapterList
 * - `chapterText(chapterUrl)` → getChapterText
 *
 * The runtime is lazily initialized on first use and cached for subsequent calls.
 * Each source instance has its own runtime (QuickJS is not thread-safe).
 *
 * Note: Unlike APK extensions that extend [NovelHttpSource], JS plugins implement
 * [NovelCatalogueSource] directly because networking is handled internally by
 * the plugin via the [NovelJsRuntime.NativeApi] bridge, not via OkHttp requests
 * constructed by the source adapter.
 *
 * The [baseUrl] property is provided for compatibility with backup source matching
 * ([eu.kanade.tachiyomi.data.backup.restore.SourceIdMapper]).
 */
class NovelJsSource(
    private val plugin: NovelPlugin.Installed,
    private val context: Context,
    private val storageDir: File,
) : NovelCatalogueSource {

    override val id: Long = NovelPluginId.toSourceId(plugin.id)
    override val name: String = plugin.name
    override val lang: String = plugin.lang

    /**
     * Base URL of the plugin's site. Used for backup source matching.
     * JS plugins manage their own URLs internally, so this is informational.
     */
    val baseUrl: String = plugin.site

    override val supportsLatest: Boolean
        get() = runBlocking { withRuntime { it.hasMethod("latestNovels") } }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val runtimeMutex = Mutex()
    private var runtime: NovelJsRuntime? = null

    private suspend fun <T> withRuntime(block: (NovelJsRuntime) -> T): T {
        return runtimeMutex.withLock {
            if (runtime == null) {
                val rt = NovelJsRuntime(plugin.id, context, storageDir)
                val script = File(plugin.filePath).readText()
                rt.loadPlugin(script, plugin.name)
                runtime = rt
            }
            block(runtime!!)
        }
    }

    override suspend fun getPopularNovels(page: Int): NovelsPage {
        return withRuntime { rt ->
            val result = rt.callPluginMethod("popularNovels", page.toString())
                ?: throw IllegalStateException("Plugin returned null for popularNovels")
            parseNovelsPage(result)
        }
    }

    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        return withRuntime { rt ->
            val queryJson = json.encodeToString(JsonPrimitive(query))
            val result = rt.callPluginMethod("searchNovels", page.toString(), queryJson)
                ?: throw IllegalStateException("Plugin returned null for searchNovels")
            parseNovelsPage(result)
        }
    }

    override suspend fun getLatestUpdates(page: Int): NovelsPage {
        return withRuntime { rt ->
            val result = rt.callPluginMethod("latestNovels", page.toString())
                ?: throw IllegalStateException("Plugin returned null for latestNovels")
            parseNovelsPage(result)
        }
    }

    override fun getFilterList(): NovelFilterList = NovelFilterList()

    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        return withRuntime { rt ->
            val urlJson = json.encodeToString(JsonPrimitive(novel.url))
            val result = rt.callPluginMethodRaw("novelDetails", urlJson)
                ?: return@withRuntime novel
            parseNovelDetails(result, novel)
        }
    }

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        return withRuntime { rt ->
            val urlJson = json.encodeToString(JsonPrimitive(novel.url))
            val result = rt.callPluginMethodRaw("novelChapters", urlJson)
                ?: return@withRuntime emptyList()
            parseChapterList(result)
        }
    }

    override suspend fun getChapterText(chapter: SNovelChapter): String {
        return withRuntime { rt ->
            val urlJson = json.encodeToString(JsonPrimitive(chapter.url))
            val result = rt.callPluginMethodRaw("chapterText", urlJson)
                ?: return@withRuntime ""
            try {
                val element = json.parseToJsonElement(result)
                element.jsonPrimitive.contentOrNull ?: ""
            } catch (e: Exception) {
                result
            }
        }
    }

    // --- Parsing helpers ---

    private fun parseNovelsPage(resultJson: String): NovelsPage {
        val obj = json.parseToJsonElement(resultJson).jsonObject
        val novels = obj["novels"]?.jsonArray?.map { parseNovelItem(it.jsonObject) } ?: emptyList()
        val hasNextPage = obj["hasNextPage"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
        return NovelsPage(novels, hasNextPage)
    }

    private fun parseNovelItem(obj: JsonObject): SNovel {
        return SNovel.create().apply {
            url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
            title = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
            author = obj["author"]?.jsonPrimitive?.contentOrNull
            artist = obj["artist"]?.jsonPrimitive?.contentOrNull
            description = obj["description"]?.jsonPrimitive?.contentOrNull
            genre = obj["genre"]?.jsonPrimitive?.contentOrNull
            thumbnail_url = obj["cover"]?.jsonPrimitive?.contentOrNull
                ?: obj["thumbnail"]?.jsonPrimitive?.contentOrNull
            status = parseStatus(obj["status"]?.jsonPrimitive?.contentOrNull)
            update_strategy = NovelUpdateStrategy.ALWAYS_UPDATE
            initialized = true
        }
    }

    private fun parseNovelDetails(resultJson: String, existing: SNovel): SNovel {
        return try {
            val obj = json.parseToJsonElement(resultJson).jsonObject
            existing.apply {
                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: title
                author = obj["author"]?.jsonPrimitive?.contentOrNull ?: author
                artist = obj["artist"]?.jsonPrimitive?.contentOrNull ?: artist
                description = obj["description"]?.jsonPrimitive?.contentOrNull ?: description
                genre = obj["genre"]?.jsonPrimitive?.contentOrNull ?: genre
                    ?: (obj["genres"] as? JsonArray)?.joinToString(", ") { it.jsonPrimitive.content }
                thumbnail_url = obj["cover"]?.jsonPrimitive?.contentOrNull
                    ?: obj["thumbnail"]?.jsonPrimitive?.contentOrNull ?: thumbnail_url
                status = parseStatus(obj["status"]?.jsonPrimitive?.contentOrNull)
                initialized = true
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "[${plugin.id}] Failed to parse novel details" }
            existing
        }
    }

    private fun parseChapterList(resultJson: String): List<SNovelChapter> {
        return try {
            val arr = json.parseToJsonElement(resultJson).jsonArray
            arr.mapIndexed { index, element ->
                val obj = element.jsonObject
                SNovelChapter.create().apply {
                    url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    chapter_number = (obj["chapterNumber"]?.jsonPrimitive?.intOrNull
                        ?: (index + 1)).toFloat()
                    // Parse date if available (ISO string → epoch millis)
                    val dateStr = obj["releaseTime"]?.jsonPrimitive?.contentOrNull
                        ?: obj["date"]?.jsonPrimitive?.contentOrNull
                    date_upload = parseDateToEpoch(dateStr)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "[${plugin.id}] Failed to parse chapter list" }
            emptyList()
        }
    }

    private fun parseDateToEpoch(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (e: Exception) {
            try {
                java.time.LocalDate.parse(dateStr)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant().toEpochMilli()
            } catch (e2: Exception) {
                0L
            }
        }
    }

    private fun parseStatus(statusStr: String?): Int {
        return when (statusStr?.lowercase()?.trim()) {
            "ongoing" -> SNovel.ONGOING
            "completed" -> SNovel.COMPLETED
            "licensed" -> SNovel.LICENSED
            "publishing finished" -> SNovel.PUBLISHING_FINISHED
            "cancelled" -> SNovel.CANCELLED
            "on hiatus" -> SNovel.ON_HIATUS
            else -> SNovel.UNKNOWN
        }
    }

    fun close() {
        runtime?.close()
        runtime = null
    }
}
