package eu.kanade.tachiyomi.metadata.stream

import eu.kanade.tachiyomi.metadata.MetadataSourceManager
import kotlinx.coroutines.flow.first
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.repository.EpisodeRepository

/**
 * Loads "virtual" episodes from Cinemeta metadata for a Cinemeta anime entry.
 *
 * Cinemeta provides episode metadata (names, numbers, descriptions) via the
 * `videos` field in [MikoMeta]. These are "virtual" episodes — they have
 * metadata but no video URL. The actual video is resolved at play time by
 * [StreamResolver].
 *
 * The virtual episodes are inserted into the episode database with
 * `animeId` pointing to the Cinemeta anime entry, so they appear in the
 * episode list on the detail screen.
 */
class LoadCinemetaEpisodes(
    private val metadataSourceManager: MetadataSourceManager,
    private val episodeRepository: EpisodeRepository,
) {
    companion object {
        private const val CINEMETA_SOURCE_ID = 0L
    }

    /**
     * Fetch the full metadata from Cinemeta and insert virtual episodes
     * into the database for [cinemetaAnime].
     *
     * @param cinemetaAnime the Cinemeta anime entry (source == 0)
     * @return the list of virtual episodes inserted, or empty list if not a Cinemeta entry
     */
    suspend fun await(cinemetaAnime: tachiyomi.domain.entries.anime.model.Anime): List<Episode> {
        if (cinemetaAnime.source != CINEMETA_SOURCE_ID) return emptyList()

        // Parse the Cinemeta URL: "cinemeta:{type}:{id}"
        val parts = cinemetaAnime.url.split(":")
        if (parts.size < 3) return emptyList()
        val type = parts[1]
        val id = parts[2]

        val cinemetaSource = metadataSourceManager.sources.firstOrNull()
            ?: return emptyList()

        val meta = try {
            cinemetaSource.getMeta(type, id)
        } catch (e: Exception) {
            return emptyList()
        }

        val videos = meta.videos ?: return emptyList()
        if (videos.isEmpty()) return emptyList()

        // Check if episodes already exist in DB
        val existing = episodeRepository.getEpisodeByAnimeId(cinemetaAnime.id)
        if (existing.isNotEmpty()) return existing

        // Convert MikoEpisode to domain Episode
        val episodes = videos.mapIndexed { index, video ->
            Episode.create().copy(
                animeId = cinemetaAnime.id,
                url = "cinemeta:episode:${video.id}",
                name = video.name.ifBlank { "Episode ${video.episode}" },
                episodeNumber = video.episode.toDouble(),
                sourceOrder = index.toLong(),
                dateFetch = System.currentTimeMillis(),
                scanlator = null,
                summary = video.overview ?: video.description,
            )
        }

        return episodeRepository.addAllEpisodes(episodes)
    }
}
