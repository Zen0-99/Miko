package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.model.Carrier
import logcat.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Canonize orchestration (Phase 13-02, shows path 19-03) — on canonical
 * entry open: serve the cached sequence via [CanonicalChapterMap] /
 * [CanonicalEpisodeMap] (SWR), and when stale, collect verified feeds
 * → POST the medium's canonize route → refresh.
 *
 * Shows canon is a *sibling* contract (D-04/D-04a): the on-device
 * MikoDB artifact couriers its `anime_episodes` rows as the reserved
 * `mikodb:map` feed, per-carrier episode feeds follow, and the worker
 * materializes `canonical_episodes`. The artifact feed is the
 * structural authority — carrier-only rows stay `single`.
 *
 * Gate rules:
 *  - Fresh cache → do nothing (the caller already rendered it).
 *  - Zero ACCEPT feeds → never POST (a tentative-only result is not
 *    trustworthy enough to rebuild canon; the entry falls back to the
 *    bound-source chapter list). On shows the artifact feed counts as
 *    authority — a covered `mk:imdb:` work still materializes canon
 *    with zero carrier feeds.
 *  - TENTATIVE feeds ride along with `verified=false` — the worker
 *    merges them and writes `identity_review`.
 *  - More than [MAX_FAILURES] source failures with nothing collected →
 *    abort, don't ship a partial picture.
 */
