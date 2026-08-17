package eu.kanade.tachiyomi.ui.metadata.stream

import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.metadata.stream.StreamResolver
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
 * - Resolving candidates via [StreamResolver]
 * - Fetching and syncing episodes from the selected source
 * - Mapping the Cinemeta virtual episode number to a real episode
 */
class StreamResolverScreenModel(
    private val streamResolver: StreamResolver,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
) : StateScreenModel<StreamResolverScreenModel.State>(State.Idle) {

    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Success(val candidates: List<StreamResolver.StreamCandidate>) : State
        data object Empty : State
        data class Error(val message: String) : State
        data class Resolving(val candidate: StreamResolver.StreamCandidate) : State
        data class EpisodeResolved(val episode: Episode) : State
        data class NoMatch(val candidate: StreamResolver.StreamCandidate, val episodes: List<Episode>) : State
    }

    /**
     * Start resolving streaming sources for [anime].
     */
    fun resolve(anime: Anime) {
        mutableState.update { State.Loading }
        screenModelScope.launchIO {
            try {
                val candidates = streamResolver.resolve(anime)
                mutableState.update {
                    when {
                        candidates.isEmpty() -> State.Empty
                        candidates.size == 1 && candidates.first().cached -> {
                            // Cached — go straight to episode resolution
                            State.Resolving(candidates.first())
                        }
                        else -> State.Success(candidates)
                    }
                }
                // If cached, auto-resolve the episode
                val state = state.value
                if (state is State.Resolving) {
                    fetchAndMapEpisode(anime, state.candidate, null)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Stream resolution failed" }
                mutableState.update { State.Error(e.message ?: "Failed to search sources") }
            }
        }
    }

    /**
     * User selected a candidate. Cache the mapping and fetch episodes.
     *
     * @param cinemetaAnime the Cinemeta entry being played
     * @param candidate the user's chosen source
     * @param episodeNumber the Cinemeta virtual episode number (null for movies)
     */
    fun selectCandidate(
        cinemetaAnime: Anime,
        candidate: StreamResolver.StreamCandidate,
        episodeNumber: Double?,
    ) {
        streamResolver.selectCandidate(cinemetaAnime, candidate)
        mutableState.update { State.Resolving(candidate) }
        screenModelScope.launchIO {
            fetchAndMapEpisode(cinemetaAnime, candidate, episodeNumber)
        }
    }

    /**
     * Fetch the episode list from the selected source, sync to DB,
     * and find the episode matching [episodeNumber].
     */
    private suspend fun fetchAndMapEpisode(
        cinemetaAnime: Anime,
        candidate: StreamResolver.StreamCandidate,
        episodeNumber: Double?,
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

            // Sync episodes to the source anime (not the Cinemeta anime)
            val newEpisodes = syncEpisodesWithSource.await(
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

            // Map by episode number
            if (episodeNumber != null && episodeNumber >= 0) {
                val matched = episodes.find { it.episodeNumber == episodeNumber }
                    ?: episodes.find { it.episodeNumber.toInt() == episodeNumber.toInt() }
                if (matched != null) {
                    mutableState.update { State.EpisodeResolved(matched) }
                    return
                }
            }

            // No match — show all episodes for manual selection
            mutableState.update { State.NoMatch(candidate, episodes) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Episode fetch/mapping failed" }
            mutableState.update { State.Error(e.message ?: "Failed to load episodes") }
        }
    }

    /**
     * Reset to idle state.
     */
    fun reset() {
        mutableState.update { State.Idle }
    }
}
