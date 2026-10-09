package eu.kanade.tachiyomi.source.novel.abs

import java.security.MessageDigest

/**
 * URL scheme + endpoint helpers for the built-in Audiobookshelf source.
 *
 * ABS content is addressed inside Miko by two opaque URL shapes:
 *  - `abs://item/{itemId}`        — a library item, stored as Novel.url
 *  - `abs://track/{itemId}/{ino}` — one audio file, stored as a chapter url
 *
 * The `ino` is the server's stable file id used by
 * `/api/items/{id}/file/{ino}`; keeping it in the chapter url lets the
 * stream resolver rebuild a playable URL without a second item fetch.
 * `canon:`/`carrier:`/`miko:` are the existing scheme namespace — `abs://`
 * joins them as the audio-carrier scheme (lv://, ia:// follow in 22-03).
 */
object AbsUrls {

    const val ITEM_PREFIX = "abs://item/"
    const val TRACK_PREFIX = "abs://track/"

    fun itemUrl(itemId: String): String = "$ITEM_PREFIX$itemId"

    fun trackUrl(itemId: String, ino: String): String = "$TRACK_PREFIX$itemId/$ino"

    fun isAbsNovelUrl(url: String): Boolean = url.startsWith(ITEM_PREFIX)

    fun isAbsChapterUrl(url: String): Boolean = url.startsWith(TRACK_PREFIX)

    /**
     * `abs://item/{itemId}` or `abs://track/{itemId}/{ino}` → itemId.
     * Tolerates trailing segments on the item id.
     */
    fun itemIdFrom(url: String): String? {
        val rest = when {
            url.startsWith(ITEM_PREFIX) -> url.removePrefix(ITEM_PREFIX)
            url.startsWith(TRACK_PREFIX) -> url.removePrefix(TRACK_PREFIX)
            else -> return null
        }
        return rest.split('/').firstOrNull()?.takeIf { it.isNotBlank() }
    }

    /** `abs://track/{itemId}/{ino}` → ino. Tolerates trailing segments. */
    fun inoFrom(url: String): String? {
        if (!url.startsWith(TRACK_PREFIX)) return null
        return url.removePrefix(TRACK_PREFIX)
            .split('/')
            .getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Direct-file stream URL — Range-capable per RESEARCH §3.1. Auth rides
     * the `Authorization: Bearer` header; `?token=` is never used for API
     * calls (the query escape is reserved for binary GETs in 22-02 if
     * headers can't ride there).
     */
    fun streamUrl(baseUrl: String, itemId: String, ino: String): String =
        "${baseUrl.trimEnd('/')}/api/items/$itemId/file/$ino"

    fun coverUrl(baseUrl: String, itemId: String): String =
        "${baseUrl.trimEnd('/')}/api/items/$itemId/cover"

    /**
     * Stable 12-hex fingerprint of the configured server — namespaces
     * `abs:{serverKey}:{itemId}` external ids so two ABS servers can't
     * collide (RESEARCH §2.4). Derived from the normalized base url,
     * never stored.
     */
    fun serverKeyFor(baseUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalizeBaseUrl(baseUrl).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    /** Lowercase + strip trailing slashes — the host/scheme portion of a
     *  base url is case-insensitive, so case must not fork the key. */
    private fun normalizeBaseUrl(baseUrl: String): String =
        baseUrl.trim().lowercase().trimEnd('/')
}
