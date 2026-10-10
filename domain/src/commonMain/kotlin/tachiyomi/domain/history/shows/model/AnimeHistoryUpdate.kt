package tachiyomi.domain.history.shows.model

import kotlin.time.Instant

data class AnimeHistoryUpdate(
    val episodeId: Long,
    val seenAt: Instant,
)
