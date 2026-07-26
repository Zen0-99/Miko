package mihon.domain.items.episode.interactor

import tachiyomi.domain.collection.anime.interactor.GetAnimeCollections
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode

/**
 * Interactor responsible for determining which episode of an anime should be downloaded.
 *
 * @property getEpisodesByAnimeId Interactor for retrieving episodes by anime ID.
 * @property downloadPreferences User preferences related to episode downloads.
 * @property getCollections Interactor for retrieving collections associated with an anime.
 */
class FilterEpisodesForDownload(
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val downloadPreferences: DownloadPreferences,
    private val getCollections: GetAnimeCollections,
) {

    /**
     * Determines which episodes of an anime should be downloaded based on user preferences.
     *
     * @param anime The anime for which episodes may be downloaded.
     * @param newEpisodes The list of new episodes available for the anime.
     * @return A list of episodes that should be downloaded
     */
    suspend fun await(anime: Anime, newEpisodes: List<Episode>): List<Episode> {
        if (
            newEpisodes.isEmpty() ||
            !downloadPreferences.downloadNewEpisodes().get() ||
            !anime.shouldDownloadNewEpisodes()
        ) {
            return emptyList()
        }
        if (!downloadPreferences.downloadNewUnseenEpisodesOnly().get()) return newEpisodes
        val seenEpisodeNumbers = getEpisodesByAnimeId.await(anime.id)
            .asSequence()
            .filter { it.seen && it.isRecognizedNumber }
            .map { it.episodeNumber }
            .toSet()
        return newEpisodes.filterNot { it.episodeNumber in seenEpisodeNumbers }
    }

    /**
     * Determines whether new episodes should be downloaded for the anime based on user preferences and the
     * collections to which the anime belongs.
     *
     * @return `true` if episodes of the anime should be downloaded
     */
    private suspend fun Anime.shouldDownloadNewEpisodes(): Boolean {
        if (!favorite) return false
        val collections = getCollections.await(id).map { it.id }.ifEmpty { listOf(DEFAULT_COLLECTION_ID) }
        val includedCollections = downloadPreferences.downloadNewEpisodeCollections().get().map { it.toLong() }
        val excludedCollections = downloadPreferences.downloadNewEpisodeCollectionsExclude().get().map { it.toLong() }
        return when {
            // Default Download from all collections
            includedCollections.isEmpty() && excludedCollections.isEmpty() -> true
            // In excluded collection
            collections.any { it in excludedCollections } -> false
            // Included collection not selected
            includedCollections.isEmpty() -> true
            // In included collection
            else -> collections.any { it in includedCollections }
        }
    }

    companion object {
        private const val DEFAULT_COLLECTION_ID = 0L
    }
}
