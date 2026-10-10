package tachiyomi.domain.readingorder.model

data class ReadingOrderProgress(
    val orderId: Long,
    val entryId: Long,
    val completed: Boolean,
    val completedAt: Long?,
)
