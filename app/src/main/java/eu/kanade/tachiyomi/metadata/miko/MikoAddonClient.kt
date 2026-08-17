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

    override suspend fun getManifest(): MikoManifest {
        cachedManifest?.let { return it }
        val response = client.newCall(GET(manifestUrl)).awaitSuccess()
        val manifest = with(json) { response.parseAs<MikoManifest>() }
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
        val url = "$baseUrl/catalog/$type/$catalogId$extraSegment.json"
        val response = client.newCall(GET(url)).awaitSuccess()
        return with(json) { response.parseAs<MikoCatalogResult>() }
    }

    override suspend fun search(type: String, query: String, skip: Int): MikoCatalogResult {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val extras = buildList {
            add("search=$encodedQuery")
            if (skip > 0) add("skip=$skip")
        }
        val url = "$baseUrl/catalog/$type/top/${extras.joinToString(",")}.json"
        val response = client.newCall(GET(url)).awaitSuccess()
        return with(json) { response.parseAs<MikoCatalogResult>() }
    }

    override suspend fun getMeta(type: String, id: String): MikoMeta {
        val url = "$baseUrl/meta/$type/$id.json"
        val response = client.newCall(GET(url)).awaitSuccess()
        return with(json) { response.parseAs<MikoMetaResponse>() }.meta
    }
}
