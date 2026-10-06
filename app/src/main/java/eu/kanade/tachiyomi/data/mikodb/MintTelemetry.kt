package eu.kanade.tachiyomi.data.mikodb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import logcat.logcat
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

/**
 * Mint-outcome telemetry (Phase 20, NCM-014) — per-provider-pkg
 * served/failed counters that accumulate locally and ride OUTBOUND on
 * the next existing MikoDB request (`telemetry` field on
 * resolve/canonize/canonizeEpisodes/ids-seen). Zero dedicated requests
 * is the locked constraint — there is no flush entry point anywhere.
 *
 * Semantics mirror [SeenReporter]: an in-memory [ConcurrentHashMap]
 * bucket covers the fast path, every outcome is persisted to
 * `mint_outcomes` before it can reach the wire (persist-before-POST —
 * a crash loses nothing already counted), and [drain] merges persisted
 * rows with still-in-flight deltas into a batch bounded at
 * [MAX_ENTRIES]. [commitFlush] clears the persisted rows ONLY after
 * the carrier request returned success — a failed carrier keeps them
 * for the next one.
 *
 * The counters are observational — the worker folds them into
 * `provider_stats.served_count`/`failed_count` diagnostics, never
 * lifecycle math — so small races resolve toward noise, not
 * correctness: a persist straggling the commit window may double-send
 * its delta once, and a persist failure drops it (logged).
 */
class MintTelemetry {

    private val handler: MangaDatabaseHandler by injectLazy()

    /** Outlives callers — persist launches never die with a screen. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Per-pkg deltas `[served, failed]` whose SQLite write hasn't
     *  completed yet. Merged onto the persisted rows at [drain] so a
     *  just-recorded outcome can't slip between the table write and
     *  the request body; decremented when the write lands. */
    private val pending = ConcurrentHashMap<String, LongArray>()

    /** A chapter/episode was successfully routed through [pkg]'s
     *  binding (a [CanonicalChapterRouter] resolve hit). */
    fun recordServed(pkg: String) = record(pkg, served = true)

    /** A carrier serve attempt failed, or a canonize POST carrying
     *  [pkg] failed. */
    fun recordFailed(pkg: String) = record(pkg, served = false)

    private fun record(pkg: String, served: Boolean) {
        val bucket = pending.computeIfAbsent(pkg) { LongArray(2) }
        synchronized(bucket) {
            if (served) bucket[0]++ else bucket[1]++
        }
        // persist-before-POST — fire-and-forget, same idiom as
        // SeenReporter.report: a crash between record and drain keeps
        // every already-launched write.
        scope.launch {
            runCatching {
                handler.await(true) {
                    mint_outcomesQueries.insertPkg(pkg)
                    if (served) {
                        mint_outcomesQueries.incrementServed(pkg)
                    } else {
                        mint_outcomesQueries.incrementFailed(pkg)
                    }
                }
            }.onFailure { logcat { "mintTelemetry persist $pkg: ${it.message}" } }
            synchronized(bucket) {
                if (served) bucket[0]-- else bucket[1]--
            }
        }
    }

    /**
     * Merge persisted + in-flight deltas into the next request's
     * `telemetry` field — [MAX_ENTRIES] at most, overflow dropped (the
     * queue is observational, never a correctness signal). Null when
     * nothing is pending so the field serializes absent.
     */
    suspend fun drain(): List<WorkIdentityApi.TelemetryEntry>? {
        val rows = runCatching {
            handler.awaitList { mint_outcomesQueries.drainAll() }
        }.getOrElse { emptyList() }
        val merged = mutableMapOf<String, WorkIdentityApi.TelemetryEntry>()
        for (row in rows) {
            merged[row.pkg_name] =
                WorkIdentityApi.TelemetryEntry(row.pkg_name, row.served, row.failed)
        }
        for ((pkg, bucket) in pending) {
            val served: Long
            val failed: Long
            synchronized(bucket) {
                served = bucket[0]
                failed = bucket[1]
            }
            if (served > 0 || failed > 0) {
                val existing = merged[pkg]
                merged[pkg] = WorkIdentityApi.TelemetryEntry(
                    pkg,
                    (existing?.served ?: 0) + served,
                    (existing?.failed ?: 0) + failed,
                )
            }
        }
        if (merged.isEmpty()) return null
        val entries = merged.values.toList()
        if (entries.size > MAX_ENTRIES) {
            logcat {
                "mintTelemetry drain: ${entries.size} entries > " +
                    "$MAX_ENTRIES — dropping overflow"
            }
        }
        return entries.take(MAX_ENTRIES)
    }

    /**
     * The carrier request carrying a [drain] batch returned success —
     * drop the persisted queue rows. Called ONLY on carrier success; a
     * failed request leaves the rows for the next one.
     */
    suspend fun commitFlush() {
        runCatching {
            handler.await(true) { mint_outcomesQueries.clearRows() }
        }.onFailure { logcat { "mintTelemetry commitFlush: ${it.message}" } }
    }

    companion object {
        /** Mirrors the worker's `telemetry.max_entries` ruleset bound —
         *  hardcoded locally; a ruleset bump just accepts whatever the
         *  client sends. */
        const val MAX_ENTRIES = 50
    }
}
