package eu.kanade.tachiyomi.source.novel.librivox

import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.novel.AudioChapterSchemes
import eu.kanade.tachiyomi.source.novel.PoliteFetch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel

/**
 * Fixture-driven vertical of the built-in LibriVox source: canned
 * `audiobooks` feed + `id=` detail payloads → [LibriVoxSource]
 * browse/details/chapter-list → `lv://`-scheme rows → `streamUriFor`
 * archive.org resolution → `toDomainNovel` flag stamp. The `transport`
 * seam returns fixture text and records every query so the
 * `status=complete&extended=1` feed shape is asserted — no OkHttp, no
 * Android, no Injekt. [PoliteFetch] spacing/backoff is exercised with
 * fake clock + delay seams.
 */
class LibriVoxSourceTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)) { "missing fixture $name" }
            .readText()

    private inner class FixtureFeed {
        val recorded = mutableListOf<String>()
        val source = LibriVoxSource()

        init {
            source.transport = { query ->
                recorded += query
                when {
                    query.contains("&id=") || query.contains("?format=json&id=") ->
                        fixture("librivox_book.json")
                    else -> fixture("librivox_feed.json")
                }
            }
        }
    }

    private fun lvNovel(bookId: String = "15147", title: String = "stub") =
        SNovel.create().apply {
            url = LibriVoxSource.bookUrl(bookId)
            this.title = title
        }

    // ---- browse --------------------------------------------------------------

    @Test
    fun `popular maps feed books to lv-scheme novels`() = runTest {
        val feed = FixtureFeed()
        val page = feed.source.getPopularNovels(1)

        assertEquals(2, page.novels.size)
        assertFalse(page.hasNextPage) // 2 < limit=50
        val query = feed.recorded.single()
        assertTrue(query.contains("status=complete"), query)
        assertTrue(query.contains("extended=1"), query)
        assertTrue(query.contains("limit=50"), query)
        assertTrue(query.contains("offset=0"), query)

        val pride = page.novels[0]
        assertEquals("lv://book/15147", pride.url)
        assertEquals("Pride and Prejudice", pride.title)
        assertEquals("Jane Austen", pride.author)
        assertEquals("Karen Savage", pride.artist)
        assertEquals(
            "https://archive.org/download/LibrivoxCdCoverArt4/pride_prejudice_1302.jpg",
            pride.thumbnail_url,
        )
        assertEquals("Romance, General Fiction", pride.genre)
    }

    @Test
    fun `page two offsets by the page size`() = runTest {
        val feed = FixtureFeed()
        feed.source.getPopularNovels(2)
        assertTrue(feed.recorded.single().contains("offset=50"))
    }

    @Test
    fun `search sends title and text filters`() = runTest {
        val feed = FixtureFeed()
        val filters = feed.source.getFilterList()
        // Drive the Author/Genre text filters directly — classes are
        // private; set state by index on the returned list.
        (filters[0] as eu.kanade.tachiyomi.novelsource.model.NovelFilter.Text).state = "Austen"
        (filters[1] as eu.kanade.tachiyomi.novelsource.model.NovelFilter.Text).state = "Romance"
        val page = feed.source.getSearchNovels(1, "pride & prejudice", filters)

        assertEquals(2, page.novels.size)
        val query = feed.recorded.single()
        assertTrue(query.contains("&title="), query)
        assertTrue(query.contains("pride"), query)
        assertTrue(query.contains("&author=Austen"), query)
        assertTrue(query.contains("&genre=Romance"), query)
        assertTrue(query.contains("status=complete"), query)
    }

    @Test
    fun `blank search degrades to the popular feed`() = runTest {
        val feed = FixtureFeed()
        feed.source.getSearchNovels(1, "  ", NovelFilterList())
        val query = feed.recorded.single()
        assertFalse(query.contains("&title="), query)
    }

    @Test
    fun `latest requests id ordering`() = runTest {
        val feed = FixtureFeed()
        feed.source.getLatestUpdates(1)
        assertTrue(feed.recorded.single().contains("order_by=id"))
    }

    // ---- details + chapters ----------------------------------------------------

    @Test
    fun `details fill metadata and mark initialized`() = runTest {
        val feed = FixtureFeed()
        val details = feed.source.getNovelDetails(lvNovel())

        assertTrue(feed.recorded.single().contains("id=15147"))
        assertEquals("Pride and Prejudice", details.title)
        assertEquals("Jane Austen", details.author)
        assertEquals("Karen Savage", details.artist)
        assertEquals("Austen's classic novel of manners.", details.description)
        assertEquals("Romance, General Fiction", details.genre)
        assertEquals(SNovel.COMPLETED, details.status)
        assertTrue(details.initialized)
    }

    @Test
    fun `chapters map sections onto the lv scheme`() = runTest {
        val chapters = FixtureFeed().source.getChapterList(lvNovel())

        // Section 3 has no listen_url — skipped per SD-5, never a crash.
        assertEquals(2, chapters.size)

        val archiveShaped = chapters[0]
        assertEquals(
            "lv://track/pride_prejudice_krs_1302_librivox/prideandprejudice_01_austen_64kb.mp3",
            archiveShaped.url,
        )
        assertEquals("Chapter 1", archiveShaped.name)
        assertEquals(1f, archiveShaped.chapter_number)
        assertEquals("Karen Savage", archiveShaped.scanlator)
        // copyright_year 2013 → 2013-01-01T00:00:00Z
        assertEquals(1356998400000L, archiveShaped.date_upload)

        val fallback = chapters[1]
        assertTrue(fallback.url.startsWith("lv://file/"), fallback.url)
    }

    @Test
    fun `non-lv novel urls produce empty chapter lists`() = runTest {
        val chapters = FixtureFeed().source.getChapterList(
            SNovel.create().apply {
                url = "https://novelupdates.com/series/x"
                title = "stub"
            },
        )
        assertTrue(chapters.isEmpty())
    }

    @Test
    fun `chapter text throws — audio sections have no text`() {
        val source = FixtureFeed().source
        assertThrows(UnsupportedOperationException::class.java) {
            kotlinx.coroutines.runBlocking {
                source.getChapterText(
                    SNovelChapter.create().apply {
                        url = "lv://track/ia_id/file_01.mp3"
                    },
                )
            }
        }
    }

    // ---- stream dispatch + flags -------------------------------------------------

    @Test
    fun `lv track urls resolve to archive org download streams`() {
        assertEquals(
            "https://archive.org/download/pride_prejudice_krs_1302_librivox/" +
                "prideandprejudice_01_austen_64kb.mp3",
            AudioChapterSchemes.streamUriFor(
                "lv://track/pride_prejudice_krs_1302_librivox/prideandprejudice_01_austen_64kb.mp3",
                null,
            ),
        )
        // The public arm ignores absSource entirely — even configured.
        assertEquals(
            "https://archive.org/download/ia_id/file_01.mp3",
            AudioChapterSchemes.streamUriFor("lv://track/ia_id/file_01.mp3", null),
        )
    }

    @Test
    fun `lv file urls resolve to the decoded feed url`() {
        val encoded = java.net.URLEncoder.encode(
            "https://ia801402.us.archive.org/13/items/x/f 02.mp3",
            "UTF-8",
        )
        assertEquals(
            "https://ia801402.us.archive.org/13/items/x/f 02.mp3",
            AudioChapterSchemes.streamUriFor("lv://file/$encoded", null),
        )
        // Non-http(s) decodings are refused — no file:// stream minting.
        assertNull(
            AudioChapterSchemes.streamUriFor(
                "lv://file/${java.net.URLEncoder.encode("file:///etc/passwd", "UTF-8")}",
                null,
            ),
        )
    }

    @Test
    fun `lv book urls stamp the audiobook flag`() = runTest {
        val page = FixtureFeed().source.getPopularNovels(1)
        val domain = page.novels.first().toDomainNovel(LibriVoxSource.LIBRIVOX_SOURCE_ID)

        assertEquals(LibriVoxSource.LIBRIVOX_SOURCE_ID, domain.source)
        assertEquals(Novel.VIEWER_FLAG_AUDIOBOOK, domain.viewerFlags)
        assertTrue(domain.isAudiobook)
    }

    // ---- PoliteFetch ----------------------------------------------------------

    @AfterEach
    fun resetPoliteFetch() = PoliteFetch.resetForTest()

    @Test
    fun `throttle spaces request starts by at least one second`() = kotlinx.coroutines.runBlocking {
        var now = 1_000_000L
        val waits = mutableListOf<Long>()
        PoliteFetch.nowMs = { now }
        PoliteFetch.delayMs = { ms -> waits += ms; now += ms }

        PoliteFetch.throttle()
        PoliteFetch.throttle()
        PoliteFetch.throttle()

        // First call is immediate (clock far past lastStartMs=0);
        // subsequent starts wait out the 1s floor.
        assertEquals(listOf(1000L, 1000L), waits)
        assertEquals(1_002_000L, now)
        Unit
    }

    @Test
    fun `fetch honors retry-after then succeeds`() = kotlinx.coroutines.runBlocking {
        var now = 1_000_000L
        val waits = mutableListOf<Long>()
        PoliteFetch.nowMs = { now }
        PoliteFetch.delayMs = { ms -> waits += ms; now += ms }

        var attempts = 0
        val result = PoliteFetch.fetch {
            attempts++
            if (attempts == 1) throw PoliteFetch.RateLimitedException(503, retryAfterMs = 2500)
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, attempts)
        assertEquals(2500L, waits.last())
        Unit
    }

    @Test
    fun `fetch backs off exponentially without retry-after and gives up`() = kotlinx.coroutines.runBlocking {
        var now = 1_000_000L
        val waits = mutableListOf<Long>()
        PoliteFetch.nowMs = { now }
        PoliteFetch.delayMs = { ms -> waits += ms; now += ms }

        var attempts = 0
        assertThrows(HttpException::class.java) {
            kotlinx.coroutines.runBlocking {
                PoliteFetch.fetch<String> {
                    attempts++
                    throw HttpException(429)
                }
            }
        }
        // 1 initial + MAX_RETRIES retries; backoff 1s → 2s → 4s and each
        // backoff already satisfies the throttle floor so no extra waits.
        assertEquals(1 + PoliteFetch.MAX_RETRIES, attempts)
        assertEquals(listOf(1000L, 2000L, 4000L), waits)
        Unit
    }

    @Test
    fun `fetch never retries non-throttle http errors`() = kotlinx.coroutines.runBlocking {
        var now = 1_000_000L
        PoliteFetch.nowMs = { now }
        PoliteFetch.delayMs = { ms -> now += ms }

        var attempts = 0
        assertThrows(HttpException::class.java) {
            kotlinx.coroutines.runBlocking {
                PoliteFetch.fetch<String> {
                    attempts++
                    throw HttpException(404)
                }
            }
        }
        assertEquals(1, attempts)
        Unit
    }
}
