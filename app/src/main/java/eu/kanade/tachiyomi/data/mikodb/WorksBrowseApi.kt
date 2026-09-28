package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Client for the comments Worker's `GET /v1/works/browse` — ranked
 * slices of the canonical catalog backing the Discovery tab's
 * manga/books idle surface (Phase 15-07). Same carrier vocabulary as
 * [WorkIdentityApi] (`medium` ∈ {manga, books}; `kind` is a csv filter
 * over the worker-assigned kind column — absent means all kinds under
 * the medium).
 *
 * Rows returned here are already minted server-side (`mk:mb:`/`mk:ol:`/
 * `mk:hs:` ids) — the browse surface reports them via `markSeen` only,
 * never re-resolves.
 */
class WorksBrowseApi(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
    private val identityApi: WorkIdentityApi = Injekt.get(),
) {

    @Serializable
    data class WorkDto(
        val miko_work_id: String,
        /** Worker-assigned kind; NULL on honest-null mints (upstream
         *  was unreachable when the row was persisted). */
        val kind: String? = null,
        val title: String,
        val author: String? = null,
        val cover_url: String? = null,
        /** MangaBaka 0–100 rating passthrough. */
        val rating: Double? = null,
        /** Catalog rank — lower is more popular. */
        val popularity: Long? = null,
        val year: Int? = null,
        val nsfw: Boolean = false,
        val genres: List<String> = emptyList(),
    )

    @Serializable
    internal data class BrowseResponse(
        val works: List<WorkDto> = emptyList(),
        val page: Int = 1,
        val has_more: Boolean = false,
    )

    data class Page(
        val items: List<WorkDto>,
        val page: Int,
        val hasMore: Boolean,
    )

    /**
     * One catalog page, or null on failure/HTTP error. 1-based [page];
     * [kinds] empty = every kind under [medium] (worker's `kind` filter
     * is dropped entirely rather than sent empty). The worker excludes
     * NSFW rows unless [includeNsfw] opts in — ranked discovery is a
     * SFW surface; adult rows must be sought via the NSFW-sources pref.
     */
    suspend fun browse(
        medium: String,
        kinds: List<String> = emptyList(),
        sort: String = SORT_POPULARITY,
        page: Int = 1,
        limit: Int = PAGE_LIMIT,
        includeNsfw: Boolean = false,
    ): Page? = try {
        val url = "${identityApi.baseUrl()}$BROWSE_PATH".toHttpUrl().newBuilder()
            .addQueryParameter("medium", medium)
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", limit.toString())
            .apply { if (kinds.isNotEmpty()) addQueryParameter("kind", kinds.joinToString(",")) }
            .apply { if (includeNsfw) addQueryParameter("nsfw", "1") }
            .build()
        val response = networkHelper.client.newCall(
            GET(url, headers = Headers.headersOf("User-Agent", WorkIdentityApi.USER_AGENT)),
        ).awaitSuccess()
        with(json) { response.parseAs<BrowseResponse>() }
            .let { Page(it.works, it.page, it.has_more) }
    } catch (e: Exception) {
        logcat(LogPriority.WARN) { "WorksBrowseApi: browse failed: ${e.message}" }
        null
    }

    companion object {
        const val SORT_POPULARITY = "popularity"
        const val SORT_RATING = "rating"
        const val SORT_NEWEST = "newest"

        private const val BROWSE_PATH = "/v1/works/browse"
        private const val PAGE_LIMIT = 40
    }
}
