package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.model.Carrier as UiCarrier
import eu.kanade.tachiyomi.data.comments.ChapterFingerprint
import eu.kanade.tachiyomi.data.comments.ChapterThreadResolver
import eu.kanade.tachiyomi.data.comments.CommentsApi
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import logcat.logcat
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Reader fan-out (Phase 13-04) — the `StreamResolver` equivalent for
 * text media: canon chapter → best installed carrier → `getPageList`,
 * walking candidates on failure.
 *
 * Candidates come in two tiers:
 *
 *  1. Canon row `refs` ∩ installed golden sources — feeds that
 *     contributed to canonization already carry per-chapter urls.
 *  2. Bound-source expansion (16-04): a carrier binding without refs
 *     (`GET /v1/works/:id/bindings` ∩ installed+enabled) — the source's
 *     own chapter list is fetched once via `providerRef`, its entry is
 *     identity-verified through [SeriesMatcher], the fingerprint is
 *     reported to `/v1/chapters/align`, and chapter names bind to canon
 *     rows via [CanonicalChapterMap.bind]. A `misaligned` verdict or a
 *     rejected identity disqualifies the source.
 *
 * Ordering within either tier: per-work preferred carrier →
 * last-working source (stickiness) → tier (S→A→B) → remaining order.
 * On success the carrier is remembered so a dead/removed source
 * doesn't get re-probed every chapter.
 */
