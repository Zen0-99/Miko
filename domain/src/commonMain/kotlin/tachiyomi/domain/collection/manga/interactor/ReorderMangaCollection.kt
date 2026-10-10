package tachiyomi.domain.collection.manga.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate

class ReorderMangaCollection(
    private val collectionRepository: MangaCollectionRepository,
) {

    private val mutex = Mutex()

    suspend fun await(collection: Collection, newIndex: Int) = withNonCancellableContext {
        mutex.withLock {
            val collections = collectionRepository.getAllMangaCollections()
                .filterNot(Collection::isSystemCollection)
                .toMutableList()

            val currentIndex = collections.indexOfFirst { it.id == collection.id }
            if (currentIndex == -1) {
                return@withNonCancellableContext Result.Unchanged
            }

            try {
                collections.add(newIndex, collections.removeAt(currentIndex))

                val updates = collections.mapIndexed { index, collection ->
                    CollectionUpdate(
                        id = collection.id,
                        order = index.toLong(),
                    )
                }

                collectionRepository.updatePartialMangaCollections(updates)
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
