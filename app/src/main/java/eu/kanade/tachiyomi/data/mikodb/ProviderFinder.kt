package eu.kanade.tachiyomi.data.mikodb

import android.graphics.drawable.Drawable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        val chapterCount: Int = 0,
        /** Matcher verdict — TENTATIVE rows surface unchecked. */
        val tentative: Boolean = false,
        /** Machine key ("timeout", "unavailable", …) — NEVER raw
         *  exception text; the sheet maps it to a readable status. */
        val error: String? = null,
        internal val feed: CanonicalFeedCollector.Feed? = null,
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
        val rows = ConcurrentHashMap<String, Row>()
        fun snapshot() = goldens.mapNotNull { rows[it.pkgName] }
            .sortedBy { goldenSources.tierRank(it.tier) }
        for (g in goldens) {
            rows[g.pkgName] = Row(
                pkgName = g.pkgName,
                displayName = meta[g.pkgName]?.name?.takeIf { it.isNotBlank() } ?: g.name,
                tier = meta[g.pkgName]?.tier ?: g.tier,
                status = Status.SEARCHING,
                icon = g.icon,
            )
        }
        val state = MutableStateFlow(Search(rows = snapshot(), searching = goldens.isNotEmpty()))
        val job = scope.launch {
            try {
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
                        tier = meta[pkg]?.tier ?: g.tier,
                        icon = g.icon,
                        status = when {
                            feed == null -> if (error != null) Status.FAILED else Status.NO_MATCH
                            feed.verdict == SeriesMatcher.Decision.REJECT -> Status.NO_MATCH
                            else -> Status.FOUND
                        },
                        foundTitle = feed?.seriesTitle,
                        thumbnailUrl = feed?.details?.thumbnailUrl,
                        chapterCount = feed?.chapters?.size ?: 0,
                        tentative = feed?.verdict == SeriesMatcher.Decision.TENTATIVE,
                        error = errorKey(error),
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
            val resp = api.canonizeEpisodes(workId, feeds) ?: return null
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
        val resp = api.canonize(WorkIdentityApi.CanonizeRequest(workId, feeds))
            ?: return null
        logcat { "providerFinder $workId: minted ${feeds.map { it.source_key }} (${resp.count} chapters)" }
        return resp.count
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
