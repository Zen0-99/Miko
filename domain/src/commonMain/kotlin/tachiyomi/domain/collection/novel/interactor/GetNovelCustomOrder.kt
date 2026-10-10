package tachiyomi.domain.collection.novel.interactor

import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository

class GetNovelCustomOrder(
    private val collectionRepository: NovelCollectionRepository,
) {
    suspend fun await(collectionId: Long): List<Long> {
        return collectionRepository.getNovelCustomOrder(collectionId)
    }
}
