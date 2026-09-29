package eu.kanade.tachiyomi.data.comments

import eu.kanade.domain.ui.model.Carrier
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Chapter/episode → canonical thread ID (NCM-002).
 *
 * Shows episodes bind directly (`shows:<workId>:e<N>`) — episode identity
 * is already canonical, no alignment gate. Manga/books chapters go through
 * [ChapterBinder] gated on the per-(work, source) alignment verdict from
 * `/v1/chapters/align`: `misaligned` → null (chapter comments hidden for
 * that source; the work-level thread is unaffected). `unknown`/offline/
 * no-verdict → allowed.
 */
class ChapterThreadResolver(
    private val api: CommentsApi = Injekt.get(),
    private val handler: MangaDatabaseHandler = Injekt.get(),
) {

    private val alignmentPending = ConcurrentHashMap.newKeySet<String>()

    /**
     * Thread ID for a shows episode, or null when the episode number is
     * unusable. No alignment check — episode numbers come from Cinemeta
     * (canonical), not from sources.
     */
    fun episode(workId: String, episodeNumber: Float): String? {
        if (episodeNumber <= 0f) return null
        return ChapterBinder.bindEpisode(workId, episodeNumber)
    }

    /**
     * Thread ID for a manga/books chapter, or null when unresolvable or
     * the source is misaligned for this work. [chapters] is the source's
     * full chapter list — used to build the alignment fingerprint on
     * first sight (fire-and-forget; the verdict applies next time).
     */
    suspend fun chapter(
        carrier: Carrier,
        workId: String,
        sourceKey: String,
        title: String,
        chapterName: String,
        chapterNumber: Float,
        chapters: List<Pair<String, Float>>,
    ): String? {
        val fingerprint = ChapterFingerprint.of(chapters)
        if (fingerprint.count == 0) return null

        // Known-bad source → no chapter threads.
        if (isMisaligned(workId, sourceKey)) return null

        val bound = ChapterBinder.bind(carrier, workId, chapterName, chapterNumber)
            ?: return null

        // Kick off a background alignment check when we haven't seen this
        // exact (work, source, fingerprint) before. Optimistic: unknown
        // verdicts allow threads (first source defines the baseline).
        maybeCheckAlignment(carrier, workId, sourceKey, title, fingerprint)
        return bound
    }

    private suspend fun isMisaligned(workId: String, sourceKey: String): Boolean =
        handler.awaitOneOrNull {
            works_cacheQueries.latestAlignment(workId, sourceKey)
        }?.status == "misaligned"

    private suspend fun maybeCheckAlignment(
        carrier: Carrier,
        workId: String,
        sourceKey: String,
        title: String,
        fingerprint: ChapterFingerprint,
    ) {
        val fpHash = fingerprintHash(fingerprint)
        val cacheKey = "$workId|$sourceKey|$fpHash"
        val cached = handler.awaitOneOrNull {
            works_cacheQueries.selectAlignment(workId, sourceKey, fpHash)
        }
        if (cached != null || !alignmentPending.add(cacheKey)) return

        val result = api.alignChapters(
            workId = workId,
            sourceKey = sourceKey,
            carrier = carrier,
            title = title,
            fingerprint = fingerprint,
        )
        if (result is CommentsApi.Result.Ok) {
            val v = result.value
            handler.await(true) {
                works_cacheQueries.upsertAlignment(
                    workId,
                    sourceKey,
                    fpHash,
                    v.status,
                    v.score,
                    v.reference,
                    System.currentTimeMillis(),
                )
            }
        }
        alignmentPending.remove(cacheKey)
    }

    // Must match worker/src/align.ts: sha256("num1,num2|namesHash") —
    // JS joins floats without trailing ".0", so format each number.
    private fun fingerprintHash(fp: ChapterFingerprint): String =
        DeviceIdentityProvider.sha256Hex(
            "${fp.numbers.joinToString(",") { ChapterBinder.formatNumber(it) }}|${fp.namesHash}",
        )
}
