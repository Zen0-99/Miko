package tachiyomi.domain.collection.anime.interactor

import tachiyomi.domain.collection.anime.repository.AnimeCollectionRepository

class GetAnimeCustomOrder(
    private val collectionRepository: AnimeCollectionRepository,
) {
    suspend fun await(collectionId: Long): List<Long> {
        return collectionRepository.getAnimeCustomOrder(collectionId)
    }
}
