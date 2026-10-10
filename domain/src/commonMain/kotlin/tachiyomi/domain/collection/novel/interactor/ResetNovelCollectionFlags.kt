package tachiyomi.domain.collection.novel.interactor

import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class ResetNovelCollectionFlags(
    private val preferences: LibraryPreferences,
    private val collectionRepository: NovelCollectionRepository,
) {

    suspend fun await() {
        val sort = preferences.novelSortingMode().get()
        collectionRepository.updateAllNovelCollectionFlags(sort.type + sort.direction)
    }
}
