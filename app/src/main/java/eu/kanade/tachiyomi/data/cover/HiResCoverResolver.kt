package eu.kanade.tachiyomi.data.cover

import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.manga.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import logcat.logcat
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import java.time.Instant

/**
 * Upgrades low-resolution source covers to high-resolution artwork for the
 * enhanced detail hero. Provider chain:
 *
 * 1. AniList — only when the entry is bound to an AniList track (the bound
 *    `remoteId` gives an exact match, no fuzzy search). Uses the public
 *    GraphQL endpoint for `coverImage.extraLarge`.
 * 2. MangaDex Covers API — title search, `includes[]=cover_art` relationship
 *    gives the original-resolution upload.
 * 3. Jikan (MAL mirror) — title search, `images.jpg.large_image_url`.
 * 4. Metron — western-comics DB; needs a user-supplied API token (see
 *    [NetworkPreferences.metronApiToken]). First matching issue's `image`.
 * 5. Grand Comics Database — anonymous REST API, series-name search then the
 *    first active issue's `cover` URL (files1.comics.org, w400).
 *
 * The first hit wins and is written to `thumbnailUrl` with a bumped
 * `coverLastModified` so Coil refetches. Entries that were checked and had
 * no result are remembered for [RETRY_INTERVAL_MS] so every detail-view open
 * doesn't hammer the APIs. User-set custom covers are never overwritten.
 */
