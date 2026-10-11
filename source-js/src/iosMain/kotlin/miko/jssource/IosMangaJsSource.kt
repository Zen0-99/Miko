package miko.jssource

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * [CatalogueSource] backed by a Miko JS plugin (kind=manga) running in
 * [JsRuntime]. Contract mirrors the novel bridge: `popularManga`,
 * `searchManga`, `latestManga`, `parseManga` (returns details + `chapters`
 * in one payload, same shape as LNReader's parseNovel) and `pageList`.
 *
 * All plugin calls are serialized through [mutex] — the engine context is
 * single-threaded and re-entrant evaluation would corrupt the polling tokens.
 */
class IosMangaJsSource(
    private val runtimeProvider: () -> JsRuntime,
    private val script: String,
    private val pluginId: String,
    private val json: Json,
    override val id: Long,
    override val name: String,
    override val lang: String,
) : CatalogueSource {

    private val mutex = Mutex()

    /**
     * Lazy bootstrap — installing many plugins must not spin up a JSCore
     * context per source at collection time.
     */
    private var runtimeOrNull: JsRuntime? = null

    private val runtime: JsRuntime
        get() = runtimeOrNull ?: runtimeProvider().also {
            it.loadPlugin(script, pluginId)
            runtimeOrNull = it
        }

    /** Releases the engine if it was ever materialized. */
    fun shutdown() {
        runtimeOrNull?.release()
        runtimeOrNull = null
    }

    // latestManga is optional — when absent, the popular listing doubles as
    // the latest feed (same fallback as the novel bridge's latestNovels).
    private var hasLatestListing: Boolean? = null

    override val supportsLatest: Boolean = true

    override suspend fun getPopularManga(page: Int): MangasPage {
        val payload = mutex.withLock {
            runtime.callPluginFunction("popularManga", listOf(page.toString(), optionsJson(false)))
        }
        return parseMangasPage(payload)
    }

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        if (query.isBlank()) return getPopularManga(page)
        val payload = mutex.withLock {
            runtime.callPluginFunction(
                "searchManga",
                listOf(json.encodeToString(query), page.toString(), "{}"),
            )
        }
        return parseMangasPage(payload)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val payload = mutex.withLock {
            val hasLatest = hasLatestListing ?: latestListingDetected().also { hasLatestListing = it }
            if (hasLatest) {
                runtime.callPluginFunction("latestManga", listOf(page.toString(), optionsJson(true)))
            } else {
                runtime.callPluginFunction("popularManga", listOf(page.toString(), optionsJson(true)))
            }
        }
        return parseMangasPage(payload)
    }

    private fun latestListingDetected(): Boolean {
        val detected = runtime.evaluate(
            "typeof __plugin.latestManga === \"function\"",
            "manga-plugin-capabilities.js",
        ) as? Boolean
        return detected == true
    }

    private fun optionsJson(showLatest: Boolean): String =
        """{"showLatestNovels":$showLatest,"filters":{}}"""

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val payload = mutex.withLock {
            runtime.callPluginFunction("parseManga", listOf(json.encodeToString(manga.url)))
        }
        val root = payload.toJsonObject() ?: return manga
        return manga.copy().apply {
            root.string("name", "title")?.let { title = it }
            root.string("author")?.let { author = it }
            root.string("artist")?.let { artist = it }
            root.string("summary", "description")?.let { description = it }
            root.textOrJoin("genres", "genre")?.let { genre = it }
            root.string("status")?.toStatusInt()?.let { status = it }
            root.string("cover", "thumbnail_url", "thumbnail")?.let { thumbnail_url = it }
            initialized = true
        }
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val payload = mutex.withLock {
            runtime.callPluginFunction("parseManga", listOf(json.encodeToString(manga.url)))
        }
        val root = payload.toJsonObject() ?: return emptyList()
        return root.chapters().mapNotNull { it.toChapter() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val payload = mutex.withLock {
            runtime.callPluginFunction("pageList", listOf(json.encodeToString(chapter.url)))
        }
        if (payload.isBlank() || payload == "null") return emptyList()
        return payload.toJsonArray().orEmpty().mapIndexedNotNull { index, element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull?.let { Page(index = index, imageUrl = it) }
                is JsonObject -> {
                    val imageUrl = element.string("imageUrl", "image", "url", "src")
                        ?: return@mapIndexedNotNull null
                    val pageUrl = element.string("pageUrl", "referer", "referrer") ?: ""
                    Page(index = index, url = pageUrl, imageUrl = imageUrl)
                }
                else -> null
            }
        }
    }

    override fun getFilterList(): FilterList = FilterList()

    // ------------------------------------------------------------------
    // Payload parsing — same key variants as the novel bridge.
    // ------------------------------------------------------------------

    private fun parseMangasPage(payload: String): MangasPage {
        val root = payload.toJsonObject()
        if (root != null) {
            val items = root["mangas"]?.jsonArrayOrNull()
                ?: root["novels"]?.jsonArrayOrNull()
                ?: root["items"]?.jsonArrayOrNull()
            if (items != null) {
                val hasNext = root["hasNextPage"]?.primitiveOrNull()?.booleanOrNull
                    ?: root["hasNext"]?.primitiveOrNull()?.booleanOrNull
                    ?: (root["totalPages"]?.primitiveOrNull()?.longOrNull?.let { total ->
                        (root["page"]?.primitiveOrNull()?.longOrNull ?: 1L) < total
                    } ?: items.isNotEmpty())
                return MangasPage(items.mapNotNull { it.toSManga() }, hasNext)
            }
        }
        val array = payload.toJsonArray() ?: return MangasPage(emptyList(), false)
        return MangasPage(array.mapNotNull { it.toSManga() }, array.isNotEmpty())
    }

    private fun JsonObject.chapters(): List<JsonObject> {
        return listOf("chapters", "items").firstNotNullOfOrNull { key ->
            this[key]?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull() }
        } ?: emptyList()
    }

    private fun JsonElement.toSManga(): SManga? {
        val obj = jsonObjectOrNull() ?: return null
        val url = obj.string("path", "url") ?: return null
        return SManga.create().apply {
            this.url = url
            title = obj.string("name", "title") ?: url
            author = obj.string("author")
            artist = obj.string("artist")
            description = obj.string("summary", "description")
            genre = obj.textOrJoin("genres", "genre")
            status = obj.string("status")?.toStatusInt() ?: SManga.UNKNOWN
            thumbnail_url = obj.string("cover", "thumbnail_url", "thumbnail")
        }
    }

    private fun JsonElement.toChapter(): SChapter? {
        val obj = jsonObjectOrNull() ?: return null
        val url = obj.string("path", "url") ?: return null
        return SChapter.create().apply {
            this.url = url
            name = obj.string("name", "title") ?: url
            chapter_number = obj.number("chapterNumber", "chapter_number")?.toFloat() ?: -1f
            date_upload = parseChapterDate(obj.string("releaseTime", "release_time"))
            scanlator = obj.string("scanlator")
        }
    }

    private fun String.toStatusInt(): Int {
        val normalized = trim().lowercase()
        return normalized.toIntOrNull()
            ?: when (normalized) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "licensed" -> SManga.LICENSED
                "publishing finished", "publishing_finished", "publishingfinished" ->
                    SManga.PUBLISHING_FINISHED
                "cancelled", "canceled", "dropped" -> SManga.CANCELLED
                "on hiatus", "on_hiatus", "hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
    }

    private fun parseChapterDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(value).toEpochMilliseconds() }
            .getOrElse {
                runCatching {
                    LocalDate.parse(value).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                }.getOrElse { 0L }
            }
    }

    private fun String.toJsonObject(): JsonObject? =
        runCatching { json.parseToJsonElement(this).jsonObjectOrNull() }.getOrNull()

    private fun String.toJsonArray(): JsonArray? =
        runCatching { json.parseToJsonElement(this).jsonArrayOrNull() }.getOrNull()

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement.primitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    private fun JsonObject.string(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { this[it]?.primitiveOrNull()?.contentOrNull }

    /** Plugins emit genres either as a string or as a string array — join arrays. */
    private fun JsonObject.textOrJoin(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            when (val element = this[key]) {
                is JsonArray -> element.mapNotNull { it.primitiveOrNull()?.contentOrNull }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")
                else -> element?.primitiveOrNull()?.contentOrNull
            }
        }

    private fun JsonObject.number(vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { this[it]?.primitiveOrNull()?.doubleOrNull }
}
