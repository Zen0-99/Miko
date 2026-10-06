package eu.kanade.tachiyomi.data.mikodb

import android.graphics.drawable.Drawable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Find Content Provider (Phase 17-02) — the user-facing mint flow for
 * works the catalog has no bindings for. Drives [CanonicalFeedCollector]
 * (already concurrent, timeout-bounded, SeriesMatcher-gated) over the
 * installed extensions of the work's medium and streams a per-source
 * status model the sheet renders; confirmed picks mint `user_picked`
 * bindings via the normal canonize feed contract.
 *
 * Searches are owned by THIS singleton (not the screen) so a dismissed
 * sheet never cancels them — reopening within [SEARCH_TTL_MS] reattaches
 * to the live StateFlow instead of restarting. Semantics: only
 * user-confirmed results are submitted — an unconfirmed match records
 * nothing (a dismissed sheet isn't a weak-negative vote).
 */
class ProviderFinder(
    private val goldenSources: GoldenSources = Injekt.get(),
    private val collector: CanonicalFeedCollector = Injekt.get(),
    private val bridge: MangaNovelIdBridge = Injekt.get(),
    private val api: WorkIdentityApi = Injekt.get(),
    private val mikoDbManager: MikoDbManager = Injekt.get(),
    private val workCarriers: WorkCarriers = Injekt.get(),
    private val episodeMap: CanonicalEpisodeMap = Injekt.get(),
    private val mintTelemetry: MintTelemetry = Injekt.get(),
    private val providerTiers: ProviderTiers = Injekt.get(),
) {

    // Outlives any ScreenModel — a sheet close must not kill the fan-out.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    enum class Status { SEARCHING, FOUND, NO_MATCH, FAILED }

    /** One suggested (bound-or-ranked) extension the repo can install —
     *  the sheet's "likely to have it" section. Built per-medium by the
     *  ScreenModels since each has its own extension manager. */
    data class RecommendedExtension(
        val pkgName: String,
        val name: String,
        val tier: String,
        val iconUrl: String?,
        val lang: String?,
    )

    /** Sheet state — also the cached-search snapshot collectors replay. */
    data class Search(
        val rows: List<Row> = emptyList(),
        val confirmed: Set<String> = emptySet(),
        val searching: Boolean = false,
        val recommendations: List<RecommendedExtension> = emptyList(),
    )

    data class Row(
        val pkgName: String,
        val displayName: String,
        val tier: String,
        val status: Status,
        /** Installed extension's icon — rendered via ExtensionIcon
         *  (browse-screen logic). */
        val icon: Drawable? = null,
        /** The extension's entry title it matched to — shown so the
         *  user can confirm "yes, that's this work". */
        val foundTitle: String? = null,
        val thumbnailUrl: String? = null,
        /** Feed size — episodes for shows feeds (`feed.chapters` is
         *  locked empty there), chapters otherwise. */
        val chapterCount: Int = 0,
        /** Matcher verdict — TENTATIVE rows surface unchecked. */
        val tentative: Boolean = false,
        /** Machine key ("timeout", "unavailable", …) — NEVER raw
         *  exception text; the sheet maps it to a readable status. */
        val error: String? = null,
        /** The medium the search ran under — the sheet picks the count
         *  label ("episodes" for shows, "chapters" otherwise). */
        val medium: String = WorkIdResolver.MEDIUM_MANGA,
        /** Stable dominantReason key (19-05, D-08) — the sheet maps it
         *  to ONE `find_provider_reason_*` string, and only on
         *  NO_MATCH / tentative FOUND rows. */
        val reason: String? = null,
        /** Canonical main episodes the feed doesn't cover (19-05,
         *  D-03b) — 0 for non-shows media, absent canon, or unknown
         *  feeds. */
        val missingCount: Int = 0,
        internal val feed: CanonicalFeedCollector.Feed? = null,
        /** Phase 20: this row's binding was just minted by this device —
         *  it is provisional until the ledger confirms it; the sheet
         *  badges the honest state. */
        val provisionalMint: Boolean = false,
        /** Phase 20-04: the worker's dynamic tier promoted this
         *  provider past its manifest tier — the sheet's
         *  "community-verified" micro-label. Equal/demoted/absent
         *  overlays never flag. */
        val communityVerified: Boolean = false,
    )

    private data class Entry(
        val startedAt: Long,
        val state: MutableStateFlow<Search>,
        val job: Job,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    /**
     * Search every candidate extension for [workId]'s titles, returning
     * the live [Search] state. A fresh (≤ [SEARCH_TTL_MS]) in-flight or
     * completed search for the same work is reused — the job keeps
     * running in the background even with no collector attached, so the
     * caller can dismiss/reopen the sheet without losing progress.
     * Returns null when the work id can't be resolved locally.
     */
    suspend fun search(
        workId: String,
        medium: String,
    ): StateFlow<Search>? {
        val key = "$medium:$workId"
        val now = System.currentTimeMillis()
        entries[key]?.takeIf { now - it.startedAt < SEARCH_TTL_MS }
            ?.let { return it.state }
        entries.entries.removeIf { now - it.value.startedAt > SEARCH_TTL_MS }

        val work = bridge.byWorkId(workId) ?: run {
            // Unknown/minted-but-uncached work — nothing to search with.
            return null
        }
        // Uncapped: the interactive search fans out to every installed
        // extension — the capped set is a background-pipeline bound.
        // Already-bound carriers are excluded — "find MORE providers"
        // means sources the user doesn't have yet.
        val boundPkgs = runCatching { workCarriers.forWork(workId, medium).carriers }
            .getOrNull()
            ?.mapTo(mutableSetOf()) { it.pkgName }
            ?: emptySet()
        val goldens = goldenSources.allForWorkRow(work, medium)
            .filter { it.pkgName !in boundPkgs }
        logcat {
            "providerFinder $workId: ${goldens.size} candidates " +
                goldens.joinToString(",") { it.pkgName.substringAfterLast('.') }
        }
        val meta = mikoDbManager.allSourceMeta()
        // 20-04: `community-verified` flags a provider whose ledger-earned
        // dynamic tier beat its manifest tier — computed once against the
        // persisted overlay (the collector callback isn't a suspend site).
        val communityVerified = goldens.mapNotNullTo(HashSet()) { g ->
            g.pkgName.takeIf {
                providerTiers.dynamic(it)
                    ?.let { d -> tierRank(d) < tierRank(meta[it]?.tier ?: "") }
                    ?: false
            }
        }
        val rows = ConcurrentHashMap<String, Row>()
        fun snapshot() = goldens.mapNotNull { rows[it.pkgName] }
            .sortedBy { tierRank(it.tier) }
        for (g in goldens) {
            rows[g.pkgName] = Row(
                pkgName = g.pkgName,
                displayName = meta[g.pkgName]?.name?.takeIf { it.isNotBlank() } ?: g.name,
                // g.tier is already the effective tier (20-04 overlay
                // applied inside GoldenSources.candidatePool).
                tier = g.tier,
                status = Status.SEARCHING,
                icon = g.icon,
                medium = medium,
                communityVerified = g.pkgName in communityVerified,
            )
        }
        val state = MutableStateFlow(Search(rows = snapshot(), searching = goldens.isNotEmpty()))
        val job = scope.launch {
            try {
                // 19-05: canon-side coverage for the "missing N" row
                // segment — absent canon (uncanonized/failed fetch)
                // just reports 0. Fetched once per search; the map's
                // SWR cache makes sheet reopens cheap.
                val canon = if (medium == WorkIdResolver.MEDIUM_SHOWS) {
                    runCatching { episodeMap.forWork(workId) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                collector.collect(
                    work,
                    medium,
                    maxFeeds = goldens.size,
                    sources = goldens,
                    topCandidates = CanonicalFeedCollector.TOP_CANDIDATES_INTERACTIVE,
                ) { pkg, feed, error ->
                    val g = goldens.firstOrNull { it.pkgName == pkg } ?: return@collect
                    rows[pkg] = Row(
                        pkgName = pkg,
                        displayName = meta[pkg]?.name?.takeIf { it.isNotBlank() } ?: g.name,
                        tier = g.tier, // effective tier — see above
                        communityVerified = pkg in communityVerified,
                        icon = g.icon,
                        status = when {
                            feed == null -> if (error != null) Status.FAILED else Status.NO_MATCH
                            feed.verdict == SeriesMatcher.Decision.REJECT -> Status.NO_MATCH
                            else -> Status.FOUND
                        },
                        foundTitle = feed?.seriesTitle,
                        thumbnailUrl = feed?.details?.thumbnailUrl,
                        chapterCount = when (medium) {
                            WorkIdResolver.MEDIUM_SHOWS -> feed?.episodes?.size ?: 0
                            else -> feed?.chapters?.size ?: 0
                        },
                        tentative = feed?.verdict == SeriesMatcher.Decision.TENTATIVE,
                        error = errorKey(error),
                        medium = medium,
                        // Reason only on the rows that expose one
                        // (19-05, D-08): NO_MATCH best-candidate and
                        // tentative FOUND. Plain FOUND/FAILED/SEARCHING
                        // never carry it.
                        reason = feed?.takeIf {
                            it.verdict == SeriesMatcher.Decision.REJECT ||
                                it.verdict == SeriesMatcher.Decision.TENTATIVE
                        }?.reasons?.let(SeriesMatcher::dominantReason),
                        missingCount = if (
                            medium == WorkIdResolver.MEDIUM_SHOWS &&
                            feed != null && feed.verdict != SeriesMatcher.Decision.REJECT &&
                            canon.isNotEmpty()
                        ) {
                            canonMissingCount(canon, feed.episodes)
                        } else {
                            0
                        },
                        feed = feed?.takeIf { it.verdict != SeriesMatcher.Decision.REJECT },
                    )
                    state.value = state.value.copy(rows = snapshot())
                    logcat {
                        "providerFinder $workId $pkg → ${rows[pkg]?.status} " +
                            "title=${feed?.seriesTitle} err=${error ?: "-"}"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                state.value = state.value.copy(searching = false)
            }
        }
        entries[key] = Entry(now, state, job)
        return state
    }

    /**
     * Drop a cached search — after `mint` the freshly-bound carriers
     * must not linger in "find more providers" rows for the TTL window.
     */
    fun invalidate(workId: String, medium: String) {
        entries.remove("$medium:$workId")
    }

    /**
     * Mint confirmed picks: each [confirmed] feed submits
     * `verified=true, provenance="user_picked"` — the worker writes the
     * binding at the 0.9 confidence floor and remerges canon. Shows
     * feeds route to `/v1/episodes/canonize` (19-03) carrying their
     * episode lists — an empty list still mints the binding, same
     * semantics as a chapterless chapter feed. [medium] defaults to the
     * chapter route so manga/books call sites are unchanged. Returns
     * the canonize response count, or null on POST failure.
     */
    suspend fun mint(
        workId: String,
        confirmed: List<CanonicalFeedCollector.Feed>,
        medium: String = WorkIdResolver.MEDIUM_MANGA,
    ): Int? {
        if (confirmed.isEmpty()) return null
        if (medium == WorkIdResolver.MEDIUM_SHOWS) {
            val feeds = confirmed.map { it.toEpisodeFeed() }
            // Phase 20: telemetry piggybacks on the mint request; a
            // failed POST marks the carried pkgs failed.
            val resp = api.canonizeEpisodes(workId, feeds, mintTelemetry.drain())
                ?: run {
                    feeds.forEach { mintTelemetry.recordFailed(it.sourceKey) }
                    return null
                }
            mintTelemetry.commitFlush()
            // The device just minted bindings — the cached matrix must
            // reflect the new provisional rows immediately, not at TTL.
            workCarriers.invalidate(workId)
            markMinted(workId, medium, feeds.mapTo(mutableSetOf()) { it.sourceKey })
            logcat {
                "providerFinder $workId: minted ${feeds.map { it.sourceKey }} " +
                    "(${resp.count} episodes)"
            }
            return resp.count
        }
        val feeds = confirmed.map { f ->
            WorkIdentityApi.CanonizeFeed(
                source_key = f.pkgName,
                tier = f.tier,
                verified = true,
                score = f.score,
                provider_ref = f.url.takeIf { it.isNotBlank() },
                provenance = "user_picked",
                chapters = f.chapters,
            )
        }
        val resp = api.canonize(
            WorkIdentityApi.CanonizeRequest(workId, feeds, mintTelemetry.drain()),
        ) ?: run {
            feeds.forEach { mintTelemetry.recordFailed(it.source_key) }
            return null
        }
        mintTelemetry.commitFlush()
        workCarriers.invalidate(workId)
        markMinted(workId, medium, feeds.mapTo(mutableSetOf()) { it.source_key })
        logcat { "providerFinder $workId: minted ${feeds.map { it.source_key }} (${resp.count} chapters)" }
        return resp.count
    }

    /** Flag the just-minted rows in the cached search so a still-open
     *  (or TTL-reattached) Find Provider sheet shows the honest
     *  provisional state until the bindings re-fetch lands. */
    private fun markMinted(workId: String, medium: String, pkgs: Set<String>) {
        entries["$medium:$workId"]?.state?.update { s ->
            s.copy(
                rows = s.rows.map { r ->
                    if (r.pkgName in pkgs) r.copy(provisionalMint = true) else r
                },
            )
        }
    }

    /** user_picked binding via the episodes route — the binding mints
     *  on `provider_ref` even when the episode list is empty. */
    private fun CanonicalFeedCollector.Feed.toEpisodeFeed() =
        WorkIdentityApi.EpisodeFeed(
            sourceKey = pkgName,
            tier = tier,
            verified = true,
            score = score,
            providerRef = url.takeIf { it.isNotBlank() },
            provenance = "user_picked",
            episodes = episodes,
        )

    /** Canon mains the feed doesn't cover (19-05 §5): a feed episode
     *  covers a canon row when its canonical `abs` (19-04-proven) or
     *  doc-local `number` matches — carrier specials/NC never count
     *  toward main coverage. */
    private fun canonMissingCount(
        canon: List<CanonicalEpisodeMap.CanonEpisode>,
        feedEpisodes: List<WorkIdentityApi.FeedEpisode>,
    ): Int {
        val feedCoords = feedEpisodes
            .filter { it.kind == null || it.kind == "episode" }
            .mapNotNull { it.abs?.toDouble() ?: it.number }
            .toSet()
        return canon.count { row ->
            if (row.kind != "episode") return@count false
            val coord = row.abs?.toDouble() ?: row.number
            // No coordinate → nothing can prove the feed covers it.
            coord == null || coord !in feedCoords
        }
    }

    /** Maps internal error strings to stable row keys — the sheet
     *  renders a readable status, logcat keeps the detail. */
    private fun errorKey(error: String?): String? = when {
        error == null -> null
        error.contains("timeout", ignoreCase = true) ||
            error.contains("timed out", ignoreCase = true) -> "timeout"
        error.contains("source unavailable", ignoreCase = true) -> "unavailable"
        else -> "failed"
    }

    companion object {
        const val MAX_RECOMMENDATIONS = 5

        /** Interactive search results live this long — dismiss/reopen
         *  reattaches; past this the next open starts fresh. */
        const val SEARCH_TTL_MS = 5 * 60 * 1000L
    }
}
