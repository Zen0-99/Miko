package eu.kanade.tachiyomi.metadata.stream.stremio

import eu.kanade.tachiyomi.metadata.miko.dto.StremioStreamDto
import eu.kanade.tachiyomi.metadata.miko.dto.StremioStreamResponse
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat

/**
 * Generic client for Stremio addon `stream` resource.
 *
 * Calls the Stremio addon protocol's stream endpoint directly to get
 * torrent streams (infoHash + fileIdx) for a movie or series episode.
 *
 * Works with any Stremio stream addon (Torrentio, etc.) — not hardcoded
 * to a specific addon URL.
 */
class StremioStreamAddonClient(
    private val networkHelper: NetworkHelper,
    private val json: Json,
) {
    private val client: OkHttpClient get() = networkHelper.client

    /**
     * Fetch streams for a movie from a Stremio addon.
     *
     * @param addonBaseUrl e.g. "https://torrentio.strem.fun"
     * @param imdbId e.g. "tt10872600"
     * @return list of stream DTOs (infoHash + fileIdx + filename)
     */
    suspend fun getMovieStreams(addonBaseUrl: String, imdbId: String): List<StremioStreamDto> {
        val url = "$addonBaseUrl/stream/movie/$imdbId.json"
        return fetchStreams(url)
    }

    /**
     * Fetch streams for a specific series episode from a Stremio addon.
     *
     * @param addonBaseUrl e.g. "https://torrentio.strem.fun"
     * @param imdbId e.g. "tt0903747"
     * @param season 1-based season number
     * @param episode 1-based episode number
     * @return list of stream DTOs (infoHash + fileIdx + filename)
     */
    suspend fun getSeriesStreams(
        addonBaseUrl: String,
        imdbId: String,
        season: Int,
        episode: Int,
    ): List<StremioStreamDto> {
        val url = "$addonBaseUrl/stream/series/$imdbId:$season:$episode.json"
        return fetchStreams(url)
    }

    private suspend fun fetchStreams(url: String): List<StremioStreamDto> {
        return try {
            val response = client.newCall(GET(url)).awaitSuccess()
            val result = with(json) { response.parseAs<StremioStreamResponse>() }
            result.streams
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "StremioStreamAddonClient: failed to fetch $url" }
            emptyList()
        }
    }
}
