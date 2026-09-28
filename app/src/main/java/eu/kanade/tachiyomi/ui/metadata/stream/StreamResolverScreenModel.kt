package eu.kanade.tachiyomi.ui.metadata.stream

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.shows.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.metadata.stream.StreamResolver
import eu.kanade.tachiyomi.metadata.stream.stremio.StremioStreamResolver
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode

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

        /**
         * Live source-search results. Emitted incrementally as each provider
         * finishes — [searching] is true while providers are still in flight.
         * [providerStatuses] drives the provider pills (greyed while LOADING).
         */
        data class Searching(
            val candidates: List<StreamResolver.StreamCandidate>,
            val searching: Boolean = true,
            val providerStatuses: Map<String, StreamResolver.ProviderStatus> = emptyMap(),
            val timedOut: Boolean = false,
            val failedSources: List<String> = emptyList(),
        ) : State
        data object Empty : State
        data class Error(val message: String) : State
        data class Resolving(val candidate: StreamResolver.StreamCandidate) : State
        data class EpisodeResolved(val episode: Episode, val streamUrl: String? = null) : State
        data class NoMatch(val candidate: StreamResolver.StreamCandidate, val episodes: List<Episode>) : State
    }

    private var resolveJob: kotlinx.coroutines.Job? = null

    /**
     * Start resolving streaming sources for [anime].
     * Always shows the picker — the user must explicitly select a source.
     * Results stream in per-provider (optimistic loading) and are cached per
     * episode in [StreamResolver], so reopening the picker doesn't re-search.
     *
     * @param episodeUrl the Cinemeta episode URL (e.g. "cinemeta:episode:tt0903747:1:1")
     *                   Used to extract season/episode for Stremio series URL construction.
     * @param episodeName the Cinemeta episode name, shown on extension rows.
     */
    fun resolve(
        anime: Anime,
        episodeNumber: Double? = null,
        episodeUrl: String? = null,
        episodeName: String? = null,
    ) {
        resolveJob?.cancel()
        mutableState.update { State.Loading }
        resolveJob = screenModelScope.launchIO {
            try {
                // Extract season and episode from the Cinemeta episode URL
                // Format: "cinemeta:episode:tt0903747:1:1" (imdb:season:episode)
                val (season, parsedEpisode) = parseSeasonEpisode(episodeUrl)
                val episode = parsedEpisode ?: episodeNumber?.toInt()

                streamResolver.search(anime, season, episode, episodeName).collect { progress ->
                    mutableState.update {
                        when {
                            progress.finished && progress.candidates.isEmpty() && progress.timedOut ->
                                State.Error("Sources are taking too long. Try again or search manually.")
                            progress.finished && progress.candidates.isEmpty() -> State.Empty
                            else -> State.Searching(
                                candidates = progress.candidates,
                                searching = !progress.finished,
                                providerStatuses = progress.providerStatuses,
                                timedOut = progress.timedOut,
                                failedSources = progress.failedSources,
                            )
                        }
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
        mutableState.update { State.Resolving(candidate) }
        screenModelScope.launchIO {
            streamResolver.selectCandidate(cinemetaAnime, candidate)
            when (candidate) {
                is StreamResolver.StreamCandidate.Stremio -> resolveStremioStream(
                    candidate,
                    cinemetaAnime,
                    episodeUrl,
                    episodeName,
                )
                is StreamResolver.StreamCandidate.Extension -> fetchAndMapEpisode(
                    cinemetaAnime,
                    candidate,
                    episodeNumber,
                    episodeName,
                    episodeUrl,
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
        cinemetaAnime: Anime,
        episodeUrl: String?,
        episodeName: String?,
    ) {
        try {
            val magnetLink = stremioStreamResolver.toMagnetLink(candidate.stream)
            val title = candidate.stream.title ?: candidate.stream.filename ?: episodeName ?: "Stream"

            android.util.Log.d("StreamResolverSM", "resolveStremioStream: magnet=${magnetLink.take(60)}...")

            // The player needs a real DB episode — resolve the Cinemeta virtual
            // episode the user clicked (inserted by LoadCinemetaEpisodes). The magnet
            // travels alongside as a serialized hoster list so the player's magnet
            // pipeline (MagnetResolver → TorrServer) handles it at play time.
            val episode = getEpisodesByAnimeId.await(cinemetaAnime.id)
                .find { it.url == episodeUrl }
            if (episode == null) {
                logcat(LogPriority.ERROR) {
                    "resolveStremioStream: no DB episode matching url=$episodeUrl for anime ${cinemetaAnime.id}"
                }
                mutableState.update { State.Error("Couldn't load this episode — try reopening the title") }
                return
            }
            mutableState.update { State.EpisodeResolved(episode, streamUrl = magnetLink) }
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
        episodeUrl: String? = null,
    ) {
        try {
            val source = candidate.source
            // Materialize the DB row now that the user picked this source —
            // search candidates are lightweight refs so results don't spam
            // the anime table with one row per extension per entry.
            val sourceAnime = streamResolver.materializeExtension(candidate)

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
                    State.Error(
                        "This source has only 1 episode — it may be a movie, not the series. Try a different source.",
                    )
                }
                return
            }

            android.util.Log.d(
                "StreamResolverSM",
                "fetchAndMapEpisode: source=${source.name} (id=${source.id}), sourceAnime=${sourceAnime.title} (id=${sourceAnime.id}, url=${sourceAnime.url}), ${sEpisodes.size} episodes fetched, cinemetaType=${candidate.cinemetaType}",
            )

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
                    android.util.Log.d(
                        "StreamResolverSM",
                        "Matched by name: ${matchedByName.name}, id=${matchedByName.id}, animeId=${matchedByName.animeId}, url=${matchedByName.url}",
                    )
                    mutableState.update { State.EpisodeResolved(matchedByName) }
                    return
                }
            }

            // 2. MikoDB-aware episode targets: bound-entry local number,
            //    season number, or whole-series absolute — ordered by the
            //    source's numbering scheme. Falls back to the bare number.
            val imdbId = cinemetaAnime.url.split(":").getOrNull(2)
            val (season, epFromUrl) = parseSeasonEpisode(episodeUrl)
            val targets = streamResolver.episodeTargets(
                source.id,
                imdbId,
                season,
                epFromUrl ?: episodeNumber?.toInt(),
            )

            val matched = targets.firstNotNullOfOrNull { target ->
                episodes.find { it.episodeNumber.toInt() == target }
            }
            if (matched != null) {
                android.util.Log.d(
                    "StreamResolverSM",
                    "Matched by episode target: ${matched.name}, id=${matched.id}, animeId=${matched.animeId}, url=${matched.url}, targets=$targets",
                )
                mutableState.update { State.EpisodeResolved(matched) }
                return
            }

            // 4. No match — show all episodes for manual selection
            android.util.Log.w("StreamResolverSM", "No episode match found — showing manual selection")
            mutableState.update { State.NoMatch(candidate, episodes) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Episode fetch/mapping failed" }
            mutableState.update { State.Error(e.message ?: "Failed to load episodes") }
        }
    }

    /** "cinemeta:episode:tt0903747:1:1" → (season, episode) */
    private fun parseSeasonEpisode(episodeUrl: String?): Pair<Int?, Int?> {
        if (episodeUrl == null || !episodeUrl.startsWith("cinemeta:episode:")) return null to null
        val parts = episodeUrl.split(":")
        return parts.getOrNull(3)?.toIntOrNull() to parts.getOrNull(4)?.toIntOrNull()
    }

    /**
     * Clear the cached source mapping AND the resolved-results cache for
     * [anime] so the picker re-searches on next play.
     * Called from the "Change source" menu option.
     */
    fun clearCachedMapping(anime: Anime, episodeUrl: String? = null, episodeNumber: Double? = null) {
        streamResolver.clearMapping(anime)
        val (season, ep) = parseSeasonEpisode(episodeUrl)
        streamResolver.invalidateSearch(anime.url, season, ep ?: episodeNumber?.toInt())
    }

    /**
     * Reset to idle state. Stops collecting resolve progress — an in-flight
     * search keeps running in [StreamResolver] and is re-attached on the
     * next [resolve] call for the same episode.
     */
    fun reset() {
        resolveJob?.cancel()
        resolveJob = null
        mutableState.update { State.Idle }
    }
}
