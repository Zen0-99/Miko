package tachiyomi.domain.entries.shows.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import miko.domain.shows.SeasonAnime
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.entries.shows.repository.AnimeRepository
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.repository.EpisodeRepository

class GetAnimeWithEpisodesAndSeasons(
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun subscribe(id: Long): Flow<Triple<Anime, List<Episode>, List<SeasonAnime>>> {
        return combine(
            animeRepository.getAnimeByIdAsFlow(id),
            episodeRepository.getEpisodeByAnimeIdAsFlow(id),
            animeRepository.getAnimeSeasonsByIdAsFlow(id),
        ) { anime, episodes, seasons ->
            Triple(anime, episodes, seasons)
        }
    }

    suspend fun awaitAnime(id: Long): Anime {
        return animeRepository.getAnimeById(id)
    }

    suspend fun awaitEpisodes(id: Long): List<Episode> {
        return episodeRepository.getEpisodeByAnimeId(id)
    }

    suspend fun awaitSeasons(id: Long): List<SeasonAnime> {
        return animeRepository.getAnimeSeasonsById(id)
    }
}
