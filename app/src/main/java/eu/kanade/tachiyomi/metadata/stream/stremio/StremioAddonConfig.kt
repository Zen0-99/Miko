package eu.kanade.tachiyomi.metadata.stream.stremio

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Manages Stremio stream addon URLs and configuration.
 *
 * Stores the addon base URLs (Torrentio, etc.) and debrid settings.
 * The addon URL is used by [StremioStreamAddonClient] to construct
 * stream API calls.
 */
class StremioAddonConfig(
    preferenceStore: PreferenceStore,
) {
    /**
     * Comma-separated list of Stremio addon base URLs.
     * Default: Torrentio.
     */
    private val addonUrlsPref: Preference<String> = preferenceStore.getString(
        "stremio_addon_urls",
        "https://torrentio.strem.fun",
    )

    /**
     * Debrid API key (empty = no debrid, use magnet+TorrServer).
     * Future: when set, streams are resolved via debrid instead of torrent server.
     */
    private val debridKeyPref: Preference<String> = preferenceStore.getString(
        "stremio_debrid_key",
        "",
    )

    /**
     * Debrid provider name: "realdebrid", "alldebrid", "premiumize", or "".
     */
    private val debridProviderPref: Preference<String> = preferenceStore.getString(
        "stremio_debrid_provider",
        "",
    )

    /**
     * Returns the list of configured addon base URLs.
     */
    fun getAddonUrls(): List<String> =
        addonUrlsPref.get().split(",").map { it.trim() }.filter { it.isNotBlank() }

    /**
     * Whether debrid is configured.
     */
    fun isDebridConfigured(): Boolean =
        debridKeyPref.get().isNotBlank() && debridProviderPref.get().isNotBlank()

    /**
     * Build the addon URL with debrid config params (if configured).
     *
     * Torrentio supports URL path params like:
     *   /debridkey=XXX/providers=YYY|ZZZ/stream/...
     *
     * This inserts the config segment between the base URL and the stream path.
     */
    fun buildAddonUrl(baseUrl: String): String {
        if (!isDebridConfigured()) return baseUrl
        val key = debridKeyPref.get()
        return "$baseUrl/debridkey=$key"
    }
}
