package eu.kanade.domain.download.shows.interactor

import eu.kanade.tachiyomi.data.download.shows.ShowsDownloadManager
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager

class DeleteEpisodeDownload(
    private val sourceManager: AnimeSourceManager,
    private val downloadManager: ShowsDownloadManager,
) {

    suspend fun awaitAll(anime: Anime, vararg episodes: Episode) = withNonCancellableContext {
        sourceManager.get(anime.source)?.let { source ->
            downloadManager.deleteEpisodes(episodes.toList(), anime, source)
        }
    }
}
