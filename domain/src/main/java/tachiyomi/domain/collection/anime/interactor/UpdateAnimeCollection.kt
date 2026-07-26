package tachiyomi.domain.collection.anime.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.collection.anime.repository.AnimeCollectionRepository
import tachiyomi.domain.collection.model.CollectionUpdate

class UpdateAnimeCollection(
    private val collectionRepository: AnimeCollectionRepository,
) {

    suspend fun await(payload: CollectionUpdate): Result = withNonCancellableContext {
        try {
            collectionRepository.updatePartialAnimeCollection(payload)
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
