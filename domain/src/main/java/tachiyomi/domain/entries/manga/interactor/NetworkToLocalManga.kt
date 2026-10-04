package tachiyomi.domain.entries.manga.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NetworkToLocalManga(
    private val mangaRepository: MangaRepository,
) {

    /** Serializes concurrent materializes so lookup-miss races can't mint
     *  duplicate canonical rows (same hazard as the novel path). */
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun await(manga: Manga): Manga {
        val lock = locks.getOrPut("${manga.source}:${manga.url}") { Mutex() }
        return lock.withLock { awaitLocked(manga) }
    }

    private suspend fun awaitLocked(manga: Manga): Manga {
        val localManga = getManga(manga.url, manga.source)
        return when {
            localManga == null -> {
                val id = insertManga(manga)
                manga.copy(id = id!!)
            }
            !localManga.favorite -> {
                // if the manga isn't a favorite, set its display title from source
                // if it later becomes a favorite, updated title will go to db
                localManga.copy(title = manga.title)
            }
            else -> {
                localManga
            }
        }
    }

    private suspend fun getManga(url: String, sourceId: Long): Manga? {
        return mangaRepository.getMangaByUrlAndSourceId(url, sourceId)
    }

    private suspend fun insertManga(manga: Manga): Long? {
        val mangaWithUuid = if (manga.uuid.isNullOrBlank()) {
            manga.copy(uuid = UUID.randomUUID().toString())
        } else {
            manga
        }
        return mangaRepository.insertManga(mangaWithUuid)
    }
}
