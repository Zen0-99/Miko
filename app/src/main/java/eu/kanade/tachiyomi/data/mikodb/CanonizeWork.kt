package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.domain.ui.model.Carrier
import logcat.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Canonize orchestration (Phase 13-02) — on canonical entry open:
 * serve the cached sequence via [CanonicalChapterMap] (SWR), and when
 * stale, collect verified feeds → POST `/v1/chapters/canonize` →
 * refresh.
 *
 * Gate rules:
 *  - Fresh cache → do nothing (the caller already rendered it).
 *  - Zero ACCEPT feeds → never POST (a tentative-only result is not
 *    trustworthy enough to rebuild canon; the entry falls back to the
 *    bound-source chapter list).
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

        /** Canonize is chapter-shaped — never runs on shows (locked
         *  ruling, 15-CONTEXT). Guarded here at the entry rather than
         *  relying on caller discipline. */
        data object UnsupportedMedium : Outcome
    }

    suspend fun run(workId: String, carrier: Carrier = Carrier.MANGA): Outcome {
        if (carrier == Carrier.SHOWS) return Outcome.UnsupportedMedium
        if (!chapterMap.isStale(workId)) return Outcome.Fresh
        val work = bridge.byWorkId(workId) ?: return Outcome.NoWork.also {
            logcat { "canonize $workId: no work row" }
        }

        val r = collector.collect(work, carrier.medium)
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

        val resp = api.canonize(
            WorkIdentityApi.CanonizeRequest(work_id = workId, feeds = feeds),
        ) ?: return Outcome.PostFailed.also {
            logcat { "canonize $workId: POST failed (${feeds.size} feeds)" }
        }

        chapterMap.invalidate(workId)
        val bestDetails = r.feeds.filter { it.details != null && !it.details.isEmpty }
            .maxByOrNull { it.score }?.details
        logcat { "canonize $workId: ${resp.count} chapters (${r.tentative.size} tentative, feeds=${r.feeds.map { it.pkgName }})" }
        return Outcome.Canonized(resp.count, r.tentative.size, bestDetails)
    }

    private fun CanonicalFeedCollector.Feed.wire(verified: Boolean) =
        WorkIdentityApi.CanonizeFeed(
            source_key = pkgName,
            tier = tier,
            verified = verified,
            score = score,
            chapters = chapters,
        )

    private companion object {
        const val MAX_FAILURES = 2
    }
}
