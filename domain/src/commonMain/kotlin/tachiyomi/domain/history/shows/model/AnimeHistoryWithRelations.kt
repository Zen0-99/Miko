package tachiyomi.domain.history.shows.model

import tachiyomi.domain.entries.shows.model.AnimeCover
import kotlin.time.Instant

data class AnimeHistoryWithRelations(
    val id: Long,
    val episodeId: Long,
    val animeId: Long,
    val title: String,
    val episodeNumber: Double,
    val seenAt: Instant?,
    val coverData: AnimeCover,
    // Cinemeta extras — season (from episode scanlator "Season N|★R.R") and
    // movie runtime, so home/history labels can show "S2 · Ep.5" or "2h 07min".
    val animeUrl: String = "",
    val runtime: String? = null,
    val episodeScanlator: String? = null,
    // Populated by CinemetaEntryUnifier when this extension-source row was
    // mapped onto a canonical Cinemeta entry. Dedupe/group consumers should
    // key on canonicalAnimeId ?: animeId — episodes of one show can land on
    // several extension entries, so raw animeId is NOT a stable show key.
    val canonicalAnimeId: Long? = null,
) {
    /** Stable per-show key across extension-source duplicate entries. */
    val showKey: Long
        get() = canonicalAnimeId ?: animeId
    val isCinemetaMovie: Boolean
        get() = animeUrl.startsWith("cinemeta:movie")

    val cinemetaSeason: Int?
        get() = episodeScanlator?.substringBefore("|")
            ?.removePrefix("Season ")?.trim()?.toIntOrNull()
            ?.takeIf { animeUrl.startsWith("cinemeta:") }
}
