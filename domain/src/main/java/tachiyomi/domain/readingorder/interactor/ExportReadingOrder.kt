package tachiyomi.domain.readingorder.interactor

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.readingorder.model.MordrEdge
import tachiyomi.domain.readingorder.model.MordrFile
import tachiyomi.domain.readingorder.model.MordrNode
import tachiyomi.domain.readingorder.model.MordrOrder
import tachiyomi.domain.readingorder.model.MordrProgress
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository
import java.io.OutputStream

class ExportReadingOrder(
    private val repository: ReadingOrderRepository,
    private val getManga: GetManga,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Export a reading order to an [OutputStream] in `.mordr` (JSON) format.
     *
     * @param orderId the reading order to export
     * @param stream the destination stream (not closed by this function)
     * @return the number of nodes included in the export
     */
    suspend fun await(orderId: Long, stream: OutputStream): Int {
        val order = repository.getReadingOrder(orderId)
            ?: error("Reading order $orderId not found")

        val nodes = repository.getNodes(orderId)
        val edges = repository.getEdges(orderId)
        val progressList = repository.getAllProgress(orderId)

        // Build node index map (mangaId -> node index in the exported list)
        val mangaIdToIndex = mutableMapOf<Long, Int>()
        val mordrNodes = nodes.mapIndexed { i, node ->
            mangaIdToIndex[node.mangaId] = i
            val manga = getManga.await(node.mangaId)
            MordrNode(
                index = i,
                source = manga?.source ?: 0L,
                url = manga?.url ?: "",
                title = manga?.title ?: "Unknown",
                position = node.position,
            )
        }

        // Translate edges from manga IDs to node indices
        val mordrEdges = edges.mapNotNull { edge ->
            val fromIdx = mangaIdToIndex[edge.fromMangaId] ?: return@mapNotNull null
            val toIdx = mangaIdToIndex[edge.toMangaId] ?: return@mapNotNull null
            MordrEdge(fromIndex = fromIdx, toIndex = toIdx)
        }

        // Translate progress from manga IDs to node indices
        val mordrProgress = progressList.mapNotNull { p ->
            val idx = mangaIdToIndex[p.mangaId] ?: return@mapNotNull null
            MordrProgress(
                nodeIndex = idx,
                completed = p.completed,
                completedAt = p.completedAt,
            )
        }

        val file = MordrFile(
            formatVersion = 1,
            order = MordrOrder(
                name = order.name,
                description = order.description,
            ),
            nodes = mordrNodes,
            edges = mordrEdges,
            progress = mordrProgress,
        )

        val encoded = json.encodeToString(file)
        stream.write(encoded.toByteArray(Charsets.UTF_8))
        stream.flush()

        return mordrNodes.size
    }
}
