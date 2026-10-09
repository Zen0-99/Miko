package eu.kanade.tachiyomi.source.novel.librivox

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.novel.PoliteFetch
import kotlinx.serialization.decodeFromString
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Built-in LibriVox source — ~19k public-domain audiobooks as a
 * first-class catalogue with zero configuration (22-03, SD-2/SD-4/SD-5).
 *
 * Fixed id [LIBRIVOX_SOURCE_ID] = -2L — reserved beside ABS (-1L) and IA
 * (-3L); negative ids can never collide with extension `generateId`
 * MD5s. Seeded into AndroidNovelSourceManager via `builtInSources`.
 *
 * Items materialize as `lv://book/{bookId}` novels; sections as
 * `lv://track/{iaIdentifier}/{fileName}` chapters (pure-parse →
 * archive.org/download stream) or `lv://file/{urlencoded}` when a
 * `listen_url` isn't archive.org-shaped (SD-1). Chapters are audio —
 * [getChapterText] throws (22-01 SD-8 convention).
 *
 * Only `status=complete` projects are served — in-progress recordings
 * point at churning `librivox.org/uploads/` temp space (plan
 * prohibition). Every request funnels through [PoliteFetch]: ≤1 rps,
 * descriptive UA, Retry-After/backoff — no auth headers ever.
 */
class LibriVoxSource : NovelCatalogueSource {

    override val id: Long = LIBRIVOX_SOURCE_ID
    override val name: String = "LibriVox"
    override val lang: String = "all"
    override val supportsLatest: Boolean = true

    /**
     * Transport seam — swapped by tests, [PoliteFetch] in production.
     * [query] carries the full `?format=json&…` query string for
     * `https://librivox.org/api/feed/audiobooks`.
     */
    internal var transport: suspend (query: String) -> String = { query ->
        PoliteFetch.get(FEED_BASE + query)
    }

    // ---- Browse ------------------------------------------------------------

    override suspend fun getPopularNovels(page: Int): NovelsPage =
        getPopularNovels(page, getFilterList())

    /** `status=complete&extended=1&limit=50&offset=` (SD-2). */
    override suspend fun getPopularNovels(page: Int, filters: NovelFilterList): NovelsPage {
        val feed = fetchFeed(feedQuery(page, filters))
        return NovelsPage(
            feed.books.map(::bookToSNovel),
            hasNextPage = feed.books.size >= PAGE_SIZE,
        )
    }

    /** `&order_by=id` per SD-2 — feed order is the fallback the live
     *  checkpoint validates. */
    override suspend fun getLatestUpdates(page: Int): NovelsPage =
        getLatestUpdates(page, getFilterList())

    override suspend fun getLatestUpdates(page: Int, filters: NovelFilterList): NovelsPage {
        val feed = fetchFeed(feedQuery(page, filters, orderById = true))
        return NovelsPage(
            feed.books.map(::bookToSNovel),
            hasNextPage = feed.books.size >= PAGE_SIZE,
        )
    }

