package tachiyomi.domain.collection.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.collection.model.Collection

class GetVisibleMangaCollections(
    private val collectionRepository: MangaCollectionRepository,
) {
    fun subscribe(): Flow<List<Collection>> {
        return collectionRepository.getAllVisibleMangaCollectionsAsFlow()
    }

    fun subscribe(mangaId: Long): Flow<List<Collection>> {
        return collectionRepository.getVisibleCollectionsByMangaIdAsFlow(mangaId)
    }

    suspend fun await(): List<Collection> {
        return collectionRepository.getAllVisibleMangaCollections()
    }

    suspend fun await(mangaId: Long): List<Collection> {
        return collectionRepository.getVisibleCollectionsByMangaId(mangaId)
    }
}
