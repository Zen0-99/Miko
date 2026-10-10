package tachiyomi.domain.entries.shows.interactor

import tachiyomi.domain.entries.shows.repository.AnimeRepository

class ResetAnimeViewerFlags(
    private val animeRepository: AnimeRepository,
) {
    suspend fun await(): Boolean {
        return animeRepository.resetAnimeViewerFlags()
    }
}
