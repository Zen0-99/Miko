package tachiyomi.domain.readingorder.interactor

import kotlinx.serialization.json.Json
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.readingorder.model.MordrFile
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository
import java.io.InputStream

class ImportReadingOrder(
    private val repository: ReadingOrderRepository,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    data class ImportResult(
        val orderId: Long,
        val orderName: String,
        val matchedNodes: Int,
        val unmatchedNodes: Int,
        val unmatchedTitles: List<String>,
        val edgesImported: Int,
        val progressImported: Int,
    )

    /**
     * Import a `.mordr` file from an [InputStream].
     *
     * - Creates a new reading order (name suffixed on collision).
     * - Matches manga by source + url; unmatched nodes are skipped.
     * - Edges and progress referencing unmatched nodes are dropped.
     *
     * @param stream the source stream (not closed by this function)
     */
    suspend fun await(
        stream: InputStream,
        nameConflictSuffix: String = " (imported)",
    ): ImportResult {
        val content = stream.bufferedReader(Charsets.UTF_8).readText()
        val file = json.decodeFromString<MordrFile>(content)

        // Resolve name conflict
        val existingOrders = repository.getAllReadingOrders().map { it.name }.toSet()
        val finalName = if (file.order.name in existingOrders) {
            file.order.name + nameConflictSuffix
        } else {
            file.order.name
        }

        // Create the reading order
        val orderId = repository.insertReadingOrder(finalName, file.order.description)

        // Match nodes by source + url, build index -> mangaId map
        val indexToMangaId = mutableMapOf<Int, Long>()
        val unmatchedTitles = mutableListOf<String>()

        for (mordrNode in file.nodes) {
            val manga = getMangaByUrlAndSourceId.await(mordrNode.url, mordrNode.source)
            if (manga != null) {
                repository.addNode(orderId, manga.id)
                indexToMangaId[mordrNode.index] = manga.id
            } else {
                unmatchedTitles.add(mordrNode.title)
            }
        }

        // Import edges (only those whose both endpoints matched)
        var edgesImported = 0
        for (mordrEdge in file.edges) {
            val fromMangaId = indexToMangaId[mordrEdge.fromIndex] ?: continue
            val toMangaId = indexToMangaId[mordrEdge.toIndex] ?: continue
            repository.addEdge(orderId, fromMangaId, toMangaId)
            edgesImported++
        }

        // Import progress (only for matched nodes)
        var progressImported = 0
        for (mordrProgress in file.progress) {
            val mangaId = indexToMangaId[mordrProgress.nodeIndex] ?: continue
            repository.setProgress(
                orderId = orderId,
                mangaId = mangaId,
                completed = mordrProgress.completed,
                completedAt = mordrProgress.completedAt,
            )
            progressImported++
        }

        return ImportResult(
            orderId = orderId,
            orderName = finalName,
            matchedNodes = indexToMangaId.size,
            unmatchedNodes = unmatchedTitles.size,
            unmatchedTitles = unmatchedTitles,
            edgesImported = edgesImported,
            progressImported = progressImported,
        )
    }
}
