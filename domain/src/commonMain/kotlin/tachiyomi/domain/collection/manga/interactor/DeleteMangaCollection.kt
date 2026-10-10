package tachiyomi.domain.collection.manga.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.collection.model.CollectionUpdate
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

class DeleteMangaCollection(
    private val collectionRepository: MangaCollectionRepository,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
) {

    suspend fun await(collectionId: Long) = withNonCancellableContext {
        try {
            collectionRepository.deleteMangaCollection(collectionId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val collections = collectionRepository.getAllMangaCollections()
        val updates = collections.mapIndexed { index, collection ->
            CollectionUpdate(
                id = collection.id,
                order = index.toLong(),
            )
        }

        val defaultCollection = libraryPreferences.defaultMangaCollection().get()
        if (defaultCollection == collectionId.toInt()) {
            libraryPreferences.defaultMangaCollection().delete()
        }

        val collectionPreferences = listOf(
            libraryPreferences.mangaUpdateCollections(),
            libraryPreferences.mangaUpdateCollections(),
            downloadPreferences.removeExcludeCollections(),
            downloadPreferences.downloadNewChapterCollections(),
            downloadPreferences.downloadNewChapterCollectionsExclude(),
        )
        val collectionIdString = collectionId.toString()
        collectionPreferences.forEach { preference ->
            val ids = preference.get()
            if (collectionIdString !in ids) return@forEach
            preference.set(ids.minus(collectionIdString))
        }

        try {
            collectionRepository.updatePartialMangaCollections(updates)
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
