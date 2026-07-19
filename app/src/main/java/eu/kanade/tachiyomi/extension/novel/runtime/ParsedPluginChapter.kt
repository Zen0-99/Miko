package eu.kanade.tachiyomi.extension.novel.runtime

/**
 * Intermediate representation of a chapter parsed from a JS plugin's
 * `novelChapters` response before normalization and conversion to
 * [eu.kanade.tachiyomi.novelsource.model.SNovelChapter].
 *
 * All fields are nullable because plugins may omit them; the
 * [NovelPluginResultNormalizer] fills in defaults based on the
 * [NovelChapterFallbackPolicy].
 */
internal data class ParsedPluginChapter(
    val name: String? = null,
    val path: String? = null,
    val releaseTime: String? = null,
    val chapterNumber: Double? = null,
    val scanlator: String? = null,
    val page: String? = null,
)
