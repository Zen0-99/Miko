package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.domain.ui.model.Carrier
import eu.kanade.tachiyomi.data.mikodb.MangaNovelIdBridge.WorkRow
import eu.kanade.tachiyomi.source.novel.AudioChapterSchemes
import eu.kanade.tachiyomi.source.novel.abs.AbsNovelSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.novel.service.NovelSourceManager
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
        /** Carrier key — "shows" | "manga" | "books"
         *  ([eu.kanade.domain.ui.model.Carrier.medium]); never mixed. */
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
        /** Remote-audio entry (ABS/LibriVox/IA scheme url or the entry's
         *  VIEWER_FLAG_AUDIOBOOK). 22-06 decision B: kind='audiobook'
         *  participates in the mk:m: mint hash, so audio editions mint
         *  their own works instead of stitching onto the text work —
         *  text candidates must therefore leave this false or their
         *  identity forks. */
        val isAudiobook: Boolean = false,
        /** Fetch disambiguator + mk:m: mint-kind seed — never asserted as
         *  the resolved kind (vocab {book, movie, series, audiobook};
         *  15-01 contract + 22-06). Shows call sites pass the Cinemeta
         *  url type when known. */
        val kindHint: String? = null,
        /** The entry's fetched chapter count — the only signal that
         *  separates same-author same-title works (a one-shot vs the
         *  series, e.g. BLAME! 1995 vs 1997). Null = unknown; hits with
         *  null totals can't be confirmed by this signal. */
        val chapterCount: Int? = null,
    )

    enum class Confidence { EXTERNAL_ID, TITLE, WORKER, LOCAL_MINT }

    data class CanonicalWorkId(
        val value: String,
        val medium: String,
        val confidence: Confidence,
        /** Worker-assigned kind when a catalog row resolved; null on
         *  LOCAL_MINT/honest-null works (never re-derived client-side). */
        val kind: String? = null,
        val nsfw: Boolean = false,
        /** Colliding title keys — later phases can disambiguate. */
        val ambiguous: Boolean = false,
    )

    private val bridge: MangaNovelIdBridge by injectLazy()
    private val api: WorkIdentityApi by injectLazy()
    private val json: Json by injectLazy()
    private val novelSourceManager: NovelSourceManager by injectLazy()

    /**
     * [excludeWorkId] drops that row from the local title hits — mint
     *  promotion uses it so the mint's own `works_cache` row can't
     *  short-circuit the Worker lookup that would upgrade it.
     */
    suspend fun resolve(candidate: Candidate, excludeWorkId: String? = null): CanonicalWorkId? {
        val resolved = resolvePipeline(candidate, excludeWorkId)
        // 22-06 (B): audio entries resolve onto their own kind='audiobook'
        // works — the resolve stamps the works_cache row (the LOCAL_MINT
        // path creates none) and upserts the url-derived provider binding
        // so re-resolution is idempotent and the Editions block can find
        // the edition by work.
        if (resolved != null && !resolved.ambiguous && candidate.isAudioCandidate()) {
            stampAudioWork(candidate, resolved.value)
        }
        return resolved
    }

    private suspend fun resolvePipeline(
        candidate: Candidate,
        excludeWorkId: String? = null,
    ): CanonicalWorkId? {
        val allowedTypes = allowedTypes(candidate.medium)
        val norm = TitleNormalizer.normalize(candidate.title) ?: return null
        val isAudio = candidate.isAudioCandidate()

        // 1. URL-carried provider IDs + ISBN against local cache.
        val ids = urlCandidates(candidate) +
            listOfNotNull(candidate.isbn?.let { normalizeIsbn(it)?.let { n -> "isbn" to n } })
        for ((provider, id) in ids) {
            val row = bridge.byExternalId(provider, id)
            if (row != null && row.mikoWorkId != excludeWorkId && row.type in allowedTypes &&
                kindCompatible(row.type, isAudio)
            ) {
                return CanonicalWorkId(
                    row.mikoWorkId,
                    candidate.medium,
                    Confidence.EXTERNAL_ID,
                    kind = row.type,
                    nsfw = row.nsfw,
                )
            }
        }

        // 2. Normalized title against local cache.
        val titleHits = bridge.byNormalizedTitle(norm)
            .filter { it.mikoWorkId != excludeWorkId }
            .filter { it.type in allowedTypes }
            .filter { kindCompatible(it.type, isAudio) }
        if (titleHits.size == 1) {
            return CanonicalWorkId(
                titleHits.single().mikoWorkId,
                candidate.medium,
                Confidence.TITLE,
                kind = titleHits.single().type,
                nsfw = titleHits.single().nsfw,
            )
        }

        // 3. Worker resolve — tries providers then title server-side.
        val resolved = resolveRemote(candidate, norm, allowedTypes)
        if (resolved != null) return resolved

        // 4. Multi-hit titles: catalog dupes + alt-title bleed make
        // multi-hits common on popular works (e.g. MangaBaka carrying
        // the same series 3×). Collapse provable same-work dupes to
        // the richest row; genuinely distinct works stay ambiguous.
        if (titleHits.size > 1) {
            disambiguateTitleHits(norm, candidate.medium, allowedTypes, candidate, isAudio)
                ?.let { return it }
            return CanonicalWorkId(
                titleHits.first().mikoWorkId,
                candidate.medium,
                Confidence.TITLE,
                kind = titleHits.first().type,
                nsfw = titleHits.first().nsfw,
                ambiguous = true,
            )
        }

        // 5. Local mint.
        return CanonicalWorkId(
            WorkIdMint.mint(
                candidate.title,
                mediumToType(
                    candidate.medium,
                    candidate.isBook,
                    candidate.kindHint,
                    isAudiobook = isAudio,
                ),
                candidate.author,
            ),
            candidate.medium,
            Confidence.LOCAL_MINT,
        )
    }

    /**
     * 22-06 (B): an audio entry is audio when the flag is set OR its
     *  persisted url carries an audio scheme — the url check covers any
     *  call site that hasn't threaded the flag yet.
     */
    private fun Candidate.isAudioCandidate(): Boolean =
        medium == MEDIUM_BOOKS &&
            (isAudiobook || url?.let(AudioChapterSchemes::isAudioNovelUrl) == true)

    /**
     * Kind parity under decision B: audio and text works sharing a title
     * are distinct works — a resolve must never land on the other side's
     * kind (a same-titled 'novel' row can't satisfy an audiobook
     * candidate and vice versa).
     */
    private fun kindCompatible(type: String, isAudio: Boolean): Boolean =
        (type == KIND_AUDIOBOOK) == isAudio

    /**
     * 22-06 (B): upsert the audio work row (LOCAL_MINT resolutions cache
     *  nothing on their own) and merge the url-derived provider binding
     *  onto the resolved work. `upsertExternalId` keys on
     *  (provider, external_id), so repeat resolves stay single-row.
     */
    private suspend fun stampAudioWork(candidate: Candidate, workId: String) {
        val providerIds = listOfNotNull(candidate.url)
            .mapNotNull { audioProviderId(it) }
            .toMap()
        val existing = bridge.byWorkId(workId)
        if (existing == null) {
            bridge.upsert(
                WorkRow(
                    mikoWorkId = workId,
                    type = KIND_AUDIOBOOK,
                    title = candidate.title,
                    author = candidate.author,
                    resolutionSource = "audio_mint",
                    externalIds = providerIds,
                ),
            )
            return
        }
        if (existing.type != KIND_AUDIOBOOK) {
            logcat {
                "audio stamp skip '${candidate.title}': $workId is kind=${existing.type}"
            }
            return
        }
        val missing = providerIds.filter { existing.externalIds[it.key] != it.value }
        if (missing.isNotEmpty()) {
            bridge.upsert(existing.copy(externalIds = existing.externalIds + missing))
        }
    }

    /** Audio-scheme novel url → (provider, external_id); the ABS arm
     *  needs the configured source for its server fingerprint. */
    private fun audioProviderId(url: String): Pair<String, String>? =
        AudioChapterSchemes.providerIdForNovelUrl(
            url,
            novelSourceManager.get(AbsNovelSource.ABS_SOURCE_ID) as? AbsNovelSource,
        )

    /**
     * [resolve] steps 1-2 only — local `works_cache` hits (URL-carried
     * provider ids + unambiguous normalized title), never the Worker and
     * never a deterministic mint. A canonical `miko:` row can only exist
     * for a work already materialized into the cache, so a null here
     * means there is nothing canonical to redirect to — used where the
     * canonical id is needed without blocking on network (home heroes).
     */
    suspend fun resolveLocal(candidate: Candidate): CanonicalWorkId? {
        val allowedTypes = allowedTypes(candidate.medium)
        val norm = TitleNormalizer.normalize(candidate.title) ?: return null
        val isAudio = candidate.isAudioCandidate()

        val ids = urlCandidates(candidate) +
            listOfNotNull(candidate.isbn?.let { normalizeIsbn(it)?.let { n -> "isbn" to n } })
        for ((provider, id) in ids) {
            val row = bridge.byExternalId(provider, id)
            if (row != null && row.type in allowedTypes && kindCompatible(row.type, isAudio)) {
                return CanonicalWorkId(
                    row.mikoWorkId,
                    candidate.medium,
                    Confidence.EXTERNAL_ID,
                    kind = row.type,
                    nsfw = row.nsfw,
                )
            }
        }

        val titleHits = bridge.byNormalizedTitle(norm)
            .filter { it.type in allowedTypes }
            .filter { kindCompatible(it.type, isAudio) }
        if (titleHits.size == 1) {
            return CanonicalWorkId(
                titleHits.single().mikoWorkId,
                candidate.medium,
                Confidence.TITLE,
                kind = titleHits.single().type,
                nsfw = titleHits.single().nsfw,
            )
        }
        if (titleHits.size > 1) {
            // Same local dupe-collapse as resolve() — no worker call,
            // still stays ambiguous on genuinely distinct works.
            return disambiguateTitleHits(norm, candidate.medium, allowedTypes, candidate, isAudio)
        }
        return null
    }

    /**
     * Multi-hit normalized-title disambiguation. Hits arriving only
     * through non-primary titles are alt-title bleed — they drop out
     * (a work's own alt titles can't claim identity against another
     * work's primary). Remaining primary-title hits sharing
     * title+year+author (absent either side) are catalog dupes of one
     * work — collapse to the richest row (provider ids, then chapter
     * count). Provably-distinct works get one deterministic sieve —
     * the candidate's chapter count against each hit's catalog
     * totalChapters (one-shot vs series, e.g. BLAME! 1995 vs 1997);
     * only hits that POSITIVELY assert a compatible total survive, a
     * unique survivor resolves, else null keeps the ambiguous bail.
     */
    private suspend fun disambiguateTitleHits(
        norm: String,
        medium: String,
        allowedTypes: Set<String>,
        candidate: Candidate,
        isAudio: Boolean = candidate.isAudioCandidate(),
    ): CanonicalWorkId? {
        val hits = bridge.byNormalizedTitleDetailed(norm)
            .filter { it.row.type in allowedTypes }
            .filter { kindCompatible(it.row.type, isAudio) }
        val primary = hits.filter { it.isPrimary }.map { it.row }
        if (primary.isEmpty()) {
            logcat { "disambiguate '$norm': ${hits.size} hits, none primary — stays ambiguous" }
            return null
        }
        if (primary.size == 1) {
            val row = primary.single()
            logcat { "disambiguate '$norm': single primary hit ${row.mikoWorkId}" }
            return CanonicalWorkId(
                row.mikoWorkId, medium, Confidence.TITLE,
                kind = row.type, nsfw = row.nsfw,
            )
        }
        val best = primary.maxWithOrNull(
            compareBy<MangaNovelIdBridge.WorkRow>(
                { it.externalIds.size },
                { it.totalChapters?.toLongOrNull() ?: 0L },
            ),
        ) ?: return null
        val dupes = primary.all { r ->
            (r.year == null || best.year == null || r.year == best.year) &&
                sameAuthor(r.author, best.author)
        }
        if (!dupes) {
            // Chapter-count sieve: distinct-signature hits (the classic
            // one-shot-vs-series collision — BLAME! 1995@1ch vs
            // 1997@66ch) are reduced to hits whose catalog total is
            // compatible with the candidate's own chapter list. If the
            // survivors are then mutually dupes (BLAME!'s 65/66-chapter
            // catalog dupe), collapse to the richest row; otherwise the
            // work is genuinely ambiguous.
            val countHits = candidate.chapterCount
                ?.takeIf { it > 0 }
                ?.let { c -> primary.filter { r -> chaptersCompatible(r.totalChapters, c) } }
                .orEmpty()
            if (countHits.isNotEmpty()) {
                val countBest = countHits.maxWithOrNull(
                    compareBy<MangaNovelIdBridge.WorkRow>(
                        { it.externalIds.size },
                        { it.totalChapters?.toLongOrNull() ?: 0L },
                    ),
                )
                val survivorsDupes = countHits.all { r ->
                    (r.year == null || countBest?.year == null || r.year == countBest.year) &&
                        sameAuthor(r.author, countBest?.author)
                }
                if (countBest != null && survivorsDupes) {
                    logcat {
                        "disambiguate '$norm': chapter-count sieve " +
                            "${countHits.size}→${countBest.mikoWorkId} " +
                            "(total=${countBest.totalChapters}, " +
                            "candidate=${candidate.chapterCount})"
                    }
                    return CanonicalWorkId(
                        countBest.mikoWorkId, medium, Confidence.TITLE,
                        kind = countBest.type, nsfw = countBest.nsfw,
                    )
                }
            }
            logcat {
                "disambiguate '$norm': ${primary.size} primary hits, distinct signatures " +
                    "[${primary.joinToString { "${it.mikoWorkId}(${it.year}/${it.author})" }}] — stays ambiguous"
            }
            return null
        }
        logcat {
            "disambiguate '$norm': ${primary.size} primary hits are dupes, " +
                "picked ${best.mikoWorkId} (ids=${best.externalIds.size})"
        }
        return CanonicalWorkId(
            best.mikoWorkId, medium, Confidence.TITLE,
            kind = best.type, nsfw = best.nsfw,
        )
    }

    /** Catalog total vs the entry's fetched count — fuzzy because
     *  catalogs count extras/collected editions differently. Null or
     *  unparseable totals assert nothing (fail closed). */
    private fun chaptersCompatible(catalogTotal: String?, candidateCount: Int): Boolean {
        val t = catalogTotal?.toDoubleOrNull() ?: return false
        val diff = kotlin.math.abs(t - candidateCount)
        return diff <= maxOf(3.0, 0.15 * maxOf(t, candidateCount.toDouble()))
    }

    /** Null-tolerant, order-insensitive author compare — catalogs flip
     *  surname order between mirrors ("Inariya Fusanosuke" vs
     *  "Fusanosuke Inariya") while a missing author on either side
     *  can't disprove identity. */
    private fun sameAuthor(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return true
        if (a.equals(b, ignoreCase = true)) return true
        val tokens = { s: String ->
            s.lowercase().split(Regex("[\\s,.;]+")).filter { it.isNotEmpty() }.toSet()
        }
        return tokens(a) == tokens(b)
    }

    private suspend fun resolveRemote(
        candidate: Candidate,
        norm: String,
        allowedTypes: Set<String>,
    ): CanonicalWorkId? {
        // One attempt per URL candidate, then a plain title lookup.
        // kind_hint rides every request — advisory only (Worker is the
        // kind authority): 'book' gates OpenLibrary, 'movie'/'series'
        // order the Cinemeta meta fetch, 'audiobook' (22-06 B) routes to
        // the worker's audio mint and never reaches MangaBaka/OL.
        val isAudio = candidate.isAudioCandidate()
        val kind = if (isAudio) {
            KIND_AUDIOBOOK
        } else {
            candidate.kindHint ?: (if (candidate.isBook) "book" else null)
        }
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
        suspend fun attempt(request: WorkIdentityApi.ResolveRequest): CanonicalWorkId? {
            val dto = api.resolve(request) ?: return null
            // `kind` is the post-15-01 taxonomy name; `type` the legacy
            // mirror. Honest-null rows (upstream-degraded mints) resolve
            // only when the Worker vouches the carrier via `medium`.
            val dtoKind = dto.kind ?: dto.type
            val kindOk = dtoKind != null && dtoKind in allowedTypes &&
                kindCompatible(dtoKind, isAudio)
            val carrierOk = dtoKind == null && dto.medium != null && dto.medium == candidate.medium
            if (!kindOk && !carrierOk) return null
            // kind-null rows skip the local upsert — works_cache.type is
            // NOT NULL and caching an unconfirmed kind would lie.
            if (dtoKind != null) bridge.upsert(dto.toWorkRow(dtoKind))
            return CanonicalWorkId(
                dto.miko_work_id,
                candidate.medium,
                Confidence.WORKER,
                kind = dtoKind,
                nsfw = dto.nsfw,
            )
        }
        for (request in attempts) {
            attempt(request)?.let { return it }
        }

        // Regional-spelling fallback on a total miss: a donor's title
        // may not exist upstream under its own spelling ("Defence" vs
        // the catalog's "Defense"). Variant lookups go through the same
        // kind/medium gates — a wrong-kind hit can't sneak in.
        for (variant in spellingVariants(candidate.title)) {
            attempt(
                WorkIdentityApi.ResolveRequest(
                    title = variant,
                    medium = candidate.medium,
                    author = candidate.author,
                    kindHint = kind,
                    isbn = isbn,
                ),
            )?.let { return it }
        }
        return null
    }

    /**
     * British↔American word swaps applied to the title — bounded, and
     * only attempted after the literal title misses upstream.
     */
    private fun spellingVariants(title: String): List<String> {
        val lower = title.lowercase()
        return SPELLING_PAIRS.flatMap { (a, b) ->
            listOfNotNull(
                variantFor(title, lower, a, b),
                variantFor(title, lower, b, a),
            )
        }.distinct()
    }

    private fun variantFor(title: String, lower: String, from: String, to: String): String? {
        if (!lower.contains(from)) return null
        return Regex("""\b${Regex.escape(from)}\b""", RegexOption.IGNORE_CASE)
            .replace(title, to)
            .takeIf { it != title }
    }

    private fun WorkIdentityApi.WorkDto.toWorkRow(kind: String) = WorkRow(
        mikoWorkId = miko_work_id,
        type = kind, title = title,
        romanizedTitle = romanized_title, nativeTitle = native_title,
        year = year?.toLong(), totalChapters = total_chapters, author = author,
        nsfw = nsfw,
        providerLinks = provider_links,
        coverUrl = cover_url,
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

    /** (provider, external_id) pairs mined from known URL shapes —
     *  web providers via [extractExternalIds] plus the work-level audio
     *  pairs (`abs:`/`librivox:`/`ia:`) from scheme urls (22-06). */
    private fun urlCandidates(candidate: Candidate): List<Pair<String, String>> {
        val urls = listOfNotNull(candidate.url) + candidate.webviewUrls
        return urls.flatMap { url ->
            extractExternalIds(url) + listOfNotNull(audioProviderId(url))
        }
    }

    companion object {
        const val MEDIUM_MANGA = "manga"
        const val MEDIUM_BOOKS = "books"
        const val MEDIUM_SHOWS = "shows"

        /** Work kind for remote-audio editions (22-06, decision B) —
         *  participates in `mk:m:` mint hashes, so audio and text
         *  editions of the same title mint distinct works. */
        const val KIND_AUDIOBOOK = "audiobook"

        /** Regional-spelling pairs tried as title variants when the
         *  literal title misses upstream. Deliberately small — every
         *  miss path pays one Worker call per generated variant. */
        private val SPELLING_PAIRS = listOf(
            "defence" to "defense",
            "offence" to "offense",
            "colour" to "color",
            "honour" to "honor",
            "favour" to "favor",
            "centre" to "center",
            "theatre" to "theater",
            "grey" to "gray",
            "neighbour" to "neighbor",
            "traveller" to "traveler",
            "labour" to "labor",
            "licence" to "license",
        )

        /** Kinds accepted for each carrier — mirrors the worker's
         *  MEDIUM_TYPES. 'oel' is a manga kind (original-English-language
         *  comics), never books. 'audiobook' is a real books kind under
         *  22-06 decision B — stamped by audio mints ([KIND_AUDIOBOOK]
         *  in the mk:m: hash) and by the worker's `mk:audio:` mints. */
        internal fun allowedTypes(medium: String) =
            when (Carrier.fromMedium(medium)) {
                Carrier.SHOWS -> setOf("anime", "movie", "series")
                Carrier.BOOKS -> setOf("book", "novel", "audiobook")
                else -> setOf("manga", "manhwa", "manhua", "comic", "webtoon", "oel", "other")
            }

        /** Kind stamped into local `mk:m:` fallback mints. Shows mints
         *  carry a shows kind (kindHint ?: "series") and books mints
         *  keep 'novel' so mk:m: hashes stay carrier-disjoint AND stable
         *  across the rename (mk:m: hashes `title|type|…`). 22-06 (B):
         *  [isAudiobook] emits 'audiobook' — audio editions mint their
         *  own works, so the flag must only be true for audio candidates
         *  (never on a text resolve or the fork splits the work). */
        internal fun mediumToType(
            medium: String,
            isBook: Boolean,
            kindHint: String?,
            isAudiobook: Boolean = false,
        ): String {
            val carrier = Carrier.fromMedium(medium)
            return when {
                carrier == Carrier.SHOWS -> kindHint ?: "series"
                isAudiobook && carrier == Carrier.BOOKS -> KIND_AUDIOBOOK
                isBook -> "book"
                carrier == Carrier.BOOKS -> "novel"
                else -> "manga"
            }
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

        // openlibrary.org/works/OL…W — published-book identity; the
        // worker stores them under provider 'openlibrary' (mk:ol: mints).
        private val OL_WORK_ID = Regex("""openlibrary\.org/works/(OL\d+W)""", RegexOption.IGNORE_CASE)

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
                OL_WORK_ID.find(url)?.let { add("openlibrary" to it.groupValues[1].uppercase()) }
            }
        }
    }
}
