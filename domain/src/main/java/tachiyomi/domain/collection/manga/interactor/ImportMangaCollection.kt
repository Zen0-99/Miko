package tachiyomi.domain.collection.manga.interactor

import kotlinx.serialization.json.Json
import tachiyomi.domain.collection.manga.model.McollCollection
import tachiyomi.domain.collection.manga.model.McollFile
import tachiyomi.domain.collection.manga.model.McollFileV1
import tachiyomi.domain.collection.manga.model.McollManga
import tachiyomi.domain.collection.manga.model.McollReadingOrder
import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.entries.manga.interactor.GetMangaByUuid
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.readingorder.repository.ReadingOrderRepository
import java.io.InputStream
import java.util.UUID

class ImportMangaCollection(
    private val collectionRepository: MangaCollectionRepository,
    private val readingOrderRepository: ReadingOrderRepository,
    private val mangaRepository: MangaRepository,
    private val getMangaByUuid: GetMangaByUuid,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    data class ImportResult(
        val collectionsCreated: Int,
        val collectionNames: List<String>,
        val readingOrdersCreated: Int,
        val mangaInserted: Int,
        val mangaMatched: Int,
        val unmatchedTitles: List<String>,
    )

    /**
     * Import a `.mcoll` file from an [InputStream].
     *
     * Supports both v1 (single collection, no UUIDs) and v2 (multi-collection,
     * reading orders, UUID-based) formats. v1 files are migrated to v2 on
     * the fly.
     *
     * - Manga are matched by UUID first, then by source + url (v1 fallback).
     * - Unmatched manga are inserted into the DB (like the backup system) so
     *   they exist even without extensions installed.
     * - Collections are created with name conflict suffixing.
     * - Reading orders are created with nodes/edges/progress resolved by UUID.
     *
     * @param stream the source stream (not closed by this function)
     */
    suspend fun await(
        stream: InputStream,
        nameConflictSuffix: String = " (imported)",
    ): ImportResult {
        val content = stream.bufferedReader(Charsets.UTF_8).readText()

        // Detect format version
        val file = if (content.contains("\"formatVersion\":2") ||
            content.contains("\"formatVersion\": 2") ||
            (content.contains("\"collections\"") && !content.contains("\"collection\":"))
        ) {
            json.decodeFromString<McollFile>(content)
        } else {
            // v1 file — migrate
            val v1 = json.decodeFromString<McollFileV1>(content)
            migrateV1ToV2(v1)
        }

        // Phase 1: Insert/match all manga, build uuid -> mangaId map
        val uuidToMangaId = mutableMapOf<String, Long>()
        var mangaInserted = 0
        var mangaMatched = 0
        val unmatchedTitles = mutableListOf<String>()

        for (mcollManga in file.manga) {
            // Try UUID match first
            val existingByUuid = if (mcollManga.uuid.isNotBlank()) {
                getMangaByUuid.await(mcollManga.uuid)
            } else {
                null
            }

            if (existingByUuid != null) {
                uuidToMangaId[mcollManga.uuid] = existingByUuid.id
                mangaMatched++
                continue
            }

            // Try source + url match (for v1 migrated manga or v2 without UUID match)
            val existingByUrl = getMangaByUrlAndSourceId.await(mcollManga.url, mcollManga.source)
            if (existingByUrl != null) {
                uuidToMangaId[mcollManga.uuid] = existingByUrl.id
                mangaMatched++
                continue
            }

            // Insert new manga (like backup restore does)
            val manga = mcollManga.toManga()
            val newId = mangaRepository.insertManga(manga)
            if (newId != null) {
                uuidToMangaId[mcollManga.uuid] = newId
                mangaInserted++
            } else {
                unmatchedTitles.add(mcollManga.title)
            }
        }

        // Phase 2: Create collections
        val existingCollectionNames = collectionRepository.getAllMangaCollections()
            .map { it.name }.toMutableSet()
        val createdCollectionNames = mutableListOf<String>()

        for (mcollCollection in file.collections) {
            val finalName = if (mcollCollection.name in existingCollectionNames) {
                mcollCollection.name + nameConflictSuffix
            } else {
                mcollCollection.name
            }
            existingCollectionNames.add(finalName)

            collectionRepository.insertMangaCollection(
                tachiyomi.domain.collection.model.Collection(
                    id = 0L,
                    name = finalName,
                    order = mcollCollection.order,
                    flags = mcollCollection.flags,
                    hidden = false,
                ),
            )

            // Find the newly created collection
            val created = collectionRepository.getAllMangaCollections()
                .last { it.name == finalName }

            // Add manga to collection
            for (uuid in mcollCollection.mangaUuids) {
                val mangaId = uuidToMangaId[uuid] ?: continue
                collectionRepository.addMangaToCollection(mangaId, created.id)
            }

            // Restore custom order
            val orderedIds = mcollCollection.customOrder
                .mapNotNull { uuid -> uuidToMangaId[uuid] }
            if (orderedIds.isNotEmpty()) {
                collectionRepository.setMangaCustomOrder(created.id, orderedIds)
            }

            createdCollectionNames.add(finalName)
        }

        // Phase 3: Create reading orders
        val existingOrderNames = readingOrderRepository.getAllReadingOrders()
            .map { it.name }.toMutableSet()
        var readingOrdersCreated = 0

        for (mcollRO in file.readingOrders) {
            val finalName = if (mcollRO.name in existingOrderNames) {
                mcollRO.name + nameConflictSuffix
            } else {
                mcollRO.name
            }
            existingOrderNames.add(finalName)

            val orderId = readingOrderRepository.insertReadingOrder(finalName, mcollRO.description)

            // Add nodes
            for (uuid in mcollRO.nodeUuids) {
                val mangaId = uuidToMangaId[uuid] ?: continue
                readingOrderRepository.addNode(orderId, mangaId)
            }

            // Add edges
            for (edge in mcollRO.edges) {
                val fromId = uuidToMangaId[edge.fromUuid] ?: continue
                val toId = uuidToMangaId[edge.toUuid] ?: continue
                readingOrderRepository.addEdge(orderId, fromId, toId)
            }

            // Add progress
            for (p in mcollRO.progress) {
                val mangaId = uuidToMangaId[p.uuid] ?: continue
                readingOrderRepository.setProgress(orderId, mangaId, p.completed, p.completedAt)
            }

            readingOrdersCreated++
        }

        return ImportResult(
            collectionsCreated = createdCollectionNames.size,
            collectionNames = createdCollectionNames,
            readingOrdersCreated = readingOrdersCreated,
            mangaInserted = mangaInserted,
            mangaMatched = mangaMatched,
            unmatchedTitles = unmatchedTitles,
        )
    }

    /**
     * Migrate a v1 file to v2 structure. v1 has no UUIDs, so we generate
     * deterministic UUIDs based on source + url so re-imports of the same
     * v1 file are consistent.
     */
    private fun migrateV1ToV2(v1: McollFileV1): McollFile {
        // Generate UUIDs deterministically from source + url
        val mangaWithUuids = v1.manga.map { v1Manga ->
            val deterministicUuid = UUID.nameUUIDFromBytes(
                "${v1Manga.source}|${v1Manga.url}".toByteArray(),
            ).toString()
            McollManga(
                uuid = deterministicUuid,
                source = v1Manga.source,
                url = v1Manga.url,
                title = v1Manga.title,
                artist = v1Manga.artist,
                author = v1Manga.author,
                description = v1Manga.description,
                genre = v1Manga.genre,
                status = v1Manga.status,
                thumbnailUrl = v1Manga.thumbnailUrl,
                initialized = v1Manga.description != null,
                favorite = true,
            )
        }

        // v1 customOrder is a list of manga IDs (positional). We can't map
        // those to UUIDs since they're DB-specific. Skip custom order in
        // v1 migration — manga will be in the collection but unordered.
        val collection = McollCollection(
            name = v1.collection.name,
            order = v1.collection.order,
            flags = v1.collection.flags,
            mangaUuids = mangaWithUuids.map { it.uuid },
            customOrder = emptyList(),
        )

        return McollFile(
            formatVersion = 2,
            collections = listOf(collection),
            readingOrders = emptyList(),
            manga = mangaWithUuids,
        )
    }

    private fun McollManga.toManga(): Manga {
        return Manga(
            id = 0L,
            source = source,
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genre = genre,
            status = status,
            thumbnailUrl = thumbnailUrl,
            favorite = favorite,
            lastUpdate = 0L,
            nextUpdate = 0L,
            fetchInterval = 0,
            dateAdded = dateAdded,
            viewerFlags = viewerFlags,
            chapterFlags = chapterFlags,
            coverLastModified = coverLastModified,
            updateStrategy = eu.kanade.tachiyomi.source.model.UpdateStrategy.entries
                .getOrElse(updateStrategy.toInt()) { eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE },
            initialized = initialized,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = version,
            uuid = uuid,
        )
    }
}
