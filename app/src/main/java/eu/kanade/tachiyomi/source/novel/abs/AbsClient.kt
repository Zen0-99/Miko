package eu.kanade.tachiyomi.source.novel.abs

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.preference.Preference
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/**
 * Single funnel point for every ABS API call (SD-7). Tests swap
 * [AbsClient.transport] for canned JSON; production's default transport
 * runs the OkHttp GET with the Bearer header from [AbsRequest.headers].
 */
data class AbsRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Minimal REST client for a self-hosted Audiobookshelf server.
 *
 * Every call funnels through [transport](AbsRequest) — the one seam that
 * carries `Authorization: Bearer <token>` so tests can assert it and
 * production can never forget it. `?token=` is NEVER used on API calls
 * (T-22-01-02; the query escape is reserved for binary GETs in 22-02 if
 * headers can't ride there).
 *
 * Credentials live in the `source_-1` SharedPreferences file (SD-2):
 * `abs_base_url` + `abs_library_id` are plain keys; the token is stored
 * under `Preference.privateKey("abs_api_key")` = `__PRIVATE_abs_api_key`
 * — masked in the UI, filtered from backups by [Preference.isPrivate],
 * and novel sources are absent from PreferenceBackupCreator.createSource
 * entirely. The token is never logged, embedded, or serialized.
 *
 * Base URLs are validated only to the http/https scheme allowlist
 * (enforced for free by HttpUrl parsing) — LAN ABS instances run
 * cleartext http by design (T-22-01-03).
 */
class AbsClient(
    private val preferences: SharedPreferences,
) {

    /** Transport seam — swapped by tests, OkHttp in production. */
    internal var transport: suspend (AbsRequest) -> String = { req -> defaultTransport(req) }

    private val httpClient by lazy {
        Injekt.get<NetworkHelper>().nonCloudflareClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Configured server base url, "" when unset. Trailing slashes stripped. */
    fun baseUrl(): String =
        preferences.getString(PREF_BASE_URL, "").orEmpty().trim().trimEnd('/')

    /** The API token — secret; never log it. */
    fun token(): String =
        preferences.getString(Preference.privateKey(PREF_API_KEY), "").orEmpty()

    fun libraryId(): String? =
        preferences.getString(PREF_LIBRARY_ID, "").orEmpty().takeIf { it.isNotBlank() }

    /** Derived host fingerprint for `abs:{serverKey}:{itemId}` external ids. */
    fun serverKey(): String = AbsUrls.serverKeyFor(baseUrl())

    fun isConfigured(): Boolean = baseUrl().isNotBlank() && token().isNotBlank()

    // ---- API ---------------------------------------------------------------

    suspend fun me(): AbsUser =
        absJson.decodeFromString(api("GET", "/api/me"))

    /** Book libraries only — podcast libraries are filtered out. */
    suspend fun libraries(): List<AbsLibrary> =
        absJson.decodeFromString<AbsLibrariesResponse>(api("GET", "/api/libraries"))
            .libraries
            .filter { it.mediaType == "book" }

    /** `GET /api/libraries/{libraryId}/items` — [page] is 0-indexed. */
    suspend fun items(
        libraryId: String,
        page: Int,
        limit: Int = 50,
        sort: String,
        desc: Boolean,
        filter: String?,
    ): AbsItemsResponse =
        absJson.decodeFromString(
            api(
                "GET",
                "/api/libraries/$libraryId/items",
                buildMap {
                    put("limit", limit.toString())
                    put("page", page.toString())
                    put("sort", sort)
                    put("desc", if (desc) "1" else "0")
                    filter?.takeIf { it.isNotBlank() }?.let { put("filter", it) }
                },
            ),
        )

    /** `GET /api/items/{itemId}` — full item incl. media.tracks. */
    suspend fun item(itemId: String): AbsItem =
        absJson.decodeFromString(api("GET", "/api/items/$itemId"))

    /** `GET /api/me/items-in-progress` — the continue-listening shelf. */
    suspend fun itemsInProgress(): List<AbsItem> {
        val body = api("GET", "/api/me/items-in-progress")
        return runCatching {
            absJson.decodeFromString<AbsItemsInProgressResponse>(body).libraryItems
        }.getOrElse {
            // Some ABS versions answer a bare array — tolerate both shapes.
            absJson.decodeFromString(body)
        }
    }

    // ---- plumbing ----------------------------------------------------------

    /** Every API call goes through here so the Bearer header rides the
     *  request the transport sees — tests assert on [AbsRequest.headers]. */
    private suspend fun api(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null,
    ): String = transport(
        AbsRequest(
            method = method,
            path = path,
            query = query,
            body = body,
            headers = mapOf("Authorization" to "Bearer ${token()}"),
        ),
    )

    private suspend fun defaultTransport(req: AbsRequest): String = withContext(Dispatchers.IO) {
        val base = baseUrl()
        require(base.isNotBlank()) { "ABS server is not configured" }
        val url = (base + req.path).toHttpUrlOrNull()
            ?.newBuilder()
            ?.apply { req.query.forEach { (k, v) -> addQueryParameter(k, v) } }
            ?.build()
            ?: throw IllegalArgumentException("Malformed ABS request url")
        val request = Request.Builder()
            .url(url)
            .apply {
                req.headers.forEach { (k, v) -> addHeader(k, v) }
                if (req.method == "GET") {
                    get()
                } else {
                    method(req.method, req.body?.toRequestBody(jsonMime))
                }
            }
            .build()
        httpClient.newCall(request).awaitSuccess().use { it.body.string() }
    }

    companion object {
        const val PREF_BASE_URL = "abs_base_url"
        const val PREF_API_KEY = "abs_api_key"
        const val PREF_LIBRARY_ID = "abs_library_id"
    }
}
