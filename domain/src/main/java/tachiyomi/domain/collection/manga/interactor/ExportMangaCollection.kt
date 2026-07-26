package tachiyomi.domain.collection.manga.interactor

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.collection.manga.model.McollCollection
import tachiyomi.domain.collection.manga.model.McollFile
import tachiyomi.domain.collection.manga.model.McollManga
import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.entries.manga.interactor.GetManga
import java.io.OutputStream

class ExportMangaCollection(
    private val collectionRepository: MangaCollectionRepository,
    private val getManga: GetManga,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Export a collection to an [OutputStream] in `.mcoll` (JSON) format.
     *
     * @param collectionId the collection to export
     * @param stream the destination stream (not closed by this function)
     * @return the number of manga included in the export
     */
    suspend fun await(collectionId: Long, stream: OutputStream): Int {
        val collection = collectionRepository.getMangaCollection(collectionId)
            ?: error("Collection $collectionId not found")

        val mangaIds = collectionRepository.getMangaIdsByCollection(collectionId)
        val mangaList = mangaIds.mapNotNull { id -> getManga.await(id) }

        val customOrder = collectionRepository.getMangaCustomOrder(collectionId)

        val file = McollFile(
            formatVersion = 1,
            collection = collection.toMcoll(),
            manga = mangaList.map { it.toMcoll() },
            customOrder = customOrder,
        )

        val encoded = json.encodeToString(file)
        stream.write(encoded.toByteArray(Charsets.UTF_8))
        stream.flush()

        return mangaList.size
    }

    private fun Collection.toMcoll() = McollCollection(
        name = name,
        order = order,
        flags = flags,
    )

    private fun tachiyomi.domain.entries.manga.model.Manga.toMcoll() = McollManga(
        source = source,
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = genre,
        status = status,
        thumbnailUrl = thumbnailUrl,
    )
}
