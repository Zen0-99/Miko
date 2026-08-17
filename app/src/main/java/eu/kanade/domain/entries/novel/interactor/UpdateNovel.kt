package eu.kanade.domain.entries.novel.interactor

import eu.kanade.domain.entries.novel.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

class UpdateNovel(
    private val novelRepository: NovelRepository,
    private val coverCache: NovelCoverCache = Injekt.get(),
) {

    suspend fun await(novelUpdate: NovelUpdate): Boolean {
        return novelRepository.updateNovel(novelUpdate)
    }

    suspend fun awaitAll(novelUpdates: List<NovelUpdate>): Boolean {
        return novelRepository.updateAllNovel(novelUpdates)
    }

    suspend fun awaitUpdateFromSource(
        localNovel: Novel,
        remoteTitle: String,
        remoteAuthor: String?,
        remoteArtist: String?,
        remoteDescription: String?,
        remoteGenre: List<String>?,
        remoteThumbnailUrl: String?,
        remoteStatus: Long,
        remoteUpdateStrategy: eu.kanade.tachiyomi.novelsource.model.NovelUpdateStrategy,
        manualFetch: Boolean,
    ): Boolean {
        val title = if (remoteTitle.isEmpty() || localNovel.favorite) null else remoteTitle
        // Protect user edits for favorited novels — don't overwrite with source data
        val author = if (localNovel.favorite) null else remoteAuthor
        val artist = if (localNovel.favorite) null else remoteArtist
        val description = if (localNovel.favorite) null else remoteDescription
        val genre = if (localNovel.favorite) null else remoteGenre
        val status = if (localNovel.favorite) null else remoteStatus

        // Cover art checker: if the cover cache file doesn't exist (e.g., after
        // a backup restore where cover files weren't included), force a cover
        // refresh. This only checks for missing covers — existing covers are
        // not re-fetched.
        //
        // Key insight: use the LOCAL thumbnailUrl for the cover cache check,
        // not the remote one. The source (e.g. Anna's Archive) may return null
        // for thumbnail_url, but the novel already has a valid thumbnailUrl in
        // the database from the backup. We just need to invalidate the cover
        // cache so Coil re-fetches the image.
        val coverFileExists = coverCache.getCoverFile(localNovel.thumbnailUrl)?.exists() == true
        val hasLocalThumbnailUrl = !localNovel.thumbnailUrl.isNullOrEmpty()
        val hasRemoteThumbnailUrl = !remoteThumbnailUrl.isNullOrEmpty()
        android.util.Log.d("UpdateNovel", "awaitUpdateFromSource: novel=${localNovel.title} coverFileExists=$coverFileExists hasLocal=$hasLocalThumbnailUrl hasRemote=$hasRemoteThumbnailUrl localUrl=${localNovel.thumbnailUrl} remoteUrl=$remoteThumbnailUrl")

        val coverLastModified = when {
            // No cover URL anywhere — can't fetch a cover
            !hasLocalThumbnailUrl && !hasRemoteThumbnailUrl -> null
            // Cover cache file is missing — force refresh to re-fetch the image.
            // Use whichever thumbnailUrl is available (prefer remote if it changed,
            // fall back to local for sources that don't return thumbnail_url).
            !coverFileExists -> {
                coverCache.deleteFromCache(localNovel, false)
                Instant.now().toEpochMilli()
            }
            // Cover exists and URL hasn't changed — no need to refresh
            !manualFetch && localNovel.thumbnailUrl == remoteThumbnailUrl -> null
            // User has a custom cover — don't overwrite
            localNovel.hasCustomCover(coverCache) -> {
                coverCache.deleteFromCache(localNovel, false)
                null
            }
            // URL changed — refresh the cover
            else -> {
                coverCache.deleteFromCache(localNovel, false)
                Instant.now().toEpochMilli()
            }
        }

        // Use remote thumbnailUrl if provided, otherwise keep the local one.
        // This ensures we don't null out the thumbnailUrl when the source
        // doesn't return one (e.g. Anna's Archive).
        val thumbnailUrl = if (hasRemoteThumbnailUrl) {
            remoteThumbnailUrl?.takeIf { it.isNotEmpty() }
        } else {
            localNovel.thumbnailUrl
        }

        android.util.Log.d("UpdateNovel", "result: novel=${localNovel.title} coverLastModified=$coverLastModified thumbnailUrl=$thumbnailUrl")

        return novelRepository.updateNovel(
            NovelUpdate(
                id = localNovel.id,
                title = title,
                coverLastModified = coverLastModified,
                author = author,
                artist = artist,
                description = description,
                genre = genre,
                thumbnailUrl = thumbnailUrl,
                status = status,
                updateStrategy = remoteUpdateStrategy,
                initialized = true,
            ),
        )
    }

    suspend fun awaitUpdateLastUpdate(novelId: Long): Boolean {
        return novelRepository.updateNovel(NovelUpdate(id = novelId, lastUpdate = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateCoverLastModified(novelId: Long): Boolean {
        return novelRepository.updateNovel(NovelUpdate(id = novelId, coverLastModified = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateFavorite(novelId: Long, favorite: Boolean): Boolean {
        val dateAdded = when (favorite) {
            true -> Instant.now().toEpochMilli()
            false -> 0
        }
        return novelRepository.updateNovel(
            NovelUpdate(id = novelId, favorite = favorite, dateAdded = dateAdded),
        )
    }
}
