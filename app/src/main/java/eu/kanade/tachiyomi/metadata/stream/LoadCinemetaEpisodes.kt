package eu.kanade.tachiyomi.metadata.stream

import eu.kanade.tachiyomi.metadata.MetadataSourceManager
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
        private val DATE_PARSER = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        private val DATE_PARSER_SIMPLE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
    }

    /**
     * Fetch the full metadata from Cinemeta and insert virtual episodes
     * into the database for [cinemetaAnime].
     *
     * For movies (type == "movie") with no videos, creates a single virtual
     * episode so the user can press play.
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

        val videos = meta.videos
        val newEpisodes = if (videos != null && videos.isNotEmpty()) {
            videos.mapIndexed { index, video ->
                Episode.create().copy(
                    animeId = cinemetaAnime.id,
                    url = "cinemeta:episode:${video.id}",
                    name = video.name.ifBlank { "Episode ${video.episode}" },
                    episodeNumber = video.episode.toDouble(),
                    sourceOrder = index.toLong(),
                    dateFetch = System.currentTimeMillis(),
                    dateUpload = parseDateToEpoch(video.firstAired ?: video.released),
                    // Encode season number and rating in scanlator for Cinemeta virtual
                    // episodes. Format: "Season N|★R.R" (rating omitted if null).
                    // The UI parses season for grouping and rating for display.
                    scanlator = buildString {
                        append("Season ${video.season}")
                        video.rating?.takeIf { it.isNotBlank() && it != "0" }?.let {
                            append("|★$it")
                        }
                    },
                    summary = video.overview ?: video.description,
                    previewUrl = video.thumbnail,
                )
            }
        } else if (type == "movie") {
            // Movies with no videos — create a single virtual episode
            listOf(
                Episode.create().copy(
                    animeId = cinemetaAnime.id,
                    url = "cinemeta:episode:$id",
                    name = cinemetaAnime.title,
                    episodeNumber = 1.0,
                    sourceOrder = 0,
                    dateFetch = System.currentTimeMillis(),
                    dateUpload = parseDateToEpoch(meta.released),
                    scanlator = null,
                    summary = meta.description,
                ),
            )
        } else {
            return emptyList()
        }

        logcat(LogPriority.DEBUG) {
            "LoadCinemetaEpisodes: new episodes scanlators: ${newEpisodes.take(5).map { it.scanlator }}"
        }

        // Check if episodes already exist in DB — if so, update their metadata
        // (dateUpload, scanlator, previewUrl, summary) to keep them in sync with
        // Cinemeta. This handles entries loaded by older code that didn't set these fields.
        val existing = episodeRepository.getEpisodeByAnimeId(cinemetaAnime.id)
        logcat(LogPriority.DEBUG) {
            "LoadCinemetaEpisodes: ${cinemetaAnime.title} — existing=${existing.size}, new=${newEpisodes.size}"
        }
        if (existing.isNotEmpty()) {
            val updates = mutableListOf<EpisodeUpdate>()
            val matchedIds = mutableSetOf<Long>()
            for (newEp in newEpisodes) {
                // Extract season prefix from new scanlator (e.g., "Season 1" from "Season 1|★7.7")
                val newSeasonPrefix = newEp.scanlator?.substringBefore("|")

                // Match by URL first (unique), then by episodeNumber + season prefix
                val match = existing.find { it.url == newEp.url && it.id !in matchedIds }
                    ?: existing.find {
                        it.episodeNumber == newEp.episodeNumber &&
                            it.scanlator?.substringBefore("|") == newSeasonPrefix &&
                            it.id !in matchedIds
                    }
                    ?: existing.find {
                        it.episodeNumber == newEp.episodeNumber &&
                            it.scanlator.isNullOrBlank() &&
                            it.id !in matchedIds
                    }
                if (match != null) {
                    matchedIds.add(match.id)
                    updates.add(
                        EpisodeUpdate(
                            id = match.id,
                            name = newEp.name,
                            dateUpload = newEp.dateUpload,
                            scanlator = newEp.scanlator,
                            summary = newEp.summary,
                            previewUrl = newEp.previewUrl,
                            sourceOrder = newEp.sourceOrder,
                        ),
                    )
                }
            }
            if (updates.isNotEmpty()) {
                logcat(LogPriority.DEBUG) {
                    "LoadCinemetaEpisodes: updating ${updates.size} episodes for ${cinemetaAnime.title}"
                }
                episodeRepository.updateAllEpisodes(updates)
            } else {
                logcat(LogPriority.WARN) {
                    "LoadCinemetaEpisodes: no updates matched for ${cinemetaAnime.title} (existing=${existing.size}, new=${newEpisodes.size})"
                }
            }
            // Return the updated episodes from DB (re-fetch to get updated values)
            val result = episodeRepository.getEpisodeByAnimeId(cinemetaAnime.id)
            logcat(LogPriority.DEBUG) {
                "LoadCinemetaEpisodes: result ${result.size} episodes, first 3 scanlators: ${result.take(3).map { it.scanlator }}"
            }
            return result
        }

        return episodeRepository.addAllEpisodes(newEpisodes)
    }

    /**
     * Parse an ISO date string to epoch millis.
     * Handles both "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" and "yyyy-MM-dd" formats.
     */
    private fun parseDateToEpoch(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        return try {
            DATE_PARSER.parse(dateString)?.time
                ?: DATE_PARSER_SIMPLE.parse(dateString)?.time
                ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
