package eu.kanade.tachiyomi.data.track.abs

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.novel.abs.AbsClient
import eu.kanade.tachiyomi.source.novel.abs.AbsProgressPatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.system.logcat
import kotlin.math.abs

/**
 * Item-level Audiobookshelf progress sync (22-04) — the RESEARCH §7.2 merge
 * implemented verbatim as a pure state machine over the injected
 * [AbsClient] transport.
 *
 * Store shape (SD-1, locked): the [pref] string is a JSON
 * `Map<"{serverKey}|{itemId}", ItemProgress>` — positions and timestamps
 * only, never the token (T-22-04-03). Chapter-level resume entries stay in
 * `pref_novel_audiobook_positions`, untouched; this row is the server-sync
 * shadow that follows the item, not the chapter list.
 *
 * Merge policy (RESEARCH §7.2 / plan SD-3, implemented verbatim in [pull]):
 *  - local `dirty` ⇒ local wins (the server never saw it) → push it.
 *  - remote absent or `remote.lastUpdate <= local.lastUpdate` ⇒ keep local.
 *  - |remote − local| position < 10 s ⇒ no-op.
 *  - `remote.lastUpdate` only "newer" within ±30 s timestamp equality ⇒
 *    ambiguous — keep local; the dirty flag, not the clock, is the
 *    skew-proof authority (SD-8, Lissen's lesson).
 *  - otherwise ⇒ adopt remote, return it as the resume seed, and arm the
 *    push guard (SD-6) so a freshly-restored position is never echoed
 *    straight back (Lissen #497 stale-clobber protection).
 *
 * Sync NEVER blocks or fails playback: every entry point degrades to
 * no-op when the client is unconfigured, transport failures log a warning
 * and leave `dirty` set for the next beat.
 */
