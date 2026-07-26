package tachiyomi.domain.collection.novel.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository

class GetVisibleNovelCollections(
    private val collectionRepository: NovelCollectionRepository,
) {

    fun subscribe(): Flow<List<Collection>> {
        return collectionRepository.subscribeAllVisible()
    }

    suspend fun await(novelId: Long?): List<Collection> {
        return if (novelId != null) {
            collectionRepository.getVisibleCollectionsByNovelId(novelId)
        } else {
            collectionRepository.subscribeAllVisible().first()
        }
    }
}
