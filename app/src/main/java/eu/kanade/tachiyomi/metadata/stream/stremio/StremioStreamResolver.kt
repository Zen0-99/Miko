package eu.kanade.tachiyomi.metadata.stream.stremio

import eu.kanade.tachiyomi.metadata.miko.dto.StremioStreamDto
import java.net.URLEncoder

/**
 * Converts a [StremioStreamDto] (infoHash + fileIdx) into a playable URL.
 *
 * Two paths:
 * 1. Debrid (future): resolve infoHash via debrid API → direct HTTP download URL
 * 2. Magnet (current): construct a magnet link from infoHash + trackers + fileIdx
 *
 * The magnet link is then resolved to a TorrServer HTTP URL by [MagnetResolver]
 * before reaching the player.
 */
class StremioStreamResolver {

    /**
     * Construct a magnet link from a Stremio stream.
     *
     * Extracts tracker URLs from the stream's `sources` field (format: "tracker:URL")
     * and builds a standard magnet URI with the infoHash, filename, and file index.
     */
    fun toMagnetLink(stream: StremioStreamDto): String {
        val infoHash = stream.infoHash
            ?: throw IllegalArgumentException("Stream has no infoHash")

        val filename = stream.filename ?: stream.title ?: "stream"
        val encodedName = URLEncoder.encode(filename, "UTF-8")

        val trackers = stream.sources
            ?.filter { it.startsWith("tracker:") }
            ?.map { it.removePrefix("tracker:") }
            ?.filter { it.isNotBlank() }
            ?: DEFAULT_TRACKERS

        val trackerParams = trackers.joinToString("") { "&tr=${URLEncoder.encode(it, "UTF-8")}" }
        val indexParam = stream.fileIdx?.let { "&index=$it" } ?: ""

        return "magnet:?xt=urn:btih:$infoHash&dn=$encodedName$indexParam$trackerParams"
    }

    /**
     * Extract the file index from a Stremio stream.
     */
    fun fileIndex(stream: StremioStreamDto): Int = stream.fileIdx ?: 0

    companion object {
        // Default trackers used when the stream doesn't include tracker sources
        private val DEFAULT_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.torrent.eu.org:451/announce",
        )
    }
}
