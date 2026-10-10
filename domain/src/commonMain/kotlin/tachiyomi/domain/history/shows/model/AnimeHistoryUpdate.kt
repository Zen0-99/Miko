package tachiyomi.domain.history.shows.model

import java.util.Date

data class AnimeHistoryUpdate(
    val episodeId: Long,
    val seenAt: Date,
)
