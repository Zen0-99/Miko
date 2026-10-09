package eu.kanade.tachiyomi.source.novel

import eu.kanade.tachiyomi.source.novel.abs.AbsNovelSource
import eu.kanade.tachiyomi.source.novel.abs.AbsUrls
import java.net.URLDecoder

/**
 * Single predicate seam for "this entry/chapter is remote audio, not
 * text". `abs://` (22-01) plus `lv://`/`ia://` (22-03 SD-1) — extend the
 * two bodies here and every consumer follows.
 *
 * Consumed by `toDomainNovel`/`copyFrom` (VIEWER_FLAG_AUDIOBOOK stamping)
 * and by 22-02's downloader/player guards, which must keep audio-scheme
 * chapters out of the `getChapterText` path.
 */
object AudioChapterSchemes {

    const val LV_BOOK_PREFIX = "lv://book/"
    const val LV_TRACK_PREFIX = "lv://track/"
    const val LV_FILE_PREFIX = "lv://file/"
    const val IA_ITEM_PREFIX = "ia://item/"
    const val IA_TRACK_PREFIX = "ia://track/"

    /** `abs://item/…` ∪ `lv://book/…` ∪ `ia://item/…` novel urls. */
    fun isAudioNovelUrl(url: String): Boolean =
        AbsUrls.isAbsNovelUrl(url) ||
            url.startsWith(LV_BOOK_PREFIX) ||
            url.startsWith(IA_ITEM_PREFIX)

    /** `abs://track/…` ∪ `lv://track/…` ∪ `lv://file/…` ∪ `ia://track/…`
     *  chapter urls. */
    fun isAudioChapterUrl(url: String): Boolean =
        AbsUrls.isAbsChapterUrl(url) ||
            url.startsWith(LV_TRACK_PREFIX) ||
            url.startsWith(LV_FILE_PREFIX) ||
            url.startsWith(IA_TRACK_PREFIX)

    /**
     * Audio-scheme chapter url → the playable stream URI, or null when the
     * scheme is unknown, the url is malformed, or the owning source isn't
     * configured. This is the single dispatch point:
     *  - `abs://track/{itemId}/{ino}` → configured-server file endpoint
     *  - `lv://track/{iaId}/{file}` / `ia://track/{id}/{file}` →
     *    `https://archive.org/download/{ia}/{file}` — pure string parse,
     *    zero API calls at play time (22-03 SD-1)
     *  - `lv://file/{encoded}` → the decoded feed-supplied URL
     *
     * The returned URI carries NO credentials — `?token=` is never
     * appended; auth rides the `Authorization` header injected by
     * [eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlaybackService]'s
     * host-scoped data source (T-22-02-01). The lv://ia:// public arms
     * never touch credentials at all (T-22-03-03).
     */
    fun streamUriFor(chapterUrl: String, absSource: AbsNovelSource?): String? = when {
        AbsUrls.isAbsChapterUrl(chapterUrl) -> absSource
            ?.takeIf { it.client.isConfigured() }
            ?.let {
                AbsUrls.streamUrl(
                    it.client.baseUrl(),
                    AbsUrls.itemIdFrom(chapterUrl) ?: return null,
                    AbsUrls.inoFrom(chapterUrl) ?: return null,
                )
            }
        chapterUrl.startsWith(LV_TRACK_PREFIX) ->
            archiveDownloadUrl(chapterUrl.removePrefix(LV_TRACK_PREFIX))
        chapterUrl.startsWith(IA_TRACK_PREFIX) ->
            archiveDownloadUrl(chapterUrl.removePrefix(IA_TRACK_PREFIX))
        chapterUrl.startsWith(LV_FILE_PREFIX) ->
            decodedPublicUrl(chapterUrl.removePrefix(LV_FILE_PREFIX))
        else -> null
    }

    /**
     * `{iaId}/{file}` → `https://archive.org/download/{iaId}/{file}`.
     * The file name is the remainder after the first '/' (it may carry
     * slashes of its own); every segment must be non-`..` so a hostile
     * persisted url can't path-traverse off the item (T-22-03-02).
     */
    private fun archiveDownloadUrl(rest: String): String? {
        val segments = rest.split('/')
        if (segments.size < 2) return null
        if (segments.any { it.isBlank() || it == ".." }) return null
        return "https://archive.org/download/$rest"
    }

    /**
     * `lv://file/{encoded}` → the decoded URL, restricted to http(s) so a
     * hostile feed value can't mint `file://`/`intent://` streams
     * (T-22-03-02).
     */
    private fun decodedPublicUrl(encoded: String): String? =
        runCatching { URLDecoder.decode(encoded, "UTF-8") }
            .getOrNull()
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}
