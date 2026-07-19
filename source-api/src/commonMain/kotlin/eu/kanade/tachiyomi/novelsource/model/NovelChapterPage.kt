package eu.kanade.tachiyomi.novelsource.model

/**
 * A single page of chapters from a paginated chapter list.
 *
 * @param chapters The chapters on this page.
 * @param totalPages The total number of pages available (1 if source doesn't support pagination).
 * @param currentPage The page number that was fetched (1-indexed).
 */
data class NovelChapterPage(
    val chapters: List<SNovelChapter>,
    val totalPages: Int,
    val currentPage: Int,
)
