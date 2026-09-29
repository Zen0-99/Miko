package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.logcat
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
    private val matcher: SeriesMatcher = Injekt.get(),
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
     */
    suspend fun collect(
        work: MangaNovelIdBridge.WorkRow,
        medium: String = "manga",
        maxFeeds: Int = 5,
    ): Result = withContext(Dispatchers.IO) {
        val query = SeriesMatcher.Query.of(work)
        val goldens = goldenSources.forWorkRow(work, medium)
        if (goldens.isEmpty()) {
            return@withContext Result(emptyList(), emptyList(), emptyList(), emptyMap())
        }

        val feeds = mutableListOf<Feed>()
        val rejected = mutableListOf<Feed>()
        val failed = mutableMapOf<String, String>()

        val isBooks = medium == WorkIdResolver.MEDIUM_BOOKS
        coroutineScope {
            goldens.map { g ->
                async {
                    // Bound each source probe — a stalled Cloudflare/resolver
                    // path must not hang the whole collect (coroutineScope
                    // waits for every child).
                    val res = try {
                        withTimeout(SOURCE_TIMEOUT_MS) {
                            runCatching {
                                if (isBooks) {
                                    collectNovelFrom(g, query, work.title ?: "")
                                } else {
                                    collectFrom(g, query, work.title ?: "")
                                }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        failed[g.pkgName] = "timeout"
                        null
                    }
                    res?.getOrElse { e ->
                        failed[g.pkgName] = e.message ?: e.javaClass.simpleName
                        null
                    }
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
    ): Feed? {
        // Resolve the first live installed source id for this package.
        val source = g.sourceIds.firstNotNullOfOrNull { id -> sourceManager.get(id) }
            as? CatalogueSource ?: return null

        // Provider-linked url tried first — but a carrier URL is usually
        // not the extension's internal url shape, so a failed or rejected
        // direct lookup falls back to title search rather than dying.
        if (!g.url.isNullOrBlank()) {
            evalMangaFeed(source, g, query, listOf(SManga.create().apply { url = g.url!! }))
                ?.takeIf { it.verdict != SeriesMatcher.Decision.REJECT }
                ?.let { return it }
        }
        val page = runCatching { source.getSearchManga(1, searchTitle, FilterList()) }
            .getOrNull() ?: return null
        return evalMangaFeed(source, g, query, page.mangas.take(TOP_CANDIDATES))
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
        if (detailed.isEmpty()) return null

        val matchCands = detailed.map { (d, _) ->
            SeriesMatcher.Candidate(
                title = d.title,
                author = d.author,
                artist = d.artist,
                chapterCount = null,
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
            .map { WorkIdentityApi.FeedChapter(name = it.name, url = it.url) }

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
    ): Feed? {
        val source = g.sourceIds.firstNotNullOfOrNull { id -> novelSourceManager.get(id) }
            as? NovelCatalogueSource ?: return null

        if (!g.url.isNullOrBlank()) {
            evalNovelFeed(source, g, query, listOf(SNovel.create().apply { url = g.url!! }))
                ?.takeIf { it.verdict != SeriesMatcher.Decision.REJECT }
                ?.let { return it }
        }
        val page = runCatching {
            source.getSearchNovels(1, searchTitle, NovelFilterList(emptyList()))
        }.getOrNull() ?: return null
        return evalNovelFeed(source, g, query, page.novels.take(TOP_CANDIDATES))
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
        if (detailed.isEmpty()) return null

        val matchCands = detailed.map { (d, _) ->
            SeriesMatcher.Candidate(
                title = d.title,
                author = d.author,
                artist = d.artist,
                chapterCount = null,
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
            .map { WorkIdentityApi.FeedChapter(name = it.name, url = it.url) }

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

    companion object {
        private const val TOP_CANDIDATES = 3
        private const val SOURCE_TIMEOUT_MS = 45_000L
    }
}
