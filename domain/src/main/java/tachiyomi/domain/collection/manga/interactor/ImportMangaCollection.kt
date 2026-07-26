package tachiyomi.domain.collection.manga.interactor

import kotlinx.serialization.json.Json
import tachiyomi.domain.collection.manga.model.McollFile
import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.model.Manga
import java.io.InputStream

class ImportMangaCollection(
    private val collectionRepository: MangaCollectionRepository,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    data class ImportResult(
        val collectionId: Long,
        val collectionName: String,
        val matchedManga: Int,
        val unmatchedManga: Int,
        val unmatchedTitles: List<String>,
    )

    /**
     * Import a `.mcoll` file from an [InputStream].
     *
     * - Creates a new collection (name suffixed if a collision occurs).
     * - Matches manga by source + url; unmatched manga are skipped and
     *   reported in [ImportResult.unmatchedTitles].
     * - Restores the custom order for matched manga only.
     *
     * @param stream the source stream (not closed by this function)
     * @param onProgress optional callback invoked per manga match attempt
     */
    suspend fun await(
        stream: InputStream,
        nameConflictSuffix: String = " (imported)",
    ): ImportResult {
        val content = stream.bufferedReader(Charsets.UTF_8).readText()
        val file = json.decodeFromString<McollFile>(content)

        val existingNames = collectionRepository.getAllMangaCollections().map { it.name }.toSet()
        val finalName = if (file.collection.name in existingNames) {
            file.collection.name + nameConflictSuffix
        } else {
            file.collection.name
        }

        // Insert the collection
        collectionRepository.insertMangaCollection(
            Collection(
                id = 0L,
                name = finalName,
                order = file.collection.order,
                flags = file.collection.flags,
                hidden = false,
            ),
        )

        // Find the newly inserted collection by name (last match)
        val createdCollection = collectionRepository.getAllMangaCollections()
            .last { it.name == finalName }

        // Match manga by source + url
        val matchedIds = mutableListOf<Long>()
        val unmatchedTitles = mutableListOf<String>()

        for (mcollManga in file.manga) {
            val manga = getMangaByUrlAndSourceId.await(mcollManga.url, mcollManga.source)
            if (manga != null) {
                collectionRepository.addMangaToCollection(manga.id, createdCollection.id)
                matchedIds.add(manga.id)
            } else {
                unmatchedTitles.add(mcollManga.title)
            }
        }

        // Restore custom order for matched manga only, preserving original order
        val orderMap = file.customOrder.withIndex().associate { it.value to it.index }
        val orderedMatchedIds = matchedIds.sortedBy { id -> orderMap[id] ?: Int.MAX_VALUE }
        if (orderedMatchedIds.isNotEmpty()) {
            collectionRepository.setMangaCustomOrder(createdCollection.id, orderedMatchedIds)
        }

        return ImportResult(
            collectionId = createdCollection.id,
            collectionName = finalName,
            matchedManga = matchedIds.size,
            unmatchedManga = unmatchedTitles.size,
            unmatchedTitles = unmatchedTitles,
        )
    }
}
