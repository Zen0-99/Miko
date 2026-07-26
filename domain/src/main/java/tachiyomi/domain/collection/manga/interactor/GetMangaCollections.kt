package tachiyomi.domain.collection.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.collection.model.Collection

class GetMangaCollections(
    private val collectionRepository: MangaCollectionRepository,
) {
    fun subscribe(): Flow<List<Collection>> {
        return collectionRepository.getAllMangaCollectionsAsFlow()
    }

    fun subscribe(mangaId: Long): Flow<List<Collection>> {
        return collectionRepository.getCollectionsByMangaIdAsFlow(mangaId)
    }

    suspend fun await(): List<Collection> {
        return collectionRepository.getAllMangaCollections()
    }

    suspend fun await(mangaId: Long): List<Collection> {
        return collectionRepository.getCollectionsByMangaId(mangaId)
    }
}