class AbsProgressSyncer(
    private val clientProvider: () -> AbsClient?,
    private val pref: Preference<String>,
    internal var nowMs: () -> Long = { System.currentTimeMillis() },
) {

    /** One pref-map row per `(serverKey, itemId)` (SD-1). */
    @Serializable
    data class ItemProgress(
        val itemGlobalMs: Long,
        val lastUpdateMs: Long,
        val dirty: Boolean,
    )

    /**
     * Per-track playback geometry for one ABS item — `(ino, startOffsetMs,
     * durationMs)` from `media.tracks` of the already-fetched AbsItem
     * (SD-2: the item is fetched once at playlist build; the syncer never
     * fetches). `itemGlobalMs = startOffsetMs + positionInTrackMs`.
     */
    data class TrackCtx(
        val ino: String,
        val startOffsetMs: Long,
        val durationMs: Long,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Serializes the push path — a pause-flush racing the 30 s heartbeat or
     * a destroy-flush must not double-PATCH or interleave a read/write.
     */
    private val flushMutex = Mutex()

    /**
     * Push guard (SD-6): pref key → suppress PATCHes until this epoch ms.
     * In-memory only — the 5 s window needn't survive a process restart.
     */
    private val justAdoptedUntilMs = mutableMapOf<String, Long>()

    // ---- store --------------------------------------------------------------

    private fun key(client: AbsClient, itemId: String) = "${client.serverKey()}|$itemId"

    private fun rows(): MutableMap<String, ItemProgress> =
        runCatching { json.decodeFromString<Map<String, ItemProgress>>(pref.get()) }
            .getOrDefault(emptyMap())
            .toMutableMap()

    private fun row(client: AbsClient, itemId: String): ItemProgress? =
        rows()[key(client, itemId)]

    private fun put(key: String, row: ItemProgress) {
        val map = rows()
        map[key] = row
        pref.set(json.encodeToString(map))
    }

    // ---- merge API ------------------------------------------------------------

    /**
     * Pull-on-open / playlist-build merge (§7.2 verbatim). Fetches
     * `GET /api/me/progress/{itemId}`, applies the merge above, and returns
     * the adopted item-global resume seed (ms) — or null when there's
     * nothing to adopt. A dirty local pushes its position inline instead.
     */
    suspend fun pull(itemId: String, tracks: List<TrackCtx>): Long? {
        val client = clientProvider() ?: return null
        val key = key(client, itemId)
        val remote = try {
            client.getProgress(itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "ABS progress pull failed for $itemId: ${e.message}" }
            null
        }
        val local = row(client, itemId)

        if (local?.dirty == true) {
            // Local truth the server never saw — local wins; push it. The
            // push guard applies here too (§7.2: "suppress the next
            // outbound PATCH") — a pull inside the adopt window must not
            // echo the just-restored position back.
            flushMutex.withLock {
                val current = row(client, itemId)?.takeIf { it.dirty }
                if (current != null && nowMs() >= (justAdoptedUntilMs[key] ?: 0L)) {
                    pushLocked(client, itemId, key, current, tracks)
                }
            }
            return null
        }

        if (remote == null) return null
        val remoteMs = (remote.currentTime * 1000).toLong().coerceAtLeast(0)
        val localMs = local?.itemGlobalMs ?: 0L
        val localTs = local?.lastUpdateMs ?: 0L
        val tsDiff = remote.lastUpdate - localTs
        if (tsDiff <= 0) return null // remote not newer than what we already have
        if (abs(remoteMs - localMs) < DIFF_IGNORE_MS) return null // <10 s — same place, skip
        if (local != null && tsDiff <= SKEW_MS) return null // equal-ish clocks — keep local

        // Adopt: row lands clean carrying the SERVER's lastUpdate so the
        // next comparison stays in the server's clock domain (§7.2).
        put(key, ItemProgress(remoteMs, remote.lastUpdate, dirty = false))
        justAdoptedUntilMs[key] = nowMs() + PUSH_GUARD_MS

        // Server-finished → adopt the row but offer no seed; the book
        // restarts rather than resuming onto its own tail.
        if (remote.isFinished) return null
        val end = totalMs(tracks)
        return if (end > 0) remoteMs.coerceIn(0, end) else remoteMs
    }

    /**
     * Heartbeat beat (§7.2 write path): the item-global position
     * (`track.startOffsetMs + positionInTrackMs`, SD-2) lands dirty
     * immediately — local row writes never touch the network and cannot
     * fail playback.
     */
    fun onPosition(itemId: String, itemGlobalMs: Long) {
        val client = clientProvider() ?: return
        put(key(client, itemId), ItemProgress(itemGlobalMs.coerceAtLeast(0), nowMs(), dirty = true))
    }

    /**
     * Push the dirty row for [itemId] — the 30 s heartbeat cadence and
     * every lifecycle boundary (pause/stop/track-boundary/destroy) funnel
     * here (SD-5). No-ops when unconfigured, clean, inside the push-guard
     * window, or missing duration context; serialized against concurrent
     * flushes. A failed push keeps `dirty` — next beat retries (§9 #11).
     */
    suspend fun flush(itemId: String, tracks: List<TrackCtx>) {
        val client = clientProvider() ?: return
        val key = key(client, itemId)
        flushMutex.withLock {
            val row = row(client, itemId)
            // Push guard (SD-6): inside the adopt window the PATCH is
            // skipped and the row KEEPS dirty — the first beat after the
            // window pushes the accrued position (never the echoed one).
            if (row != null && row.dirty && nowMs() >= (justAdoptedUntilMs[key] ?: 0L)) {
                pushLocked(client, itemId, key, row, tracks)
            }
        }
    }

    /**
     * Fire-and-forget flush for teardown paths that can't suspend —
     * [AudiobookPlaybackService.onDestroy] hands its final push here. The
     * job lands on [scope] (the controller's session scope outlives the
     * service) and serializes through the same mutex, so a destroy racing
     * a heartbeat never double-pushes.
     */
    fun flushAll(scope: CoroutineScope, itemId: String?, tracks: List<TrackCtx>) {
        if (itemId == null) return
        scope.launch { flush(itemId, tracks) }
    }

    /**
     * `PATCH /api/me/progress/{itemId}` for a dirty row. On success the row
     * goes clean with the server's echoed `lastUpdate` (shared merge clock);
     * a 404 falls back once to `batch/update` (SD-4); anything else leaves
     * `dirty` set — offline degrade, never a playback failure.
     */
    private suspend fun pushLocked(
        client: AbsClient,
        itemId: String,
        key: String,
        row: ItemProgress,
        tracks: List<TrackCtx>,
    ) {
        val endMs = totalMs(tracks)
        if (endMs <= 0) return // no duration ctx yet — keep dirty for a later beat
        val finished = isFinished(row.itemGlobalMs, tracks)
        val durationSec = endMs / 1000.0
        val currentTimeSec = ((if (finished) endMs else row.itemGlobalMs) / 1000.0)
            .coerceIn(0.0, durationSec)
        val patch = AbsProgressPatch(
            currentTime = currentTimeSec,
            duration = durationSec,
            progress = if (finished) 1.0 else (currentTimeSec / durationSec).coerceIn(0.0, 1.0),
            isFinished = finished,
            hideFromContinueListening = false,
        )
        try {
            val echoed = client.patchProgress(itemId, patch)
            put(
                key,
                row.copy(
                    lastUpdateMs = echoed?.lastUpdate?.takeIf { it > 0 } ?: nowMs(),
                    dirty = false,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code == 404) {
                // SD-4 fallback — older ABS versions only expose the batch shape.
                try {
                    client.patchProgressBatch(itemId, patch)
                    put(key, row.copy(lastUpdateMs = nowMs(), dirty = false))
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    logcat(LogPriority.WARN) { "ABS batch progress push failed for $itemId: ${e2.message}" }
                }
            } else {
                logcat(LogPriority.WARN) { "ABS progress push failed for $itemId: HTTP ${e.code}" }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "ABS progress push failed for $itemId: ${e.message}" }
        }
    }

    // ---- helpers --------------------------------------------------------------

    companion object {
        /** |remote − local| position diff below this is ignored (§7.2). */
        const val DIFF_IGNORE_MS = 10_000L

        /** Push-guard window after a remote adoption (SD-6). */
        const val PUSH_GUARD_MS = 5_000L

        /** Finished threshold — position within this of the item's end (SD-7). */
        const val FINISHED_WITHIN_MS = 2_000L

        /** Timestamp-equality tolerance — the ±30 s clock-skew guard (SD-8). */
        const val SKEW_MS = 30_000L

        /** Total item duration = end of the last track in the ordered list. */
        fun totalMs(tracks: List<TrackCtx>): Long =
            tracks.maxOfOrNull { it.startOffsetMs + it.durationMs } ?: 0L

        /** SD-7: within 2 s of the final track's end counts as finished. */
        fun isFinished(itemGlobalMs: Long, tracks: List<TrackCtx>): Boolean {
            val endMs = totalMs(tracks)
            return endMs > 0 && itemGlobalMs >= endMs - FINISHED_WITHIN_MS
        }

        /**
         * itemGlobalMs → (track ctx, offset within that track) — the reverse
         * of `startOffsetMs + positionInTrackMs` (SD-2). The returned offset
         * is clamped into the track's duration.
         */
        fun trackIndexFor(itemGlobalMs: Long, tracks: List<TrackCtx>): Pair<TrackCtx, Long>? {
            val ordered = tracks.sortedBy { it.startOffsetMs }
            val ctx = ordered.firstOrNull { itemGlobalMs < it.startOffsetMs + it.durationMs }
                ?: ordered.lastOrNull()
                ?: return null
            return ctx to (itemGlobalMs - ctx.startOffsetMs).coerceIn(0L, ctx.durationMs)
        }
    }
}
