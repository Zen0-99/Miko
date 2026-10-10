package tachiyomi.domain.history.shows.model

import kotlin.time.Instant

data class AnimeHistory(
    val id: Long,
    val episodeId: Long,
    val seenAt: Instant?,
) {
    companion object {
        fun create() = AnimeHistory(
            id = -1L,
            episodeId = -1L,
            seenAt = null,
        )
    }
}
