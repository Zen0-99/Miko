package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.data.mikodb.MangaNovelIdBridge.WorkRow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uy.kohesive.injekt.injectLazy

/**
 * Ordered canonical work resolution for manga/novel library entries.
 *
 * 1. local `works_cache` external-ID hit (URL-carried provider IDs)
 * 2. local `works_cache` normalized-title hit
 * 3. Worker `resolve` endpoint (MangaBaka mirror in D1); miss rows write
 *    the per-work delta into `works_cache`
 * 4. local deterministic mint [WorkIdMint] — collision-review deferred
 *
 * Step 4 runs only when the Worker is unreachable or has no match;
 * identical mints dedupe cross-source entries for the same work.
 */
class WorkIdResolver {

    data class Candidate(
        /** "manga" | "novel" | "shows" — entry's medium, never mixed. */
        val medium: String,
        val title: String,
        val url: String? = null,
        val author: String? = null,
        /** Source domains for URL ID mining (e.g. readcomicsonline.ru). */
        val webviewUrls: List<String> = emptyList(),
        /** Published-book signal (Anna's Archive etc.) — ISBN-10/13. */
        val isbn: String? = null,
        /** Marks published-book sources; gates the Worker's OpenLibrary path. */
        val isBook: Boolean = false,
        /** Fetch disambiguator + mk:m: mint-kind seed — never asserted as
         *  the resolved kind (vocab {book, movie, series}; 15-01 contract).
         *  Shows call sites pass the Cinemeta url type when known. */
        val kindHint: String? = null,
    )

    enum class Confidence { EXTERNAL_ID, TITLE, WORKER, LOCAL_MINT }

    data class CanonicalWorkId(
        val value: String,
        val medium: String,
        val confidence: Confidence,
        /** Colliding title keys — later phases can disambiguate. */
        val ambiguous: Boolean = false,
    )

    private val bridge: MangaNovelIdBridge by injectLazy()
    private val api: WorkIdentityApi by injectLazy()
    private val json: Json by injectLazy()

    suspend fun resolve(candidate: Candidate): CanonicalWorkId? {
        val allowedTypes = allowedTypes(candidate.medium)
        val norm = TitleNormalizer.normalize(candidate.title) ?: return null

        // 1. URL-carried provider IDs + ISBN against local cache.
        val ids = urlCandidates(candidate) +
            listOfNotNull(candidate.isbn?.let { normalizeIsbn(it)?.let { n -> "isbn" to n } })
        for ((provider, id) in ids) {
            val row = bridge.byExternalId(provider, id)
            if (row != null && row.type in allowedTypes) {
                return CanonicalWorkId(row.mikoWorkId, candidate.medium, Confidence.EXTERNAL_ID)
            }
        }

        // 2. Normalized title against local cache.
        val titleHits = bridge.byNormalizedTitle(norm)
            .filter { it.type in allowedTypes }
        if (titleHits.size == 1) {
            return CanonicalWorkId(titleHits.single().mikoWorkId, candidate.medium, Confidence.TITLE)
        }

        // 3. Worker resolve — tries providers then title server-side.
        val resolved = resolveRemote(candidate, norm, allowedTypes)
        if (resolved != null) return resolved

        // 4. Multi-hit title ambiguity with no server: leave unresolved
        // rather than guessing between distinct works.
        if (titleHits.size > 1) {
            return CanonicalWorkId(
                titleHits.first().mikoWorkId,
                candidate.medium,
                Confidence.TITLE,
                ambiguous = true,
            )
        }

        // 5. Local mint.
        return CanonicalWorkId(
            WorkIdMint.mint(
                candidate.title,
                mediumToType(candidate.medium, candidate.isBook, candidate.kindHint),
                candidate.author,
            ),
            candidate.medium,
            Confidence.LOCAL_MINT,
        )
    }

