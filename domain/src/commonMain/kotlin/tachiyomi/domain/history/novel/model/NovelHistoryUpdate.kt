package tachiyomi.domain.history.novel.model

import kotlin.time.Instant

data class NovelHistoryUpdate(
    val chapterId: Long,
    val readAt: Instant,
    val sessionReadDuration: Long,
)
