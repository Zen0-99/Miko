package eu.kanade.tachiyomi.source.novel

import eu.kanade.tachiyomi.source.novel.abs.AbsNovelSource
import eu.kanade.tachiyomi.source.novel.abs.AbsUrls

/**
 * Single predicate seam for "this entry/chapter is remote audio, not
 * text". `abs://` today; `lv://`/`ia://` join in 22-03 — extend the two
 * bodies here and every consumer follows.
 *
 * Consumed by `toDomainNovel`/`copyFrom` (VIEWER_FLAG_AUDIOBOOK stamping)
 * and by 22-02's downloader/player guards, which must keep audio-scheme
 * chapters out of the `getChapterText` path.
 */
object AudioChapterSchemes {

    /** `abs://item/…` (and later `lv://…`, `ia://…`) novel urls. */
    fun isAudioNovelUrl(url: String): Boolean = AbsUrls.isAbsNovelUrl(url)

    /** `abs://track/…` (and later `lv://…`, `ia://…`) chapter urls. */
    fun isAudioChapterUrl(url: String): Boolean = AbsUrls.isAbsChapterUrl(url)

    /**
     * Audio-scheme chapter url → the playable stream URI, or null when the
     * scheme is unknown, the url is malformed, or the owning source isn't
     * configured. This is the single dispatch point — 22-03 adds the
     * `lv://`/`ia://` arms.
     *
     * The returned URI carries NO credentials — `?token=` is never
     * appended; auth rides the `Authorization` header injected by
     * [eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook.AudiobookPlaybackService]'s
     * host-scoped data source (T-22-02-01).
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
        else -> null
    }
}
