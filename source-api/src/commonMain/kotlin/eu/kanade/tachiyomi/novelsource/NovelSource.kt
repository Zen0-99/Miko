package eu.kanade.tachiyomi.novelsource

import eu.kanade.tachiyomi.novelsource.model.NovelChapterPage
import eu.kanade.tachiyomi.novelsource.model.NovelComment
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter

/**
 * A basic interface for creating a novel source. It could be an online source, a local source, etc.
 */
interface NovelSource {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Whether this source is rate-limited and would benefit from a manual refresh option.
     * Sources with rate limiting may fail to fetch all data in one go.
     */
    val isRateLimited: Boolean
        get() = false

    /**
     * Whether this source supports reading chapter comments.
     * If true, the reader will show a comments button after each chapter.
     */
    val supportsComments: Boolean
        get() = false

    /**
     * Get the updated details for a novel.
     *
     * @param novel the novel to update.
     * @return the updated novel.
     */
    suspend fun getNovelDetails(novel: SNovel): SNovel

    /**
     * Get all the available chapters for a novel.
     *
     * @param novel the novel to update.
     * @return the chapters for the novel.
     */
    suspend fun getChapterList(novel: SNovel): List<SNovelChapter>

    /**
     * Get the latest chapters for a novel, fetching only enough to get
     * chapters beyond what the user already has.
     *
     * Sources that support pagination can override this to avoid fetching
     * the entire chapter list when only new chapters are needed.
     *
     * @param novel the novel to update.
     * @param existingCount the number of chapters the user already has.
     * @return the chapters for the novel (may be a partial list if the source supports incremental fetching).
     */
    suspend fun getLatestChapters(novel: SNovel, existingCount: Int): List<SNovelChapter> = getChapterList(novel)

    /**
     * Get a single page of chapters for a novel.
     *
     * Sources that support pagination can override this to allow incremental fetching
     * with progress tracking. The default implementation fetches all chapters on page 1.
     *
     * @param novel the novel to update.
     * @param page the page number (1-indexed).
     * @return a [NovelChapterPage] containing the chapters on this page and the total page count.
     */
    suspend fun getChapterListPage(novel: SNovel, page: Int): NovelChapterPage {
        if (page == 1) {
            val chapters = getChapterList(novel)
            return NovelChapterPage(chapters, 1, 1)
        }
        return NovelChapterPage(emptyList(), 1, 1)
    }

    /**
     * Get the text content of a chapter.
     *
     * @param chapter the chapter.
     * @return the text content (HTML or plain text) for the chapter.
     */
    suspend fun getChapterText(chapter: SNovelChapter): String

    /**
     * Get comments for a chapter.
     * Only called if [supportsComments] returns true.
     *
     * @param chapter the chapter to fetch comments for.
     * @return List of comments for this chapter.
     */
    suspend fun getChapterComments(chapter: SNovelChapter): List<NovelComment> = emptyList()
}
