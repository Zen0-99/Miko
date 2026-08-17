package eu.kanade.domain.entries.manga.interactor

import eu.kanade.domain.entries.manga.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.ZonedDateTime

class UpdateManga(
    private val mangaRepository: MangaRepository,
    private val mangaFetchInterval: MangaFetchInterval,
) {

    suspend fun await(mangaUpdate: MangaUpdate): Boolean {
        return mangaRepository.updateManga(mangaUpdate)
    }

    suspend fun awaitAll(mangaUpdates: List<MangaUpdate>): Boolean {
        return mangaRepository.updateAllManga(mangaUpdates)
    }

    suspend fun awaitUpdateFromSource(
        localManga: Manga,
        remoteManga: SManga,
        manualFetch: Boolean,
        coverCache: MangaCoverCache = Injekt.get(),
    ): Boolean {
        val remoteTitle = try {
            remoteManga.title
        } catch (_: UninitializedPropertyAccessException) {
            ""
        }

        // if the manga isn't a favorite, set its title from source and update in db
        val title = if (remoteTitle.isEmpty() || localManga.favorite) null else remoteTitle

        // Cover art checker: if the cover cache file doesn't exist (e.g., after
        // a backup restore where cover files weren't included), force a cover
        // refresh. Uses the LOCAL thumbnailUrl for the check, not the remote —
        // the source may return null thumbnail_url but the manga already has
        // a valid URL in the database.
        val coverFileExists = coverCache.getCoverFile(localManga.thumbnailUrl)?.exists() == true
        val hasLocalThumbnailUrl = !localManga.thumbnailUrl.isNullOrEmpty()
        val hasRemoteThumbnailUrl = !remoteManga.thumbnail_url.isNullOrEmpty()
        android.util.Log.d("UpdateManga", "awaitUpdateFromSource: manga=${localManga.title} coverFileExists=$coverFileExists hasLocal=$hasLocalThumbnailUrl hasRemote=$hasRemoteThumbnailUrl localUrl=${localManga.thumbnailUrl} remoteUrl=${remoteManga.thumbnail_url}")

        val coverLastModified =
            when {
                // No cover URL anywhere — can't fetch a cover
                !hasLocalThumbnailUrl && !hasRemoteThumbnailUrl -> null
                // Cover cache file is missing — force refresh
                !coverFileExists -> {
                    coverCache.deleteFromCache(localManga, false)
                    val now = Instant.now().toEpochMilli()
                    android.util.Log.d("UpdateManga", "coverLastModified set to $now (cover file was missing, forcing refresh)")
                    now
                }
                // Cover exists and URL hasn't changed — no need to refresh
                !manualFetch && localManga.thumbnailUrl == remoteManga.thumbnail_url -> null
                localManga.isLocal() -> Instant.now().toEpochMilli()
                localManga.hasCustomCover(coverCache) -> {
                    coverCache.deleteFromCache(localManga, false)
                    null
                }
                else -> {
                    coverCache.deleteFromCache(localManga, false)
                    Instant.now().toEpochMilli()
                }
            }

        // Use remote thumbnailUrl if provided, otherwise keep the local one
        val thumbnailUrl = if (hasRemoteThumbnailUrl) {
            remoteManga.thumbnail_url?.takeIf { it.isNotEmpty() }
        } else {
            localManga.thumbnailUrl
        }

        android.util.Log.d("UpdateManga", "result: manga=${localManga.title} coverLastModified=$coverLastModified thumbnailUrl=$thumbnailUrl")

        return mangaRepository.updateManga(
            MangaUpdate(
                id = localManga.id,
                title = title,
                coverLastModified = coverLastModified,
                author = remoteManga.author,
                artist = remoteManga.artist,
                description = remoteManga.description,
                genre = remoteManga.getGenres(),
                thumbnailUrl = thumbnailUrl,
                status = remoteManga.status.toLong(),
                updateStrategy = remoteManga.update_strategy,
                initialized = true,
            ),
        )
    }

    suspend fun awaitUpdateFetchInterval(
        manga: Manga,
        dateTime: ZonedDateTime = ZonedDateTime.now(),
        window: Pair<Long, Long> = mangaFetchInterval.getWindow(dateTime),
    ): Boolean {
        return mangaRepository.updateManga(
            mangaFetchInterval.toMangaUpdate(manga, dateTime, window),
        )
    }

    suspend fun awaitUpdateLastUpdate(mangaId: Long): Boolean {
        return mangaRepository.updateManga(MangaUpdate(id = mangaId, lastUpdate = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateCoverLastModified(mangaId: Long): Boolean {
        return mangaRepository.updateManga(MangaUpdate(id = mangaId, coverLastModified = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateFavorite(mangaId: Long, favorite: Boolean): Boolean {
        val dateAdded = when (favorite) {
            true -> Instant.now().toEpochMilli()
            false -> 0
        }
        return mangaRepository.updateManga(
            MangaUpdate(id = mangaId, favorite = favorite, dateAdded = dateAdded),
        )
    }
}
