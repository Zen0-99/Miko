package eu.kanade.tachiyomi.metadata.openlibrary

import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi
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
 * OpenLibrary `GET /trending/{daily|weekly|monthly}.json` client backing
 * the BOOKS carrier's "Books" pill in Discovery (Phase 15-07). No auth;
 * the response's `works[]` carry `/works/OL…W` keys — the same identity
 * the Worker mints as `mk:ol:` rows, so a tap can materialize the
 * canonical novel row deterministically.
 *
 * The feed is single-page (no offset param — `limit` only), so the
 * browse surface loads it once per pill visit.
 */
class OpenLibraryTrending(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {

    enum class Period(val param: String) {
        DAILY("daily"),
        WEEKLY("weekly"),
        MONTHLY("monthly"),
    }

    data class Item(
        /** OpenLibrary work id — "OL45883W" (the doc's `key` minus `/works/`). */
        val workKey: String,
        val title: String,
        val author: String?,
        val year: Int?,
        val coverUrl: String?,
        /** First ISBN the doc carries, when valid — the resolve path's
         *  strongest identity signal (edition → work rollup). */
        val isbn: String? = null,
    )

    @Serializable
    internal data class TrendingResponse(
        val works: List<WorkDoc> = emptyList(),
    )

    @Serializable
    internal data class WorkDoc(
        val key: String? = null,
        val title: String? = null,
        val author_name: List<String> = emptyList(),
        val first_publish_year: Int? = null,
        val cover_i: Long? = null,
        val isbn: List<String> = emptyList(),
    )

    /** The trending list (newest first), or null on failure/HTTP error. */
    suspend fun trending(
        period: Period = Period.DAILY,
        limit: Int = DEFAULT_LIMIT,
    ): List<Item>? = try {
        val url = "$BASE_URL/trending/${period.param}.json".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .build()
        val response = networkHelper.client.newCall(
            GET(url, headers = Headers.headersOf("User-Agent", WorkIdentityApi.USER_AGENT)),
        ).awaitSuccess()
        with(json) { response.parseAs<TrendingResponse>() }
            .works.mapNotNull { it.toItem() }
    } catch (e: Exception) {
        logcat(LogPriority.WARN) { "OpenLibraryTrending: trending failed: ${e.message}" }
        null
    }

    companion object {
        private const val BASE_URL = "https://openlibrary.org"
        private const val COVER_BASE = "https://covers.openlibrary.org/b/id"
        private const val DEFAULT_LIMIT = 50

        /** "/works/OL45883W" → "OL45883W" (null for non-work keys). */
        internal fun workIdFromKey(key: String?): String? =
            WORK_KEY.matchEntire(key ?: "")?.groupValues?.get(1)

        /** cover_i → `https://covers.openlibrary.org/b/id/{id}-M.jpg`. */
        internal fun coverUrl(coverI: Long?): String? =
            coverI?.let { "$COVER_BASE/$it-M.jpg" }

        internal fun WorkDoc.toItem(): Item? {
            val workKey = workIdFromKey(key) ?: return null
            val title = title?.takeIf { it.isNotBlank() } ?: return null
            return Item(
                workKey = workKey,
                title = title,
                // The resolver's author match compares against a single
                // normalized name — the first author is the strongest.
                author = author_name.firstOrNull(),
                year = first_publish_year,
                coverUrl = coverUrl(cover_i),
                isbn = isbn.firstNotNullOfOrNull(WorkIdResolver::normalizeIsbn),
            )
        }

        private val WORK_KEY = Regex("""/works/(OL\d+W)$""")
    }
}
