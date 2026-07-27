package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

/**
 * Checks whether adding an edge `fromMangaId → toMangaId` would create a
 * cycle in the reading order DAG.
 *
 * A cycle would form if `toMangaId` can already reach `fromMangaId` via
 * existing edges. This performs a DFS from `toMangaId` following edges
 * forward (from → to) and returns true if `fromMangaId` is reachable.
 */
class CheckReadingOrderCycle(
    private val repository: ReadingOrderRepository,
) {

    /**
     * @return true if adding the edge would create a cycle
     */
    suspend fun await(orderId: Long, fromMangaId: Long, toMangaId: Long): Boolean {
        if (fromMangaId == toMangaId) return true

        val edges = repository.getEdges(orderId)
        // Build adjacency list: fromMangaId -> list of toMangaId
        val adjacency = edges.groupBy(
            keySelector = { it.fromMangaId },
            valueTransform = { it.toMangaId },
        )

        val visited = mutableSetOf<Long>()
        val stack = ArrayDeque<Long>()
        stack.addLast(toMangaId)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current == fromMangaId) return true
            if (!visited.add(current)) continue

            adjacency[current]?.forEach { next ->
                if (next !in visited) {
                    stack.addLast(next)
                }
            }
        }

        return false
    }
}
