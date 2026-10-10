package tachiyomi.domain.collection.shows.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.shows.repository.AnimeCollectionRepository

class GetVisibleAnimeCollections(
    private val collectionRepository: AnimeCollectionRepository,
) {
    fun subscribe(): Flow<List<Collection>> {
        return collectionRepository.getAllVisibleAnimeCollectionsAsFlow()
    }

    fun subscribe(animeId: Long): Flow<List<Collection>> {
        return collectionRepository.getVisibleCollectionsByAnimeIdAsFlow(animeId)
    }

    suspend fun await(): List<Collection> {
        return collectionRepository.getAllVisibleAnimeCollections()
    }

    suspend fun await(animeId: Long): List<Collection> {
        return collectionRepository.getVisibleCollectionsByAnimeId(animeId)
    }
}
