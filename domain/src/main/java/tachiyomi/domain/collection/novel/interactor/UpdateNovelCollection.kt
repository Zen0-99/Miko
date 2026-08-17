package tachiyomi.domain.collection.novel.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository
import tachiyomi.domain.collection.model.CollectionUpdate

class UpdateNovelCollection(
    private val collectionRepository: NovelCollectionRepository,
) {

    suspend fun await(payload: CollectionUpdate): Result = withNonCancellableContext {
        try {
            collectionRepository.updatePartialNovelCollection(payload)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class Error(val error: Exception) : Result
    }
}
