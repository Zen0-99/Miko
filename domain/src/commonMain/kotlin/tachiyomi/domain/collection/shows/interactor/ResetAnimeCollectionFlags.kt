package tachiyomi.domain.collection.shows.interactor

import tachiyomi.domain.collection.shows.repository.AnimeCollectionRepository
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class ResetAnimeCollectionFlags(
    private val preferences: LibraryPreferences,
    private val collectionRepository: AnimeCollectionRepository,
) {

    suspend fun await() {
        val sort = preferences.showsSortingMode().get()
        collectionRepository.updateAllAnimeCollectionFlags(sort.type + sort.direction)
    }
}
