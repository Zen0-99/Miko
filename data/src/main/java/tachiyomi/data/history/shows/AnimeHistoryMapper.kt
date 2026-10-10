package tachiyomi.data.history.shows

import tachiyomi.domain.entries.shows.model.AnimeCover
import tachiyomi.domain.history.shows.model.AnimeHistory
import tachiyomi.domain.history.shows.model.AnimeHistoryWithRelations
import kotlin.time.Instant

object AnimeHistoryMapper {
    fun mapAnimeHistory(
        id: Long,
        episodeId: Long,
        seenAt: Instant?,
    ): AnimeHistory = AnimeHistory(
        id = id,
        episodeId = episodeId,
        seenAt = seenAt,
    )

    fun mapAnimeHistoryWithRelations(
        historyId: Long,
        animeId: Long,
        episodeId: Long,
        title: String,
        thumbnailUrl: String?,
        sourceId: Long,
        isFavorite: Boolean,
        coverLastModified: Long,
        animeUrl: String,
        runtime: String?,
        episodeNumber: Double,
        episodeScanlator: String?,
        seenAt: Instant?,
    ): AnimeHistoryWithRelations = AnimeHistoryWithRelations(
        id = historyId,
        episodeId = episodeId,
        animeId = animeId,
        title = title,
        episodeNumber = episodeNumber,
        seenAt = seenAt,
        animeUrl = animeUrl,
        runtime = runtime,
        episodeScanlator = episodeScanlator,
        coverData = AnimeCover(
            animeId = animeId,
            sourceId = sourceId,
            isAnimeFavorite = isFavorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
