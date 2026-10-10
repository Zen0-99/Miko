package tachiyomi.domain.history.manga.model

import kotlin.time.Instant

data class MangaHistory(
    val id: Long,
    val chapterId: Long,
    val readAt: Instant?,
    val readDuration: Long,
) {
    companion object {
        fun create() = MangaHistory(
            id = -1L,
            chapterId = -1L,
            readAt = null,
            readDuration = -1L,
        )
    }
}
