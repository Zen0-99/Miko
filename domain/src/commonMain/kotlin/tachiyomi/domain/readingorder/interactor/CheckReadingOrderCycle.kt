package tachiyomi.domain.readingorder.interactor

import tachiyomi.domain.readingorder.repository.ReadingOrderRepository

class CheckReadingOrderCycle(
    private val repository: ReadingOrderRepository,
) {

    suspend fun await(orderId: Long, fromEntryId: Long, toEntryId: Long): Boolean {
        if (fromEntryId == toEntryId) return true

        val edges = repository.getEdges(orderId)
        val adjacency = edges.groupBy(
            keySelector = { it.fromEntryId },
            valueTransform = { it.toEntryId },
        )

        val visited = mutableSetOf<Long>()
        val stack = ArrayDeque<Long>()
        stack.addLast(toEntryId)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current == fromEntryId) return true
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
