package tachiyomi.domain.collection.manga.interactor

import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository

class GetMangaCustomOrder(
    private val collectionRepository: MangaCollectionRepository,
) {
    suspend fun await(collectionId: Long): List<Long> {
        return collectionRepository.getMangaCustomOrder(collectionId)
    }
}
