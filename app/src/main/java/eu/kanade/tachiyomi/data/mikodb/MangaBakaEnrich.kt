package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.domain.entries.manga.model.hasCustomCover
import eu.kanade.domain.entries.novel.model.hasCustomCover
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Lazy detail enrichment for MangaBaka-seeded canonical works
 * (`mk:mb:` ids).
 *
 * Seeded rows carry only browse-card fields — title, cover, rating —
 * so a canonical entry opens as an empty stub: no description, junk
 * genres, and `provider_links = '[]'`, which leaves [GoldenSources]
 * nothing to bind and the canonize pipeline starved. On canonical
 * entry open this fetches MangaBaka's public series detail and writes:
 *
 *  - description / genres / author / status / cover → the entry row
 *  - `links_v2` webplatform URLs → `provider_links` on the bridge row
 *    (the ammunition GoldenSources intersects against installed
 *    extension miko.json provider declarations)
 *  - `source` ids (anilist / kitsu / my_anime_list / manga_updates /
 *    shikimori / anime_planet / anime_news_network) → `external_ids`
 *    crosswalk rows
 *
 * Runs once per work: the bridge row is tagged
 * `resolution_source = "mb_detail"` after a fetch — success or clean
 * miss — so repeat opens stay local-only. Provider slugs mirror the
 * Worker's linkEntries convention (resolve.ts) so both sides agree on
 * `webtoons`, `naver`, `royalroad`, `novelupdates`, `mangadex` ids.
 */
