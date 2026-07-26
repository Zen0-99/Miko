package tachiyomi.domain.collection.manga.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.repository.MangaRepository

class SetMangaCollections(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(mangaId: Long, collectionIds: List<Long>) {
        try {
            mangaRepository.setMangaCollections(mangaId, collectionIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
