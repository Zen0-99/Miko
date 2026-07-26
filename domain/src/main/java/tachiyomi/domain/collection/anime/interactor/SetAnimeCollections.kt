package tachiyomi.domain.collection.anime.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.repository.AnimeRepository

class SetAnimeCollections(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(animeId: Long, collectionIds: List<Long>) {
        try {
            animeRepository.setAnimeCollections(animeId, collectionIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