    private suspend fun resolveRemote(
        candidate: Candidate,
        norm: String,
        allowedTypes: Set<String>,
    ): CanonicalWorkId? {
        // One attempt per URL candidate, then a plain title lookup.
        // kind_hint rides every request — advisory only (Worker is the
        // kind authority): 'book' gates OpenLibrary, 'movie'/'series'
        // order the Cinemeta meta fetch.
        val kind = candidate.kindHint ?: (if (candidate.isBook) "book" else null)
        val isbn = candidate.isbn?.let { normalizeIsbn(it) }
        val attempts = urlCandidates(candidate).map { (provider, id) ->
            WorkIdentityApi.ResolveRequest(
                provider = provider,
                externalId = id,
                title = candidate.title,
                medium = candidate.medium,
                author = candidate.author,
                kindHint = kind,
                isbn = isbn,
            )
        } + WorkIdentityApi.ResolveRequest(
            title = candidate.title,
            medium = candidate.medium,
            author = candidate.author,
            kindHint = kind,
            isbn = isbn,
        )
        for (request in attempts) {
            val dto = api.resolve(request) ?: continue
            // `kind` is the post-15-01 taxonomy name; `type` the legacy
            // mirror. Honest-null rows (upstream-degraded mints) resolve
            // only when the Worker vouches the carrier via `medium`.
            val dtoKind = dto.kind ?: dto.type
            val kindOk = dtoKind != null && dtoKind in allowedTypes
            val carrierOk = dtoKind == null && dto.medium != null && dto.medium == candidate.medium
            if (!kindOk && !carrierOk) continue
            // kind-null rows skip the local upsert — works_cache.type is
            // NOT NULL and caching an unconfirmed kind would lie.
            if (dtoKind != null) bridge.upsert(dto.toWorkRow(dtoKind))
            return CanonicalWorkId(dto.miko_work_id, candidate.medium, Confidence.WORKER)
        }
        return null
    }

    private fun WorkIdentityApi.WorkDto.toWorkRow(kind: String) = WorkRow(
        mikoWorkId = miko_work_id,
        type = kind, title = title,
        romanizedTitle = romanized_title, nativeTitle = native_title,
        year = year?.toLong(), totalChapters = total_chapters, author = author,
        providerLinks = provider_links,
        resolutionSource = "worker",
        externalIds = external_ids + linkExternalIds(provider_links),
    )

    /** provider_links entries also resolve as external IDs — uniform
     * lookup for mangadex/webtoons/novelupdates/royalroad URLs. */
    private fun linkExternalIds(providerLinks: String): Map<String, String> = try {
        json.parseToJsonElement(providerLinks).jsonArray
            .mapNotNull { el ->
                val obj = el.jsonObject
                val p = obj["provider"]?.jsonPrimitive?.content
                val e = obj["external_id"]?.jsonPrimitive?.content
                if (p != null && e != null) p to e else null
            }
            .toMap()
    } catch (_: Exception) {
        emptyMap()
    }

    /** (provider, external_id) pairs mined from known URL shapes. */
    private fun urlCandidates(candidate: Candidate): List<Pair<String, String>> {
        val urls = listOfNotNull(candidate.url) + candidate.webviewUrls
        return urls.flatMap { extractExternalIds(it) }
    }

