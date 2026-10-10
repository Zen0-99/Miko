package tachiyomi.domain.history.shows.interactor

import tachiyomi.domain.history.shows.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.shows.repository.AnimeHistoryRepository

class RemoveAnimeHistory(
    private val repository: AnimeHistoryRepository,
) {

    suspend fun awaitAll(): Boolean {
        return repository.deleteAllAnimeHistory()
    }

    suspend fun await(history: AnimeHistoryWithRelations) {
        repository.resetAnimeHistory(history.id)
    }

    suspend fun await(animeId: Long) {
        repository.resetHistoryByAnimeId(animeId)
    }
}
