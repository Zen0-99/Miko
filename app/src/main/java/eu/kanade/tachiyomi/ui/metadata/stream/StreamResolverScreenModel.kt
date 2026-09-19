package eu.kanade.tachiyomi.ui.metadata.stream

import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.metadata.stream.StreamResolver
import eu.kanade.tachiyomi.metadata.stream.stremio.StremioStreamResolver
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.entries.anime.model.Anime
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.common.util.lang.launchIO

/**
 * Screen model for the stream resolution flow.
 *
 * Manages the state of searching for streaming sources when the user
 * presses play on a Cinemeta entry. Handles:
 * - Resolving candidates via [StreamResolver] (Stremio addons + Aniyomi extensions)
 * - For Stremio streams: converting to magnet link and creating a synthetic episode
 * - For extension sources: fetching and syncing episodes from the selected source
 */
class StreamResolverScreenModel(
    private val streamResolver: StreamResolver,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val stremioStreamResolver: StremioStreamResolver,
) : StateScreenModel<StreamResolverScreenModel.State>(State.Idle) {

    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Success(
            val candidates: List<StreamResolver.StreamCandidate>,
            val timedOut: Boolean = false,
            val failedSources: List<String> = emptyList(),
        ) : State
        data object Empty : State
        data class Error(val message: String) : State
        data class Resolving(val candidate: StreamResolver.StreamCandidate) : State
        data class EpisodeResolved(val episode: Episode) : State
        data class NoMatch(val candidate: StreamResolver.StreamCandidate, val episodes: List<Episode>) : State
    }

    /**
     * Start resolving streaming sources for [anime].
     * Always shows the picker — the user must explicitly select a source.
     * Cached mappings are shown first in the list with a "Cached" badge.
     *
     * @param episodeUrl the Cinemeta episode URL (e.g. "cinemeta:episode:tt0903747:1:1")
     *                   Used to extract season/episode for Stremio series URL construction.
     */
    fun resolve(anime: Anime, episodeNumber: Double? = null, episodeUrl: String? = null) {
        mutableState.update { State.Loading }
        screenModelScope.launchIO {
            try {
                // Extract season and episode from the Cinemeta episode URL
                // Format: "cinemeta:episode:tt0903747:1:1" (imdb:season:episode)
                val season: Int?
                val episode: Int?
                if (episodeUrl != null && episodeUrl.startsWith("cinemeta:episode:")) {
                    val episodeParts = episodeUrl.split(":")
                    // cinemeta:episode:tt0903747:1:1 → [cinemeta, episode, tt0903747, 1, 1]
                    season = episodeParts.getOrNull(3)?.toIntOrNull()
                    episode = episodeParts.getOrNull(4)?.toIntOrNull()
                } else {
                    season = null
                    episode = episodeNumber?.toInt()
                }

                val result = streamResolver.resolve(anime, season, episode)
                mutableState.update {
                    when {
                        result.candidates.isEmpty() && result.timedOut ->
                            State.Error("Sources are taking too long. Try again or search manually.")
                        result.candidates.isEmpty() -> State.Empty
                        else -> State.Success(result.candidates, result.timedOut, result.failedSources)
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Stream resolution failed" }
                mutableState.update { State.Error(e.message ?: "Failed to search sources") }
            }
        }
    }

    /**
     * User selected a candidate. Cache the mapping and resolve the episode.
     *
     * For Stremio streams: convert to magnet link, create a synthetic Episode.
     * For extension sources: fetch episode list and match by number/name.
     *
     * @param cinemetaAnime the Cinemeta entry being played
     * @param candidate the user's chosen source
     * @param episodeNumber the Cinemeta virtual episode number (null for movies)
     * @param episodeName the Cinemeta virtual episode name (for name-based matching)
     * @param episodeUrl the Cinemeta episode URL (for Stremio magnet construction)
     */
    fun selectCandidate(
        cinemetaAnime: Anime,
        candidate: StreamResolver.StreamCandidate,
        episodeNumber: Double?,
        episodeName: String? = null,
        episodeUrl: String? = null,
    ) {
        streamResolver.selectCandidate(cinemetaAnime, candidate)
        mutableState.update { State.Resolving(candidate) }
        screenModelScope.launchIO {
            when (candidate) {
                is StreamResolver.StreamCandidate.Stremio -> resolveStremioStream(
                    candidate, episodeUrl, episodeName,
                )
                is StreamResolver.StreamCandidate.Extension -> fetchAndMapEpisode(
                    cinemetaAnime, candidate, episodeNumber, episodeName,
                )
            }
        }
    }

    /**
     * Resolve a Stremio stream to a playable episode.
     *
     * Converts the Stremio stream (infoHash + fileIdx) to a magnet link
     * using [StremioStreamResolver]. The magnet link is stored as the
     * episode URL — [MagnetResolver] resolves it to a TorrServer HTTP URL
     * at play time.
     */
    private suspend fun resolveStremioStream(
        candidate: StreamResolver.StreamCandidate.Stremio,
        episodeUrl: String?,
        episodeName: String?,
    ) {
        try {
            val magnetLink = stremioStreamResolver.toMagnetLink(candidate.stream)
            val title = candidate.stream.title ?: candidate.stream.filename ?: episodeName ?: "Stream"

            android.util.Log.d("StreamResolverSM", "resolveStremioStream: magnet=${magnetLink.take(60)}...")

            // Create a synthetic Episode with the magnet link as URL.
            // The player will resolve this via MagnetResolver at play time.
            val episode = Episode.create().copy(
                animeId = -1L, // Not tied to a source anime
                url = magnetLink,
                name = title,
                episodeNumber = 1.0,
                sourceOrder = 0,
                dateFetch = System.currentTimeMillis(),
            )
            mutableState.update { State.EpisodeResolved(episode) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Stremio stream resolution failed" }
            mutableState.update { State.Error(e.message ?: "Failed to resolve Stremio stream") }
        }
    }

    /**
     * Fetch the episode list from the selected extension source, sync to DB,
     * and find the episode matching [episodeNumber] and/or [episodeName].
     *
     * Matching strategy (in order):
     * 1. Exact name match (case-insensitive, trimmed)
     * 2. Episode number match (exact double)
     * 3. Episode number match (floored int)
     * 4. No match — show all episodes for manual selection
     */
    private suspend fun fetchAndMapEpisode(
        cinemetaAnime: Anime,
        candidate: StreamResolver.StreamCandidate.Extension,
        episodeNumber: Double?,
        episodeName: String? = null,
    ) {
        try {
            val source = candidate.source
            val sourceAnime = candidate.anime

            // Fetch episodes from the source
            val sEpisodes = source.getEpisodeList(sourceAnime.toSAnime())
            if (sEpisodes.isEmpty()) {
                mutableState.update { State.Error("No episodes found on ${source.name}") }
                return
            }

            // If the Cinemeta entry is a series but the source only has 1 episode,
            // it's likely a movie — warn the user and clear the bad cache
            val isSeries = candidate.cinemetaType == "series"
            if (isSeries && sEpisodes.size == 1 && episodeNumber != null && episodeNumber > 1) {
                logcat(LogPriority.WARN) {
                    "fetchAndMapEpisode: source has only 1 episode but we need episode $episodeNumber — likely a movie match, not a series. Clearing cache."
                }
                streamResolver.clearMapping(cinemetaAnime)
                mutableState.update {
                    State.Error("This source has only 1 episode — it may be a movie, not the series. Try a different source.")
                }
                return
            }

            android.util.Log.d("StreamResolverSM", "fetchAndMapEpisode: source=${source.name} (id=${source.id}), sourceAnime=${sourceAnime.title} (id=${sourceAnime.id}, url=${sourceAnime.url}), ${sEpisodes.size} episodes fetched, cinemetaType=${candidate.cinemetaType}")

            // Sync episodes to the source anime (not the Cinemeta anime)
            syncEpisodesWithSource.await(
                sEpisodes,
                sourceAnime,
                source,
                manualFetch = false,
            )

            // Get all episodes for the source anime from DB
            val episodes = getEpisodesByAnimeId.await(sourceAnime.id)
            if (episodes.isEmpty()) {
                mutableState.update { State.Error("Failed to load episodes from ${source.name}") }
                return
            }

            logcat(LogPriority.DEBUG) {
                "fetchAndMapEpisode: looking for epNum=$episodeNumber, name='$episodeName' in ${episodes.size} episodes from DB"
            }

            // 1. Try exact name match (case-insensitive, trimmed)
            if (!episodeName.isNullOrBlank()) {
                val cleanName = episodeName.trim()
                val matchedByName = episodes.find { ep ->
                    ep.name.trim().equals(cleanName, ignoreCase = true)
                }
                if (matchedByName != null) {
                    android.util.Log.d("StreamResolverSM", "Matched by name: ${matchedByName.name}, id=${matchedByName.id}, animeId=${matchedByName.animeId}, url=${matchedByName.url}")
                    mutableState.update { State.EpisodeResolved(matchedByName) }
                    return
                }
            }

            // 2. Try exact episode number match
            if (episodeNumber != null && episodeNumber >= 0) {
                val matched = episodes.find { it.episodeNumber == episodeNumber }
                if (matched != null) {
                    android.util.Log.d("StreamResolverSM", "Matched by episodeNumber: ${matched.name}, id=${matched.id}, animeId=${matched.animeId}, url=${matched.url}")
                    mutableState.update { State.EpisodeResolved(matched) }
                    return
                }
                // 3. Try floored int match
                val matchedInt = episodes.find { it.episodeNumber.toInt() == episodeNumber.toInt() }
                if (matchedInt != null) {
                    android.util.Log.d("StreamResolverSM", "Matched by floored episodeNumber: ${matchedInt.name}, id=${matchedInt.id}, animeId=${matchedInt.animeId}, url=${matchedInt.url}")
                    mutableState.update { State.EpisodeResolved(matchedInt) }
                    return
                }
            }

            // 4. No match — show all episodes for manual selection
            android.util.Log.w("StreamResolverSM", "No episode match found — showing manual selection")
            mutableState.update { State.NoMatch(candidate, episodes) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Episode fetch/mapping failed" }
            mutableState.update { State.Error(e.message ?: "Failed to load episodes") }
        }
    }

    /**
     * Clear the cached source mapping for [anime] so the picker shows again
     * on next play. Called from the "Change source" menu option.
     */
    fun clearCachedMapping(anime: Anime) {
        streamResolver.clearMapping(anime)
    }

    /**
     * Reset to idle state.
     */
    fun reset() {
        mutableState.update { State.Idle }
    }
}
