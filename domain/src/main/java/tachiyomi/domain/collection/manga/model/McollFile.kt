package tachiyomi.domain.collection.manga.model

import kotlinx.serialization.Serializable

/**
 * Portable `.mcoll` file format for exporting/importing collections and
 * their associated reading orders.
 *
 * **Format v2** — supports multiple collections, embedded reading orders,
 * and UUID-based manga identity. Manga data is bundled (like the backup
 * system) so entries exist on import even without extensions installed.
 *
 * **Format v1** — single collection, no reading orders, manga identified by
 * source + url. v1 files are still importable; the importer migrates them.
 *
 * Manga are identified by [McollManga.uuid] — this ID persists across
 * instances regardless of url/extension changes. If the user migrates an
 * entry (e.g. from one source to another), the UUID stays the same and the
 * reading order remains intact.
 *
 * Reading orders reference manga by UUID, not by index, so they are
 * self-contained regardless of collection membership.
 *
 * Format version is pinned at 2. v1 is accepted on import and migrated.
 * Future breaking changes must bump [formatVersion] and keep
 * backward-compatible parsing for older versions.
 */
@Serializable
data class McollFile(
    val formatVersion: Int = 2,
    val collections: List<McollCollection> = emptyList(),
    val readingOrders: List<McollReadingOrder> = emptyList(),
    val manga: List<McollManga> = emptyList(),
)

@Serializable
data class McollCollection(
    val name: String,
    val order: Long = 0,
    val flags: Long = 0,
    /** UUIDs of manga in this collection */
    val mangaUuids: List<String> = emptyList(),
    /** Custom order: list of manga UUIDs in the saved display order */
    val customOrder: List<String> = emptyList(),
)

@Serializable
data class McollReadingOrder(
    val name: String,
    val description: String? = null,
    /** UUIDs of manga that are nodes in this reading order */
    val nodeUuids: List<String> = emptyList(),
    /** Edges: fromUuid must be read before toUuid */
    val edges: List<McollEdge> = emptyList(),
    /** Progress per node */
    val progress: List<McollProgress> = emptyList(),
)

@Serializable
data class McollEdge(
    val fromUuid: String,
    val toUuid: String,
)

@Serializable
data class McollProgress(
    val uuid: String,
    val completed: Boolean,
    val completedAt: Long? = null,
)

@Serializable
data class McollManga(
    val uuid: String,
    val source: Long,
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Long = 0,
    val thumbnailUrl: String? = null,
    val initialized: Boolean = false,
    val favorite: Boolean = true,
    val viewerFlags: Long = 0,
    val chapterFlags: Long = 0,
    val dateAdded: Long = 0,
    val coverLastModified: Long = 0,
    val updateStrategy: Int = 0,
    val version: Long = 0,
)

// ── v1 compatibility shim ──────────────────────────────────────────────

/**
 * v1 format — single collection, no reading orders, no UUIDs.
 * Used only for parsing v1 files on import; then migrated to v2.
 */
@Serializable
data class McollFileV1(
    val formatVersion: Int = 1,
    val collection: McollCollectionV1,
    val manga: List<McollMangaV1> = emptyList(),
    val customOrder: List<Long> = emptyList(),
)

@Serializable
data class McollCollectionV1(
    val name: String,
    val order: Long = 0,
    val flags: Long = 0,
)

@Serializable
data class McollMangaV1(
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