class CanonizeWork(
    private val bridge: MangaNovelIdBridge = Injekt.get(),
    private val collector: CanonicalFeedCollector = Injekt.get(),
    private val chapterMap: CanonicalChapterMap = Injekt.get(),
    private val api: WorkIdentityApi = Injekt.get(),
    private val goldenSources: GoldenSources = Injekt.get(),
    private val episodeMap: CanonicalEpisodeMap = Injekt.get(),
    private val mikoDbManager: MikoDbManager = Injekt.get(),
    private val mintTelemetry: MintTelemetry = Injekt.get(),
    private val workCarriers: WorkCarriers = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) {

    sealed interface Outcome {
        /** [bestDetails] — metadata from the highest-scoring ACCEPT
         *  feed; lets callers backfill sparse canonical entries from
         *  the extension that actually won the identity gate. */
        data class Canonized(
            val count: Int,
            val tentative: Int,
            val bestDetails: CanonicalFeedCollector.FeedDetails? = null,
        ) : Outcome
        data object Fresh : Outcome
        data object NoWork : Outcome
        data object NoVerifiedFeeds : Outcome
        data object TooManyFailures : Outcome
        data object PostFailed : Outcome
    }

    suspend fun run(
        workId: String,
        carrier: Carrier = Carrier.MANGA,
        extraSources: List<GoldenSources.GoldenSource> = emptyList(),
    ): Outcome = when (carrier) {
        Carrier.SHOWS -> runShows(workId, carrier, extraSources)
        else -> runChapters(workId, carrier, extraSources)
    }

    /** Manga/books path — POST `/v1/chapters/canonize`. */
    private suspend fun runChapters(
        workId: String,
        carrier: Carrier,
        extraSources: List<GoldenSources.GoldenSource>,
    ): Outcome {
        // Empty canon bypasses the recency gate — a wiped/never-built
        // sequence must rebuild now, not wait out the interval.
        if (chapterMap.forWork(workId).isNotEmpty() && !isCanonizeDue(workId)) {
            return Outcome.Fresh
        }
        val work = bridge.byWorkId(workId) ?: return Outcome.NoWork.also {
            logcat { "canonize $workId: no work row" }
        }

        // Extra candidates (a migrating favorite's own source, 17-03)
        // lead the list so their ext-internal URL survives the pkg dedupe
        // and lands in the canon refs as that source's carrier path.
        val sources = feedSources(work, carrier.medium, extraSources)
        val r = collector.collect(
            work,
            carrier.medium,
            maxFeeds = sources?.size?.coerceAtMost(MAX_FEEDS) ?: MAX_FEEDS_DEFAULT,
            sources = sources,
        )
        if (r.feeds.isEmpty() && r.failed.size > MAX_FAILURES) {
            return Outcome.TooManyFailures.also {
                logcat { "canonize $workId: ${r.failed.size} source failures (${r.failed.keys})" }
            }
        }
        if (r.feeds.isEmpty()) {
            return Outcome.NoVerifiedFeeds.also {
                logcat {
                    "canonize $workId: no verified feeds " +
                        "(tentative=${r.tentative.size} rejected=${r.rejected.size} failed=${r.failed.keys})"
                }
            }
        }

        val feeds = r.feeds.map { it.wire(verified = true) } +
            r.tentative.map { it.wire(verified = false) }

        // Phase 20: bundled mint telemetry rides the canonize body;
        // a POST failure marks every carried pkg failed.
        val resp = api.canonize(
            WorkIdentityApi.CanonizeRequest(
                work_id = workId,
                feeds = feeds,
                telemetry = mintTelemetry.drain(),
            ),
        ) ?: return Outcome.PostFailed.also {
            feeds.forEach { mintTelemetry.recordFailed(it.source_key) }
            logcat { "canonize $workId: POST failed (${feeds.size} feeds)" }
        }
        mintTelemetry.commitFlush()

        // A solidify or this device's own provider_ref mint changed the
        // matrix — the 6h cache can't wait for TTL.
        if (resp.solidified.isNotEmpty() || feeds.any { it.provider_ref != null }) {
            workCarriers.invalidate(workId)
        }
        sourcePreferences.markCanonizeRan(workId)
        chapterMap.invalidate(workId)
        val bestDetails = r.feeds.filter { it.details != null && !it.details.isEmpty }
            .maxByOrNull { it.score }?.details
        logcat {
            "canonize $workId: ${resp.count} chapters (${r.tentative.size} tentative, feeds=${r.feeds.map {
                it.pkgName
            }})"
        }
        return Outcome.Canonized(resp.count, r.tentative.size, bestDetails)
    }

    /**
     * Shows path (19-03) — POST `/v1/episodes/canonize` with the
     * `mikodb:map` artifact feed leading the carrier feeds. The
     * artifact feed only exists for `mk:imdb:` works the artifact
     * covers; `mk:m:`/`mk:hs:` ids and uncovered imdb works canonize
     * carrier-feeds-only (rows stay `single` — the artifact is what
     * lifts a row to `verified`).
     */
    private suspend fun runShows(
        workId: String,
        carrier: Carrier,
        extraSources: List<GoldenSources.GoldenSource>,
    ): Outcome {
        if (episodeMap.forWork(workId).isNotEmpty() && !isCanonizeDue(workId)) {
            return Outcome.Fresh
        }
        val work = bridge.byWorkId(workId) ?: return Outcome.NoWork.also {
            logcat { "canonize(episodes) $workId: no work row" }
        }

        val sources = feedSources(work, carrier.medium, extraSources)
        val r = collector.collect(
            work,
            carrier.medium,
            maxFeeds = sources?.size?.coerceAtMost(MAX_FEEDS) ?: MAX_FEEDS_DEFAULT,
            sources = sources,
        )
        if (r.feeds.isEmpty() && r.failed.size > MAX_FAILURES) {
            return Outcome.TooManyFailures.also {
                logcat { "canonize(episodes) $workId: ${r.failed.size} source failures (${r.failed.keys})" }
            }
        }
        val artifact = artifactFeed(workId)
        if (artifact == null && r.feeds.isEmpty()) {
            return Outcome.NoVerifiedFeeds.also {
                logcat {
                    "canonize(episodes) $workId: no artifact coverage and no verified feeds " +
                        "(tentative=${r.tentative.size} rejected=${r.rejected.size} failed=${r.failed.keys})"
                }
            }
        }

        // Artifact feed FIRST — the worker merge grants it structural
        // authority (its values win over carrier absence).
        val feeds = listOfNotNull(artifact) +
            r.feeds.map { it.episodeWire(verified = true) } +
            r.tentative.map { it.episodeWire(verified = false) }

        val resp = api.canonizeEpisodes(workId, feeds, mintTelemetry.drain())
            ?: return Outcome.PostFailed.also {
                feeds.forEach { mintTelemetry.recordFailed(it.sourceKey) }
                logcat { "canonize(episodes) $workId: POST failed (${feeds.size} feeds)" }
            }
        mintTelemetry.commitFlush()

        if (resp.solidified.isNotEmpty() || feeds.any { it.providerRef != null }) {
            workCarriers.invalidate(workId)
        }
        episodeMap.invalidate(workId)
        episodeMap.prime(workId)
        sourcePreferences.markCanonizeRan(workId)
        val bestDetails = r.feeds.filter { it.details != null && !it.details.isEmpty }
            .maxByOrNull { it.score }?.details
        logcat {
            "canonize(episodes) $workId: ${resp.count} episodes " +
                "(artifact=${artifact != null} tentative=${r.tentative.size} feeds=${r.feeds.map { it.pkgName }})"
        }
        return Outcome.Canonized(resp.count, r.tentative.size, bestDetails)
    }

    /**
     * The `mikodb:map` artifact feed — the work's `anime_episodes` rows
     * verbatim (D-04a Option A courier). Null for non-`mk:imdb:` ids
     * and artifacts without the table/coverage — the merge degrades to
     * carrier-only `single` canon, never crashes.
     */
    private fun artifactFeed(workId: String): WorkIdentityApi.EpisodeFeed? {
        if (!workId.startsWith(IMDB_WORK_PREFIX)) return null
        val imdbId = workId.removePrefix(IMDB_WORK_PREFIX)
        val episodes = mikoDbManager.bridge()
            ?.episodesForImdb(imdbId)
            .orEmpty()
            .map { it.toFeedEpisode() }
        if (episodes.isEmpty()) return null
        return WorkIdentityApi.EpisodeFeed(
            sourceKey = ARTIFACT_SOURCE_KEY,
            verified = true,
            score = 1.0,
            provenance = "device_feed",
            episodes = episodes,
        )
    }

    /** Artifact row → structured feed episode — `key`/`doc`/`eids` are
     *  what keep this feed inside the worker's ≥90% forge guard. */
    private fun AnimeIdBridge.AnimeEpisode.toFeedEpisode() =
        WorkIdentityApi.FeedEpisode(
            name = title ?: "Episode ${number ?: epKey}",
            key = epKey,
            doc = "anidb:$anidbId",
            // Artifact type vocabulary → worker kind vocabulary
            // (trailer/parody fold into special).
            kind = when (type) {
                "main" -> "episode"
                "nc" -> "nc"
                else -> "special"
            },
            number = number,
            abs = absNumber,
            season = season,
            epInSeason = epInSeason,
            airDate = airDate,
            dateUpload = airDateAsEpochMs(),
            image = image,
            overview = overview,
            runtime = runtime,
            anidbEid = anidbEid,
            tvdbEid = tvdbEid,
            tvdbShowId = tvdbShowId,
            filler = filler,
        )

    private fun CanonicalFeedCollector.Feed.wire(
        verified: Boolean,
        provenance: String? = null,
    ) = WorkIdentityApi.CanonizeFeed(
        source_key = pkgName,
        tier = tier,
        verified = verified,
        score = score,
        provider_ref = url.takeIf { it.isNotBlank() },
        provenance = provenance,
        chapters = chapters,
    )

    private fun CanonicalFeedCollector.Feed.episodeWire(
        verified: Boolean,
        provenance: String? = null,
    ) = WorkIdentityApi.EpisodeFeed(
        sourceKey = pkgName,
        tier = tier,
        verified = verified,
        score = score,
        providerRef = url.takeIf { it.isNotBlank() },
        provenance = provenance,
        episodes = episodes,
    )

    /**
     * Feed-source selection honoring the entry's carrier pin (16-04):
     *
     *  - Pinned work → only the pinned extension's list is collected.
     *    The pin is the user's statement of which carrier to trust —
     *    other carriers' feeds don't gate freshness — and the pinned
     *    feed still merges into shared canon, so chapters it posts
     *    first materialize as canon rows routed to it. A pin that no
     *    longer resolves (extension uninstalled) falls back to the
     *    default golden set; a resolved pin whose feed rejects keeps
     *    the pin exclusive (NoVerifiedFeeds rather than a silent
     *    other-source refresh).
     *  - Automatic → every readable bound carrier (the user's
     *    connections in the carrier matrix) joins the golden set, so
     *    each connected extension's newest chapters merge into one
     *    canon list — "chapter 100 from NovelFull, chapter 101 from
     *    WeebCentral". Bound feeds lead the pkg-dedupe so their
     *    ext-internal URLs win the canon refs.
     *
     * Null = collector default (capped golden set).
     */
    private suspend fun feedSources(
        work: MangaNovelIdBridge.WorkRow,
        medium: String,
        extraSources: List<GoldenSources.GoldenSource>,
    ): List<GoldenSources.GoldenSource>? {
        val pinned = sourcePreferences.preferredCarrierFor(work.mikoWorkId)
        if (pinned != null) {
            val pinnedSources = goldenSources.allForWorkRow(work, medium)
                .filter { it.pkgName == pinned }
            if (pinnedSources.isEmpty()) {
                logcat { "canonize ${work.mikoWorkId}: pinned carrier $pinned unresolved — golden set" }
                return null
            }
            return (extraSources + pinnedSources).distinctBy { it.pkgName }
        }
        val boundPkgs = runCatching { workCarriers.forWork(work.mikoWorkId, medium) }
            .getOrNull()
            ?.readable
            ?.mapTo(HashSet()) { it.pkgName }
            .orEmpty()
        if (extraSources.isEmpty() && boundPkgs.isEmpty()) return null
        val bound = if (boundPkgs.isEmpty()) {
            emptyList()
        } else {
            goldenSources.allForWorkRow(work, medium).filter { it.pkgName in boundPkgs }
        }
        return (extraSources + bound + goldenSources.forWorkRow(work, medium))
            .distinctBy { it.pkgName }
    }

    /**
     * Rebuild gate: a work is due when this device hasn't POSTed feeds
     * for it in [CANONIZE_INTERVAL_MS]. The chapter/episode read caches
     * CANNOT gate this — their `fetchedAt` refreshes on every UI read
     * (carrier-label lookups call `forWork` on detail open), which
     * kept canonize permanently "fresh" and starved feed posts: the
     * worker canon froze at whatever the last canonize produced while
     * the app happily rendered it. Persisted per work so app restarts
     * don't re-fan-out the collect.
     */
    private fun isCanonizeDue(workId: String): Boolean =
        System.currentTimeMillis() - sourcePreferences.canonizeRanAtFor(workId) > CANONIZE_INTERVAL_MS

    companion object {
        const val MAX_FAILURES = 2
        const val CANONIZE_INTERVAL_MS = 24L * 60 * 60 * 1000

        /** Accepted-feed cap: Automatic bound-union sets pass their own
         *  size (a bound carrier's feed should never truncate), still
         *  bounded so the canonize POST stays reasonable. */
        const val MAX_FEEDS = 15
        const val MAX_FEEDS_DEFAULT = 5

        /** Reserved artifact `source_key` — the worker demotes any feed
         *  claiming it without artifact-shaped rows (forge guard). */
        const val ARTIFACT_SOURCE_KEY = "mikodb:map"
        const val IMDB_WORK_PREFIX = "mk:imdb:"
    }
}
