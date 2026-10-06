package eu.kanade.tachiyomi.data.comments

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.tachiyomi.data.mikodb.CanonicalEpisodeMap
import eu.kanade.tachiyomi.data.mikodb.CanonizeWork
import eu.kanade.tachiyomi.data.mikodb.EpisodeCoverage
import eu.kanade.tachiyomi.data.mikodb.MikoDbManager
import eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Chapter/episode → canonical thread ID (NCM-002).
 *
 * Shows episodes bind canonical identity when the work's
 * `canonical_episodes` sequence is loaded (19-04, D-06): mains thread
 * under `shows:<workId>:e<abs>`, specials/NC under
 * `shows:<workId>:s<season>e<ep>`. Canon absent, offline, or stale →
 * the legacy `e<raw>` key, so episode threads stay usable.
 *
 * Manga/books chapters go through [ChapterBinder] gated on the
 * per-(work, source) alignment verdict from `/v1/chapters/align`:
 * `misaligned` → null (chapter comments hidden for that source; the
 * work-level thread is unaffected). `unknown`/offline/no-verdict →
 * allowed.
 */
class ChapterThreadResolver(
    private val api: CommentsApi = Injekt.get(),
    private val handler: MangaDatabaseHandler = Injekt.get(),
    private val episodeMap: CanonicalEpisodeMap = Injekt.get(),
    private val mikoDbManager: MikoDbManager = Injekt.get(),
    private val identityApi: WorkIdentityApi = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) {

    private val alignmentPending = ConcurrentHashMap.newKeySet<String>()

    /**
     * Thread ID for a shows episode, or null when the episode number is
     * unusable. [season] is the Cinemeta "Season N" carrier (0 =
     * specials); null means absolute numbering.
     *
     * Alignment mirrors [chapter]: when a [sourceKey] and the carrier's
     * episode list are available, the list is fingerprinted (numbers
     * canonicalized into abs space when the bound artifact entry proves
     * the mapping) and posted on a stale `chapter_alignment` verdict.
     * Unlike chapters the verdict is advisory — a `misaligned` verdict
     * still binds, because episode threads are display-only and the
     * recorded verdict feeds coverage/routing signals (DECIDED §7).
     */
    suspend fun episode(
        workId: String,
        season: Int?,
        episodeNumber: Float,
        sourceKey: String? = null,
        title: String? = null,
        episodes: List<Pair<String, Float>> = emptyList(),
    ): String? {
        if (episodeNumber <= 0f) return null

        val canon = episodeMap.forWork(workId)
        val bound = canon.takeIf { it.isNotEmpty() }
            ?.let { list -> bindCanonEpisode(list, season, episodeNumber) }
            ?.let {
                ChapterBinder.bindEpisodeCanon(
                    workId, it.kind, it.abs, it.season, it.epInSeason, it.number,
                )
            }
            ?: ChapterBinder.bindEpisode(workId, episodeNumber)

        if (!sourceKey.isNullOrBlank()) {
            val fingerprint = ChapterFingerprint.of(episodes)
            if (fingerprint.count > 0) {
                maybeCheckAlignment(
                    Carrier.SHOWS, workId, sourceKey, title,
                    canonicalizeForShowsAlign(workId, fingerprint),
                )
            }
        }
        return bound
    }

    /** Season-carrying refs bind strictly on (season, ep_in_season) —
     *  [CanonicalEpisodeMap.bind]'s abs fallback must not fire: a
     *  "Season 2" local numbered 5 is canonical abs ~29, not 5. */
    private fun bindCanonEpisode(
        canon: List<CanonicalEpisodeMap.CanonEpisode>,
        season: Int?,
        episodeNumber: Float,
    ): CanonicalEpisodeMap.CanonEpisode? {
        val n = episodeNumber.toDouble()
        return if (season != null) {
            canon.firstOrNull { it.season == season && it.epInSeason?.toDouble() == n }
        } else {
            episodeMap.bind(1, n, canon)
        }
    }

    /** Rewrites the fingerprint's numbers into canonical abs space
     *  when the preferred carrier's stored binding proves the artifact
     *  entry (D-05); returns the raw fingerprint unchanged otherwise. */
    private suspend fun canonicalizeForShowsAlign(
        workId: String,
        fingerprint: ChapterFingerprint,
    ): ChapterFingerprint {
        if (!workId.startsWith(CanonizeWork.IMDB_WORK_PREFIX)) return fingerprint
        val pkg = sourcePreferences.preferredCarrierFor(workId) ?: return fingerprint
        val bridge = mikoDbManager.bridge() ?: return fingerprint
        val ref = runCatching { identityApi.getBindings(workId) }.getOrNull()
            ?.bindings?.firstOrNull { it.source_key == pkg }?.provider_ref
            ?: return fingerprint
        val entry = EpisodeCoverage.boundEntry(
            bridge.byImdb(workId.removePrefix(CanonizeWork.IMDB_WORK_PREFIX)),
            ref,
            mikoDbManager.sourceMeta(pkg)?.idType,
        ) ?: return fingerprint
        val anidb = entry.anidbId ?: return fingerprint
        val abs = EpisodeCoverage.canonicalizeNumbers(
            fingerprint.numbers.map { it.toDouble() },
            bridge.episodesForAnidb(anidb),
        )
        if (abs.isEmpty()) return fingerprint
        val numbers = fingerprint.numbers
            .mapNotNull { abs[it.toDouble()]?.toFloat() }
            .sorted()
        return fingerprint.copy(numbers = numbers, count = numbers.size)
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
        title: String?,
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
