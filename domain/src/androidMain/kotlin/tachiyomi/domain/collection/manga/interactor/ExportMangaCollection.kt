package tachiyomi.domain.collection.manga.interactor

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.collection.manga.model.McollCollection
import tachiyomi.domain.collection.manga.model.McollEdge
import tachiyomi.domain.collection.manga.model.McollFile
import tachiyomi.domain.collection.manga.model.McollManga
import tachiyomi.domain.collection.manga.model.McollProgress
import tachiyomi.domain.collection.manga.model.McollReadingOrder
import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.readingorder.interactor.GetReadingOrders
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository
import java.io.OutputStream

class ExportMangaCollection(
    private val collectionRepository: MangaCollectionRepository,
    private val readingOrderRepository: ReadingOrderRepository,
    private val getManga: GetManga,
    private val getReadingOrders: GetReadingOrders,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Result of checking which reading orders would be partially exported.
     */
    data class CrossCollectionWarning(
        val readingOrderName: String,
        val readingOrderId: Long,
        /** Manga UUIDs that are in this reading order but NOT in the selected collections */
        val orphanedMangaTitles: List<String>,
    )

    /**
     * Check which reading orders connected to the selected collections have
     * nodes that belong to other (non-selected) collections. The user should
     * be warned and given the option to include those collections too.
     *
     * @param collectionIds collections selected for export
     * @return warnings for each affected reading order
     */
    suspend fun checkCrossCollection(
        collectionIds: Set<Long>,
    ): List<CrossCollectionWarning> {
        // Gather all manga UUIDs in the selected collections
        val selectedMangaIds = collectionIds
            .flatMap { collectionRepository.getMangaIdsByCollection(it) }
            .toSet()

        // Find all reading orders that involve any of these manga
        val affectedOrders = mutableMapOf<Long, MutableSet<Long>>() // orderId -> mangaIds
        for (mangaId in selectedMangaIds) {
            val orders = getReadingOrders.awaitForEntry(mangaId)
            for (order in orders) {
                affectedOrders.getOrPut(order.id) { mutableSetOf() }.add(mangaId)
            }
        }

        val warnings = mutableListOf<CrossCollectionWarning>()
        for ((orderId, _) in affectedOrders) {
            val order = getReadingOrders.await(orderId) ?: continue
            val allNodes = readingOrderRepository.getNodes(orderId)
            val orphanedTitles = mutableListOf<String>()

            for (node in allNodes) {
                if (node.entryId !in selectedMangaIds) {
                    val manga = getManga.await(node.entryId)
                    orphanedTitles.add(manga?.title ?: "Unknown")
                }
            }

            if (orphanedTitles.isNotEmpty()) {
                warnings.add(
                    CrossCollectionWarning(
                        readingOrderName = order.name,
                        readingOrderId = orderId,
                        orphanedMangaTitles = orphanedTitles,
                    ),
                )
            }
        }

        return warnings
    }

    /**
     * Export one or more collections (with their reading orders) to an
     * [OutputStream] in `.mcoll` (JSON v2) format.
     *
     * @param collectionIds collections to export
     * @param includeReadingOrders if true, embed reading orders connected to
     *   the selected collections
     * @param stream the destination stream (not closed by this function)
     * @return the number of manga included in the export
     */
    suspend fun await(
        collectionIds: Set<Long>,
        includeReadingOrders: Boolean,
        stream: OutputStream,
    ): Int {
        // Gather all manga in the selected collections
        val collectionMangaIds = mutableMapOf<Long, List<Long>>() // collectionId -> mangaIds
        val allMangaIds = mutableSetOf<Long>()
        for (collectionId in collectionIds) {
            val ids = collectionRepository.getMangaIdsByCollection(collectionId)
            collectionMangaIds[collectionId] = ids
            allMangaIds.addAll(ids)
        }

        // If including reading orders, gather their nodes too
        val readingOrderData = mutableListOf<ReadingOrderExportData>()
        if (includeReadingOrders) {
            val seenOrders = mutableSetOf<Long>()
            for (mangaId in allMangaIds) {
                val orders = getReadingOrders.awaitForEntry(mangaId)
                for (order in orders) {
                    if (seenOrders.add(order.id)) {
                        val nodes = readingOrderRepository.getNodes(order.id)
                        val edges = readingOrderRepository.getEdges(order.id)
                        val progress = readingOrderRepository.getAllProgress(order.id)
                        readingOrderData.add(
                            ReadingOrderExportData(
                                order = order,
                                nodes = nodes,
                                edges = edges,
                                progress = progress,
                            ),
                        )
                        // Add node manga IDs to the export set
                        allMangaIds.addAll(nodes.map { it.entryId })
                    }
                }
            }
        }

        // Build manga list with UUIDs
        val mangaIdToUuid = mutableMapOf<Long, String>()
        val mcollMangaList = allMangaIds.mapNotNull { mangaId ->
            val manga = getManga.await(mangaId) ?: return@mapNotNull null
            val uuid = manga.uuid ?: java.util.UUID.randomUUID().toString()
            mangaIdToUuid[mangaId] = uuid
            manga.toMcoll(uuid)
        }

        // Build collections
        val mcollCollections = collectionIds.map { collectionId ->
            val collection = collectionRepository.getMangaCollection(collectionId)
                ?: error("Collection $collectionId not found")
            val mangaIds = collectionMangaIds[collectionId] ?: emptyList()
            val uuids = mangaIds.mapNotNull { mangaIdToUuid[it] }
            val customOrder = collectionRepository.getMangaCustomOrder(collectionId)
                .mapNotNull { mangaId -> mangaIdToUuid[mangaId] }
            McollCollection(
                name = collection.name,
                order = collection.order,
                flags = collection.flags,
                mangaUuids = uuids,
                customOrder = customOrder,
            )
        }

        // Build reading orders
        val mcollReadingOrders = readingOrderData.map { rod ->
            McollReadingOrder(
                name = rod.order.name,
                description = rod.order.description,
                nodeUuids = rod.nodes.mapNotNull { mangaIdToUuid[it.entryId] },
                edges = rod.edges.mapNotNull { edge ->
                    val fromUuid = mangaIdToUuid[edge.fromEntryId] ?: return@mapNotNull null
                    val toUuid = mangaIdToUuid[edge.toEntryId] ?: return@mapNotNull null
                    McollEdge(fromUuid = fromUuid, toUuid = toUuid)
                },
                progress = rod.progress.mapNotNull { p ->
                    val uuid = mangaIdToUuid[p.entryId] ?: return@mapNotNull null
                    McollProgress(uuid = uuid, completed = p.completed, completedAt = p.completedAt)
                },
            )
        }

        val file = McollFile(
            formatVersion = 2,
            collections = mcollCollections,
            readingOrders = mcollReadingOrders,
            manga = mcollMangaList,
        )

        val encoded = json.encodeToString(file)
        stream.write(encoded.toByteArray(Charsets.UTF_8))
        stream.flush()

        return mcollMangaList.size
    }

    private data class ReadingOrderExportData(
        val order: tachiyomi.domain.readingorder.model.ReadingOrder,
        val nodes: List<tachiyomi.domain.readingorder.model.ReadingOrderNode>,
        val edges: List<tachiyomi.domain.readingorder.model.ReadingOrderEdge>,
        val progress: List<tachiyomi.domain.readingorder.model.ReadingOrderProgress>,
    )

    private fun tachiyomi.domain.entries.manga.model.Manga.toMcoll(uuid: String) = McollManga(
        uuid = uuid,
        source = source,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre,
        status = status,
        thumbnailUrl = thumbnailUrl,
        initialized = initialized,
        favorite = favorite,
        viewerFlags = viewerFlags,
        chapterFlags = chapterFlags,
        dateAdded = dateAdded,
        coverLastModified = coverLastModified,
        updateStrategy = updateStrategy.ordinal,
        version = version,
    )
}
