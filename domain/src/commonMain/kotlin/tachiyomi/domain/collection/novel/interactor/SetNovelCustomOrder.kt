package tachiyomi.domain.collection.novel.interactor

import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository

class SetNovelCustomOrder(
    private val collectionRepository: NovelCollectionRepository,
) {
    suspend fun await(collectionId: Long, novelIds: List<Long>) {
        collectionRepository.setNovelCustomOrder(collectionId, novelIds)
    }
}