class HiResCoverResolver(
    private val networkHelper: NetworkHelper,
    private val json: Json,
    private val getMangaTracks: GetMangaTracks,
    private val updateManga: UpdateManga,
    private val mangaRepository: MangaRepository,
    private val coverCache: MangaCoverCache,
    private val preferenceStore: PreferenceStore,
    private val networkPreferences: NetworkPreferences,
) {
    companion object {
        private const val ANILIST_API = "https://graphql.anilist.co"
        private const val MANGADEX_API = "https://api.mangadex.org"
        private const val MANGADEX_UPLOADS = "https://uploads.mangadex.org"
        private const val JIKAN_API = "https://api.jikan.moe/v4"
        private const val METRON_API = "https://metron.cloud/api"
        private const val GCD_API = "https://www.comics.org/api"
        private const val RETRY_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000 // weekly

        private val HIRES_HOSTS = listOf(
            "uploads.mangadex.org",
            "images.mangabaka.dev",
            "s4.anilist.co",
            "cdn.myanimelist.net",
            "metron.cloud",
            "files.comics.org",
            "files1.comics.org",
        )

        /** True when the URL points at a recognized hi-res provider host. */
        fun isPreferredCoverUrl(url: String?): Boolean =
            url != null && HIRES_HOSTS.any { url.contains(it) }

        /**
         * Cover precedence shared by discovery feeds and cells: [current]
         * wins when it already holds recognized hi-res art and [incoming]
         * doesn't — automatic cover writes (feed seeds, refresh's
         * transient `thumbnail_url` rewrites) may upgrade but never
         * downgrade. A human's pick passes `manual = true` to always
         * apply. Blank/equal [incoming] is a no-op either way.
         */
        fun preferCover(current: String?, incoming: String?, manual: Boolean = false): String? {
            if (incoming.isNullOrBlank() || incoming == current) return current
            if (!manual && isPreferredCoverUrl(current) && !isPreferredCoverUrl(incoming)) {
                return current
            }
            return incoming
        }
    }

    private val client: OkHttpClient get() = networkHelper.client

    // Serialize upgrades — Jikan is rate-limited to ~3 req/s and a flurry of
    // detail opens shouldn't fire parallel requests.
    private val mutex = Mutex()

    // Bumped whenever a provider call throws — the mass sweeps watch this to
    // distinguish "no match found" from "provider is down/rate-limiting".
    private val providerFailures = java.util.concurrent.atomic.AtomicInteger(0)

    private fun checkedAt(kind: String, id: Long) =
        preferenceStore.getLong("hires_cover_checked_${kind}_$id", 0L)

    private fun isHiRes(url: String?) = isPreferredCoverUrl(url)

    /**
     * Cheap local check — no DB reads, no network. False when the manga
     * already has ANY cover: established art (extension-provided or a
     * previously fetched provider cover) is sticky and never restomped —
     * cover choice will be a user feature, so the resolver only fills
     * entries that have no art at all. Custom covers are always kept.
     */
    fun shouldUpgradeCover(manga: Manga): Boolean {
        if (manga.hasCustomCover() || !manga.thumbnailUrl.isNullOrBlank()) return false
        val checked = checkedAt("manga", manga.id).get()
        return checked == 0L || Instant.now().toEpochMilli() - checked >= RETRY_INTERVAL_MS
    }

    suspend fun upgradeMangaCover(manga: Manga) {
        val now = Instant.now().toEpochMilli()

        // One-time repair: covers cached before the MangaDex Referer fix are
        // "You can read this at MangaDex" placeholder files — wipe the cached
        // file and bump coverLastModified (part of the Coil cache key) so the
        // next load refetches the real art.
        val repairMarker = preferenceStore.getBoolean("hires_cover_repaired_${manga.id}", false)
        if (!repairMarker.get() && manga.thumbnailUrl?.contains("mangadex") == true) {
            coverCache.deleteFromCache(manga)
            updateManga.await(MangaUpdate(id = manga.id, coverLastModified = now))
            repairMarker.set(true)
            logcat(LogPriority.DEBUG) { "HiResCover: repaired placeholder cover for manga ${manga.id}" }
        }

        val marker = checkedAt("manga", manga.id)
        // Established art is sticky — a resolved provider cover or the
        // extension's own thumbnail is never overwritten by a later
        // resolution pass. Only coverless entries resolve below.
        if (!manga.thumbnailUrl.isNullOrBlank() ||
            (marker.get() > 0L && now - marker.get() < RETRY_INTERVAL_MS)
        ) {
            return
        }
        if (manga.hasCustomCover()) return

        val anilistId = getMangaTracks.await(manga.id)
            .firstOrNull { it.trackerId == TrackerManager.ANILIST }
            ?.remoteId
        val url = mutex.withLock { resolveCover(manga.title, anilistId) }
        marker.set(now)
        if (url != null && url != manga.thumbnailUrl) {
            updateManga.await(
                MangaUpdate(
                    id = manga.id,
                    thumbnailUrl = url,
                    coverLastModified = now,
                ),
            )
            logcat(LogPriority.DEBUG) { "HiResCover: manga ${manga.id} cover upgraded -> $url" }
        }
    }

    /**
     * One-time mass repair for placeholder files cached before the MangaDex
     * Referer fix — covers for entries that are never opened would keep
     * serving the stale "You can read this at MangaDex" file, so the whole
     * library gets swept once at app start.
     */
    suspend fun repairStaleMangaDexCovers() = withIOContext {
        val marker = preferenceStore.getBoolean("hires_cover_mass_repair_v1", false)
        if (marker.get()) return@withIOContext
        val now = Instant.now().toEpochMilli()
        val stale = mangaRepository.getMangaFavorites()
            .filter { it.thumbnailUrl?.contains("mangadex") == true }
        stale.forEach { manga ->
            coverCache.deleteFromCache(manga)
            updateManga.await(MangaUpdate(id = manga.id, coverLastModified = now))
        }
        marker.set(true)
        if (stale.isNotEmpty()) {
            logcat(LogPriority.DEBUG) { "HiResCover: repaired ${stale.size} stale MangaDex covers" }
        }
    }

    /**
     * One-time re-verify for covers stored before the title-match fix —
     * MangaDex's fuzzy search previously accepted a zero-score result, so some
     * entries carry art from an unrelated series. Re-runs the chain; applies
     * the URL only when the chain produces a (different) confident match.
     */
    suspend fun reverifyMangaDexCovers() = withIOContext {
        val marker = preferenceStore.getBoolean("hires_cover_match_v3", false)
        if (marker.get()) return@withIOContext
        // Established covers are sticky — only entries with no art at
        // all get resolved. A stored MangaDex/provider cover must not be
        // replaced by a later fuzzy match.
        val candidates = mangaRepository.getMangaFavorites()
            .filter { !it.hasCustomCover() && it.thumbnailUrl.isNullOrBlank() }
        var updated = 0
        var consecutiveProviderOutages = 0
        for (manga in candidates) {
            val anilistId = getMangaTracks.await(manga.id)
                .firstOrNull { it.trackerId == TrackerManager.ANILIST }
                ?.remoteId
            val failuresBefore = providerFailures.get()
            val url = mutex.withLock { resolveCover(manga.title, anilistId) }
            if (url != null && url != manga.thumbnailUrl) {
                updateManga.await(
                    MangaUpdate(
                        id = manga.id,
                        thumbnailUrl = url,
                        coverLastModified = Instant.now().toEpochMilli(),
                    ),
                )
                updated++
            }
            // A null result with fresh provider errors means the APIs are
            // down/rate-limiting us — bail out unmarked so a later launch
            // retries instead of marking the sweep done on an outage.
            val threw = providerFailures.get() != failuresBefore
            consecutiveProviderOutages = if (url == null && threw) {
                consecutiveProviderOutages + 1
            } else {
                0
            }
            if (consecutiveProviderOutages >= 5) {
                logcat(LogPriority.WARN) {
                    "HiResCover: re-verify aborted after provider outages ($updated/${candidates.size} updated)"
                }
                return@withIOContext
            }
            kotlinx.coroutines.delay(350) // stay under Jikan/GCD rate limits
        }
        marker.set(true)
        logcat(LogPriority.DEBUG) { "HiResCover: re-verify updated $updated/${candidates.size} covers" }
    }

    private suspend fun resolveCover(title: String, anilistId: Long?): String? {
        if (anilistId != null) {
            anilistCover(anilistId)?.let { return it }
        }
        return mangadexCover(title)
            ?: jikanCover(title)
            ?: metronCover(title)
            ?: gcdCover(title)
    }

    /** Exact-ID lookup via AniList's public GraphQL — no auth needed for media reads. */
    private suspend fun anilistCover(mediaId: Long): String? {
        return try {
            val payload = buildJsonObject {
                put(
                    "query",
                    "query(\$id:Int){Media(id:\$id,type:MANGA){coverImage{extraLarge large}}}",
                )
                putJsonObject("variables") { put("id", mediaId) }
            }
            withIOContext {
                client.newCall(
                    POST(ANILIST_API, body = payload.toString().toRequestBody(jsonMime)),
                )
                    .awaitSuccess()
                    .use { json.parseToJsonElement(it.body.string()) }
                    .jsonObject["data"]?.jsonObject
                    ?.get("Media")?.jsonObject
                    ?.get("coverImage")?.jsonObject
                    ?.let { cover ->
                        cover["extraLarge"]?.jsonPrimitive?.content
                            ?: cover["large"]?.jsonPrimitive?.content
                    }
            }
        } catch (e: Exception) {
            providerFailures.incrementAndGet()
            logcat(LogPriority.WARN) { "HiResCover: AniList lookup failed for $mediaId: ${e.message}" }
            null
        }
    }

    private suspend fun mangadexCover(title: String): String? {
        return try {
            withIOContext {
                val url = "$MANGADEX_API/manga?title=${title.encodeUrl()}&limit=5" +
                    "&includes[]=cover_art&order[followedCount]=desc" +
                    "&contentRating[]=safe&contentRating[]=suggestive" +
                    "&contentRating[]=erotica&contentRating[]=pornographic"
                val root = client.newCall(GET(url))
                    .awaitSuccess()
                    .use { json.parseToJsonElement(it.body.string()) }
                    .jsonObject
                val results = root["data"]?.jsonArray ?: return@withIOContext null
                // MangaDex title search is full-text — results may share no
                // title with the query at all, so a zero-score candidate must
                // be rejected or it picks arbitrary art.
                val manga = results
                    .map { it.jsonObject }
                    .filter { scoreTitleMatch(title, it) > 0 }
                    .maxByOrNull { scoreTitleMatch(title, it) }
                    ?: return@withIOContext null
                val mangaId = manga["id"]?.jsonPrimitive?.content ?: return@withIOContext null
                val fileName = manga["relationships"]?.jsonArray
                    ?.map { it.jsonObject }
                    ?.firstOrNull { it["type"]?.jsonPrimitive?.content == "cover_art" }
                    ?.get("attributes")?.jsonObject
                    ?.get("fileName")?.jsonPrimitive?.content
                    ?: return@withIOContext null
                "$MANGADEX_UPLOADS/covers/$mangaId/$fileName"
            }
        } catch (e: Exception) {
            providerFailures.incrementAndGet()
            logcat(LogPriority.WARN) { "HiResCover: MangaDex lookup failed for '$title': ${e.message}" }
            null
        }
    }

    private suspend fun jikanCover(title: String): String? {
        return try {
            withIOContext {
                val url = "$JIKAN_API/manga?q=${title.encodeUrl()}&limit=5"
                val root = client.newCall(GET(url))
                    .awaitSuccess()
                    .use { json.parseToJsonElement(it.body.string()) }
                    .jsonObject
                val results = root["data"]?.jsonArray ?: return@withIOContext null
                val entry = results
                    .map { it.jsonObject }
                    .filter { manga ->
                        val titles = listOfNotNull(
                            manga["title"]?.jsonPrimitive?.content,
                            manga["title_english"]?.jsonPrimitive?.content,
                            manga["title_japanese"]?.jsonPrimitive?.content,
                        )
                        titles.any { it.equals(title, ignoreCase = true) }
                    }
                    .firstOrNull()
                    ?: return@withIOContext null
                entry["images"]?.jsonObject
                    ?.get("jpg")?.jsonObject
                    ?.get("large_image_url")?.jsonPrimitive?.content
            }
        } catch (e: Exception) {
            providerFailures.incrementAndGet()
            logcat(LogPriority.WARN) { "HiResCover: Jikan lookup failed for '$title': ${e.message}" }
            null
        }
    }

    /**
     * Metron (metron.cloud) — western-comics DB. Requires the user's API
     * token; silently skipped when unset. Series name search, then the
     * series' nested issue list for the first `image`.
     */
    private suspend fun metronCover(title: String): String? {
        val token = networkPreferences.metronApiToken().get().trim()
        if (token.isEmpty()) return null
        val headers = Headers.headersOf("Authorization", "Bearer $token")
        return try {
            withIOContext {
                val seriesRoot = client.newCall(
                    GET("$METRON_API/series/?name=${title.encodeUrl()}", headers = headers),
                )
                    .awaitSuccess()
                    .use { json.parseToJsonElement(it.body.string()) }
                    .jsonObject
                val series = seriesRoot["results"]?.jsonArray
                    ?.map { it.jsonObject }
                    ?.filter { obj ->
                        val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
                        name.equals(title, ignoreCase = true) ||
                            name.startsWith(title, ignoreCase = true) ||
                            title.startsWith(name, ignoreCase = true)
                    }
                    ?.firstOrNull()
                    ?: return@withIOContext null
                val seriesId = series["id"]?.jsonPrimitive?.content ?: return@withIOContext null
                client.newCall(
                    GET("$METRON_API/series/$seriesId/issue_list/", headers = headers),
                )
                    .awaitSuccess()
                    .use { json.parseToJsonElement(it.body.string()) }
                    .let { root ->
                        val issues = when (root) {
                            is JsonObject -> root["results"]?.jsonArray
                            is JsonArray -> root
                            else -> null
                        }
                        issues?.map { it.jsonObject }
                            ?.firstNotNullOfOrNull {
                                it["image"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
                            }
                    }
            }
        } catch (e: Exception) {
            providerFailures.incrementAndGet()
            logcat(LogPriority.WARN) { "HiResCover: Metron lookup failed for '$title': ${e.message}" }
            null
        }
    }

    /**
     * Grand Comics Database — anonymous REST API. Series-name search picks the
     * best same-named series (US/English preferred), then the first active
     * issue's `cover` URL. The cover is fetched once to verify it actually
     * loads — files1.comics.org sits behind Cloudflare and can challenge
     * image requests, in which case we skip rather than store a dead URL.
     */
    private suspend fun gcdCover(title: String): String? {
        return try {
            withIOContext {
                val root = client.newCall(
                    GET("$GCD_API/series/name/${title.encodeUrl()}/?format=json"),
                )
                    .awaitSuccess()
                    .use { json.parseToJsonElement(it.body.string()) }
                val seriesArray = when (root) {
                    is JsonObject -> root["results"]?.jsonArray
                    is JsonArray -> root
                    else -> null
                } ?: return@withIOContext null
                val series = seriesArray
                    .map { it.jsonObject }
                    .filter { obj ->
                        val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
                        name.equals(title, ignoreCase = true) ||
                            name.startsWith(title, ignoreCase = true) ||
                            title.startsWith(name, ignoreCase = true)
                    }
                    .maxByOrNull { obj ->
                        val lang = obj["language"]?.jsonPrimitive?.content
                        val country = obj["country"]?.jsonPrimitive?.content
                        (if (lang == "en") 2 else 0) + (if (country == "us") 1 else 0)
                    }
                    ?: return@withIOContext null
                val issueUrl = series["active_issues"]?.jsonArray
                    ?.firstOrNull()?.jsonPrimitive?.content
                    ?: return@withIOContext null
                val issue = client.newCall(
                    GET(issueUrl + if ("?" in issueUrl) "&format=json" else "?format=json"),
                )
                    .awaitSuccess()
                    .use { json.parseToJsonElement(it.body.string()) }
                    .jsonObject
                val cover = issue["cover"]?.jsonPrimitive?.content
                    ?.takeIf(String::isNotBlank)
                    ?: return@withIOContext null
                // Verify the image actually serves — Cloudflare-challenged
                // hosts answer 403, and storing a dead URL is worse than none.
                client.newCall(GET(cover)).execute().use { resp ->
                    if (resp.isSuccessful) cover else null
                }
            }
        } catch (e: Exception) {
            providerFailures.incrementAndGet()
            logcat(LogPriority.WARN) { "HiResCover: GCD lookup failed for '$title': ${e.message}" }
            null
        }
    }

    /** 2 = exact title/alt-title match, 1 = prefix match, 0 = no match. */
    private fun scoreTitleMatch(query: String, manga: kotlinx.serialization.json.JsonObject): Int {
        val titles = buildList {
            manga["attributes"]?.jsonObject?.get("title")?.jsonObject
                ?.values?.forEach { runCatching { add(it.jsonPrimitive.content) } }
            manga["attributes"]?.jsonObject?.get("altTitles")?.jsonArray
                ?.forEach { alt ->
                    alt.jsonObject.values.forEach { runCatching { add(it.jsonPrimitive.content) } }
                }
        }
        return when {
            titles.any { it.equals(query, ignoreCase = true) } -> 2
            titles.any { it.startsWith(query, ignoreCase = true) } -> 1
            else -> 0
        }
    }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
