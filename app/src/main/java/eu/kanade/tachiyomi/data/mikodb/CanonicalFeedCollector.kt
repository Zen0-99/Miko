package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import logcat.logcat
import tachiyomi.domain.source.manga.service.MangaSourceManager
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
    private val matcher: SeriesMatcher = Injekt.get(),
) {

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

        coroutineScope {
            goldens.map { g ->
                async {
                    runCatching { collectFrom(g, query, work.title ?: "") }
                        .getOrElse { e ->
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

        // Provider-linked sources skip search — url is the canonical entry.
        val candidates: List<SManga> = if (!g.url.isNullOrBlank()) {
            listOf(SManga.create().apply { url = g.url!! })
        } else {
            val page = source.getSearchManga(1, searchTitle, FilterList())
            page.mangas.take(TOP_CANDIDATES)
        }
        if (candidates.isEmpty()) return null

        // Fetch details for each candidate; verify pairwise for margin.
        val detailed = candidates.mapNotNull { sm ->
            runCatching { source.getMangaDetails(sm) to sm }
                .getOrNull()
        }
        if (detailed.isEmpty()) return null

        val matchCands = detailed.map { (d, sm) ->
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
        )
    }

    companion object {
        private const val TOP_CANDIDATES = 3
    }
}
