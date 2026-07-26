package tachiyomi.domain.collection.anime.interactor

import tachiyomi.domain.collection.anime.repository.AnimeCollectionRepository

class SetAnimeCustomOrder(
    private val collectionRepository: AnimeCollectionRepository,
) {
    suspend fun await(collectionId: Long, animeIds: List<Long>) {
        collectionRepository.setAnimeCustomOrder(collectionId, animeIds)
    }
}
