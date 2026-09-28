package tachiyomi.domain.history.shows.model

import tachiyomi.domain.entries.shows.model.AnimeCover
import java.util.Date

data class AnimeHistoryWithRelations(
    val id: Long,
    val episodeId: Long,
    val animeId: Long,
    val title: String,
    val episodeNumber: Double,
    val seenAt: Date?,
    val coverData: AnimeCover,
    // Cinemeta extras — season (from episode scanlator "Season N|★R.R") and
    // movie runtime, so home/history labels can show "S2 · Ep.5" or "2h 07min".
    val animeUrl: String = "",
    val runtime: String? = null,
    val episodeScanlator: String? = null,
) {
    val isCinemetaMovie: Boolean
        get() = animeUrl.startsWith("cinemeta:movie")

    val cinemetaSeason: Int?
        get() = episodeScanlator?.substringBefore("|")
            ?.removePrefix("Season ")?.trim()?.toIntOrNull()
            ?.takeIf { animeUrl.startsWith("cinemeta:") }
}
