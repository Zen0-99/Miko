package tachiyomi.domain.readingorder.model

data class ReadingOrderEdge(
    val id: Long,
    val orderId: Long,
    val fromEntryId: Long,
    val toEntryId: Long,
)