    /** `&title={q}` + `&author=`/`&genre=` text filters server-side (SD-2). */
    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        if (query.isBlank()) return getPopularNovels(page, filters)
        val feed = fetchFeed(feedQuery(page, filters, title = query))
        return NovelsPage(
            feed.books.map(::bookToSNovel),
            hasNextPage = feed.books.size >= PAGE_SIZE,
        )
    }

    override fun getFilterList(): NovelFilterList = NovelFilterList(
        AuthorFilter(),
        GenreFilter(),
    )

    // ---- Details / chapters --------------------------------------------------

    /** `?id={bookId}&extended=1` → full SNovel; readers → artist (SD-4). */
    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        val book = fetchBook(bookIdFrom(novel.url) ?: return novel) ?: return novel
        return novel.copy().apply {
            title = book.title.takeIf { it.isNotBlank() } ?: title
            author = book.authors.map { it.displayName() }
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            artist = book.readers.map { it.displayName() }
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            description = book.description.takeIf { it.isNotBlank() }
            genre = book.genres.map { it.name }
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            thumbnail_url = book.coverart_jpg.takeIf { it.isNotBlank() }
                ?: book.coverart_medium.takeIf { it.isNotBlank() }
                ?: book.coverart_thumbnail.takeIf { it.isNotBlank() }
            status = SNovel.COMPLETED
            initialized = true
        }
    }

    /**
     * `sections[]` → one chapter per playable segment (SD-5):
     * archive.org-shaped `listen_url`s ride `lv://track/{iaId}/{file}`
     * (pure-parse stream, no play-time API call); anything else falls
     * back to `lv://file/{encoded}`. Sections without a `listen_url`
     * are skipped with a warn — never a crash.
     */
    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        val book = fetchBook(bookIdFrom(novel.url) ?: return emptyList()) ?: return emptyList()
        val readers = book.readers.map { it.displayName() }
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
        val dateUpload = copyrightYearToMs(book.copyright_year)
        var skipped = 0
        val chapters = book.sections.mapNotNull { section ->
            val url = lvChapterUrl(section)
            if (url == null) {
                skipped++
                null
            } else {
                SNovelChapter.create().apply {
                    this.url = url
                    name = section.title.takeIf { it.isNotBlank() }
                        ?: "Section ${section.section_number}"
                    chapter_number = section.section_number.toFloatOrNull() ?: 0f
                    date_upload = dateUpload
                    scanlator = readers
                }
            }
        }
        if (skipped > 0) {
            logcat(LogPriority.WARN) {
                "LibriVox book ${book.id}: $skipped/${book.sections.size} sections " +
                    "had no listen_url — skipped (SD-5)"
            }
        }
        return chapters
    }

    /** Audio chapters — no text (22-01 SD-8 convention). */
    override suspend fun getChapterText(chapter: SNovelChapter): String {
        throw UnsupportedOperationException("LibriVox chapters are audio tracks — no text")
    }

    // ---- plumbing ------------------------------------------------------------

    private suspend fun fetchFeed(query: String): LvFeedResponse =
        lvJson.decodeFromString(transport(query))

    /** `?format=json&id={id}&extended=1` → the single book, or null. */
    private suspend fun fetchBook(bookId: String): LvBook? =
        fetchFeed("?format=json&id=${urlEncode(bookId)}&extended=1")
            .books.firstOrNull()

    private fun feedQuery(
        page: Int,
        filters: NovelFilterList,
        title: String? = null,
        orderById: Boolean = false,
    ): String = buildString {
        append("?format=json&status=complete&extended=1")
        append("&limit=$PAGE_SIZE&offset=${(page - 1).coerceAtLeast(0) * PAGE_SIZE}")
        title?.let { append("&title=${urlEncode(it)}") }
        filters.filterIsInstance<AuthorFilter>().firstOrNull()
            ?.state?.takeIf { it.isNotBlank() }
            ?.let { append("&author=${urlEncode(it)}") }
        filters.filterIsInstance<GenreFilter>().firstOrNull()
            ?.state?.takeIf { it.isNotBlank() }
            ?.let { append("&genre=${urlEncode(it)}") }
        if (orderById) append("&order_by=id")
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    // ---- mapping -------------------------------------------------------------

    private fun bookToSNovel(book: LvBook): SNovel = SNovel.create().apply {
        url = bookUrl(book.id)
        title = book.title
        author = book.authors.map { it.displayName() }
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
        artist = book.readers.map { it.displayName() }
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
        description = book.description.takeIf { it.isNotBlank() }
        genre = book.genres.map { it.name }
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
        thumbnail_url = book.coverart_jpg.takeIf { it.isNotBlank() }
            ?: book.coverart_medium.takeIf { it.isNotBlank() }
            ?: book.coverart_thumbnail.takeIf { it.isNotBlank() }
    }

    private class AuthorFilter : NovelFilter.Text("Author")

    private class GenreFilter : NovelFilter.Text("Genre")

    companion object {
        /** Reserved built-in id — ABS=-1L, IA=-3L (22-01 companion note). */
        const val LIBRIVOX_SOURCE_ID = -2L

        private const val FEED_BASE = "https://librivox.org/api/feed/audiobooks"
        private const val PAGE_SIZE = 50
        private const val LV_BOOK_PREFIX = "lv://book/"
        private const val LV_TRACK_PREFIX = "lv://track/"
        private const val LV_FILE_PREFIX = "lv://file/"

        /** `archive.org/download/{iaId}/{file}` — the stable shape
         *  `status=complete` sections carry (RESEARCH §9 #6). */
        private val ARCHIVE_DOWNLOAD = Regex("""^https?://archive\.org/download/([^/]+)/(.+)$""")

        fun bookUrl(bookId: String): String = "$LV_BOOK_PREFIX$bookId"

        /** `lv://book/{id}` → id, tolerating trailing segments. */
        fun bookIdFrom(url: String): String? =
            url.takeIf { it.startsWith(LV_BOOK_PREFIX) }
                ?.removePrefix(LV_BOOK_PREFIX)
                ?.split('/')
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }

        /**
         * section → `lv://track/{iaId}/{file}` for archive.org listen_urls,
         * else `lv://file/{urlencoded}`; null when `listen_url` is absent
         * (SD-5 skip).
         */
        internal fun lvChapterUrl(section: LvSection): String? {
            val listen = section.listen_url?.takeIf { it.isNotBlank() } ?: return null
            val match = ARCHIVE_DOWNLOAD.find(listen)
            return if (match != null) {
                "$LV_TRACK_PREFIX${match.groupValues[1]}/${match.groupValues[2]}"
            } else {
                "$LV_FILE_PREFIX${URLEncoder.encode(listen, "UTF-8")}"
            }
        }

        /** `copyright_year` ("2008") → epoch ms; 0L when unparseable. */
        internal fun copyrightYearToMs(year: String?): Long =
            year?.trim()?.toIntOrNull()?.let { y ->
                runCatching {
                    LocalDate.of(y, 1, 1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli()
                }.getOrDefault(0L)
            } ?: 0L
    }
}
