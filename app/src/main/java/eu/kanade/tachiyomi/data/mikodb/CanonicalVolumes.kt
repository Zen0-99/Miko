package eu.kanade.tachiyomi.data.mikodb

import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-work volume covers + chapter→volume boundaries (Phase 21-03 /
 * NCM-016). The worker's `GET /v1/works/:id/volumes` response is cached
 * in `canonical_volumes`/`canonical_volume_map`/`canonical_volume_meta`
 * so a work is fetched at most once per device — a `canonical_volume_meta`
 * row is written even for empty responses, so unmapped works never
 * re-request. Boundary confidence below [MIN_CONFIDENCE] keeps the
 * chapter list flat.
 */
class CanonicalVolumes(
    private val api: WorkIdentityApi = Injekt.get(),
) {

    private val handler: MangaDatabaseHandler by injectLazy()

    /** Single-flight per work — concurrent detail opens share one fetch. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    data class VolumeMeta(
        val volumeNo: Double,
        val coverUrl: String?,
        val coverThumbUrl: String?,
        val coverBlurhash: String?,
        val pages: Long?,
    )

    data class WorkVolumes(
        /** Mean boundary confidence reported by the worker; null = unmapped. */
        val boundaryConfidence: Double?,
        /** chapter number → volume number */
        val boundaries: Map<Double, Double>,
        /** volume_no → displayable cover/meta (en preferred, then ja). */
        val covers: Map<Double, VolumeMeta>,
    ) {
        /** Only high-confidence, non-empty maps may drive grouping. */
        fun groupable(): Boolean =
            boundaries.isNotEmpty() && (boundaryConfidence ?: 0.0) >= MIN_CONFIDENCE

        /**
         * Pill-style grouping for [chapterNumbers] (the visible list's
         * parsed chapter numbers). Volumes present among the chapters
         * sort ascending; chapters the map doesn't cover land in
         * [ungroupedLabel] appended last — the honest tail, never an
         * "unknown volume" bucket. Null when the map isn't groupable
         * or no visible chapter maps anywhere.
         */
        fun grouping(chapterNumbers: List<Double>, ungroupedLabel: String): Grouping? {
            if (!groupable()) return null
            val vols = chapterNumbers
                .mapNotNull { boundaries[it] }
                .distinct()
                .sorted()
            if (vols.isEmpty()) return null
            val labels = vols.map(::volumeLabel)
            val hasUnmapped = chapterNumbers.any { it !in boundaries }
            return Grouping(
                groups = if (hasUnmapped) labels + ungroupedLabel else labels,
                chapterToGroup = chapterNumbers
                    .mapNotNull { n -> boundaries[n]?.let { n to volumeLabel(it) } }
                    .toMap(),
                meta = vols.associate { volumeLabel(it) to (covers[it] ?: VolumeMeta(it, null, null, null, null)) },
            )
        }

        companion object {
            /** Pill/header label for a volume number — matches the
             *  "Volume N" shape ChapterNameGroup emits for marked names. */
            fun volumeLabel(volumeNo: Double): String =
                "Volume " + if (volumeNo % 1.0 == 0.0) volumeNo.toInt().toString() else volumeNo.toString()
        }
    }

    data class Grouping(
        /** Ordered pill labels — volumes ascending, then the ungrouped tail. */
        val groups: List<String>,
        /** chapter number → group label (absent = ungrouped tail). */
        val chapterToGroup: Map<Double, String>,
        /** group label → cover/pages (always present for volume labels). */
        val meta: Map<String, VolumeMeta>,
    )

    /** Cache-only read — null when the work was never fetched. */
    suspend fun peek(workId: String): WorkVolumes? {
        val meta = handler.awaitOneOrNull {
            canonical_volumesQueries.metaForWork(workId)
        } ?: return null
        return load(workId, meta.boundary_confidence)
    }

    /**
     * Fetch once per work and persist. Silent on transport failure —
     * the meta row is only written on a parsed response so an offline
     * open doesn't burn the fetch-once marker. A worker miss also lands
     * a meta row (empty volumes + null confidence) so unmapped works
     * never re-request.
     */
    suspend fun ensureFetched(workId: String): WorkVolumes? {
        if (!inFlight.add(workId)) return peek(workId)
        try {
            val cached = handler.awaitOneOrNull {
                canonical_volumesQueries.metaForWork(workId)
            }
            if (cached != null) {
                return load(workId, cached.boundary_confidence)
            }
            val response = api.getVolumes(workId) ?: return null
            handler.await(true) {
                canonical_volumesQueries.deleteWorkVolumes(workId)
                canonical_volumesQueries.deleteWorkBoundaries(workId)
                for (v in response.volumes) {
                    canonical_volumesQueries.insertVolume(
                        workId = workId,
                        volumeNo = v.volume_no,
                        language = v.language,
                        coverUrl = v.cover_url,
                        coverThumbUrl = v.cover_thumb_url,
                        coverBlurhash = v.cover_blurhash,
                        isbn = v.isbn,
                        releaseDate = v.release_date,
                        pages = v.pages?.toLong(),
                        source = v.source,
                    )
                }
                for ((chapter, volumeNo) in response.boundaries) {
                    val ch = chapter.toDoubleOrNull() ?: continue
                    canonical_volumesQueries.insertBoundary(
                        workId = workId,
                        chapter = ch,
                        volumeNo = volumeNo,
                        confidence = response.boundary_confidence ?: 0.0,
                        scheme = null,
                        sources = "[]",
                    )
                }
                canonical_volumesQueries.upsertMeta(
                    workId = workId,
                    boundaryConfidence = response.boundary_confidence,
                    finalVolume = response.final_volume,
                    coversCount = (response.covers_count ?: response.volumes.size).toLong(),
                    fetchedAt = System.currentTimeMillis(),
                )
            }
            return load(workId, response.boundary_confidence)
        } finally {
            inFlight.remove(workId)
        }
    }

    private suspend fun load(workId: String, boundaryConfidence: Double?): WorkVolumes {
        val volRows = handler.awaitList {
            canonical_volumesQueries.volumesForWork(workId)
        }
        val boundRows = handler.awaitList {
            canonical_volumesQueries.boundariesForWork(workId)
        }
        val covers = volRows
            .sortedWith(compareBy({ it.volume_no }, { languageRank(it.language) }))
            .groupBy { it.volume_no }
            .mapValues { (_, rows) ->
                val best = rows.first()
                VolumeMeta(
                    volumeNo = best.volume_no,
                    coverUrl = best.cover_url,
                    coverThumbUrl = best.cover_thumb_url ?: best.cover_url,
                    coverBlurhash = best.cover_blurhash,
                    pages = best.pages,
                )
            }
        return WorkVolumes(
            boundaryConfidence = boundaryConfidence,
            boundaries = boundRows.associate { it.chapter to it.volume_no },
            covers = covers,
        )
    }

    private fun languageRank(language: String): Int = when (language.lowercase()) {
        "en" -> 0
        "ja" -> 1
        else -> 2
    }

    companion object {
        /** Worker-reported mean boundary confidence floor for grouping. */
        const val MIN_CONFIDENCE = 0.5
    }
}