class MangaBakaEnrich(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
    private val bridge: MangaNovelIdBridge = Injekt.get(),
    private val goldenSources: GoldenSources = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val novelRepository: NovelRepository = Injekt.get(),
) {

    // ---------- public API ----------

    /** Enrich the bridge row + the manga entry for [workId]. No-op on
     *  non-`mk:mb:` ids, already-enriched rows, or fetch failure. */
    suspend fun run(workId: String, manga: Manga): Boolean {
        clearTypeSeed(manga)
        // A bare entry means an earlier enrich tagged the work but its
        // entry write never landed (dupe row, transient update failure)
        // — the tag must not gate the fetch that repairs it.
        val bare = manga.description.isNullOrBlank() || manga.thumbnailUrl.isNullOrBlank()
        val detail = fetchDetail(workId, force = bare) ?: return false
        // Don't downgrade a recognized hi-res/custom cover to the
        // MangaBaka art — the resolver upgrades to original-resolution
        // provider covers and enrichment must not stomp them.
        val keepCover = manga.hasCustomCover() ||
            eu.kanade.tachiyomi.data.cover.HiResCoverResolver
                .isPreferredCoverUrl(manga.thumbnailUrl)
        // Entry metadata first — the user-facing fix. A bridge-row write
        // failure must not starve the visible update (and vice versa).
        runCatching {
            mangaRepository.updateManga(
                MangaUpdate(
                    id = manga.id,
                    author = detail.author,
                    description = detail.description,
                    genre = detail.genres.takeIf { it.isNotEmpty() },
                    thumbnailUrl = detail.coverUrl.takeUnless { keepCover },
                    coverLastModified = detail.coverUrl
                        ?.takeUnless { keepCover }
                        ?.let { System.currentTimeMillis() },
                    status = detail.status?.let { CanonicalCatalog.statusOf(it, manga = true) },
                ),
            )
        }.onFailure { e ->
            logcat(LogPriority.WARN) { "MangaBakaEnrich: entry update failed for $workId: ${e.message}" }
        }
        runCatching { enrichBridgeRow(workId, detail) }
            .onFailure { e ->
                logcat(LogPriority.WARN) { "MangaBakaEnrich: bridge write failed for $workId: ${e.message}" }
            }
        return true
    }

    /** Same as [run] for novels/books (`mk:mb:` novel rows). */
    suspend fun run(workId: String, novel: Novel): Boolean {
        clearTypeSeed(novel)
        val bare = novel.description.isNullOrBlank() || novel.thumbnailUrl.isNullOrBlank()
        val detail = fetchDetail(workId, force = bare) ?: return false
        val keepCover = novel.hasCustomCover() ||
            eu.kanade.tachiyomi.data.cover.HiResCoverResolver
                .isPreferredCoverUrl(novel.thumbnailUrl)
        runCatching {
            novelRepository.updateNovel(
                NovelUpdate(
                    id = novel.id,
                    author = detail.author,
                    description = detail.description,
                    genre = detail.genres.takeIf { it.isNotEmpty() },
                    thumbnailUrl = detail.coverUrl.takeUnless { keepCover },
                    coverLastModified = detail.coverUrl
                        ?.takeUnless { keepCover }
                        ?.let { System.currentTimeMillis() },
                    status = detail.status?.let { CanonicalCatalog.statusOf(it, manga = false) },
                ),
            )
        }.onFailure { e ->
            logcat(LogPriority.WARN) { "MangaBakaEnrich: entry update failed for $workId: ${e.message}" }
        }
        runCatching { enrichBridgeRow(workId, detail) }
            .onFailure { e ->
                logcat(LogPriority.WARN) { "MangaBakaEnrich: bridge write failed for $workId: ${e.message}" }
            }
        return true
    }

    /**
     * Earlier materialization seeded `genre` with the work's type
     * ("oel", "webtoon", …) so a bare tag stayed visible. A single-tag
     * genre matching a taxonomy word is that seed — clear it so a work
     * MangaBaka has no genres for doesn't carry junk taxonomy forever.
     */
    private suspend fun clearTypeSeed(manga: Manga) {
        val g = manga.genre ?: return
        if (g.size == 1 && g[0].lowercase() in TAXONOMY_KINDS) {
            runCatching {
                mangaRepository.updateManga(MangaUpdate(id = manga.id, genre = emptyList()))
            }
        }
    }

    private suspend fun clearTypeSeed(novel: Novel) {
        val g = novel.genre ?: return
        if (g.size == 1 && g[0].lowercase() in TAXONOMY_KINDS) {
            runCatching {
                novelRepository.updateNovel(NovelUpdate(id = novel.id, genre = emptyList()))
            }
        }
    }

    // ---------- fetch ----------

    private suspend fun fetchDetail(workId: String, force: Boolean = false): Detail? {
        val mbId = workId.removePrefix(MB_PREFIX)
            .takeIf { it != workId && it.isNotBlank() } ?: return null
        val row = bridge.byWorkId(workId)
        if (row?.resolutionSource == RESOLUTION_MB_DETAIL && !force) return null
        return try {
            val response = networkHelper.client.newCall(
                GET(
                    "$BASE_URL/v1/series/$mbId",
                    headers = Headers.headersOf("User-Agent", WorkIdentityApi.USER_AGENT),
                ),
            ).awaitSuccess()
            with(json) { response.parseAs<SeriesEnvelope>().data }?.toDetail()
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "MangaBakaEnrich: detail fetch failed for $workId: ${e.message}" }
            null
        }
    }

    // ---------- bridge row ----------

    /**
     * Merge webplatform links into `provider_links` and source ids into
     * `external_ids`, then tag the row so the fetch never repeats.
     */
    private suspend fun enrichBridgeRow(workId: String, detail: Detail) {
        val row = bridge.byWorkId(workId) ?: return
        val mergedLinks = (goldenSources.providerLinksOf(row.providerLinks) +
            detail.providerLinks)
            .distinctBy { it.provider to it.url }
        val linksJson = mergedLinks.joinToString(",", "[", "]") { l ->
            buildString {
                append("{\"provider\":\"").append(l.provider.escapeJson()).append('\"')
                append(",\"external_id\":")
                append(l.externalId?.let { "\"${it.escapeJson()}\"" } ?: "null")
                append(",\"url\":\"").append(l.url.orEmpty().escapeJson()).append("\"}")
            }
        }
        bridge.upsert(
            row.copy(
                providerLinks = linksJson,
                resolutionSource = RESOLUTION_MB_DETAIL,
            ),
            externalIds = row.externalIds + detail.externalIds,
        )
    }

    // ---------- wire shape ----------

    @Serializable
    private data class SeriesEnvelope(val data: SeriesDto? = null)

    @Serializable
    private data class SeriesDto(
        val description: String? = null,
        val status: String? = null,
        val authors: JsonElement? = null,
        val genres: JsonElement? = null,
        val genres_v2: JsonElement? = null,
        val cover: JsonObject? = null,
        val links_v2: List<LinkDto>? = null,
        val source: Map<String, SourceDto>? = null,
    )

    @Serializable
    internal data class LinkDto(
        val url: String? = null,
        /** Host string, e.g. "www.webnovel.com". */
        val name: String? = null,
        /** "webplatform" is the content-carrier class. */
        val type: String? = null,
        val language: String? = null,
    )

    @Serializable
    private data class SourceDto(val id: JsonElement? = null)

    private data class Detail(
        val description: String? = null,
        val status: String? = null,
        val author: String? = null,
        val genres: List<String> = emptyList(),
        val coverUrl: String? = null,
        val providerLinks: List<GoldenSources.ProviderLink> = emptyList(),
        val externalIds: Map<String, String> = emptyMap(),
    )

    private fun SeriesDto.toDetail(): Detail = Detail(
        description = description?.takeIf { it.isNotBlank() },
        status = status,
        author = nameListOf(authors).firstOrNull(),
        // MangaBaka genre slugs arrive lowercase ("sci-fi") — title-case
        // them so canonical rows render like extension-fetched entries.
        genres = (nameListOf(genres).ifEmpty { nameListOf(genres_v2) })
            .map { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } },
        coverUrl = coverUrlOf(cover),
        providerLinks = providerLinksOfLinks(links_v2),
        externalIds = sourceIdsOf(source),
    )

    // ---------- parsing helpers ----------

    /** Elements may be bare strings or objects ({name}) — authors and
     *  genres_v2 both appear in either shape. */
    private fun nameListOf(el: JsonElement?): List<String> {
        if (el !is JsonArray) return emptyList()
        return el.mapNotNull { item ->
            when (item) {
                is JsonObject -> item["name"]?.jsonPrimitive?.contentOrNull
                    ?: item["title"]?.jsonPrimitive?.contentOrNull
                else -> (item as? JsonPrimitive)?.contentOrNull
            }
        }
    }

    private fun coverUrlOf(cover: JsonObject?): String? {
        // Full-res original first (the x250 imgproxy variant renders
        // visibly soft as an entry cover), then largest-to-smallest.
        (cover?.get("raw") as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
            ?.let { return it }
        fun variant(name: String): String? = (cover?.get(name) as? JsonObject)
            ?.get("x1")?.jsonPrimitive?.contentOrNull
        return variant("x350") ?: variant("x250") ?: variant("x150")
    }

    private fun sourceIdsOf(source: Map<String, SourceDto>?): Map<String, String> =
        source.orEmpty().mapNotNull { (provider, dto) ->
            val id = (dto.id as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            id?.let { provider to it }
        }.toMap()

    private fun String.escapeJson() = replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private const val BASE_URL = "https://api.mangabaka.org"
        private const val MB_PREFIX = "mk:mb:"
        /** Bridge-row marker: detail already fetched (success or miss).
         *  Bumped to v2 — rows enriched under v1 carried the x250 proxy
         *  cover and lowercase genres, so they re-fetch once. */
        internal const val RESOLUTION_MB_DETAIL = "mb_detail_v2"

        /** Taxonomy words that are kinds, not genres — the materialize
         *  seed artifact that [clearTypeSeed] removes. */
        private val TAXONOMY_KINDS = setOf(
            "manga", "manhwa", "manhua", "comic", "webtoon", "oel", "other",
            "book", "novel",
        )

        /**
         * links_v2 webplatform URLs → provider_links entries. Provider slug
         * comes from the worker's URL-id regexes first, then a host-suffix
         * table, then the bare host — matching resolve.ts's linkEntries
         * fallback so both sides speak the same vocabulary.
         */
        internal fun providerLinksOfLinks(links: List<LinkDto>?): List<GoldenSources.ProviderLink> =
            links.orEmpty()
                .filter { it.type == "webplatform" && !it.url.isNullOrBlank() }
                .map { link ->
                    val url = link.url!!
                    var provider: String? = null
                    var extId: String? = null
                    for ((re, name) in LINK_ID) {
                        re.find(url)?.let { m ->
                            provider = name
                            extId = m.groupValues.getOrNull(1)
                        }
                        if (provider != null) break
                    }
                    GoldenSources.ProviderLink(
                        provider = provider ?: hostProviderOf(url, link.name),
                        externalId = extId,
                        url = url,
                    )
                }

        // ---------- provider slugs ----------

        internal fun hostProviderOf(url: String, name: String?): String {
            val host = (name?.takeIf { it.isNotBlank() }
                ?: runCatching { java.net.URI(url).host }.getOrNull())
                ?.lowercase()
                ?.removePrefix("www.")
                ?: return name?.lowercase() ?: "unknown"
            for ((suffix, provider) in HOST_PROVIDERS) {
                if (host == suffix || host.endsWith(".$suffix")) return provider
            }
            // Fallback: second-level label ("comic.example.com" → "example").
            val labels = host.split('.')
            return if (labels.size >= 2) labels[labels.size - 2] else host
        }


        // Worker resolve.ts LINK_ID — same regexes, same provider names.
        private val LINK_ID: List<Pair<Regex, String>> = listOf(
            Regex("""title_no=(\d+)""", RegexOption.IGNORE_CASE) to "webtoons",
            Regex("""titleId=(\d+)""", RegexOption.IGNORE_CASE) to "naver",
            Regex("""royalroad\.com/fiction/(\d+)""", RegexOption.IGNORE_CASE) to "royalroad",
            Regex("""novelupdates\.com/series/([\w-]+)""", RegexOption.IGNORE_CASE) to "novelupdates",
            Regex("""mangadex\.org/(?:title|manga)/([0-9a-f-]{36})""", RegexOption.IGNORE_CASE) to "mangadex",
        )

        /** Host suffix → provider slug; ordered most-specific first. */
        private val HOST_PROVIDERS: List<Pair<String, String>> = listOf(
            "mangaplus.shueisha.co.jp" to "mangaplus",
            "page.kakao.com" to "kakao_page",
            "webtoon.kakao.com" to "kakao",
            "comic.naver.com" to "naver",
            "series.naver.com" to "naver",
            "manga.bilibili.com" to "bilibili_comics",
            "comic.pixiv.net" to "pixiv_comic",
            "comic-walker.com" to "comic_walker",
            "webnovel.com" to "webnovel",
            "royalroad.com" to "royalroad",
            "novelupdates.com" to "novelupdates",
            "mangadex.org" to "mangadex",
            "tapas.io" to "tapas",
            "webtoons.com" to "webtoons",
            "piccoma.com" to "piccoma",
            "kodansha.us" to "kodansha",
            "yenpress.com" to "yenpress",
            "sevenseasentertainment.com" to "sevenseas",
            "naver.com" to "naver",
            "kakao.com" to "kakao",
            "bilibili.com" to "bilibili",
            "viz.com" to "viz",
            "shueisha.co.jp" to "shueisha",
        )
    }
}
