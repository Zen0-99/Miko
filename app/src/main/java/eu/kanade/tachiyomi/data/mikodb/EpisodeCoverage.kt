package eu.kanade.tachiyomi.data.mikodb

/**
 * Canonical-vs-carrier coverage diff (19-04, D-03b) — pure functions
 * over the canonical episode sequence and the artifact's per-doc
 * `anime_episodes` rows.
 *
 * `unserved`/`served` split the canon list by whether any *readable*
 * carrier feed contributes the row — 19-05 renders the unserved side
 * as the missing-episodes surface. `boundEntry`/`canonicalizeNumbers`
 * lift a carrier's raw numbering into canonical absolute space when
 * the carrier's bound artifact entry proves the mapping; nothing is
 * asserted without artifact evidence.
 *
 * All inputs arrive as parameters — callers resolve
 * [MikoDbManager.SourceMeta] fields and [AnimeIdBridge] rows.
 */
object EpisodeCoverage {

    /**
     * Canon rows no readable carrier serves. [readablePkgs] is the
     * `WorkCarriers.Matrix.readable` pkg-name set; the artifact feed
     * (`mikodb:map`) is never a serving carrier — an artifact-only row
     * with no carrier overlap is exactly the gap 19-05 reports.
     */
    fun unserved(
        canon: List<CanonicalEpisodeMap.CanonEpisode>,
        readablePkgs: Set<String>,
    ): List<CanonicalEpisodeMap.CanonEpisode> {
        val carriers = readablePkgs - CanonizeWork.ARTIFACT_SOURCE_KEY
        return canon.filter { it.sources.intersect(carriers).isEmpty() }
    }

    /** Complement of [unserved] — canon rows at least one readable
     *  carrier contributes. */
    fun served(
        canon: List<CanonicalEpisodeMap.CanonEpisode>,
        readablePkgs: Set<String>,
    ): List<CanonicalEpisodeMap.CanonEpisode> {
        val carriers = readablePkgs - CanonizeWork.ARTIFACT_SOURCE_KEY
        return canon.filter { it.sources.intersect(carriers).isNotEmpty() }
    }

    /**
     * Resolve which artifact `anime` entry a carrier's provider ref
     * names. [idType] is the declaring vocabulary —
     * `SourceMeta.idType` ("anilist", "anilist+mal", …) or a provider
     * name on a work's `provider_links` entry. A ref only proves the
     * entry it names *in its own id space*; anything else returns null.
     */
    fun boundEntry(
        rows: List<AnimeIdBridge.AnimeMapping>,
        providerRef: String?,
        idType: String?,
    ): AnimeIdBridge.AnimeMapping? {
        val ref = providerRef?.trim()?.toLongOrNull() ?: return null
        val t = idType.orEmpty().lowercase()
        if (t.contains("anilist")) {
            rows.firstOrNull { it.anilistId == ref }?.let { return it }
        }
        if (t.contains("mal")) {
            rows.firstOrNull { it.malId == ref }?.let { return it }
        }
        return null
    }

    /**
     * Carrier raw number → canonical abs, proven by the bound entry's
     * `anime_episodes` rows. Only `type == "main"` rows map — specials
     * and NCs live in their own keyspace and never mint abs identity.
     * Artifact rows with a null `absNumber` (roughly half upstream)
     * leave the carrier number unmapped — a wrong map is worse than
     * none.
     */
    fun canonicalizeNumbers(
        numbers: List<Double>,
        entryRows: List<AnimeIdBridge.AnimeEpisode>,
    ): Map<Double, Int> =
        numbers.mapNotNull { n ->
            entryRows.firstOrNull { it.type == "main" && it.number == n }
                ?.absNumber
                ?.let { n to it }
        }.toMap()
}
