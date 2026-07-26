package tachiyomi.domain.collection.manga.model

import kotlinx.serialization.Serializable

/**
 * Portable `.mcoll` file format for exporting/importing a manga collection.
 *
 * Manga are identified by source + url (the same keys used by the backup
 * system) with title as a fallback for matching when the source is not
 * installed on the target instance.
 *
 * Format version is pinned at 1. Future breaking changes must bump
 * [formatVersion] and keep backward-compatible parsing for older versions.
 */
@Serializable
data class McollFile(
    val formatVersion: Int = 1,
    val collection: McollCollection,
    val manga: List<McollManga> = emptyList(),
    val customOrder: List<Long> = emptyList(),
)

@Serializable
data class McollCollection(
    val name: String,
    val order: Long = 0,
    val flags: Long = 0,
)

@Serializable
data class McollManga(
    val source: Long,
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Long = 0,
    val thumbnailUrl: String? = null,
)
