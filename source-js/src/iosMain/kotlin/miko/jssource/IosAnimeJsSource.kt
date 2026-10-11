package miko.jssource

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
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
 * [AnimeCatalogueSource] backed by a Miko JS plugin (kind=anime/show) in
 * [JsRuntime]. Contract mirrors the novel bridge: `popularAnime`,
 * `searchAnime`, `latestAnime`, `parseAnime` (details + `episodes` +
 * optional `seasons` in one payload, LNReader-style), `videoList` and the
 * optional `hosterList`.
 *
 * All plugin calls are serialized through [mutex] — the engine context is
 * single-threaded and re-entrant evaluation would corrupt the polling tokens.
 */
class IosAnimeJsSource(
    private val runtimeProvider: () -> JsRuntime,
    private val script: String,
    private val pluginId: String,
    private val json: Json,
    override val id: Long,
    override val name: String,
    override val lang: String,
) : AnimeCatalogueSource {

    private val mutex = Mutex()

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

    private var hasLatestListing: Boolean? = null

    override val supportsLatest: Boolean = true

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val payload = mutex.withLock {
            runtime.callPluginFunction("popularAnime", listOf(page.toString(), optionsJson(false)))
        }
        return parseAnimesPage(payload)
    }

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage {
        val payload = mutex.withLock {
            runtime.callPluginFunction(
                "searchAnime",
                listOf(
                    json.encodeToString(query),
                    page.toString(),
                    animeFilterValuesJson(filters),
                ),
            )
        }
        return parseAnimesPage(payload)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val payload = mutex.withLock {
            val hasLatest = hasLatestListing ?: latestListingDetected().also { hasLatestListing = it }
            if (hasLatest) {
                runtime.callPluginFunction("latestAnime", listOf(page.toString(), optionsJson(true)))
            } else {
                runtime.callPluginFunction("popularAnime", listOf(page.toString(), optionsJson(true)))
            }
        }
        return parseAnimesPage(payload)
    }

    private fun latestListingDetected(): Boolean {
        val detected = runtime.evaluate(
            "typeof __plugin.latestAnime === \"function\"",
            "anime-plugin-capabilities.js",
        ) as? Boolean
        return detected == true
    }

    private fun optionsJson(showLatest: Boolean): String =
        """{"showLatestNovels":$showLatest,"filters":{}}"""

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> {
        val payload = mutex.withLock {
            runtime.callPluginFunction("parseAnime", listOf(json.encodeToString(anime.url)))
        }
        val root = payload.toJsonObject() ?: return listOf(anime)
        val seasons = root["seasons"]?.jsonArrayOrNull()?.mapNotNull { it.toSAnime() }
        return seasons?.takeIf { it.isNotEmpty() } ?: listOf(anime)
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val payload = mutex.withLock {
            runtime.callPluginFunction("parseAnime", listOf(json.encodeToString(anime.url)))
        }
        val root = payload.toJsonObject() ?: return anime
        return anime.copy().apply {
            root.string("name", "title")?.let { title = it }
            root.string("author")?.let { author = it }
            root.string("artist")?.let { artist = it }
            root.string("summary", "description")?.let { description = it }
            root.textOrJoin("genres", "genre")?.let { genre = it }
            root.string("status")?.toStatusInt()?.let { status = it }
            root.string("cover", "thumbnail_url", "thumbnail")?.let { thumbnail_url = it }
            root.string("background", "background_url", "banner")?.let { background_url = it }
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val payload = mutex.withLock {
            runtime.callPluginFunction("parseAnime", listOf(json.encodeToString(anime.url)))
        }
        val root = payload.toJsonObject() ?: return emptyList()
        return root.episodes().mapNotNull { it.toEpisode() }
    }

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val detected = mutex.withLock {
            val hasHosters = runtime.evaluate(
                "typeof __plugin.hosterList === \"function\"",
                "anime-plugin-capabilities.js",
            ) as? Boolean
            if (hasHosters != true) return@withLock null
            runtime.callPluginFunction("hosterList", listOf(json.encodeToString(episode.url)))
        }
        val array = detected?.toJsonArray() ?: return noHosterFallback(episode)
        val hosters = array.mapNotNull { it.toHoster() }
        return hosters.takeIf { it.isNotEmpty() } ?: noHosterFallback(episode)
    }

    /**
     * Plugin has no `hosterList` — the episode's video list IS the hoster
     * list (Aniyomi's `NO_HOSTER_LIST` convention), resolved lazily via
     * `videoList` on the episode url carried in [Hoster.internalData].
     */
    private fun noHosterFallback(episode: SEpisode): List<Hoster> = listOf(
        Hoster(
            hosterName = Hoster.NO_HOSTER_LIST,
            internalData = episode.url,
            lazy = true,
        ),
    )

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val payload = mutex.withLock {
            runtime.callPluginFunction("videoList", listOf(json.encodeToString(episode.url)))
        }
        return parseVideoList(payload)
    }

    override suspend fun getVideoList(hoster: Hoster): List<Video> {
        val target = hoster.internalData.ifBlank { hoster.hosterUrl }
        if (target.isBlank()) return emptyList()
        val payload = mutex.withLock {
            runtime.callPluginFunction("videoList", listOf(json.encodeToString(target)))
        }
        return parseVideoList(payload)
    }

    override fun getFilterList(): AnimeFilterList {
        // Fresh parse per call; the carrier source holds the mutable list.
        val payload = runCatching {
            runtime.evaluate(
                "JSON.stringify(__plugin && __plugin.filters ? __plugin.filters : {})",
                "anime-plugin-filters.js",
            ) as? String
        }.getOrNull()
        return jsDefsToAnimeFilterList(parseJsFilterDefs(payload, json))
    }

    // ------------------------------------------------------------------
    // Payload parsing — same key variants as the novel bridge.
    // ------------------------------------------------------------------

    private fun parseAnimesPage(payload: String): AnimesPage {
        val root = payload.toJsonObject()
        if (root != null) {
            val items = root["animes"]?.jsonArrayOrNull()
                ?: root["novels"]?.jsonArrayOrNull()
                ?: root["items"]?.jsonArrayOrNull()
            if (items != null) {
                val hasNext = root["hasNextPage"]?.primitiveOrNull()?.booleanOrNull
                    ?: root["hasNext"]?.primitiveOrNull()?.booleanOrNull
                    ?: (root["totalPages"]?.primitiveOrNull()?.longOrNull?.let { total ->
                        (root["page"]?.primitiveOrNull()?.longOrNull ?: 1L) < total
                    } ?: items.isNotEmpty())
                return AnimesPage(items.mapNotNull { it.toSAnime() }, hasNext)
            }
        }
        val array = payload.toJsonArray() ?: return AnimesPage(emptyList(), false)
        return AnimesPage(array.mapNotNull { it.toSAnime() }, array.isNotEmpty())
    }

    private fun parseVideoList(payload: String): List<Video> {
        if (payload.isBlank() || payload == "null") return emptyList()
        val array = payload.toJsonArray() ?: return emptyList()
        return array.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull?.let {
                    Video(videoUrl = it, videoTitle = "")
                }
                is JsonObject -> {
                    val url = element.string("videoUrl", "url", "src", "link")
                        ?: return@mapNotNull null
                    Video(
                        videoUrl = url,
                        videoTitle = element.string("title", "name", "label") ?: "",
                        resolution = element.number("resolution", "height")?.toInt(),
                        preferred = element["preferred"]?.primitiveOrNull()?.booleanOrNull ?: false,
                    )
                }
                else -> null
            }
        }
    }

    private fun JsonElement.toHoster(): Hoster? {
        val obj = jsonObjectOrNull() ?: return null
        val url = obj.string("hosterUrl", "url", "link") ?: return null
        val embeddedVideos = obj["videos"]?.jsonArrayOrNull()
            ?.let { parseVideoList(it.toString()) }
        return Hoster(
            hosterUrl = url,
            hosterName = obj.string("name", "hosterName", "title") ?: url,
            videoList = embeddedVideos,
            internalData = obj.string("internalData", "data") ?: "",
            lazy = embeddedVideos == null,
        )
    }

    private fun JsonObject.episodes(): List<JsonObject> {
        return listOf("episodes", "chapters", "items").firstNotNullOfOrNull { key ->
            this[key]?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull() }
        } ?: emptyList()
    }

    private fun JsonElement.toSAnime(): SAnime? {
        val obj = jsonObjectOrNull() ?: return null
        val url = obj.string("path", "url") ?: return null
        return SAnime.create().apply {
            this.url = url
            title = obj.string("name", "title") ?: url
            author = obj.string("author")
            artist = obj.string("artist")
            description = obj.string("summary", "description")
            genre = obj.textOrJoin("genres", "genre")
            status = obj.string("status")?.toStatusInt() ?: SAnime.UNKNOWN
            thumbnail_url = obj.string("cover", "thumbnail_url", "thumbnail")
            season_number = obj.number("seasonNumber", "season_number") ?: 0.0
        }
    }

    private fun JsonElement.toEpisode(): SEpisode? {
        val obj = jsonObjectOrNull() ?: return null
        val url = obj.string("path", "url") ?: return null
        return SEpisode.create().apply {
            this.url = url
            name = obj.string("name", "title") ?: url
            episode_number = obj.number("episodeNumber", "episode_number", "chapterNumber")
                ?.toFloat() ?: -1f
            date_upload = parseChapterDate(obj.string("releaseTime", "release_time"))
            scanlator = obj.string("scanlator")
            summary = obj.string("summary", "description")
            preview_url = obj.string("preview", "preview_url", "thumbnail")
            fillermark = obj["filler"]?.primitiveOrNull()?.booleanOrNull
                ?: obj["fillermark"]?.primitiveOrNull()?.booleanOrNull
                ?: false
        }
    }

    private fun String.toStatusInt(): Int {
        val normalized = trim().lowercase()
        return normalized.toIntOrNull()
            ?: when (normalized) {
                "ongoing" -> SAnime.ONGOING
                "completed" -> SAnime.COMPLETED
                "licensed" -> SAnime.LICENSED
                "publishing finished", "publishing_finished", "publishingfinished" ->
                    SAnime.PUBLISHING_FINISHED
                "cancelled", "canceled", "dropped" -> SAnime.CANCELLED
                "on hiatus", "on_hiatus", "hiatus" -> SAnime.ON_HIATUS
                else -> SAnime.UNKNOWN
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
