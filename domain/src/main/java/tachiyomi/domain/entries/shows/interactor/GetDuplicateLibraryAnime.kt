package tachiyomi.domain.entries.shows.interactor

import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.entries.shows.repository.AnimeRepository

class GetDuplicateLibraryAnime(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(anime: Anime): List<Anime> {
        return animeRepository.getDuplicateLibraryAnime(anime.id, anime.title.lowercase())
    }
}
