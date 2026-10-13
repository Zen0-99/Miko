package miko.ui.catalog

import eu.kanade.tachiyomi.metadata.miko.dto.MikoCatalogResult
import eu.kanade.tachiyomi.metadata.miko.dto.MikoManifest
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMeta
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMetaResponse
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMetaShort
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/**
 * Android's `MikoAddonClient` — a Stremio-addon metadata source
 * (manifest + `/catalog/{type}/{id}/...json` + `/meta/{type}/{id}.json`).
 * Ktor port; `source-api`'s `MetadataSource` shape and DTOs are already
 * shared.
 */
class MikoAddonClient(
    private val manifestUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val client by lazy { HttpClient() }

    private val baseUrl: String = manifestUrl.substringBeforeLast("/")

    val name: String = "Cinemeta"
    val types: List<String> = listOf("movie", "series")

    private var cachedManifest: MikoManifest? = null

    private suspend inline fun <reified T> fetch(url: String): T =
        json.decodeFromString(client.get(url).bodyAsText())

    suspend fun getManifest(): MikoManifest {
        cachedManifest?.let { return it }
        val manifest = fetch<MikoManifest>(manifestUrl)
        cachedManifest = manifest
        return manifest
    }

    suspend fun getCatalog(
        type: String,
        catalogId: String,
        skip: Int = 0,
        genre: String? = null,
    ): MikoCatalogResult {
        val extras = buildList {
            genre?.let { add("genre=$it") }
            if (skip > 0) add("skip=$skip")
        }
        val extraSegment = if (extras.isEmpty()) "" else "/${extras.joinToString(",")}"
        return fetch("$baseUrl/catalog/$type/$catalogId$extraSegment.json")
    }

    suspend fun search(type: String, query: String, skip: Int = 0): MikoCatalogResult {
        val extras = buildList {
            add("search=${encodeQueryValue(query)}")
            if (skip > 0) add("skip=$skip")
        }
        return fetch("$baseUrl/catalog/$type/top/${extras.joinToString(",")}.json")
    }

    suspend fun getMeta(type: String, id: String): MikoMeta =
        fetch<MikoMetaResponse>("$baseUrl/meta/$type/$id.json").meta

    private fun encodeQueryValue(value: String): String =
        buildString {
            for (c in value) {
                when {
                    c.isLetterOrDigit() || c in "-._~" -> append(c)
                    c == ' ' -> append("%20")
                    else -> c.toString().encodeToByteArray()
                        .forEach { b ->
                            val v = b.toInt() and 0xFF
                            append('%').append(v.toString(16).padStart(2, '0').uppercase())
                        }
                }
            }
        }
}

/**
 * Android's `CinemetaCatalogFeed` — catalog fetch + meta→entry
 * materialization for `Feed.Cinemeta` rows. On iOS the mikoDB bridge is
 * absent, so dedupe falls back to the meta id itself (entries that
 * don't collapse are never dropped).
 */
class CinemetaCatalogFeed(
    private val cinemeta: MikoAddonClient,
) {
    suspend fun fetchCatalog(type: String, catalogId: String, skip: Int = 0): MikoCatalogResult =
        cinemeta.getCatalog(type, catalogId, skip)

    suspend fun searchCatalog(type: String, query: String, skip: Int = 0): MikoCatalogResult =
        cinemeta.search(type, query, skip)

    /** The manifest's catalog list — the builder's feed options. */
    suspend fun catalogs() = runCatching { cinemeta.getManifest().catalogs }
        .getOrDefault(emptyList())

    /**
     * Collapse catalog/search results that resolve to the same canonical
     * anime. Android keys by the MikoDB main-entry identity (MAL id);
     * without the bridge the key is the meta id itself — entries that
     * don't map are never dropped.
     */
    fun dedupeMetas(metas: List<MikoMetaShort>): List<MikoMetaShort> {
        val seen = mutableSetOf<String>()
        return metas.filter { seen.add("imdb:${it.id}") }
    }

    companion object {
        const val CINEMETA_SOURCE_ID = 0L
        const val CINEMETA_MANIFEST_URL = "https://v3-cinemeta.strem.io/manifest.json"
    }
}
