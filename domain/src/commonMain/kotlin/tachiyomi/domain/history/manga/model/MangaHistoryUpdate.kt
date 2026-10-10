package tachiyomi.domain.history.manga.model

import kotlin.time.Instant

data class MangaHistoryUpdate(
    val chapterId: Long,
    val readAt: Instant,
    val sessionReadDuration: Long,
)
