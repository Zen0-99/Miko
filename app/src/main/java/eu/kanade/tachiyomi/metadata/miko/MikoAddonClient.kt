package eu.kanade.tachiyomi.metadata.miko

import eu.kanade.tachiyomi.metadata.MetadataSource
import eu.kanade.tachiyomi.metadata.miko.dto.MikoCatalogResult
import eu.kanade.tachiyomi.metadata.miko.dto.MikoManifest
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMeta
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMetaResponse
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.net.URLEncoder

class MikoAddonClient(
    private val manifestUrl: String,
    private val networkHelper: NetworkHelper,
    private val json: Json,
) : MetadataSource {

    private val client: OkHttpClient get() = networkHelper.client

    private val baseUrl: String = manifestUrl.substringBeforeLast("/")

    override val id: Long = manifestUrl.hashCode().toLong()
    override val name: String = "Cinemeta"
    override val types: List<String> = listOf("movie", "series")

    private var cachedManifest: MikoManifest? = null

    // parseAs streams the body on the calling thread — callers on the main
    // dispatcher hit NetworkOnMainThreadException (StrictMode) without this.
    private suspend fun <T> fetch(url: String, decode: okhttp3.Response.() -> T): T =
        withContext(Dispatchers.IO) {
            client.newCall(GET(url)).awaitSuccess().use { it.decode() }
        }

    override suspend fun getManifest(): MikoManifest {
        cachedManifest?.let { return it }
        val manifest = fetch(manifestUrl) { with(json) { parseAs<MikoManifest>() } }
        cachedManifest = manifest
        return manifest
    }

    override suspend fun getCatalog(
        type: String,
        catalogId: String,
        skip: Int,
        genre: String?,
    ): MikoCatalogResult {
        val extras = buildList {
            genre?.let { add("genre=$it") }
            if (skip > 0) add("skip=$skip")
        }
        val extraSegment = if (extras.isEmpty()) "" else "/${extras.joinToString(",")}"
        return fetch("$baseUrl/catalog/$type/$catalogId$extraSegment.json") {
            with(json) { parseAs<MikoCatalogResult>() }
        }
    }

    override suspend fun search(type: String, query: String, skip: Int): MikoCatalogResult {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val extras = buildList {
            add("search=$encodedQuery")
            if (skip > 0) add("skip=$skip")
        }
        return fetch("$baseUrl/catalog/$type/top/${extras.joinToString(",")}.json") {
            with(json) { parseAs<MikoCatalogResult>() }
        }
    }

    override suspend fun getMeta(type: String, id: String): MikoMeta {
        return fetch("$baseUrl/meta/$type/$id.json") {
            with(json) { parseAs<MikoMetaResponse>() }.meta
        }
    }
}
