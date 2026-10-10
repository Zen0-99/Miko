package tachiyomi.domain.entries.shows.interactor

import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.entries.shows.repository.AnimeRepository

class GetAnimeByUrlAndSourceId(
    private val animeRepository: AnimeRepository,
) {
    suspend fun await(url: String, sourceId: Long): Anime? {
        return animeRepository.getAnimeByUrlAndSourceId(url, sourceId)
    }
}
