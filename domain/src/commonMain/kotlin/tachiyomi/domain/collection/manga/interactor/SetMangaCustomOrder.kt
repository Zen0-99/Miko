package tachiyomi.domain.collection.manga.interactor

import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository

class SetMangaCustomOrder(
    private val collectionRepository: MangaCollectionRepository,
) {
    suspend fun await(collectionId: Long, mangaIds: List<Long>) {
        collectionRepository.setMangaCustomOrder(collectionId, mangaIds)
    }
}
