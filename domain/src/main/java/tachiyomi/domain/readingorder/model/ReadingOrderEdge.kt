package tachiyomi.domain.readingorder.model

data class ReadingOrderEdge(
    val id: Long,
    val orderId: Long,
    val fromMangaId: Long,
    val toMangaId: Long,
)
