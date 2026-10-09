package eu.kanade.tachiyomi.source.novel.ia

import eu.kanade.domain.entries.novel.model.toDomainNovel
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.novel.AudioChapterSchemes
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel

/**
 * Fixture-driven vertical of the built-in Internet Archive source:
 * canned `advancedsearch` + `metadata/{id}` payloads →
 * [InternetArchiveSource] browse/details/chapter-list → `ia://`-scheme
 * rows → `streamUriFor` archive.org resolution → `toDomainNovel` flag
 * stamp. Both LibriVox-dedup gates are exercised: the query exclusion
 * (`NOT collection:librivoxaudio` in every q=) plus the
 * `external-identifier`/`identifier-access` back-link drop at doc AND
 * chapter-list level (SD-7). Transport seam only — no OkHttp.
 */
class InternetArchiveSourceTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)) { "missing fixture $name" }
            .readText()

    /** A metadata payload for an item IA mirrors from LibriVox — the
     *  identifier-access back-link triggers gate 2 (SD-7). */
    private val librivoxBackedMetadataJson = """
        {"metadata": {
            "identifier": "librivox_mirror_x",
            "title": "Pride and Prejudice (LibriVox)",
            "creator": "Jane Austen",
            "identifier-access": "https://librivox.org/api/feed/audiobooks/id/15147"
        }, "files": [
            {"name": "pride_01.mp3", "format": "VBR MP3", "track": "1"}
        ]}
    """.trimIndent()

    private inner class FixtureServer {
        val recorded = mutableListOf<String>()
        val source = InternetArchiveSource()

        init {
            source.transport = { url ->
                recorded += url
                when {
                    url.contains("advancedsearch.php") -> fixture("ia_search.json")
                    url.contains("/metadata/librivox_mirror_x") -> librivoxBackedMetadataJson
                    url.contains("/metadata/") -> fixture("ia_metadata.json")
                    else -> error("unexpected IA url $url")
                }
            }
        }
    }

    private fun iaNovel(identifier: String = "sherlock_holmes_001", title: String = "stub") =
        SNovel.create().apply {
            url = InternetArchiveSource.itemUrl(identifier)
            this.title = title
        }

    // ---- browse --------------------------------------------------------------

    @Test
    fun `popular scopes to spoken-word minus librivoxaudio`() = runTest {
        val server = FixtureServer()
        val page = server.source.getPopularNovels(1)

        val url = server.recorded.single()
        assertTrue(url.contains("advancedsearch.php"), url)
        val decoded = java.net.URLDecoder.decode(url, "UTF-8")
        assertTrue(decoded.contains("mediatype:audio"), decoded)
        assertTrue(decoded.contains("NOT collection:librivoxaudio"), decoded)
        assertTrue(decoded.contains("sort[]=downloads desc"), decoded)
        assertTrue(url.contains("output=json"), url)

        // librivox_mirror_x carries an external-identifier back-link —
        // dropped before it can double-shelf (SD-7 gate 1).
        assertEquals(
            listOf("ia://item/sherlock_holmes_001", "ia://item/oral_history_mix"),
            page.novels.map { it.url },
        )
        // numFound=3, start=0, rows=50 → 50 < 3 false.
        assertFalse(page.hasNextPage)

        val holmes = page.novels[0]
        assertEquals("The Adventures of Sherlock Holmes", holmes.title)
        assertEquals("Arthur Conan Doyle", holmes.author)
        assertEquals(
            "https://archive.org/services/img/sherlock_holmes_001",
            holmes.thumbnail_url,
        )
        // Array-valued creator coerces to a joined string.
        assertEquals("Smith, John, Doe, Jane", page.novels[1].author)
    }

    @Test
    fun `search intersects the scope with title or creator match`() = runTest {
        val server = FixtureServer()
        server.source.getSearchNovels(1, "sherlock \"homes", NovelFilterList())

        val decoded = java.net.URLDecoder.decode(server.recorded.single(), "UTF-8")
        assertTrue(decoded.contains("title:\"sherlock  homes\""), decoded)
        assertTrue(decoded.contains("creator:\"sherlock  homes\""), decoded)
        assertTrue(decoded.contains("NOT collection:librivoxaudio"), decoded)
    }

    @Test
    fun `latest sorts by addeddate desc`() = runTest {
        val server = FixtureServer()
        server.source.getLatestUpdates(1)
        val decoded = java.net.URLDecoder.decode(server.recorded.single(), "UTF-8")
        assertTrue(decoded.contains("sort[]=addeddate desc"), decoded)
    }

    @Test
    fun `hasNextPage follows numFound past the page window`() = runTest {
        val server = FixtureServer()
        server.source.transport = { url ->
            server.recorded += url
            """{"response":{"numFound":200,"start":50,"docs":[]}}"""
        }
        assertTrue(server.source.getPopularNovels(2).hasNextPage) // 50+50 < 200
        server.source.transport = { url ->
            server.recorded += url
            """{"response":{"numFound":60,"start":50,"docs":[]}}"""
        }
        assertFalse(server.source.getPopularNovels(2).hasNextPage) // 100 !< 60
    }

    // ---- details + chapters ----------------------------------------------------

    @Test
    fun `details fill metadata and mark initialized`() = runTest {
        val server = FixtureServer()
        val details = server.source.getNovelDetails(iaNovel())

        assertTrue(server.recorded.single().contains("/metadata/sherlock_holmes_001"))
        assertEquals("The Adventures of Sherlock Holmes", details.title)
        assertEquals("Arthur Conan Doyle", details.author)
        // HTML-lite stripped from IA's markup descriptions.
        assertEquals("Twelve stories narrated by volunteers.", details.description)
        assertEquals("audiobook, detective fiction", details.genre)
        assertEquals(
            "https://archive.org/services/img/sherlock_holmes_001",
            details.thumbnail_url,
        )
        assertEquals(SNovel.COMPLETED, details.status)
        assertTrue(details.initialized)
        assertNull(details.artist) // IA has no narrator field (SD-4)
    }

    @Test
    fun `chapters filter to audio formats ordered by track`() = runTest {
        val chapters = FixtureServer().source.getChapterList(iaNovel())

        // Item Image / Metadata / Torrent / Text PDF rows are out.
        assertEquals(
            listOf(
                "ia://track/sherlock_holmes_001/holmes_01_doyle.mp3",
                "ia://track/sherlock_holmes_001/holmes_02_doyle.mp3",
                "ia://track/sherlock_holmes_001/holmes_03_doyle.ogg",
                "ia://track/sherlock_holmes_001/holmes_04_doyle.m4b",
                "ia://track/sherlock_holmes_001/holmes_10_doyle.mp3",
                "ia://track/sherlock_holmes_001/holmes_appendix.mp3",
            ),
            chapters.map { it.url },
        )
        assertEquals("A Scandal in Bohemia", chapters[0].name)
        assertEquals(1f, chapters[0].chapter_number)
        // "4/10" style track parses to its leading number.
        assertEquals(4f, chapters[3].chapter_number)
        // Untracked file sorts last, numbered by position.
        assertEquals(6f, chapters[5].chapter_number)
    }

    @Test
    fun `librivox-backed items return no chapters — gate two`() = runTest {
        val server = FixtureServer()
        val chapters = server.source.getChapterList(
            iaNovel(identifier = "librivox_mirror_x", title = "P&P"),
        )
        assertTrue(chapters.isEmpty())
        assertTrue(server.recorded.single().contains("/metadata/librivox_mirror_x"))
    }

    @Test
    fun `non-ia novel urls produce empty chapter lists`() = runTest {
        val chapters = FixtureServer().source.getChapterList(
            SNovel.create().apply {
                url = "lv://book/15147"
                title = "stub"
            },
        )
        assertTrue(chapters.isEmpty())
    }

    @Test
    fun `chapter text throws — audio files have no text`() {
        val source = FixtureServer().source
        assertThrows(UnsupportedOperationException::class.java) {
            kotlinx.coroutines.runBlocking {
                source.getChapterText(
                    SNovelChapter.create().apply {
                        url = "ia://track/some_item/f.mp3"
                    },
                )
            }
        }
    }

    // ---- stream dispatch + flags -------------------------------------------------

    @Test
    fun `ia track urls resolve to archive org downloads`() {
        assertEquals(
            "https://archive.org/download/sherlock_holmes_001/holmes_01_doyle.mp3",
            AudioChapterSchemes.streamUriFor(
                "ia://track/sherlock_holmes_001/holmes_01_doyle.mp3",
                null,
            ),
        )
    }

    @Test
    fun `ia track urls reject path traversal`() {
        assertNull(AudioChapterSchemes.streamUriFor("ia://track/../../x", null))
        assertNull(AudioChapterSchemes.streamUriFor("ia://track/../evil/f.mp3", null))
    }

    @Test
    fun `ia item urls stamp the audiobook flag`() = runTest {
        val page = FixtureServer().source.getPopularNovels(1)
        val domain = page.novels.first().toDomainNovel(InternetArchiveSource.IA_SOURCE_ID)

        assertEquals(InternetArchiveSource.IA_SOURCE_ID, domain.source)
        assertEquals(Novel.VIEWER_FLAG_AUDIOBOOK, domain.viewerFlags)
        assertTrue(domain.isAudiobook)
    }
}
