package eu.kanade.tachiyomi.source.novel

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
}
