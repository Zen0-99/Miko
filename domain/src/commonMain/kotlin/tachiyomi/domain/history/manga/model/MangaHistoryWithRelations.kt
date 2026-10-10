package tachiyomi.domain.history.manga.model

import tachiyomi.domain.entries.manga.model.MangaCover
import kotlin.time.Instant

data class MangaHistoryWithRelations(
    val id: Long,
    val chapterId: Long,
    val mangaId: Long,
    val title: String,
    val chapterNumber: Double,
    val readAt: Instant?,
    val readDuration: Long,
    val coverData: MangaCover,
)
