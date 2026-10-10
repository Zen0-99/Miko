package tachiyomi.domain.history.shows.interactor

import tachiyomi.domain.history.shows.model.AnimeHistoryUpdate
import tachiyomi.domain.history.shows.repository.AnimeHistoryRepository

class UpsertAnimeHistory(
    private val historyRepository: AnimeHistoryRepository,
) {

    suspend fun await(historyUpdate: AnimeHistoryUpdate) {
        historyRepository.upsertAnimeHistory(historyUpdate)
    }
}
