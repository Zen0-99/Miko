package tachiyomi.domain.collection.manga.interactor

import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class ResetMangaCollectionFlags(
    private val preferences: LibraryPreferences,
    private val collectionRepository: MangaCollectionRepository,
) {

    suspend fun await() {
        val sort = preferences.mangaSortingMode().get()
        collectionRepository.updateAllMangaCollectionFlags(sort.type + sort.direction)
    }
}
