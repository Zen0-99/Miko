package tachiyomi.domain.entries.manga.interactor

import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository

class GetMangaByUuid(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(uuid: String): Manga? {
        return mangaRepository.getMangaByUuid(uuid)
    }
}
