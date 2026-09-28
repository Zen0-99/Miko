package tachiyomi.domain.entries.shows.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.entries.shows.repository.AnimeRepository

class GetAnimeFavorites(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(): List<Anime> {
        return animeRepository.getAnimeFavorites()
    }

    fun subscribe(sourceId: Long): Flow<List<Anime>> {
        return animeRepository.getAnimeFavoritesBySourceId(sourceId)
    }
}
