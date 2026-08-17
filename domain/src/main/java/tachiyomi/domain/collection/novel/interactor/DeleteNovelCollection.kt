package tachiyomi.domain.collection.novel.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository
import tachiyomi.domain.collection.model.CollectionUpdate
import tachiyomi.domain.library.service.LibraryPreferences

class DeleteNovelCollection(
    private val collectionRepository: NovelCollectionRepository,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend fun await(collectionId: Long) = withNonCancellableContext {
        try {
            collectionRepository.delete(collectionId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val collections = collectionRepository.getAllNovelCollections()
        val updates = collections.mapIndexed { index, collection ->
            CollectionUpdate(
                id = collection.id,
                order = index.toLong(),
            )
        }

        val defaultCollection = libraryPreferences.defaultNovelCollection().get()
        if (defaultCollection == collectionId.toInt()) {
            libraryPreferences.defaultNovelCollection().delete()
        }

        val collectionPreferences = listOf(
            libraryPreferences.novelUpdateCollections(),
            libraryPreferences.novelUpdateCollectionsExclude(),
        )
        val collectionIdString = collectionId.toString()
        collectionPreferences.forEach { preference ->
            val ids = preference.get()
            if (collectionIdString !in ids) return@forEach
            preference.set(ids.minus(collectionIdString))
        }

        try {
            collectionRepository.updatePartialNovelCollections(updates)
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
