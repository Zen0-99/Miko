package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.logcat
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Canonical feed collector (Phase 13-01): acquires verified chapter
 * lists for a canonical work by driving the *installed* manga
 * extensions (which handle Cloudflare/WebView internally — the probe
 * showed ~1/3 of golden sources are unreachable by plain HTTP).
 *
 * Pipeline per golden source:
 *   provider-linked url → getMangaDetails → SeriesMatcher.verify
 *   else title search → top candidates → getMangaDetails → verifyAll
 *   → fetchChapterList on ACCEPT/TENTATIVE results only
 *
 * A source's feed contributes to canonical construction only when the
 * matcher clears it — the v2 probe's slug-similar wrong-series feeds
 * (solo-max-level-newbie et al.) are rejected at the gate.
 */
class CanonicalFeedCollector(
    private val goldenSources: GoldenSources = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val matcher: SeriesMatcher = Injekt.get(),
    private val mikoDbManager: MikoDbManager = Injekt.get(),
    private val sourcePreferences: eu.kanade.domain.source.service.SourcePreferences = Injekt.get(),
    private val identityApi: WorkIdentityApi = Injekt.get(),
) {

    /** Entry metadata carried by a source's series details — lets the
     * caller backfill sparse canonical stubs from whichever extension
     * actually won the identity gate (13-02). */
    data class FeedDetails(
        val description: String? = null,
        val author: String? = null,
        val genre: List<String> = emptyList(),
        val thumbnailUrl: String? = null,
        val status: Int? = null,
    ) {
        val isEmpty: Boolean
            get() = description.isNullOrBlank() && author.isNullOrBlank() &&
                genre.isEmpty() && thumbnailUrl.isNullOrBlank()
    }

    data class Feed(
        val sourceId: Long,
        val pkgName: String,
        val tier: String,
        val url: String,
        val seriesTitle: String,
        /** (name, url) pairs — urls populate canonical `refs` for the
         * 13-04 reader fan-out. */
        val chapters: List<WorkIdentityApi.FeedChapter>,
        val verdict: SeriesMatcher.Decision,
        val score: Double,
        val reasons: List<String>,
        val details: FeedDetails? = null,
        /** Carrier episode rows — populated only on the shows path
         *  (19-03 courier); stays empty for manga/books feeds. `abs`
         *  is canonicalized by 19-04's [EpisodeCoverage] pass when the
         *  bound artifact entry proves the carrier's numbering. */
        val episodes: List<WorkIdentityApi.FeedEpisode> = emptyList(),
    )

    data class Result(
        /** ACCEPT feeds — cleared floor + margin. */
        val feeds: List<Feed>,
        /** TENTATIVE feeds — floor but thin margin; submitted with
         * `verified=false` so the worker flags `identity_review`. */
        val tentative: List<Feed>,
        val rejected: List<Feed>,
        val failed: Map<String, String>, // pkgName → error
    ) {
        val hasTentative: Boolean get() = tentative.isNotEmpty()
    }

    /**
     * Collect verified chapter feeds for [work]. ACCEPT feeds first,
     * TENTATIVE appended after (caller should flag `identity_review`
     * when [Result.hasTentative]). Caps at [maxFeeds] accepted feeds.
     *
     * [onSourceDone] fires as each source resolves — the Find Provider
     *  sheet (17-02) streams live status from it.
     */
    suspend fun collect(
        work: MangaNovelIdBridge.WorkRow,
        medium: String = "manga",
        maxFeeds: Int = 5,
        /** Candidate override — the Find Provider sheet (17-02) passes
         *  the uncapped `allForWorkRow` set; null = capped pipeline set. */
        sources: List<GoldenSources.GoldenSource>? = null,
        /** Search-result window evaluated per source — the interactive
         *  path passes a wider window since extension ordering can push
         *  the real entry behind spin-offs/colored editions. */
        topCandidates: Int = TOP_CANDIDATES,
        onSourceDone: ((pkgName: String, feed: Feed?, error: String?) -> Unit)? = null,
    ): Result = withContext(Dispatchers.IO) {
        // 19-05: shows queries carry the artifact's season/part/format
        // expectations (veto signals); manga/books stay on the plain
        // work-row query — books candidates never assert season data.
        val query = when {
            medium == WorkIdResolver.MEDIUM_SHOWS -> {
                val animeRows = work.mikoWorkId
                    .takeIf { it.startsWith(CanonizeWork.IMDB_WORK_PREFIX) }
                    ?.let {
                        mikoDbManager.bridge()?.byImdb(it.removePrefix(CanonizeWork.IMDB_WORK_PREFIX))
                    }
                    .orEmpty()
                if (animeRows.isNotEmpty()) {
                    SeriesMatcher.Query.forShows(work, animeRows)
                } else {
                    SeriesMatcher.Query.of(work)
                }
            }
            else -> SeriesMatcher.Query.of(work)
        }
        val goldens = sources ?: goldenSources.forWorkRow(work, medium)
        if (goldens.isEmpty()) {
            return@withContext Result(emptyList(), emptyList(), emptyList(), emptyMap())
        }

        val feeds = mutableListOf<Feed>()
        val rejected = mutableListOf<Feed>()
        val failed = mutableMapOf<String, String>()

        coroutineScope {
            goldens.map { g ->
                async {
                    // Bound each source probe — a stalled Cloudflare/resolver
                    // path must not hang the whole collect (coroutineScope
                    // waits for every child).
                    val res = try {
                        withTimeout(SOURCE_TIMEOUT_MS) {
                            runCatching {
                                when (medium) {
                                    WorkIdResolver.MEDIUM_BOOKS ->
                                        collectNovelFrom(g, query, work.title ?: "", topCandidates)
                                    WorkIdResolver.MEDIUM_SHOWS ->
                                        collectAnimeFrom(
                                            g, query, work.title ?: "", topCandidates,
                                            work.mikoWorkId,
                                        )
                                    else ->
                                        collectFrom(g, query, work.title ?: "", topCandidates)
                                }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        failed[g.pkgName] = "timeout"
                        null
                    }
                    val feed = res?.getOrElse { e ->
                        // Cancellation must propagate — it carries the
                        // scope's identity (Coroutine "Screen"…) and
                        // surfaces as meaningless raw text on the row.
                        if (e is CancellationException) throw e
                        failed[g.pkgName] = e.message ?: e.javaClass.simpleName
                        null
                    }
                    logcat {
                        "canonicalFeed ${g.pkgName} → ${feed?.seriesTitle ?: "-"} " +
                            "${feed?.verdict ?: "-"} ${feed?.score ?: ""} " +
                            "err=${failed[g.pkgName] ?: "-"}"
                    }
                    onSourceDone?.invoke(g.pkgName, feed, failed[g.pkgName])
                    feed
                }
            }.forEach { deferred ->
                val f = deferred.await() ?: return@forEach
                when (f.verdict) {
                    SeriesMatcher.Decision.ACCEPT -> feeds += f
                    SeriesMatcher.Decision.TENTATIVE -> feeds += f
                    SeriesMatcher.Decision.REJECT -> rejected += f
                }
            }
        }

        val accepted = feeds.filter { it.verdict == SeriesMatcher.Decision.ACCEPT }.take(maxFeeds)
        val tentative = feeds.filter { it.verdict == SeriesMatcher.Decision.TENTATIVE }
        Result(
            feeds = accepted,
            tentative = tentative.take(2),
            rejected = rejected,
            failed = failed,
        )
    }

    private suspend fun collectFrom(
        g: GoldenSources.GoldenSource,
        query: SeriesMatcher.Query,
        searchTitle: String,
        topCandidates: Int = TOP_CANDIDATES,
    ): Feed? {
        // Resolve the first live installed source id for this package.
        val source = g.sourceIds.firstNotNullOfOrNull { id -> sourceManager.get(id) }
            as? CatalogueSource ?: error("source unavailable")

        // Provider-linked url tried first — but a carrier URL is usually
        // not the extension's internal url shape, so a failed or rejected
        // direct lookup falls back to title search rather than dying.
        if (!g.url.isNullOrBlank()) {
            evalMangaFeed(source, g, query, listOf(SManga.create().apply { url = g.url!! }))
                ?.takeIf { it.verdict != SeriesMatcher.Decision.REJECT }
                ?.let { return it }
        }
        // Each source's own filter list — extensions read expected filter
        // slots by position/type and throw on an empty list (the raw
        // NoSuchElementException leak we used to surface).
        val page = runCatching { source.getSearchManga(1, searchTitle, source.getFilterList()) }
            .getOrElse { e -> throw IllegalStateException("search failed: ${e.message}") }
        logcat { "canonicalFeed ${g.pkgName} search '${searchTitle}': ${page.mangas.size} hits" }
        return evalMangaFeed(source, g, query, page.mangas.take(topCandidates))
    }

    /** Details → pairwise verify → chapter list for [candidates]. */
    private suspend fun evalMangaFeed(
        source: CatalogueSource,
        g: GoldenSources.GoldenSource,
        query: SeriesMatcher.Query,
        candidates: List<SManga>,
    ): Feed? {
        if (candidates.isEmpty()) return null
        val detailed = candidates.mapNotNull { sm ->
            runCatching { source.getMangaDetails(sm) to sm }
                .getOrNull()
        }
        // Search hit but every details fetch failed — that's a source
        // failure, not "no match"; surface it instead of a silent null.
        if (detailed.isEmpty()) error("details fetch failed for ${candidates.size} hits")

        val matchCands = detailed.map { (d, _) ->
            SeriesMatcher.Candidate(
                title = d.title,
                author = d.author,
                artist = d.artist,
                chapterCount = null,
                format = SeriesMatcher.formatMarker(d.title),
            )
        }
        val verdicts = matcher.verifyAll(query, matchCands)

        val idx = verdicts.indices.firstOrNull {
            verdicts[it].decision != SeriesMatcher.Decision.REJECT
        } ?: verdicts.indices.maxByOrNull { verdicts[it].score } ?: return null
        val verdict = verdicts[idx]
        val (details, sm) = detailed[idx]

        // Chapter list only when the identity gate passed.
        if (verdict.decision == SeriesMatcher.Decision.REJECT) {
            return Feed(
                source.id, g.pkgName, g.tier, sm.url, details.title,
                emptyList(), verdict.decision, verdict.score, verdict.reasons,
            )
        }

        val chapters = runCatching { source.getChapterList(sm) }
            .getOrElse { e ->
                logcat { "canonicalFeed ${g.pkgName} chapterlist: ${e.message}" }
                emptyList()
            }
            .map {
                WorkIdentityApi.FeedChapter(
                    name = it.name,
                    url = it.url,
                    date_upload = it.date_upload.takeIf { d -> d > 0L },
                )
            }

        return Feed(
            sourceId = source.id,
            pkgName = g.pkgName,
            tier = g.tier,
            url = sm.url,
            seriesTitle = details.title,
            chapters = chapters,
            verdict = verdict.decision,
            score = verdict.score,
            reasons = verdict.reasons,
            details = FeedDetails(
                description = details.description?.takeIf { it.isNotBlank() },
                author = details.author?.takeIf { it.isNotBlank() },
                genre = details.genre.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                thumbnailUrl = details.thumbnail_url?.takeIf { it.isNotBlank() },
                status = details.status.takeIf { it != SManga.UNKNOWN },
            ),
        )
    }

    /**
     * Book/novel collection — same shape as [collectFrom] but drives
     * [NovelCatalogueSource] via [NovelSourceManager]: search → details
     * → verify → chapter list.
     */
    private suspend fun collectNovelFrom(
        g: GoldenSources.GoldenSource,
        query: SeriesMatcher.Query,
        searchTitle: String,
        topCandidates: Int = TOP_CANDIDATES,
    ): Feed? {
        val source = g.sourceIds.firstNotNullOfOrNull { id -> novelSourceManager.get(id) }
            as? NovelCatalogueSource ?: error("source unavailable")

        if (!g.url.isNullOrBlank()) {
            evalNovelFeed(source, g, query, listOf(SNovel.create().apply { url = g.url!! }))
                ?.takeIf { it.verdict != SeriesMatcher.Decision.REJECT }
                ?.let { return it }
        }
        val page = runCatching {
            source.getSearchNovels(1, searchTitle, source.getFilterList())
        }.getOrElse { e -> throw IllegalStateException("search failed: ${e.message}") }
        logcat { "canonicalFeed(novel) ${g.pkgName} search '${searchTitle}': ${page.novels.size} hits" }
        return evalNovelFeed(source, g, query, page.novels.take(topCandidates))
    }

    private suspend fun evalNovelFeed(
        source: NovelCatalogueSource,
        g: GoldenSources.GoldenSource,
        query: SeriesMatcher.Query,
        candidates: List<SNovel>,
    ): Feed? {
        if (candidates.isEmpty()) return null
        val detailed = candidates.mapNotNull { sn ->
            runCatching { source.getNovelDetails(sn) to sn }
                .getOrNull()
        }
        if (detailed.isEmpty()) error("details fetch failed for ${candidates.size} hits")

        val matchCands = detailed.map { (d, _) ->
            SeriesMatcher.Candidate(
                title = d.title,
                author = d.author,
                artist = d.artist,
                chapterCount = null,
                format = SeriesMatcher.formatMarker(d.title),
            )
        }
        val verdicts = matcher.verifyAll(query, matchCands)

        val idx = verdicts.indices.firstOrNull {
            verdicts[it].decision != SeriesMatcher.Decision.REJECT
        } ?: verdicts.indices.maxByOrNull { verdicts[it].score } ?: return null
        val verdict = verdicts[idx]
        val (details, sn) = detailed[idx]

        if (verdict.decision == SeriesMatcher.Decision.REJECT) {
            return Feed(
                source.id, g.pkgName, g.tier, sn.url, details.title,
                emptyList(), verdict.decision, verdict.score, verdict.reasons,
            )
        }

        val chapters = runCatching { source.getChapterList(sn) }
            .getOrElse { e ->
                logcat { "canonicalFeed(novel) ${g.pkgName} chapterlist: ${e.message}" }
                emptyList()
            }
            .map {
                WorkIdentityApi.FeedChapter(
                    name = it.name,
                    url = it.url,
                    date_upload = it.date_upload.takeIf { d -> d > 0L },
                )
            }

        return Feed(
            sourceId = source.id,
            pkgName = g.pkgName,
            tier = g.tier,
            url = sn.url,
            seriesTitle = details.title,
            chapters = chapters,
            verdict = verdict.decision,
            score = verdict.score,
            reasons = verdict.reasons,
            details = FeedDetails(
                description = details.description?.takeIf { it.isNotBlank() },
                author = details.author?.takeIf { it.isNotBlank() },
                genre = details.genre.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                thumbnailUrl = details.thumbnail_url?.takeIf { it.isNotBlank() },
                status = details.status.takeIf { it != SNovel.UNKNOWN },
            ),
        )
    }

    /**
     * Shows collection (17-02, 19-03, abs canonicalization 19-04) —
     * same shape but drives [AnimeCatalogueSource] via
     * [AnimeSourceManager]; the feed carries the source's episode list
     * as [FeedEpisode] rows for the `/v1/episodes/canonize` courier
     * (D-04a). `number` stays the carrier's raw numbering; `abs` is
     * filled from the bound artifact entry's rows when proven (D-06).
     * SeriesMatcher still gates identity — a wrong anime match never
     * binds.
     */
    private suspend fun collectAnimeFrom(
        g: GoldenSources.GoldenSource,
        query: SeriesMatcher.Query,
        searchTitle: String,
        topCandidates: Int = TOP_CANDIDATES,
        workId: String = "",
    ): Feed? {
        val source = g.sourceIds.firstNotNullOfOrNull { id -> animeSourceManager.get(id) }
            as? AnimeCatalogueSource ?: error("source unavailable")

        if (!g.url.isNullOrBlank()) {
            evalAnimeFeed(source, g, query, listOf(SAnime.create().apply { url = g.url!! }), workId)
                ?.takeIf { it.verdict != SeriesMatcher.Decision.REJECT }
                ?.let { return it }
        }
        val page = runCatching {
            source.getSearchAnime(1, searchTitle, source.getFilterList())
        }.getOrElse { e -> throw IllegalStateException("search failed: ${e.message}") }
        logcat { "canonicalFeed(anime) ${g.pkgName} search '${searchTitle}': ${page.animes.size} hits" }
        return evalAnimeFeed(source, g, query, page.animes.take(topCandidates), workId)
    }

    private suspend fun evalAnimeFeed(
        source: AnimeCatalogueSource,
        g: GoldenSources.GoldenSource,
        query: SeriesMatcher.Query,
        candidates: List<SAnime>,
        workId: String,
    ): Feed? {
        if (candidates.isEmpty()) return null
        val detailed = candidates.mapNotNull { sa ->
            runCatching { source.getAnimeDetails(sa.copy()) to sa }
                .getOrNull()
        }
        if (detailed.isEmpty()) error("details fetch failed for ${candidates.size} hits")

        // 19-05: shows candidates assert season/part parsed from the
        // title (hard vetoes against the query's expected set) plus the
        // title-marker format (soft penalty vs the artifact media type).
        val matchCands = detailed.map { (d, _) ->
            val (season, part) = SeriesMatcher.parseSeasonPart(d.title)
            SeriesMatcher.Candidate(
                title = d.title,
                author = d.author,
                artist = d.artist,
                chapterCount = null,
                season = season,
                part = part,
                format = SeriesMatcher.formatMarker(d.title),
            )
        }
        val verdicts = matcher.verifyAll(query, matchCands)

        val idx = verdicts.indices.firstOrNull {
            verdicts[it].decision != SeriesMatcher.Decision.REJECT
        } ?: verdicts.indices.maxByOrNull { verdicts[it].score } ?: return null
        val verdict = verdicts[idx]
        val (details, sa) = detailed[idx]

        if (verdict.decision == SeriesMatcher.Decision.REJECT) {
            return Feed(
                source.id, g.pkgName, g.tier, sa.url, details.title,
                emptyList(), verdict.decision, verdict.score, verdict.reasons,
            )
        }

        // Carrier episode list — the canonize courier payload (19-03).
        // A failed list fetch degrades to an empty feed (the binding
        // still mints; canonical structure comes from the artifact feed).
        val episodes = runCatching { source.getEpisodeList(sa) }
            .getOrElse { e ->
                logcat { "canonicalFeed(anime) ${g.pkgName} episodelist: ${e.message}" }
                emptyList()
            }
            .map { ep ->
                WorkIdentityApi.FeedEpisode(
                    name = ep.name,
                    url = ep.url,
                    dateUpload = ep.date_upload.takeIf { d -> d > 0L },
                    kind = nameKind(ep.name),
                    number = ep.episode_number.toDouble().takeIf { n -> n >= 0.0 },
                )
            }
            // 19-04 abs canonicalization: when the bound artifact
            // entry's episode rows prove the carrier's numbering, lift
            // `abs` into canonical space (the worker merge-key prefers
            // abs). Unmapped numbers keep abs=null — a wrong map is
            // worse than none; divergence flags police raw merges.
            .let { canonicalizeAbs(workId, g, sa.url, it) }

        return Feed(
            sourceId = source.id,
            pkgName = g.pkgName,
            tier = g.tier,
            url = sa.url,
            seriesTitle = details.title,
            chapters = emptyList(),
            verdict = verdict.decision,
            score = verdict.score,
            reasons = verdict.reasons,
            details = FeedDetails(
                description = details.description?.takeIf { it.isNotBlank() },
                author = details.author?.takeIf { it.isNotBlank() },
                genre = details.getGenres().orEmpty(),
                thumbnailUrl = details.thumbnail_url?.takeIf { it.isNotBlank() },
                status = null,
            ),
            episodes = episodes,
        )
    }

    /** Minimal kind hint from the episode name — specials/OVAs and
     *  non-credit OP/ED get their own keyspace in the worker merge;
     *  everything else is a main 'episode'. */
    private fun nameKind(name: String): String = when {
        SPECIAL_NAME_RE.containsMatchIn(name) -> "special"
        NC_NAME_RE.containsMatchIn(name) -> "nc"
        else -> "episode"
    }

    /**
     * Carrier-number → canonical-abs overlay (19-04, D-06). Binding
     * signals in order: the winning entry's own extension url under the
     * source's declared id vocabulary (that url mints the
     * `provider_bindings` row), the work's provider-link id under its
     * provider vocabulary, then the preferred carrier's stored binding
     * (Design §10 — blank/unmappable refs fall back to the donor
     * carrier's proven entry). All three only choose WHICH artifact
     * entry proves the mapping; the carrier's own numbers still have to
     * match `anime_episodes.number` to canonicalize.
     */
    private suspend fun canonicalizeAbs(
        workId: String,
        g: GoldenSources.GoldenSource,
        providerRef: String?,
        episodes: List<WorkIdentityApi.FeedEpisode>,
    ): List<WorkIdentityApi.FeedEpisode> {
        if (episodes.none { it.number != null }) return episodes
        if (!workId.startsWith(CanonizeWork.IMDB_WORK_PREFIX)) return episodes
        val bridge = mikoDbManager.bridge() ?: return episodes
        val rows = bridge.byImdb(workId.removePrefix(CanonizeWork.IMDB_WORK_PREFIX))
        if (rows.isEmpty()) return episodes

        val entry = EpisodeCoverage.boundEntry(
            rows, providerRef, mikoDbManager.sourceMeta(g.pkgName)?.idType,
        ) ?: EpisodeCoverage.boundEntry(rows, g.externalId, g.provider)
            ?: preferredBoundEntry(workId, rows)
            ?: return episodes

        val anidb = entry.anidbId ?: return episodes
        val abs = EpisodeCoverage.canonicalizeNumbers(
            episodes.mapNotNull { it.number },
            bridge.episodesForAnidb(anidb),
        )
        if (abs.isEmpty()) return episodes
        return episodes.map { ep ->
            ep.number?.let { abs[it] }?.let { ep.copy(abs = it) } ?: ep
        }
    }

    /** The donor carrier's stored provider ref, resolved under its own
     *  id vocabulary — the §10 fallback when a feed carries no usable
     *  `provider_ref`. */
    private suspend fun preferredBoundEntry(
        workId: String,
        rows: List<AnimeIdBridge.AnimeMapping>,
    ): AnimeIdBridge.AnimeMapping? {
        val pkg = sourcePreferences.preferredCarrierFor(workId) ?: return null
        val ref = runCatching { identityApi.getBindings(workId) }.getOrNull()
            ?.bindings?.firstOrNull { it.source_key == pkg }?.provider_ref
            ?: return null
        return EpisodeCoverage.boundEntry(rows, ref, mikoDbManager.sourceMeta(pkg)?.idType)
    }

    companion object {
        private const val TOP_CANDIDATES = 3

        /** Wider search window for the interactive find-provider path —
         *  common titles surface spin-offs above the main entry. */
        const val TOP_CANDIDATES_INTERACTIVE = 8

        private const val SOURCE_TIMEOUT_MS = 45_000L

        private val SPECIAL_NAME_RE =
            Regex("""\b(Special|OVA|OAD|ONA)\b""", RegexOption.IGNORE_CASE)
        private val NC_NAME_RE =
            Regex("""\bNC ?(OP|ED)\b|creditless""", RegexOption.IGNORE_CASE)
    }
}
