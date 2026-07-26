package tachiyomi.domain.collection.anime.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.anime.repository.AnimeCollectionRepository
import tachiyomi.domain.collection.model.CollectionUpdate
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

class DeleteAnimeCollection(
    private val collectionRepository: AnimeCollectionRepository,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
) {

    suspend fun await(collectionId: Long) = withNonCancellableContext {
        try {
            collectionRepository.deleteAnimeCollection(collectionId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val collections = collectionRepository.getAllAnimeCollections()
        val updates = collections.mapIndexed { index, collection ->
            CollectionUpdate(
                id = collection.id,
                order = index.toLong(),
            )
        }

        val defaultCollection = libraryPreferences.defaultAnimeCollection().get()
        if (defaultCollection == collectionId.toInt()) {
            libraryPreferences.defaultAnimeCollection().delete()
        }

        val collectionPreferences = listOf(
            libraryPreferences.animeUpdateCollections(),
            libraryPreferences.animeUpdateCollectionsExclude(),
            downloadPreferences.removeExcludeAnimeCollections(),
            downloadPreferences.downloadNewEpisodeCollections(),
            downloadPreferences.downloadNewEpisodeCollectionsExclude(),
        )
        val collectionIdString = collectionId.toString()
        collectionPreferences.forEach { preference ->
            val ids = preference.get()
            if (collectionIdString !in ids) return@forEach
            preference.set(ids.minus(collectionIdString))
        }

        try {
            collectionRepository.updatePartialAnimeCollections(updates)
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
