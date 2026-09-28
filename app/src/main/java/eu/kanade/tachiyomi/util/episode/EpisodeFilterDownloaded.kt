package eu.kanade.tachiyomi.util.episode

import eu.kanade.tachiyomi.data.download.shows.ShowsDownloadCache
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Returns a copy of the list with not downloaded chapters removed.
 */
fun List<Episode>.filterDownloadedEpisodes(anime: Anime): List<Episode> {
    if (anime.isLocal()) return this

    val downloadCache: ShowsDownloadCache = Injekt.get()

    return filter {
        downloadCache.isEpisodeDownloaded(
            it.name,
            it.scanlator,
            anime.title,
            anime.source,
            false,
        )
    }
}
