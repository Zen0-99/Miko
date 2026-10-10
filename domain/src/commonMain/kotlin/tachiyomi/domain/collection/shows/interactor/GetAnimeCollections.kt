package tachiyomi.domain.collection.shows.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.shows.repository.AnimeCollectionRepository

class GetAnimeCollections(
    private val collectionRepository: AnimeCollectionRepository,
) {

    fun subscribe(): Flow<List<Collection>> {
        return collectionRepository.getAllAnimeCollectionsAsFlow()
    }

    fun subscribe(animeId: Long): Flow<List<Collection>> {
        return collectionRepository.getCollectionsByAnimeIdAsFlow(animeId)
    }

    suspend fun await(): List<Collection> {
        return collectionRepository.getAllAnimeCollections()
    }

    suspend fun await(animeId: Long): List<Collection> {
        return collectionRepository.getCollectionsByAnimeId(animeId)
    }
}
