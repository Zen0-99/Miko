package tachiyomi.domain.history.novel.model

import tachiyomi.domain.entries.novel.model.NovelCover
import kotlin.time.Instant

data class NovelHistoryWithRelations(
    val id: Long,
    val chapterId: Long,
    val novelId: Long,
    val title: String,
    val chapterName: String? = null,
    val chapterNumber: Double,
    val readAt: Instant?,
    val readDuration: Long,
    val coverData: NovelCover,
)
