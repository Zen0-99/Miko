package eu.kanade.tachiyomi.data.mikodb

import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Canonical episode map (Phase 19-03, D-04a) — fetches a shows work's
 * canonical episode sequence from the comments Worker's
 * `GET /v1/works/{id}/episodes`, caches it SWR, and binds
 * (season, episode)/absolute coordinates onto canonical rows.
 *
 * The sibling of [CanonicalChapterMap] — same LRU+staleness shape,
 * episode vocabulary. `kind` is `episode|special|nc` verbatim from the
 * worker (never coerced: specials/NC must not bind like main rows).
 * This is the canonical identity D-06's per-episode comment keys and
 * 19-04's thread binding/hydration resolve against.
 */
class CanonicalEpisodeMap(
    private val api: WorkIdentityApi = Injekt.get(),
) {

    data class CanonEpisode(
        val seq: Int,
        /** 'episode' | 'special' | 'nc' — worker vocabulary. */
        val kind: String,
        /** Kind-scoped doc-local number (artifact or carrier-merged). */
        val number: Double?,
        /** Canonical absolute episode number — null on carrier-only
         *  rows until 19-04 canonicalization. */
        val abs: Int?,
        val season: Int?,
        val epInSeason: Int?,
        /** Artifact ep_key verbatim — "1", "S3", "C2". */
        val epKey: String?,
        /** Artifact doc namespace — "anidb:1234". */
        val doc: String?,
        val label: String,
        val airDate: String?,
        /** Canonical air_date as epoch ms, else earliest feed date. */
        val dateUpload: Long?,
        val image: String?,
        val overview: String?,
        val runtime: Int?,
        val anidbEid: Long?,
        val tvdbEid: Long?,
        val tvdbShowId: Long?,
        val filler: Boolean,
        val confidence: String,
        val sources: List<String>,
        val refs: Map<String, String>,
    )

    private companion object {
        const val CACHE_LIMIT = 64

        /** SWR window — mirrors [CanonicalChapterMap.STALE_AFTER_MS]:
         * a cached sequence older than this re-canonizes in the
         * background while still serving immediately. */
        const val STALE_AFTER_MS = 24L * 60 * 60 * 1000
    }

    /** Process-level LRU of canonical lists keyed by work id. */
    private val cache = object : LinkedHashMap<String, List<CanonEpisode>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<CanonEpisode>>?) =
            size > CACHE_LIMIT
    }

    /** Fetch timestamps for staleness checks (keyed alongside [cache]). */
    private val fetchedAt = HashMap<String, Long>()

    /** Canonical episode sequence for a work; empty list when never
     *  canonized or on failure. */
    suspend fun forWork(workId: String): List<CanonEpisode> {
        synchronized(cache) { cache[workId] }?.let { return it }
        val dto = api.getEpisodes(workId) ?: return emptyList()
        val parsed = dto.episodes.map { e ->
            CanonEpisode(
                seq = e.seq,
                kind = e.kind,
                number = e.number,
                abs = e.abs,
                season = e.season,
                epInSeason = e.epInSeason,
                epKey = e.epKey,
                doc = e.doc,
                label = e.label,
                airDate = e.airDate,
                dateUpload = e.dateUpload,
                image = e.image,
                overview = e.overview,
                runtime = e.runtime,
                anidbEid = e.anidbEid,
                tvdbEid = e.tvdbEid,
                tvdbShowId = e.tvdbShowId,
                filler = e.filler != 0,
                confidence = e.confidence,
                sources = e.sources,
                refs = e.refs,
            )
        }
        // Same merged-sibling hazard as chapters: two feeds can carry
        // the same episode under different labels and land as separate
        // seqs. Dedupe on the merge-key identity (kind + doc + the
        // kind-scoped coordinate the worker merged on); rows with no
        // coordinate can't be proven dupes and pass through.
        val seen = HashSet<List<Any?>>()
        val list = parsed.filter { e ->
            val coord = if (e.kind == "episode") e.abs ?: e.number else e.epInSeason ?: e.number
            if (coord == null) return@filter true
            seen.add(listOf(e.kind, e.doc.orEmpty(), coord))
        }
        // Empty sequences are NOT cached — "no feed materialized yet",
        // not a fresh result — canonize must retry.
        if (list.isEmpty()) return emptyList()
        synchronized(cache) {
            cache[workId] = list
            fetchedAt[workId] = System.currentTimeMillis()
        }
        return list
    }

    /** True when the work has no cached sequence or it exceeds the SWR window. */
    fun isStale(workId: String): Boolean = synchronized(cache) {
        if (cache[workId].isNullOrEmpty()) return true
        val ts = fetchedAt[workId] ?: return true
        System.currentTimeMillis() - ts > STALE_AFTER_MS
    }

    /** Drop the cached list after a canonize rebuild. */
    fun invalidate(workId: String) {
        synchronized(cache) {
            cache.remove(workId)
            fetchedAt.remove(workId)
        }
    }

    /** Re-fetch into the cache right after a canonize post — the
     *  remerge lands before the POST returns, so this warms the fresh
     *  sequence rather than waiting for the next staleness window. */
    suspend fun prime(workId: String): List<CanonEpisode> {
        invalidate(workId)
        return forWork(workId)
    }

    /**
     * Bind a (season, episode) coordinate to its canonical row — the
     * Cinemeta coord first matches (season, epInSeason); an absolute
     * number falls through to canonical absolute entries (kind
     * 'episode' with a materialized [CanonEpisode.abs]).
     */
    fun bind(season: Int?, ep: Double, canon: List<CanonEpisode>): CanonEpisode? =
        canon.firstOrNull { it.season == season && it.epInSeason?.toDouble() == ep }
            ?: canon.firstOrNull { it.kind == "episode" && it.abs?.toDouble() == ep }

    /** Bind an absolute episode number to its canonical row. */
    fun bindAbs(abs: Int, canon: List<CanonEpisode>): CanonEpisode? =
        canon.firstOrNull { it.kind == "episode" && it.abs == abs }

    /** Bind a kind-scoped number — specials/NC bind inside their own
     *  keyspace so an "S1" never collides with main episode 1. */
    fun bindKind(kind: String, number: Double, canon: List<CanonEpisode>): CanonEpisode? =
        canon.firstOrNull { it.kind == kind && (it.number == number || it.epInSeason?.toDouble() == number) }
}
