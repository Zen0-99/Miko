package tachiyomi.domain.collection.novel.interactor

import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate
import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository
import tachiyomi.domain.library.novel.model.NovelLibrarySort
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.random.Random

class SetSortModeForNovelCollection(
    private val preferences: LibraryPreferences,
    private val collectionRepository: NovelCollectionRepository,
) {

    suspend fun await(
        collectionId: Long?,
        type: NovelLibrarySort.Type,
        direction: NovelLibrarySort.Direction,
    ) {
        val collection = collectionId?.let { collectionRepository.getNovelCollection(it) }
        val flags = (collection?.flags ?: 0) + type + direction
        if (type == NovelLibrarySort.Type.Random) {
            preferences.randomNovelSortSeed().set(Random.nextInt())
        }
        if (collection != null && preferences.perCollectionDisplaySettings().get()) {
            collectionRepository.updatePartialNovelCollection(
                CollectionUpdate(
                    id = collection.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.novelSortingMode().set(NovelLibrarySort(type, direction))
            collectionRepository.updateAllNovelCollectionFlags(flags)
        }
    }

    suspend fun await(
        collection: Collection?,
        type: NovelLibrarySort.Type,
        direction: NovelLibrarySort.Direction,
    ) {
        await(collection?.id, type, direction)
    }
}
