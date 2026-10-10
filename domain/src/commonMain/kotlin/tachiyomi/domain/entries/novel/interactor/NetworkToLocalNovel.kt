package tachiyomi.domain.entries.novel.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository

class NetworkToLocalNovel(
    private val novelRepository: NovelRepository,
) {
    /**
     * Per-(url, source) mutex: concurrent materializes (prefetch, detail
     * open, hub bind) would otherwise all lookup-miss and each insert a
     * fresh row. This serializes them so the first insert is found by
     * the rest.
     */
    private val locksGuard = Mutex()
    private val locks = LinkedHashMap<String, Mutex>()

    suspend fun await(novel: Novel): Novel {
        val lock = locksGuard.withLock { locks.getOrPut("${novel.source}:${novel.url}") { Mutex() } }
        return lock.withLock { awaitLocked(novel) }
    }

    private suspend fun awaitLocked(novel: Novel): Novel {
        val localNovel = novelRepository.getNovelByUrlAndSourceId(novel.url, novel.source)
        return when {
            localNovel == null -> {
                val id = novelRepository.insertNovel(novel)
                novel.copy(id = id ?: -1L)
            }
            !localNovel.favorite -> {
                // Refresh the stub in place — the previous insert-with-id
                // path was a no-op (`novelsQueries.insert` never binds _id),
                // so every materialize of a non-favorite row minted a
                // duplicate and scattered chapters/metadata across copies.
                val merged = localNovel.copy(
                    title = novel.title,
                    artist = novel.artist ?: localNovel.artist,
                    author = novel.author ?: localNovel.author,
                    description = novel.description ?: localNovel.description,
                    genre = novel.genre ?: localNovel.genre,
                    status = novel.status,
                    thumbnailUrl = novel.thumbnailUrl ?: localNovel.thumbnailUrl,
                    updateStrategy = novel.updateStrategy,
                    initialized = novel.initialized,
                )
                novelRepository.updateNovel(
                    NovelUpdate(
                        id = localNovel.id,
                        title = novel.title,
                        artist = novel.artist,
                        author = novel.author,
                        description = novel.description,
                        genre = novel.genre,
                        status = novel.status,
                        thumbnailUrl = novel.thumbnailUrl,
                        updateStrategy = novel.updateStrategy,
                        initialized = novel.initialized,
                    ),
                )
                merged
            }
            else -> localNovel
        }
    }
}
