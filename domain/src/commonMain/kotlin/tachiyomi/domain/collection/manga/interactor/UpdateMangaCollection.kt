package tachiyomi.domain.collection.manga.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.collection.model.CollectionUpdate

class UpdateMangaCollection(
    private val collectionRepository: MangaCollectionRepository,
) {

    suspend fun await(payload: CollectionUpdate): Result = withNonCancellableContext {
        try {
            collectionRepository.updatePartialMangaCollection(payload)
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
