package eu.kanade.tachiyomi.metadata.stream

import android.util.Log
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import miko.core.common.torrent.TorrentServerApi
import miko.core.common.torrent.TorrentServerUtils
import tachiyomi.core.common.util.lang.withIOContext

/**
 * Centralized magnet-to-HTTP URL resolution via TorrServer.
 *
 * Ensures TorrServer is started, adds the magnet, and returns a playable
 * HTTP URL. Both MPV and ExoPlayer receive only HTTP URLs — no magnet
 * handling in player code.
 *
 * Session-level cache avoids re-adding the same magnet.
 */
class MagnetResolver(
    private val torrentServerApi: TorrentServerApi,
    private val torrentServerUtils: TorrentServerUtils,
) {
    private val cache = mutableMapOf<String, String>() // magnet → HTTP URL

    /**
     * Resolve a magnet/torrent URL to a playable TorrServer HTTP URL.
     *
     * @param magnetUrl magnet link (may contain index= param)
     * @param title display title for the torrent in TorrServer
     * @return playable HTTP URL from TorrServer, or null on failure
     */
    suspend fun resolve(magnetUrl: String, title: String = "Stream"): String? = withIOContext {
        // Return cached resolution if available
        cache[magnetUrl]?.let { return@withIOContext it }

        // Ensure TorrServer is running in-process
        TorrentServerService.startInProcess()

        // Wait for server to be ready (up to 15 seconds)
        val ready = waitForTorrentServer()
        if (!ready) {
            Log.e("MagnetResolver", "TorrServer not ready after 15s")
            return@withIOContext null
        }

        // Extract file index from magnet URL
        var index = 0
        if (magnetUrl.contains("index=")) {
            index = try {
                magnetUrl.substringAfter("index=").substringBefore("&").toInt()
            } catch (_: NumberFormatException) {
                0
            }
        }

        // Add torrent to TorrServer
        val torrent = try {
            torrentServerApi.addTorrent(magnetUrl, title, "", "", false)
        } catch (e: Exception) {
            Log.e("MagnetResolver", "Failed to add torrent: ${e.message}")
            return@withIOContext null
        }

        // Get playable HTTP URL
        val httpUrl = torrentServerUtils.getTorrentPlayLink(torrent, index)
        Log.d("MagnetResolver", "Resolved magnet → $httpUrl")

        cache[magnetUrl] = httpUrl
        httpUrl
    }

    /**
     * Check if a URL is a magnet/torrent link that needs resolution.
     */
    fun needsResolution(url: String): Boolean {
        return url.startsWith("magnet") ||
            url.endsWith(".torrent") ||
            url.startsWith(torrentServerApi.hostUrl).not() && url.contains("infoHash")
    }

    private suspend fun waitForTorrentServer(): Boolean {
        repeat(15) {
            try {
                val echo = torrentServerApi.echo()
                if (echo.isNotEmpty()) {
                    Log.d("MagnetResolver", "TorrServer ready after ${it + 1}s")
                    return true
                }
            } catch (_: Exception) {
                // Server not ready yet
            }
            kotlinx.coroutines.delay(1000)
        }
        return false
    }

    /**
     * Clear the session cache. Call when leaving the player.
     */
    fun clearCache() {
        cache.clear()
    }
}
