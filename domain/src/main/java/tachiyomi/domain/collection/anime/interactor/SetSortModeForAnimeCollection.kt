package tachiyomi.domain.collection.anime.interactor

import tachiyomi.domain.collection.anime.repository.AnimeCollectionRepository
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.random.Random

class SetSortModeForAnimeCollection(
    private val preferences: LibraryPreferences,
    private val collectionRepository: AnimeCollectionRepository,
) {

    suspend fun await(
        collectionId: Long?,
        type: AnimeLibrarySort.Type,
        direction: AnimeLibrarySort.Direction,
    ) {
        val collection = collectionId?.let { collectionRepository.getAnimeCollection(it) }
        val flags = (collection?.flags ?: 0) + type + direction
        if (type == AnimeLibrarySort.Type.Random) {
            preferences.randomAnimeSortSeed().set(Random.nextInt())
        }
        if (collection != null && preferences.perCollectionDisplaySettings().get()) {
            collectionRepository.updatePartialAnimeCollection(
                CollectionUpdate(
                    id = collection.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.animeSortingMode().set(AnimeLibrarySort(type, direction))
            collectionRepository.updateAllAnimeCollectionFlags(flags)
        }
    }

    suspend fun await(
        collection: Collection?,
        type: AnimeLibrarySort.Type,
        direction: AnimeLibrarySort.Direction,
    ) {
        await(collection?.id, type, direction)
    }
}
