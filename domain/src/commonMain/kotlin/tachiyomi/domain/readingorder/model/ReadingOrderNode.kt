package tachiyomi.domain.readingorder.model

data class ReadingOrderNode(
    val id: Long,
    val orderId: Long,
    val entryId: Long,
    val position: Long,
)
