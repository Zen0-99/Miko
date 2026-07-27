package tachiyomi.domain.readingorder.model

import kotlinx.serialization.Serializable

/**
 * Portable `.mordr` file format for exporting/importing a Reading Order.
 *
 * Nodes (manga) are identified by source + url + title — the same keys used
 * by the backup system and `.mcoll` files. Edges reference nodes by index
 * into the [nodes] list, which keeps the format self-contained and immune
 * to manga ID differences across instances.
 *
 * Format version is pinned at 1. Future breaking changes must bump
 * [formatVersion] and keep backward-compatible parsing for older versions.
 */
@Serializable
data class MordrFile(
    val formatVersion: Int = 1,
    val order: MordrOrder,
    val nodes: List<MordrNode> = emptyList(),
    val edges: List<MordrEdge> = emptyList(),
    val progress: List<MordrProgress> = emptyList(),
)

@Serializable
data class MordrOrder(
    val name: String,
    val description: String? = null,
)

@Serializable
data class MordrNode(
    val index: Int,
    val source: Long,
    val url: String,
    val title: String,
    val position: Long = 0,
)

@Serializable
data class MordrEdge(
    val fromIndex: Int,
    val toIndex: Int,
)

@Serializable
data class MordrProgress(
    val nodeIndex: Int,
    val completed: Boolean,
    val completedAt: Long? = null,
)
