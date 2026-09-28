package tachiyomi.domain.collection.shows.interactor

import tachiyomi.domain.collection.shows.repository.AnimeCollectionRepository

class GetAnimeCustomOrder(
    private val collectionRepository: AnimeCollectionRepository,
) {
    suspend fun await(collectionId: Long): List<Long> {
        return collectionRepository.getAnimeCustomOrder(collectionId)
    }
}