    companion object {
        const val MEDIUM_MANGA = "manga"
        const val MEDIUM_NOVEL = "novel"
        const val MEDIUM_SHOWS = "shows"

        /** MangaBaka/OpenLibrary types accepted for each entry medium. */
        internal fun allowedTypes(medium: String) = when (medium) {
            MEDIUM_SHOWS -> setOf("anime", "movie", "series")
            // 'book' rows (OpenLibrary works) count as novel-medium.
            MEDIUM_NOVEL -> setOf("novel", "oel", "book")
            else -> setOf("manga", "manhwa", "manhua", "oel", "other")
        }

        /** Kind stamped into local `mk:m:` fallback mints. Shows mints
         *  carry a shows kind (kindHint ?: "series") so mk:m: hashes
         *  stay carrier-disjoint from same-titled manga mints. */
        internal fun mediumToType(medium: String, isBook: Boolean, kindHint: String?) = when {
            medium == MEDIUM_SHOWS -> kindHint ?: "series"
            isBook -> "book"
            medium == MEDIUM_NOVEL -> "novel"
            else -> "manga"
        }

        private val MANGADEX_UUID = Regex("""/manga/([0-9a-f-]{36})""", RegexOption.IGNORE_CASE)
        private val WEBTOONS_ID = Regex("""[?&]title_no=(\d+)""", RegexOption.IGNORE_CASE)
        private val MAL_ID = Regex("""myanimelist\.net/manga/(\d+)""", RegexOption.IGNORE_CASE)
        private val MU_ID =
            Regex("""(?:mangaupdates\.com|www\.mangaupdates\.com)/series/([\w-]+)""", RegexOption.IGNORE_CASE)
        private val NU_ID = Regex("""novelupdates\.com/series/([\w-]+)""", RegexOption.IGNORE_CASE)
        private val RR_ID = Regex("""royalroad\.com/fiction/(\d+)""", RegexOption.IGNORE_CASE)
        private val GENRE_ISBN = Regex("""^isbn:([0-9Xx-]{9,17})$""", RegexOption.IGNORE_CASE)
        private val ISBN_SHAPE = Regex("""\d{9}[\dX]|\d{13}""")

        // "cinemeta:series:tt0903747" / "cinemeta:episode:tt0903747:1:1"
        private val CINEMETA_URL =
            Regex("""^cinemeta:(series|movie|episode):(tt\d+)""", RegexOption.IGNORE_CASE)

        // hentaistream provider-host urls carry the provider slug as the
        // last path segment (worker-side slugRegistry canonicalizes).
        private val HS_URL_SLUG = Regex(
            """https?://(?:[\w-]+\.)*(?:hentaimama|hentaisea|hentaitv)\.[\w.]+/(?:[\w-]+/)*?([\w-]+)/?(?:[?#].*)?$""",
            RegexOption.IGNORE_CASE,
        )

        /** "cinemeta:series:tt0903747" → ("series", "tt0903747"); episode
         *  urls report "series" (kind_hint fetch order only). */
        fun cinemetaRef(url: String): Pair<String, String>? =
            CINEMETA_URL.find(url)?.let {
                val type = if (it.groupValues[1] == "episode") "series" else it.groupValues[1]
                type to it.groupValues[2]
            }

        /** "isbn:9780441172719" genre tags (Anna's Archive) → normalized ISBN. */
        fun isbnFromGenre(genre: List<String>?): String? =
            genre.orEmpty().asSequence()
                .mapNotNull { GENRE_ISBN.matchEntire(it.trim())?.groupValues?.get(1) }
                .map { normalizeIsbn(it) }
                .firstOrNull()

        /** Digits + X only; null unless it's a plausible ISBN-10/13. */
        fun normalizeIsbn(raw: String): String? {
            val clean = raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
            return clean.takeIf { it.matches(ISBN_SHAPE) }
        }

        fun extractExternalIds(url: String): List<Pair<String, String>> {
            val u = url.lowercase()
            return buildList {
                // cinemeta:{series|movie|episode}:tt… is an app-internal
                // scheme — must run BEFORE the http(s) bail or
                // Cinemeta-bound shows never produce an imdb candidate.
                cinemetaRef(u)?.let { add("imdb" to it.second) }
                if (!u.startsWith("http")) return@buildList
                MANGADEX_UUID.find(url)?.let { add("mangadex" to it.groupValues[1]) }
                WEBTOONS_ID.find(url)?.let { add("webtoons" to it.groupValues[1]) }
                MAL_ID.find(url)?.let { add("my_anime_list" to it.groupValues[1]) }
                MU_ID.find(url)?.let { add("manga_updates" to it.groupValues[1]) }
                NU_ID.find(url)?.let { add("novelupdates" to it.groupValues[1]) }
                RR_ID.find(url)?.let { add("royalroad" to it.groupValues[1]) }
                HS_URL_SLUG.find(u)?.let { add("hs" to it.groupValues[1]) }
            }
        }
    }
}
