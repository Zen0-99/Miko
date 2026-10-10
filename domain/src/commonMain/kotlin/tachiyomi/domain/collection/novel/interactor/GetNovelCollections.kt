package tachiyomi.domain.collection.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository

class GetNovelCollections(
    private val collectionRepository: NovelCollectionRepository,
) {

    fun subscribe(): Flow<List<Collection>> {
        return collectionRepository.subscribeAll()
    }

    suspend fun await(): List<Collection> {
        return collectionRepository.getAllNovelCollections()
    }

    suspend fun await(novelId: Long): List<Collection> {
        return collectionRepository.getCollectionsByNovelId(novelId)
    }
}
