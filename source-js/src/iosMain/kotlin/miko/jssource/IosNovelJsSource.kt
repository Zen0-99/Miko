package miko.jssource

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.decodeFromString
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
 * [NovelCatalogueSource] backed by an LNReader-style JS plugin running in
 * [JsRuntime] (JavaScriptCore on iOS). Mirrors the Android `NovelJsSource`
 * call surface: plugin functions `popularNovels`, `searchNovels`,
 * `latestNovels`, `parseNovel`, `parseChapter` and the `filters` descriptor.
 *
 * All plugin calls are serialized through [mutex] — the engine context is
 * single-threaded and re-entrant evaluation would corrupt the polling tokens.
 */
class IosNovelJsSource(
    private val runtimeProvider: () -> JsRuntime,
    private val script: String,
    private val pluginId: String,
    private val json: Json,
    override val id: Long,
    override val name: String,
    override val lang: String,
) : NovelCatalogueSource {

    private val mutex = Mutex()

    /**
     * The engine bootstraps lazily on first plugin call — installing many
     * plugins must not spin up a JSCore context per source at collection
     * time (mirrors Android's lazy NovelJsRuntimeBinder). Every call site
     * holds [mutex], so materialization stays single-threaded.
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

    // Mirrors Android's capabilities.latestListingMethod detection:
    // `typeof __plugin.latestNovels === "function"` -> latestNovels, else the
    // popularNovels listing doubles as the latest feed.
    private var hasLatestListing: Boolean? = null

    override val supportsLatest: Boolean = true

    override suspend fun getPopularNovels(page: Int): NovelsPage {
        val payload = mutex.withLock {
            runtime.callPluginFunction("popularNovels", listOf(page.toString(), optionsJson(false)))
        }
        return parseNovelsPage(payload)
    }

    override suspend fun getSearchNovels(
        page: Int,
        query: String,
        filters: NovelFilterList,
    ): NovelsPage {
        if (query.isBlank()) return getPopularNovels(page)
        val filtersJson = if (filters.isEmpty()) "{}" else buildFiltersJson(filters)
        val payload = mutex.withLock {
            runtime.callPluginFunction(
                "searchNovels",
                listOf(json.encodeToString(query), page.toString(), filtersJson),
            )
        }
        return parseNovelsPage(payload)
    }

    override suspend fun getLatestUpdates(page: Int): NovelsPage {
        val payload = mutex.withLock {
            val hasLatest = hasLatestListing ?: latestListingDetected().also { hasLatestListing = it }
            if (hasLatest) {
                runtime.callPluginFunction("latestNovels", listOf(page.toString(), optionsJson(true)))
            } else {
                runtime.callPluginFunction("popularNovels", listOf(page.toString(), optionsJson(true)))
            }
        }
        return parseNovelsPage(payload)
    }

    private fun latestListingDetected(): Boolean {
        val detected = runtime.evaluate(
            "typeof __plugin.latestNovels === \"function\"",
            "novel-plugin-capabilities.js",
        ) as? Boolean
        return detected == true
    }

    private fun optionsJson(showLatest: Boolean): String =
        """{"showLatestNovels":$showLatest,"filters":{}}"""

    override fun getFilterList(): NovelFilterList {
        // Filter schema parsing arrives with the filter UI (27-02); plugins
        // without filters expose an empty list.
        return NovelFilterList(emptyList())
    }

    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        val payload = mutex.withLock {
            runtime.callPluginFunction("parseNovel", listOf(json.encodeToString(novel.url)))
        }
        val parsed = parseNovelObject(payload) ?: return novel
        return novel.copy().apply {
            parsed.title?.let { title = it }
            parsed.author?.let { author = it }
            parsed.artist?.let { artist = it }
            parsed.description?.let { description = it }
            parsed.genre?.let { genre = it }
            parsed.status?.let { status = it }
            parsed.thumbnail?.let { thumbnail_url = it }
            initialized = true
        }
    }

    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        val payload = mutex.withLock {
            runtime.callPluginFunction("parseNovel", listOf(json.encodeToString(novel.url)))
        }
        val root = payload.toJsonObject() ?: return emptyList()
        return root.chapters().mapNotNull { it.toChapter() }
    }

    override suspend fun getChapterText(chapter: SNovelChapter): String {
        val payload = mutex.withLock {
            runtime.callPluginFunction("parseChapter", listOf(json.encodeToString(chapter.url)))
        }
        if (payload.isBlank() || payload == "null") return ""
        return runCatching { json.decodeFromString<String>(payload) }.getOrDefault(payload)
    }

    // ------------------------------------------------------------------
    // Payload parsing — key variants mirror NovelJsPayloadParser
    // (name|title, path|url, cover|thumbnail_url|thumbnail, ...)
    // ------------------------------------------------------------------

    private fun parseNovelsPage(payload: String): NovelsPage {
        val root = payload.toJsonObject()
        if (root != null) {
            val items = root["novels"]?.jsonArrayOrNull() ?: root["items"]?.jsonArrayOrNull()
            if (items != null) {
                val hasNext = root["hasNextPage"]?.primitiveOrNull()?.booleanOrNull
                    ?: root["hasNext"]?.primitiveOrNull()?.booleanOrNull
                    ?: (root["totalPages"]?.primitiveOrNull()?.longOrNull?.let { total ->
                        (root["page"]?.primitiveOrNull()?.longOrNull ?: 1L) < total
                    } ?: items.isNotEmpty())
                return NovelsPage(items.mapNotNull { it.toSNovel() }, hasNext)
            }
        }
        val array = payload.toJsonArray() ?: return NovelsPage(emptyList(), false)
        return NovelsPage(array.mapNotNull { it.toSNovel() }, array.isNotEmpty())
    }

    private fun parseNovelObject(payload: String): ParsedNovel? {
        val root = payload.toJsonObject() ?: return null
        return ParsedNovel(
            title = root.string("name", "title"),
            author = root.string("author"),
            artist = root.string("artist"),
            description = root.string("summary", "description"),
            genre = root.textOrJoin("genres", "genre"),
            status = root.string("status")?.toStatusInt(),
            thumbnail = root.string("cover", "thumbnail_url", "thumbnail"),
        )
    }

    private fun JsonObject.chapters(): List<JsonObject> {
        return listOf("chapters", "items").firstNotNullOfOrNull { key ->
            this[key]?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull() }
        } ?: emptyList()
    }

    private fun JsonElement.toSNovel(): SNovel? {
        val obj = jsonObjectOrNull() ?: return null
        val url = obj.string("path", "url") ?: return null
        return SNovel.create().apply {
            this.url = url
            title = obj.string("name", "title") ?: url
            author = obj.string("author")
            artist = obj.string("artist")
            description = obj.string("summary", "description")
            genre = obj.textOrJoin("genres", "genre")
            status = obj.string("status")?.toStatusInt() ?: SNovel.UNKNOWN
            thumbnail_url = obj.string("cover", "thumbnail_url", "thumbnail")
        }
    }

    private fun JsonElement.toChapter(): SNovelChapter? {
        val obj = jsonObjectOrNull() ?: return null
        val url = obj.string("path", "url") ?: return null
        return SNovelChapter.create().apply {
            this.url = url
            name = obj.string("name", "title") ?: url
            chapter_number = obj.number("chapterNumber", "chapter_number")?.toFloat() ?: -1f
            date_upload_raw = obj.string("releaseTime", "release_time")
            date_upload = parseChapterDate(date_upload_raw)
            scanlator = obj.string("scanlator")
        }
    }

    private fun String.toStatusInt(): Int {
        val normalized = trim().lowercase()
        return normalized.toIntOrNull()
            ?: when (normalized) {
                "ongoing" -> SNovel.ONGOING
                "completed" -> SNovel.COMPLETED
                "licensed" -> SNovel.LICENSED
                "publishing finished", "publishing_finished", "publishingfinished" ->
                    SNovel.PUBLISHING_FINISHED
                "cancelled", "canceled", "dropped" -> SNovel.CANCELLED
                "on hiatus", "on_hiatus", "hiatus" -> SNovel.ON_HIATUS
                else -> SNovel.UNKNOWN
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

    private fun buildFiltersJson(filters: NovelFilterList): String {
        // Minimal encoding: each filter contributes its state under its name —
        // extended serialization lands with the filter UI.
        val entries = filters.map { filter ->
            val encoded = encodeFilterState(filter.state)
            "\"${filter.name.replace("\"", "\\\"")}\":$encoded"
        }
        return entries.joinToString(prefix = "{", postfix = "}")
    }

    private fun encodeFilterState(state: Any?): String = when (state) {
        null -> "null"
        is String -> json.encodeToString(state)
        is Boolean, is Int, is Long, is Double, is Float -> state.toString()
        is NovelFilter.Sort.Selection ->
            """{"index":${state.index},"ascending":${state.ascending}}"""
        is List<*> -> state.joinToString(prefix = "[", postfix = "]") { encodeFilterState(it) }
        else -> json.encodeToString(state.toString())
    }

    private fun String.toJsonObject(): JsonObject? =
        runCatching { json.parseToJsonElement(this).jsonObjectOrNull() }.getOrNull()

    private fun String.toJsonArray(): JsonArray? =
        runCatching { json.parseToJsonElement(this).jsonArrayOrNull() }.getOrNull()

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

    private data class ParsedNovel(
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: String? = null,
        val status: Int? = null,
        val thumbnail: String? = null,
    )
}
