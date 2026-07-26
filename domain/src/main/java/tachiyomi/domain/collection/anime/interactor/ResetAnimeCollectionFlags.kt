package tachiyomi.domain.collection.anime.interactor

import tachiyomi.domain.collection.anime.repository.AnimeCollectionRepository
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class ResetAnimeCollectionFlags(
    private val preferences: LibraryPreferences,
    private val collectionRepository: AnimeCollectionRepository,
) {

    suspend fun await() {
        val sort = preferences.animeSortingMode().get()
        collectionRepository.updateAllAnimeCollectionFlags(sort.type + sort.direction)
    }
}