class CanonicalChapterRouter(
    private val chapterMap: CanonicalChapterMap = Injekt.get(),
    private val goldenSources: GoldenSources = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
    private val workCarriers: WorkCarriers = Injekt.get(),
    private val bridge: MangaNovelIdBridge = Injekt.get(),
    private val matcher: SeriesMatcher = Injekt.get(),
    private val commentsApi: CommentsApi = Injekt.get(),
    private val handler: MangaDatabaseHandler = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) {

    data class Candidate(
        val sourceId: Long,
        val pkgName: String,
        val tier: String,
        val chapterUrl: String,
    )

    data class Resolved(
        val source: HttpSource,
        val chapter: SChapter,
        val pages: List<Page>,
        val pkgName: String,
    )

    /** workId → last-working carrier pkgName. Process-scoped. */
    private val lastWorking = ConcurrentHashMap<String, String>()

    /** `workId|pkgName` → seq→carrier-chapter-url expansion.
     *  Process-scoped. */
    private val expansions = ConcurrentHashMap<String, Map<Int, String>>()

    /** Keys already attempted — includes failures, so a disqualified
     *  carrier isn't re-probed every chapter (ConcurrentHashMap can't
     *  hold null values). */
    private val expansionTried = ConcurrentHashMap.newKeySet<String>()

    /** Fingerprints currently being reported — dedupe concurrent posts. */
    private val alignPending = ConcurrentHashMap.newKeySet<String>()

    /** Ordered candidates for a canonical row — refs ∩ installed, then
     *  bound-source expansion when no ref carrier is installed. */
    suspend fun candidates(workId: String, seq: Int): List<Candidate> =
        candidatesFor(workId, seq, WorkIdResolver.MEDIUM_MANGA) { sourceManager.get(it) != null }

    /** Ordered candidates for a canonical book/novel row. */
    suspend fun novelCandidates(workId: String, seq: Int): List<Candidate> =
        candidatesFor(workId, seq, WorkIdResolver.MEDIUM_BOOKS) { novelSourceManager.get(it) != null }

    private suspend fun candidatesFor(
        workId: String,
        seq: Int,
        medium: String,
        isInstalled: (Long) -> Boolean,
    ): List<Candidate> {
        val ranked = directCandidatesFor(workId, seq, medium, isInstalled)
        if (ranked.isNotEmpty()) return ranked
        return expandedCandidates(workId, seq, medium, isInstalled)
    }

    /** Refs ∩ installed, ordered — the cheap half of candidate
     *  resolution (no bound-source expansion, no source I/O).
     *  Joined against the carrier matrix, NOT the capped golden set:
     *  `goldenSources.forWork` truncates broad candidates to
     *  BROAD_FALLBACK_MAX and SFW-sorts flagged extensions last, so a
     *  user-picked carrier that minted chapters could be invisible to
     *  both labels and routing here. */
    private suspend fun directCandidatesFor(
        workId: String,
        seq: Int,
        medium: String,
        isInstalled: (Long) -> Boolean,
    ): List<Candidate> {
        val row = chapterMap.forWork(workId).firstOrNull { it.seq == seq } ?: return emptyList()
        val byPkg = runCatching { workCarriers.forWork(workId, medium) }
            .getOrNull()?.readable?.associateBy { it.pkgName } ?: emptyMap()
        val direct = row.refs.mapNotNull { (pkg, url) ->
            val c = byPkg[pkg] ?: return@mapNotNull null
            val sourceId = c.sourceIds.firstOrNull(isInstalled)
                ?: return@mapNotNull null
            Candidate(sourceId, pkg, c.tier, url)
        }
        return direct.sortedWith(candidateOrder(workId, seq))
    }

    /** Carrier a chapter row would route through right now — top direct
     *  candidate's pkg, null when only expansion could serve (label as
     *  "Automatic"; the router resolves at read). Never does source I/O. */
    suspend fun peekCarrier(workId: String, seq: Int): String? =
        directCandidatesFor(workId, seq, WorkIdResolver.MEDIUM_MANGA) {
            sourceManager.get(it) != null
        }.firstOrNull()?.pkgName

    /** per-chapter override → preferred carrier → sticky → tier. */
    private fun candidateOrder(workId: String, seq: Int): Comparator<Candidate> {
        val chapterPref = sourcePreferences.preferredChapterCarrier(workId, seq)
        val preferred = sourcePreferences.preferredCarrierFor(workId)
        val sticky = lastWorking[workId]
        return compareBy(
            { c: Candidate ->
                when (c.pkgName) {
                    chapterPref -> 0
                    preferred -> 1
                    sticky -> 2
                    else -> 3
                }
            },
            { tierRank(it.tier) },
        )
    }

    // ---------- bound-source expansion (16-04) ----------

    private suspend fun expandedCandidates(
        workId: String,
        seq: Int,
        medium: String,
        isInstalled: (Long) -> Boolean,
    ): List<Candidate> {
        val matrix = runCatching { workCarriers.forWork(workId, medium) }.getOrNull()
            ?: return emptyList()
        if (matrix.readable.isEmpty()) return emptyList()
        val canon = chapterMap.forWork(workId)
        if (canon.isEmpty()) return emptyList()

        return matrix.readable.mapNotNull { carrier ->
            val map = expansionFor(workId, carrier, canon, medium) ?: return@mapNotNull null
            val url = map[seq] ?: return@mapNotNull null
            val sourceId = carrier.sourceIds.firstOrNull(isInstalled) ?: return@mapNotNull null
            Candidate(sourceId, carrier.pkgName, carrier.tier, url)
        }.sortedWith(candidateOrder(workId, seq))
    }

    private suspend fun expansionFor(
        workId: String,
        carrier: WorkCarriers.Carrier,
        canon: List<CanonicalChapterMap.CanonChapter>,
        medium: String,
    ): Map<Int, String>? {
        val key = "$workId|${carrier.pkgName}"
        if (expansionTried.add(key)) {
            val map = runCatching { buildExpansion(workId, carrier, canon, medium) }
                .onFailure { logcat { "canonRouter expansion $key: ${it.message}" } }
                .getOrNull()
            if (map != null) expansions[key] = map
        }
        return expansions[key]
    }

    /**
     * Fetch the bound source's own chapter list, identity-gate it, and
     * bind chapter names onto canon seqs. Null when the carrier can't be
     * trusted for this work.
     */
    private suspend fun buildExpansion(
        workId: String,
        carrier: WorkCarriers.Carrier,
        canon: List<CanonicalChapterMap.CanonChapter>,
        medium: String,
    ): Map<Int, String>? {
        val isBooks = medium == WorkIdResolver.MEDIUM_BOOKS
        val work = bridge.byWorkId(workId) ?: return null
        val query = SeriesMatcher.Query.of(work)

        if (isBooks) {
            val source = carrier.sourceIds
                .firstNotNullOfOrNull { novelSourceManager.get(it) } as? NovelCatalogueSource
                ?: return null
            val sn = SNovel.create().apply { url = carrier.providerRef }
            val details = runCatching { source.getNovelDetails(sn) }.getOrNull() ?: return null
            if (!identityOk(query, details.title, details.author, details.artist)) return null
            val chapters = runCatching { source.getChapterList(sn) }.getOrNull() ?: return null
            if (chapters.isEmpty()) return null
            if (isMisaligned(workId, carrier.pkgName, work.title ?: "", medium,
                    chapters.map { it.name to it.chapter_number })) return null
            val map = mutableMapOf<Int, String>()
            for (ch in chapters) {
                val row = chapterMap.bind(ch.name, canon) ?: continue
                map.putIfAbsent(row.seq, ch.url)
            }
            return map.takeIf { it.isNotEmpty() }
        }

        val source = carrier.sourceIds
            .firstNotNullOfOrNull { sourceManager.get(it) } as? HttpSource
            ?: return null
        val sm = SManga.create().apply { url = carrier.providerRef }
        val details = runCatching { source.getMangaDetails(sm) }.getOrNull() ?: return null
        if (!identityOk(query, details.title, details.author, details.artist)) return null
        val chapters = runCatching { source.getChapterList(sm) }.getOrNull() ?: return null
        if (chapters.isEmpty()) return null
        if (isMisaligned(workId, carrier.pkgName, work.title ?: "", medium,
                chapters.map { it.name to it.chapter_number })) return null
        val map = mutableMapOf<Int, String>()
        for (ch in chapters) {
            val row = chapterMap.bind(ch.name, canon) ?: continue
            map.putIfAbsent(row.seq, ch.url)
        }
        return map.takeIf { it.isNotEmpty() }
    }

    /** Same identity gate the canonize collector applies — a bound ref
     *  resolving to the wrong series must never route. */
    private fun identityOk(
        query: SeriesMatcher.Query,
        title: String?,
        author: String?,
        artist: String?,
    ): Boolean {
        if (title.isNullOrBlank()) return false
        val v = matcher.verify(
            query,
            SeriesMatcher.Candidate(title = title, author = author, artist = artist),
        )
        return v.decision != SeriesMatcher.Decision.REJECT
    }

    /**
     * Report the source's chapter fingerprint to `/v1/chapters/align`
     * and answer whether the verdict (cached or fresh) is `misaligned`.
     * Unknown/offline → not misaligned (optimistic, same rule as
     * [ChapterThreadResolver]); the per-chapter name bind still gates
     * wrong-work lists locally.
     */
    private suspend fun isMisaligned(
        workId: String,
        sourceKey: String,
        title: String,
        medium: String,
        chapters: List<Pair<String, Float>>,
    ): Boolean {
        val fp = ChapterFingerprint.of(chapters)
        if (fp.count == 0) return true
        val fpHash = fingerprintHash(fp)
        val cached = handler.awaitOneOrNull {
            works_cacheQueries.selectAlignment(workId, sourceKey, fpHash)
        }
        if (cached != null) return cached.status == "misaligned"

        val key = "$workId|$sourceKey|$fpHash"
        if (!alignPending.add(key)) return false
        val result = commentsApi.alignChapters(
            workId = workId,
            sourceKey = sourceKey,
            carrier = if (medium == WorkIdResolver.MEDIUM_BOOKS) UiCarrier.BOOKS else UiCarrier.MANGA,
            title = title,
            fingerprint = fp,
        )
        alignPending.remove(key)
        if (result is CommentsApi.Result.Ok) {
            val v = result.value
            handler.await(true) {
                works_cacheQueries.upsertAlignment(
                    workId, sourceKey, fpHash,
                    v.status, v.score, v.reference, System.currentTimeMillis(),
                )
            }
            return v.status == "misaligned"
        }
        return false
    }

    // Must match worker/src/align.ts: sha256("num1,num2|namesHash").
    private fun fingerprintHash(fp: ChapterFingerprint): String =
        eu.kanade.tachiyomi.data.comments.DeviceIdentityProvider.sha256Hex(
            "${fp.numbers.joinToString(",") {
                eu.kanade.tachiyomi.data.comments.ChapterBinder.formatNumber(it)
            }}|${fp.namesHash}",
        )

    /**
     * Walk candidates, fetching `getPageList` on each until one returns
     * pages. Returns the winning source + routed chapter + page list,
     * or null when no installed carrier serves the chapter.
     */
    suspend fun resolve(workId: String, seq: Int, label: String): Resolved? {
        for (c in candidates(workId, seq)) {
            val src = sourceManager.get(c.sourceId) as? HttpSource ?: continue
            val sch = SChapter.create().apply {
                url = c.chapterUrl
                name = label
            }
            val pages = runCatching { src.getPageList(sch) }
                .onFailure { logcat { "canonRouter $workId@${c.pkgName}: ${it.message}" } }
                .getOrNull()
                ?: continue
            if (pages.isEmpty()) continue
            lastWorking[workId] = c.pkgName
            return Resolved(src, sch, pages, c.pkgName)
        }
        return null
    }

    /** Read entry-point: resolve a materialized canon chapter row. */
    suspend fun resolveForRead(manga: Manga, chapterUrl: String, label: String): Resolved? {
        val workId = CanonicalCatalog.workIdFromMangaUrl(manga.url) ?: return null
        val seq = seqFromCanonUrl(workId, chapterUrl) ?: return null
        return resolve(workId, seq, label)
    }

    /**
     * Download entry-point: pick the carrier a canon chapter should be
     * downloaded through WITHOUT fetching its page list (the downloader
     * fetches — resolution only needs routing). Honors the same ordering
     * as reads: per-chapter override → preferred carrier → sticky → tier.
     * [forcedPkg] (per-chapter download pick) restricts the walk to that
     * carrier only — a manual choice, not a ranking hint.
     */
    suspend fun downloadTarget(
        workId: String,
        seq: Int,
        forcedPkg: String? = null,
    ): Pair<HttpSource, Candidate>? {
        // A pinned carrier is EXCLUSIVE for downloads — the user asked for
        // that provider, so unlike reads there's no silent fall-through.
        // Pin order mirrors candidateOrder: manual pick → chapter override
        // → work pick. Null pin = Automatic → ordered candidates.
        val pin = forcedPkg
            ?: sourcePreferences.preferredChapterCarrier(workId, seq)
            ?: sourcePreferences.preferredCarrierFor(workId)
        val cands = candidates(workId, seq)
            .let { list -> if (pin != null) list.filter { it.pkgName == pin } else list }
        for (c in cands) {
            val src = sourceManager.get(c.sourceId) as? HttpSource ?: continue
            return src to c
        }
        return null
    }

    /** Novel counterpart of [downloadTarget] — carrier source + chapter url. */
    suspend fun novelDownloadTarget(
        workId: String,
        seq: Int,
        forcedPkg: String? = null,
    ): Pair<eu.kanade.tachiyomi.novelsource.online.NovelHttpSource, Candidate>? {
        // Pinned carriers are exclusive for downloads — see downloadTarget.
        val pin = forcedPkg
            ?: sourcePreferences.preferredChapterCarrier(workId, seq)
            ?: sourcePreferences.preferredCarrierFor(workId)
        val cands = novelCandidates(workId, seq)
            .let { list -> if (pin != null) list.filter { it.pkgName == pin } else list }
        for (c in cands) {
            val src = novelSourceManager.get(c.sourceId)
                as? eu.kanade.tachiyomi.novelsource.online.NovelHttpSource ?: continue
            return src to c
        }
        return null
    }

    /**
     * Novel counterpart of [resolveForRead]: canon chapter → best
     * installed novel carrier → `getChapterText`, walking candidates
     * on failure. Returns the chapter text, or null when no installed
     * carrier serves it. [onCarrier] receives the winning source's
     * display name so callers can surface carrier provenance.
     */
    suspend fun resolveNovelText(
        novel: Novel,
        chapterUrl: String,
        label: String,
        onCarrier: ((sourceName: String) -> Unit)? = null,
    ): String? {
        val workId = CanonicalCatalog.workIdFromNovelUrl(novel.url) ?: return null
        val seq = seqFromCanonUrl(workId, chapterUrl) ?: return null
        for (c in novelCandidates(workId, seq)) {
            val src = novelSourceManager.get(c.sourceId) as? NovelCatalogueSource ?: continue
            val sch = SNovelChapter.create().apply {
                url = c.chapterUrl
                name = label
            }
            val text = runCatching { src.getChapterText(sch) }
                .onFailure { logcat { "canonRouter(novel) $workId@${c.pkgName}: ${it.message}" } }
                .getOrNull()
                ?: continue
            if (text.isBlank()) continue
            lastWorking[workId] = c.pkgName
            onCarrier?.invoke(src.name)
            return text
        }
        return null
    }

    companion object {
        fun isCanonUrl(url: String) = url.startsWith("canon:")

        /** `canon:{workId}:{seq}` → seq. */
        fun seqFromCanonUrl(workId: String, url: String): Int? =
            url.removePrefix("canon:$workId:").toIntOrNull()

        private fun tierRank(tier: String) = when (tier.uppercase()) {
            "S" -> 0
            "A" -> 1
            "B" -> 2
            "C" -> 3
            else -> 4
        }
    }
}
