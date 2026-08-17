package tachiyomi.domain.collection.novel.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.library.service.LibraryPreferences

class CreateNovelCollectionWithName(
    private val collectionRepository: NovelCollectionRepository,
    private val preferences: LibraryPreferences,
) {

    private val initialFlags: Long
        get() {
            val sort = preferences.novelSortingMode().get()
            return sort.type.flag or sort.direction.flag
        }

    suspend fun await(name: String): Result = withNonCancellableContext {
        val collections = collectionRepository.getAllNovelCollections()
        val nextOrder = collections.maxOfOrNull { it.order }?.plus(1) ?: 0
        val newCollection = Collection(
            id = 0,
            name = name,
            order = nextOrder,
            flags = initialFlags,
            hidden = false,
        )

        try {
            collectionRepository.insertNovelCollection(newCollection)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
