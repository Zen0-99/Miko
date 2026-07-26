package tachiyomi.domain.readingorder.model

import java.io.Serializable

data class ReadingOrder(
    val id: Long,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
) : Serializable
