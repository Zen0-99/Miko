package tachiyomi.domain.collection.anime.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.anime.repository.AnimeCollectionRepository
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate

class HideAnimeCollection(
    private val collectionRepository: AnimeCollectionRepository,
) {

    suspend fun await(collection: Collection) = withNonCancellableContext {
        val update = CollectionUpdate(
            id = collection.id,
            hidden = !collection.hidden,
        )

        try {
            collectionRepository.updatePartialAnimeCollection(update)
            RenameAnimeCollection.Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed class Result {
        data object Success : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
