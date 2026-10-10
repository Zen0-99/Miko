package tachiyomi.domain.collection.shows.interactor

import tachiyomi.domain.collection.shows.repository.AnimeCollectionRepository

class SetAnimeCustomOrder(
    private val collectionRepository: AnimeCollectionRepository,
) {
    suspend fun await(collectionId: Long, animeIds: List<Long>) {
        collectionRepository.setAnimeCustomOrder(collectionId, animeIds)
    }
}
