package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.injectLazy

/**
 * Client for the comments Worker's canonical-identity endpoints.
 * Implemented server side in Phase 8 (`POST /v1/works/resolve`) — this
 * client degrades to null on any failure so callers fall back to local
 * minting. The Worker lazy-mirrors MangaBaka into D1; responses are
 * upserted into `works_cache` by [WorkIdResolver].
 */
class WorkIdentityApi {

    @Serializable
    data class ResolveRequest(
        val provider: String? = null,
        @SerialName("external_id")
        val externalId: String? = null,
        val title: String,
        val medium: String,
        val author: String? = null,
        val year: Int? = null,
        /** Fetch disambiguator — never the resolved kind ("book" gates
         *  OpenLibrary; "movie"/"series" order the Cinemeta meta fetch).
         *  Renamed from `kind` in Phase 15 (the response owns that word);
         *  the Worker accepts the legacy field for one deploy cycle. */
        @SerialName("kind_hint")
        val kindHint: String? = null,
        /** Strong book identity — ISBN-10/13, edition → work rollup. */
        val isbn: String? = null,
    )

    @Serializable
    data class WorkDto(
        val miko_work_id: String,
        /** DEPRECATED mirror of [kind] — NULL on honest-null mints
         *  (identity persisted while the upstream meta source is down). */
        val type: String? = null,
        /** Worker-assigned kind; NULL when unresolved upstream. */
        val kind: String? = null,
        /** Carrier: "shows" | "manga" | "books" (post-15-01 workers). */
        val medium: String? = null,
        val nsfw: Boolean = false,
        /** Raw MangaBaka rating passthrough (erotica/pornographic/
         *  suggestive) — kept so the borderline 'suggestive' tag can be
         *  surfaced later without re-deriving nsfw. */
        val content_rating: String? = null,
        val title: String,
        val romanized_title: String? = null,
        val native_title: String? = null,
        val year: Int? = null,
        val total_chapters: String? = null,
        val author: String? = null,
        val provider_links: String = "[]",
        val external_ids: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class ResolveResponse(val work: WorkDto? = null)

    private val networkHelper: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val json: Json by injectLazy()

    private val baseUrlPref by lazy {
        preferenceStore.getString("miko_comments_base_url", DEFAULT_BASE_URL)
    }

    fun baseUrl(): String = baseUrlPref.get().trim().trimEnd('/')

    /** Resolve to a canonical work row, or null on miss/timeout/offline. */
    suspend fun resolve(request: ResolveRequest): WorkDto? = try {
        val body = json.encodeToString(request).toRequestBody(jsonMime)
        with(json) {
            networkHelper.client.newCall(
                POST(
                    "${baseUrl()}/v1/works/resolve",
                    headers = Headers.headersOf("User-Agent", USER_AGENT),
                    body = body,
                ),
            ).awaitSuccess().parseAs<ResolveResponse>().work
        }
    } catch (_: Exception) {
        null
    }

    // ---------- Phase 12-04: canonical chapter map ----------

    @Serializable
    data class CanonChapterDto(
        val seq: Int,
        val number: Double? = null,
        val part: Int? = null,
        val volume: Double? = null,
        val kind: String = "chapter",
        val label: String = "",
        val confidence: String = "single",
        val sources: List<String> = emptyList(),
        val refs: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class ChaptersResponse(
        val work_id: String,
        val chapters: List<CanonChapterDto> = emptyList(),
    )

    @Serializable
    data class FeedChapter(val name: String, val url: String? = null)

    @Serializable
    data class CanonizeFeed(
        val source_key: String,
        val tier: String? = null,
        val verified: Boolean = true,
        val score: Double? = null,
        val chapters: List<FeedChapter>,
    )

    @Serializable
    data class CanonizeRequest(val work_id: String, val feeds: List<CanonizeFeed>)

    @Serializable
    data class CanonizeResponse(
        val work_id: String,
        val count: Int,
        val verified: Int,
        val skipped: Int,
        val conflicts: Int,
        val divergent: List<String> = emptyList(),
    )

    /** The work's canonical chapter sequence — empty list if never canonized; null on failure. */
    suspend fun getChapters(workId: String): ChaptersResponse? = try {
        networkHelper.client.newCall(
            okhttp3.Request.Builder()
                .url("${baseUrl()}/v1/works/${java.net.URLEncoder.encode(workId, "UTF-8")}/chapters")
                .headers(Headers.headersOf("User-Agent", USER_AGENT))
                .get()
                .build(),
        ).awaitSuccess().let { with(json) { it.parseAs<ChaptersResponse>() } }
    } catch (_: Exception) {
        null
    }

    /** Submit golden-source feeds for canonization. Null on failure. */
    suspend fun canonize(request: CanonizeRequest): CanonizeResponse? = try {
        val body = json.encodeToString(request).toRequestBody(jsonMime)
        with(json) {
            networkHelper.client.newCall(
                POST(
                    "${baseUrl()}/v1/chapters/canonize",
                    headers = Headers.headersOf("User-Agent", USER_AGENT),
                    body = body,
                ),
            ).awaitSuccess().parseAs<CanonizeResponse>()
        }
    } catch (_: Exception) {
        null
    }

    // ---------- Phase 14-01: coverage-gap telemetry ----------

    /**
     * Reports a resolved/minted work id so MikoDB can rank coverage gaps.
     * Sends ONLY the id + medium — no user, device, or metadata.
     * Returns false on any failure; callers never retry.
     */
    suspend fun markSeen(workId: String, medium: String): Boolean = try {
        val body = json.encodeToString(mapOf("id" to workId, "medium" to medium))
            .toRequestBody(jsonMime)
        networkHelper.client.newCall(
            POST(
                "${baseUrl()}/v1/ids/seen",
                headers = Headers.headersOf("User-Agent", USER_AGENT),
                body = body,
            ),
        ).awaitSuccess()
        true
    } catch (_: Exception) {
        false
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://miko-comments.keypop3750.workers.dev"

        // Explicit UA: Cloudflare bot rules (1010) reject synthetic agents
        // like python-urllib — the okhttp default could trip them too.
        const val USER_AGENT = "Miko/2.0 (Android)"
    }
}
