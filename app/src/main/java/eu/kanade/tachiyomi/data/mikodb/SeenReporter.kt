package eu.kanade.tachiyomi.data.mikodb

import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent once-per-install gate in front of [WorkIdentityApi.markSeen]
 * (Phase 15-07 follow-up — KV-cost hygiene).
 *
 * `/v1/ids/seen` is a D1 write per call. The mint-on-browse callers used
 * to dedupe with an in-memory set, so every app restart re-reported every
 * rendered catalog row. This reporter keeps the dedupe in the
 * `seen_reported` table: an id is POSTed at most once per install
 * lifetime, and the in-memory set short-circuits even the local SELECT.
 *
 * Scope: `/v1/ids/seen` exists to rank *uncatalogued* works for
 * promotion — seeded rows (`mk:mb:`/`mk:hs:`) carry no coverage signal,
 * so [report] no-ops on them. `mk:m:` (local mints), `mk:ol:` and
 * `mk:imdb:` (fresh server-side mints) are the ids worth reporting.
 *
 * Backup note: `seen_reported` is not in the backup protobuf; a restore
 * re-reports each rendered work once — harmless (idempotent `seen_count`)
 * and arguably correct, since a reinstall is renewed interest.
 */
class SeenReporter {

    private val handler: MangaDatabaseHandler by injectLazy()
    private val api: WorkIdentityApi by injectLazy()

    /** Session fast-path in front of the persisted table. */
    private val reported = ConcurrentHashMap.newKeySet<String>()

    /**
     * True when this id was reported on a previous (or this) session —
     * callers can use it to skip the *resolve* that precedes a report,
     * not just the write. Does not mark anything.
     */
    suspend fun wasReported(workId: String): Boolean {
        if (workId in reported) return true
        val persisted = (handler.awaitOneOrNull {
            seen_reportedQueries.existsReported(workId)
        } ?: 0L) != 0L
        if (persisted) reported.add(workId)
        return persisted
    }

    /** True when the work id is worth a coverage report — seeded catalog
     *  ids (`mk:mb:`/`mk:hs:`) are already tracked server-side. */
    fun isReportable(workId: String): Boolean = !workId.startsWith("mk:mb:") &&
        !workId.startsWith("mk:hs:")

    /**
     * Fire the `/v1/ids/seen` report iff this id was never reported on
     * this install. The row is persisted BEFORE the POST so a crashed
     * request can't loop-retry — one lost report is acceptable telemetry
     * noise, a write loop is not.
     *
     * @return true if a report was attempted (false = deduped/skipped).
     */
    suspend fun report(workId: String, medium: String): Boolean {
        if (!isReportable(workId)) return false
        if (!reported.add(workId)) return false
        val alreadyReported = (handler.awaitOneOrNull {
            seen_reportedQueries.existsReported(workId)
        } ?: 0L) != 0L
        if (alreadyReported) return false
        handler.await {
            seen_reportedQueries.insertReported(workId, medium, System.currentTimeMillis())
        }
        api.markSeen(workId, medium)
        return true
    }
}
