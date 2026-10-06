package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.data.comments.DeviceIdentityProvider
import eu.kanade.tachiyomi.network.HttpException
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
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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
        /** Bundled mint telemetry (Phase 20) — piggybacks on this call,
         *  never its own request. */
        val telemetry: List<TelemetryEntry>? = null,
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
        val cover_url: String? = null,
        val external_ids: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class ResolveResponse(val work: WorkDto? = null)

    private val networkHelper: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val json: Json by injectLazy()
    private val identityProvider: DeviceIdentityProvider by injectLazy()

    private val baseUrlPref by lazy {
        preferenceStore.getString("miko_comments_base_url", DEFAULT_BASE_URL)
    }

    fun baseUrl(): String = baseUrlPref.get().trim().trimEnd('/')

    /**
     * Bearer plumbing (Phase 20): mutation paths (`read=false`) register
     * the device lazily via [DeviceIdentityProvider.ensureToken] so every
     * mint carries identity; read paths attach the cached pref token
     * only — never an auth network call just to read.
     */
    private suspend fun authHeaders(read: Boolean): Headers {
        val token = if (read) identityProvider.token() else identityProvider.ensureToken()
        return Headers.headersOf(
            "User-Agent",
            USER_AGENT,
            *(token?.let { arrayOf("Authorization", "Bearer $it") } ?: emptyArray()),
        )
    }

    /**
     * Execute [build] once; on HTTP 401 refresh the device token and
     * retry once (the comments mapError convention). A second 401 or a
     * failed refresh rethrows and degrades to the caller's fallback.
     */
    private suspend fun authedCall(read: Boolean, build: (Headers) -> Request): Response = try {
        networkHelper.client.newCall(build(authHeaders(read))).awaitSuccess()
    } catch (e: HttpException) {
        if (e.code != 401) throw e
        identityProvider.refreshToken() ?: throw e
        networkHelper.client.newCall(build(authHeaders(read))).awaitSuccess()
    }

    /** Resolve to a canonical work row, or null on miss/timeout/offline. */
    suspend fun resolve(request: ResolveRequest): WorkDto? = try {
        val body = json.encodeToString(request).toRequestBody(jsonMime)
        with(json) {
            authedCall(read = true) { headers ->
                POST(
                    "${baseUrl()}/v1/works/resolve",
                    headers = headers,
                    body = body,
                )
            }.parseAs<ResolveResponse>().work
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
        /** Earliest carrier upload date (epoch ms); null when unknown. */
        val date_upload: Long? = null,
    )

    @Serializable
    data class ChaptersResponse(
        val work_id: String,
        val chapters: List<CanonChapterDto> = emptyList(),
    )

    @Serializable
    data class FeedChapter(
        val name: String,
        val url: String? = null,
        /** Source-reported upload time (epoch ms); carried into the
         *  canonical row so materialized chapters show a real date
         *  instead of the sync stamp. */
        val date_upload: Long? = null,
    )

    @Serializable
    data class CanonizeFeed(
        val source_key: String,
        val tier: String? = null,
        val verified: Boolean = true,
        val score: Double? = null,
        /** Source-internal entry ref — mints the provider_bindings row
         *  server-side. Without it a feed contributes chapters but the
         *  work↔carrier link never persists (17-01). */
        val provider_ref: String? = null,
        /** 'device_feed' default; 'user_picked' for Find Provider sheet
         *  confirmations (confidence floor applied server-side). */
        val provenance: String? = null,
        val chapters: List<FeedChapter>,
    )

    @Serializable
    data class CanonizeRequest(
        val work_id: String,
        val feeds: List<CanonizeFeed>,
        /** Bundled mint telemetry (Phase 20). */
        val telemetry: List<TelemetryEntry>? = null,
    )

    @Serializable
    data class CanonizeResponse(
        val work_id: String,
        val count: Int,
        val verified: Int,
        val skipped: Int,
        val conflicts: Int,
        val divergent: List<String> = emptyList(),
        /** Provider refs that solidified on this request (20-01 workers).
         *  Empty on pre-20 workers — the client treats it as "nothing
         *  changed" and skips the matrix-cache invalidation. */
        val solidified: List<String> = emptyList(),
    )

    /** The work's canonical chapter sequence — empty list if never canonized; null on failure. */
    suspend fun getChapters(workId: String): ChaptersResponse? = try {
        authedCall(read = true) { headers ->
            okhttp3.Request.Builder()
                .url("${baseUrl()}/v1/works/${java.net.URLEncoder.encode(workId, "UTF-8")}/chapters")
                .headers(headers)
                .get()
                .build()
        }.let { with(json) { it.parseAs<ChaptersResponse>() } }
    } catch (_: Exception) {
        null
    }

    /** Submit golden-source feeds for canonization. Null on failure. */
    suspend fun canonize(request: CanonizeRequest): CanonizeResponse? = try {
        val body = json.encodeToString(request).toRequestBody(jsonMime)
        with(json) {
            authedCall(read = false) { headers ->
                POST(
                    "${baseUrl()}/v1/chapters/canonize",
                    headers = headers,
                    body = body,
                )
            }.parseAs<CanonizeResponse>()
        }
    } catch (_: Exception) {
        null
    }

    // ---------- Phase 19-03: canonical episode map (D-04a courier) ----------
    // The episodes contract is a sibling of the chapters one — shows
    // canon lives in `canonical_episodes`, never in `canonical_chapters`
    // (its kind vocabulary is chapter-shaped). `FeedEpisode` carries the
    // full structured surface: artifact feeds emit key/doc/abs/season/
    // eids verbatim from `anime_episodes`; carrier feeds emit
    // name/url/number only — their `abs` canonicalization is 19-04.

    @Serializable
    data class FeedEpisode(
        val name: String,
        val url: String? = null,
        /** Source-reported upload/air time (epoch ms). */
        @SerialName("date_upload")
        val dateUpload: Long? = null,
        /** 'episode'|'special'|'nc' — absent derives from the `key`
         *  prefix worker-side, else 'episode'. */
        val kind: String? = null,
        /** Kind-scoped doc-local number — the carrier's raw number for
         *  carrier feeds; the AniDB doc number for artifact rows. */
        val number: Double? = null,
        /** Artifact ep_key verbatim — "1", "S3", "C2" (artifact feeds only). */
        val key: String? = null,
        /** Artifact doc namespace — "anidb:1234" (artifact feeds only). */
        val doc: String? = null,
        /** Canonical absolute episode number — artifact-asserted only;
         *  carrier abs canonicalization lands in 19-04. */
        val abs: Int? = null,
        val season: Int? = null,
        @SerialName("ep_in_season")
        val epInSeason: Int? = null,
        /** ISO date ("1998-11-13") — wins date_upload semantics (D-05). */
        @SerialName("air_date")
        val airDate: String? = null,
        val image: String? = null,
        val overview: String? = null,
        val runtime: Int? = null,
        @SerialName("anidb_eid")
        val anidbEid: Long? = null,
        @SerialName("tvdb_eid")
        val tvdbEid: Long? = null,
        @SerialName("tvdb_show_id")
        val tvdbShowId: Long? = null,
        val filler: Boolean? = null,
    )

    @Serializable
    data class EpisodeFeed(
        @SerialName("source_key")
        val sourceKey: String,
        val tier: String? = null,
        val verified: Boolean = true,
        val score: Double? = null,
        /** Carrier's entry url — mints the provider_bindings row
         *  server-side (same contract as the chapters route). */
        @SerialName("provider_ref")
        val providerRef: String? = null,
        /** 'device_feed' default; 'user_picked' for Find Provider
         *  confirmations (confidence floor applied server-side). */
        val provenance: String? = null,
        val episodes: List<FeedEpisode>,
    )

    @Serializable
    data class EpisodesCanonizeRequest(
        @SerialName("work_id")
        val workId: String,
        val feeds: List<EpisodeFeed>,
        /** Bundled mint telemetry (Phase 20). */
        val telemetry: List<TelemetryEntry>? = null,
    )

    @Serializable
    data class CanonizeEpisodesResponse(
        @SerialName("work_id")
        val workId: String,
        val count: Int,
        val verified: Int,
        val skipped: Int,
        val conflicts: Int,
        val divergent: List<String> = emptyList(),
        /** Provider refs that solidified on this request (20-01
         *  workers). Empty on pre-20 workers. */
        val solidified: List<String> = emptyList(),
    )

    @Serializable
    data class CanonEpisodeDto(
        val seq: Int,
        val kind: String = "episode",
        val number: Double? = null,
        val abs: Int? = null,
        val season: Int? = null,
        @SerialName("ep_in_season")
        val epInSeason: Int? = null,
        @SerialName("ep_key")
        val epKey: String? = null,
        val doc: String? = null,
        val label: String = "",
        @SerialName("air_date")
        val airDate: String? = null,
        @SerialName("date_upload")
        val dateUpload: Long? = null,
        val image: String? = null,
        val overview: String? = null,
        val runtime: Int? = null,
        @SerialName("anidb_eid")
        val anidbEid: Long? = null,
        @SerialName("tvdb_eid")
        val tvdbEid: Long? = null,
        @SerialName("tvdb_show_id")
        val tvdbShowId: Long? = null,
        val filler: Int = 0,
        val confidence: String = "single",
        val sources: List<String> = emptyList(),
        val refs: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class EpisodesResponse(
        @SerialName("work_id")
        val workId: String,
        val episodes: List<CanonEpisodeDto> = emptyList(),
    )

    /** The work's canonical episode sequence — empty list if never canonized; null on failure. */
    suspend fun getEpisodes(workId: String): EpisodesResponse? = try {
        authedCall(read = true) { headers ->
            okhttp3.Request.Builder()
                .url("${baseUrl()}/v1/works/${java.net.URLEncoder.encode(workId, "UTF-8")}/episodes")
                .headers(headers)
                .get()
                .build()
        }.let { with(json) { it.parseAs<EpisodesResponse>() } }
    } catch (_: Exception) {
        null
    }

    /** Submit the artifact + carrier episode feeds for canonization.
     *  Null on failure. */
    suspend fun canonizeEpisodes(
        workId: String,
        feeds: List<EpisodeFeed>,
        telemetry: List<TelemetryEntry>? = null,
    ): CanonizeEpisodesResponse? = try {
        val body = json.encodeToString(EpisodesCanonizeRequest(workId, feeds, telemetry))
            .toRequestBody(jsonMime)
        with(json) {
            authedCall(read = false) { headers ->
                POST(
                    "${baseUrl()}/v1/episodes/canonize",
                    headers = headers,
                    body = body,
                )
            }.parseAs<CanonizeEpisodesResponse>()
        }
    } catch (_: Exception) {
        null
    }

    // ---------- Phase 16-02/16-04: carrier bindings + alignment ----------

    @Serializable
    data class BindingDto(
        val source_key: String,
        val provider_ref: String,
        val confidence: Double? = null,
        val provenance: String? = null,
        val last_seen_at: Long? = null,
        /** 'confirmed' | 'provisional' | 'quarantined' (Phase 20).
         *  NULL on pre-20 workers — the client maps it to
         *  confirmed-equivalent semantics. */
        val status: String? = null,
        /** This device minted the binding (server-computed from the
         *  Bearer hash — never trusted when client-supplied). */
        val mine: Boolean = false,
        /** Ledger-derived binding score; display/diagnostic only. */
        val points: Double? = null,
    )

    @Serializable
    data class BindingsResponse(
        val work_id: String,
        val bindings: List<BindingDto> = emptyList(),
    )

    /** The work's durable carrier bindings — which providers/extensions
     *  hold an entry for it (16-02). Empty list when none; null on failure. */
    suspend fun getBindings(workId: String): BindingsResponse? = try {
        authedCall(read = true) { headers ->
            okhttp3.Request.Builder()
                .url("${baseUrl()}/v1/works/${java.net.URLEncoder.encode(workId, "UTF-8")}/bindings")
                .headers(headers)
                .get()
                .build()
        }.let { with(json) { it.parseAs<BindingsResponse>() } }
    } catch (_: Exception) {
        null
    }

    // ---------- Phase 20: binding lifecycle ----------

    @Serializable
    data class UnbindRequest(
        val work_id: String,
        val provider: String,
        val provider_ref: String,
    )

    /** Outcome of `POST /v1/bindings/unbind` (20-02; the endpoint may be
     *  absent on a pre-20-02 worker — that is [NotFound], not a crash). */
    sealed interface UnbindResult {
        data object Ok : UnbindResult

        /** Endpoint absent (404) OR the row isn't this device's own
         *  provisional mint — the worker maps both to 404. */
        data object NotFound : UnbindResult
        data object Failed : UnbindResult
    }

    /**
     * Revoke this device's own provisional binding ("wrong work? unbind").
     * The worker deletes the row + counts a kind='unbind' report.
     */
    suspend fun unbindBinding(
        workId: String,
        provider: String,
        providerRef: String,
    ): UnbindResult = try {
        val body = json.encodeToString(UnbindRequest(workId, provider, providerRef))
            .toRequestBody(jsonMime)
        authedCall(read = false) { headers ->
            POST(
                "${baseUrl()}/v1/bindings/unbind",
                headers = headers,
                body = body,
            )
        }
        UnbindResult.Ok
    } catch (e: HttpException) {
        if (e.code == 404) UnbindResult.NotFound else UnbindResult.Failed
    } catch (_: Exception) {
        UnbindResult.Failed
    }

    // ---------- Phase 20-04: provider trust overlay ----------

    @Serializable
    data class TierDto(
        /** Community-earned tier — promotes only, never 'S'. */
        val dynamic_tier: String? = null,
        /** Report-derank ceiling — demotes only. */
        val tier_cap: String? = null,
        val minted_count: Int = 0,
        val solidified_count: Int = 0,
    )

    @Serializable
    data class TiersResponse(
        val version: Int = 0,
        val providers: Map<String, TierDto> = emptyMap(),
    )

    /**
     * `GET /v1/providers/tiers` — the worker's dynamic-tier/cap overlay
     * for every tracked provider. PERIODIC ONLY: [ProviderTiers] holds
     * the 1h TTL + stale cache; this must never be called per mint or
     * per render. Null on any failure (incl. a pre-20-04 worker's 404)
     * — callers fall back to manifest tiers.
     */
    suspend fun getProviderTiers(): TiersResponse? = try {
        authedCall(read = true) { headers ->
            okhttp3.Request.Builder()
                .url("${baseUrl()}/v1/providers/tiers")
                .headers(headers)
                .get()
                .build()
        }.let { with(json) { it.parseAs<TiersResponse>() } }
    } catch (_: Exception) {
        null
    }

    // ---------- Phase 14-01: coverage-gap telemetry ----------

    @Serializable
    data class SeenRequest(
        val id: String,
        val medium: String,
        /** Bundled mint telemetry (Phase 20). */
        val telemetry: List<TelemetryEntry>? = null,
    )

    /**
     * Reports a resolved/minted work id so MikoDB can rank coverage gaps.
     * Sends ONLY the id + medium — no user, device, or metadata beyond
     * the shared Bearer identity. Returns false on any failure; callers
     * never retry.
     */
    suspend fun markSeen(
        workId: String,
        medium: String,
        telemetry: List<TelemetryEntry>? = null,
    ): Boolean = try {
        val body = json.encodeToString(SeenRequest(workId, medium, telemetry))
            .toRequestBody(jsonMime)
        authedCall(read = true) { headers ->
            POST(
                "${baseUrl()}/v1/ids/seen",
                headers = headers,
                body = body,
            )
        }
        true
    } catch (_: Exception) {
        false
    }

    /**
     * One bundled mint-telemetry row (Phase 20): per-package
     * served/failed counters accumulated by [MintTelemetry] and carried
     * piggyback on the next existing request body — never a dedicated
     * flush call. Observational only server-side.
     */
    @Serializable
    data class TelemetryEntry(
        val pkg: String,
        val served: Long = 0,
        val failed: Long = 0,
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://miko-comments.keypop3750.workers.dev"

        // Explicit UA: Cloudflare bot rules (1010) reject synthetic agents
        // like python-urllib — the okhttp default could trip them too.
        const val USER_AGENT = "Miko/2.0 (Android)"
    }
}
