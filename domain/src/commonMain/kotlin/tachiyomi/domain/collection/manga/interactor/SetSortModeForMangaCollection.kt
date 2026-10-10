package tachiyomi.domain.collection.manga.interactor

import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.random.Random

class SetSortModeForMangaCollection(
    private val preferences: LibraryPreferences,
    private val collectionRepository: MangaCollectionRepository,
) {

    suspend fun await(
        collectionId: Long?,
        type: MangaLibrarySort.Type,
        direction: MangaLibrarySort.Direction,
    ) {
        val collection = collectionId?.let { collectionRepository.getMangaCollection(it) }
        val flags = (collection?.flags ?: 0) + type + direction
        if (type == MangaLibrarySort.Type.Random) {
            preferences.randomMangaSortSeed().set(Random.nextInt())
        }
        if (collection != null && preferences.perCollectionDisplaySettings().get()) {
            collectionRepository.updatePartialMangaCollection(
                CollectionUpdate(
                    id = collection.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.mangaSortingMode().set(MangaLibrarySort(type, direction))
            collectionRepository.updateAllMangaCollectionFlags(flags)
        }
    }

    suspend fun await(
        collection: Collection?,
        type: MangaLibrarySort.Type,
        direction: MangaLibrarySort.Direction,
    ) {
        await(collection?.id, type, direction)
    }
}
