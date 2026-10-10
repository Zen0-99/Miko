package tachiyomi.domain.readingorder.model

import tachiyomi.domain.util.PlatformSerializable

data class ReadingOrder(
    val id: Long,
    val name: String,
    val description: String?,
    val entryKind: String,
    val createdAt: Long,
    val updatedAt: Long,
) : PlatformSerializable
