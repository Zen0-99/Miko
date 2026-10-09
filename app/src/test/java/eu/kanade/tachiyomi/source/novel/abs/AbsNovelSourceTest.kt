package eu.kanade.tachiyomi.source.novel.abs

import android.content.SharedPreferences
import eu.kanade.domain.entries.novel.model.copyFrom
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
import tachiyomi.domain.library.model.LibrarySubtype

/**
 * Fixture-driven vertical of the built-in ABS source: canned
 * `/api/me`, `/api/libraries`, `/items`, `/items/{id}` payloads →
 * [AbsClient] → [AbsNovelSource] browse/details/chapter-list →
 * `abs://`-scheme rows → `toDomainNovel` flag stamp → AUDIOBOOK
 * classification. No OkHttp, no Android, no Injekt — the transport seam
 * (SD-7) returns fixture text by path and records every request so the
 * Bearer header and the no-`?token=` rule are both asserted.
 */
class AbsNovelSourceTest {

    // ---- fakes -------------------------------------------------------------

    /** SharedPreferences stub — only getString is read by AbsClient. */
    private class FakePrefs(private val values: Map<String, String>) : SharedPreferences {
        override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String?, defValue: Int) = defValue
        override fun getLong(key: String?, defValue: Long) = defValue
        override fun getFloat(key: String?, defValue: Float) = defValue
        override fun getBoolean(key: String?, defValue: Boolean) = defValue
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)) { "missing fixture $name" }
            .readText()

    /** One in-progress entry sharing id `it-1` with abs_items.json — the
     *  popular merge must dedupe it. */
    private val inProgressJson = """
        {"libraryItems":[
          {"id":"it-1","ino":"1001","libraryId":"lib-1",
           "path":"/audiobooks/Frank Herbert/Dune","mediaType":"book",
           "media":{"metadata":{"title":"Dune","authorName":"Frank Herbert",
           "narratorName":"Ray Porter"}},"addedAt":1759000000}
        ]}
    """.trimIndent()

    private val meJson = """{"id":"u1","username":"karol","type":"root"}"""

    private inner class FixtureServer(prefs: Map<String, String>, fixtureFor: (String) -> String) {
        val client: AbsClient = AbsClient(FakePrefs(prefs))
        val recorded = mutableListOf<AbsRequest>()

        init {
            client.transport = { req ->
                recorded += req
                when {
                    req.path == "/api/me" -> meJson
                    req.path == "/api/libraries" -> fixtureFor("abs_libraries.json")
                    req.path == "/api/me/items-in-progress" -> inProgressJson
                    req.path.startsWith("/api/libraries/") && req.path.endsWith("/items") ->
                        fixtureFor("abs_items.json")
                    req.path.startsWith("/api/items/") -> fixtureFor("abs_item.json")
                    else -> error("unexpected ABS path ${req.path}")
                }
            }
        }
    }

    private val configuredPrefs = mapOf(
        "abs_base_url" to "http://abs.test:13378",
        "__PRIVATE_abs_api_key" to "tok-test",
        "abs_library_id" to "lib-1",
    )

    private fun newServer(): FixtureServer = FixtureServer(configuredPrefs, ::fixture)

    private fun newSource(): AbsNovelSource = AbsNovelSource(newServer().client)

    // ---- client --------------------------------------------------------------

    @Test
    fun `me parses the user`() = runTest {
        val server = newServer()
        val user = server.client.me()
        assertEquals("u1", user.id)
        assertEquals("karol", user.username)
        assertEquals("root", user.type)
    }

    @Test
    fun `libraries drops non-book libraries`() = runTest {
        val libs = newServer().client.libraries()
        assertEquals(1, libs.size)
        assertEquals("lib-1", libs[0].id)
        assertEquals("Audiobooks", libs[0].name)
    }

    @Test
    fun `every request carries the bearer token and never a token query`() = runTest {
        val server = newServer()
        val source = AbsNovelSource(server.client)
        source.getPopularNovels(1)
        // title is `lateinit` on SNovelImpl — SNovel.copy() reads it, so
        // details input must carry one like a real browse result.
        source.getNovelDetails(
            SNovel.create().apply {
                url = AbsUrls.itemUrl("it-1")
                title = "Dune"
            },
        )
        source.getChapterList(
            SNovel.create().apply {
                url = AbsUrls.itemUrl("it-1")
                title = "Dune"
            },
        )
        server.client.me()

        assertTrue(server.recorded.isNotEmpty())
        server.recorded.forEach { req ->
            assertEquals("Bearer tok-test", req.headers["Authorization"], "missing bearer on ${req.path}")
            assertFalse(req.query.containsKey("token"), "?token= must never ride an API call")
            assertFalse(req.path.contains("token="), "?token= must never ride an API call")
        }
    }

    @Test
    fun `unconfigured source surfaces a setup error`() = runTest {
        val source = AbsNovelSource(FixtureServer(emptyMap(), ::fixture).client)
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { source.getPopularNovels(1) }
        }
    }

    // ---- browse --------------------------------------------------------------

    @Test
    fun `popular page one merges in-progress over recent and dedupes`() = runTest {
        val server = newServer()
        val source = AbsNovelSource(server.client)
        val page = source.getPopularNovels(1)

        assertEquals(3, page.novels.size)
        assertFalse(page.hasNextPage)
        // it-1 arrives twice (in-progress + recent) and must appear once,
        // first — the in-progress ordering wins.
        assertEquals("abs://item/it-1", page.novels[0].url)
        assertEquals(
            listOf("abs://item/it-1", "abs://item/it-2", "abs://item/it-3"),
            page.novels.map { it.url },
        )
        val dune = page.novels[0]
        assertEquals("Dune", dune.title)
        assertEquals("Frank Herbert", dune.author)
        assertEquals("Ray Porter", dune.artist)
        assertEquals("http://abs.test:13378/api/items/it-1/cover", dune.thumbnail_url)
    }

    @Test
    fun `popular page two is items pagination only`() = runTest {
        val server = newServer()
        val source = AbsNovelSource(server.client)
        val page = source.getPopularNovels(2)
        assertEquals(3, page.novels.size)
        val itemsReq = server.recorded.last { it.path.endsWith("/items") }
        assertEquals("1", itemsReq.query["page"])
        assertEquals("addedAt", itemsReq.query["sort"])
        assertEquals("1", itemsReq.query["desc"])
    }

    @Test
    fun `latest is recently-added order`() = runTest {
        val server = newServer()
        val page = AbsNovelSource(server.client).getLatestUpdates(1)
        assertEquals(3, page.novels.size)
        val req = server.recorded.last { it.path.endsWith("/items") }
        assertEquals("addedAt", req.query["sort"])
        assertEquals("1", req.query["desc"])
        assertEquals("0", req.query["page"])
    }

    @Test
    fun `search sends the query as the ABS filter`() = runTest {
        val server = newServer()
        val page = AbsNovelSource(server.client)
            .getSearchNovels(1, "dune", NovelFilterList())
        assertEquals(3, page.novels.size)
        val req = server.recorded.last { it.path.endsWith("/items") }
        assertEquals("dune", req.query["filter"])
        assertEquals("media.metadata.title", req.query["sort"])
        assertEquals("0", req.query["desc"])
    }

    // ---- details + chapters ----------------------------------------------------

    @Test
    fun `details fill metadata and mark the novel initialized`() = runTest {
        val source = newSource()
        val details = source.getNovelDetails(
            SNovel.create().apply {
                url = AbsUrls.itemUrl("it-1")
                title = "stub"
            },
        )
        assertEquals("Dune", details.title)
        assertEquals("Frank Herbert", details.author)
        assertEquals("Ray Porter", details.artist)
        assertEquals(
            "Set on the desert planet Arrakis, Dune is the story of Paul Atreides.",
            details.description,
        )
        assertEquals("Sci-Fi, Classic", details.genre)
        assertEquals("http://abs.test:13378/api/items/it-1/cover", details.thumbnail_url)
        assertEquals(SNovel.COMPLETED, details.status)
        assertTrue(details.initialized)
    }

    @Test
    fun `chapters map tracks onto the abs scheme`() = runTest {
        val chapters = newSource().getChapterList(
            SNovel.create().apply { url = AbsUrls.itemUrl("it-1") },
        )
        assertEquals(3, chapters.size)

        val first = chapters[0]
        assertEquals("abs://track/it-1/f1", first.url)
        assertEquals("Part 1", first.name)
        assertEquals(1f, first.chapter_number)
        assertEquals(1759000000L, first.date_upload)
        assertEquals("Ray Porter", first.scanlator)
        assertEquals("abs://track/it-1/f2", chapters[1].url)
        assertEquals(2f, chapters[1].chapter_number)
        assertEquals("abs://track/it-1/f3", chapters[2].url)
    }

    @Test
    fun `chapter text throws — audio tracks have no text`() {
        val source = newSource()
        assertThrows(UnsupportedOperationException::class.java) {
            kotlinx.coroutines.runBlocking {
                source.getChapterText(
                    SNovelChapter.create().apply { url = AbsUrls.trackUrl("it-1", "f1") },
                )
            }
        }
    }

    // ---- flag + subtype classification -------------------------------------------

    @Test
    fun `abs urls stamp the audiobook flag and resolve the AUDIOBOOK subtype`() = runTest {
        val page = newSource().getPopularNovels(1)
        val domain = page.novels.first().toDomainNovel(AbsNovelSource.ABS_SOURCE_ID)

        assertEquals(AbsNovelSource.ABS_SOURCE_ID, domain.source)
        assertEquals(Novel.VIEWER_FLAG_AUDIOBOOK, domain.viewerFlags)
        assertTrue(domain.isAudiobook)

        // The resolver's first branch — the flag short-circuits BOOK/NOVEL
        // without any works_cache wait.
        val subtype = when {
            domain.isAudiobook -> LibrarySubtype.AUDIOBOOK
            else -> LibrarySubtype.NOVEL
        }
        assertEquals(LibrarySubtype.AUDIOBOOK, subtype)
    }

    @Test
    fun `non-audio urls do not stamp the flag`() {
        val snovel = SNovel.create().apply {
            url = "https://novelupdates.com/series/some-series"
            title = "web novel"
        }
        val domain = snovel.toDomainNovel(sourceId = 42L)
        assertEquals(0L, domain.viewerFlags)
        assertFalse(domain.isAudiobook)
    }

    @Test
    fun `copyFrom ORs the flag in and never clears it`() {
        val audio = SNovel.create().apply {
            url = AbsUrls.itemUrl("it-1")
            title = "Dune"
        }
        val flagged = audio.toDomainNovel(AbsNovelSource.ABS_SOURCE_ID)
        assertTrue(flagged.isAudiobook)

        // Refresh with a non-audio SNovel keeps the bit.
        val refreshed = flagged.copyFrom(
            SNovel.create().apply {
                url = "https://example.org/book"
                title = "Dune"
                author = "Frank Herbert"
            },
        )
        assertTrue(refreshed.isAudiobook)

        // An unflagged row refreshed from an audio SNovel gains the bit.
        val plain = Novel.create().copy(id = 7L, source = AbsNovelSource.ABS_SOURCE_ID)
        val adopted = plain.copyFrom(audio)
        assertEquals(Novel.VIEWER_FLAG_AUDIOBOOK, adopted.viewerFlags)
        assertTrue(adopted.isAudiobook)
    }

    // ---- url scheme ----------------------------------------------------------------

    @Test
    fun `audio scheme predicates separate abs urls from everything else`() {
        assertTrue(AudioChapterSchemes.isAudioNovelUrl("abs://item/it-1"))
        assertFalse(AudioChapterSchemes.isAudioNovelUrl("abs://track/it-1/f1"))

        assertTrue(AudioChapterSchemes.isAudioChapterUrl("abs://track/it-1/f1"))
        assertFalse(AudioChapterSchemes.isAudioChapterUrl("abs://item/it-1"))

        // The existing scheme namespace and plain web urls stay out.
        for (url in listOf(
            "canon:mk:mb:123:4",
            "carrier:6400:https://annas-archive.org/md5/x",
            "miko:novel:mk:ol:OL42W",
            "https://novelupdates.com/series/x",
            "",
        )) {
            assertFalse(AudioChapterSchemes.isAudioNovelUrl(url), url)
            assertFalse(AudioChapterSchemes.isAudioChapterUrl(url), url)
        }
    }

    @Test
    fun `abs url helpers build and parse the scheme`() {
        assertEquals("abs://item/it-1", AbsUrls.itemUrl("it-1"))
        assertEquals("abs://track/it-1/f9", AbsUrls.trackUrl("it-1", "f9"))

        assertEquals("it-1", AbsUrls.itemIdFrom("abs://item/it-1"))
        assertEquals("it-1", AbsUrls.itemIdFrom("abs://track/it-1/f9"))
        assertEquals("f9", AbsUrls.inoFrom("abs://track/it-1/f9"))
        assertNull(AbsUrls.inoFrom("abs://item/it-1"))
        assertNull(AbsUrls.itemIdFrom("miko:novel:mk:ol:OL42W"))

        assertEquals(
            "http://abs.test:13378/api/items/it-1/file/f1",
            AbsUrls.streamUrl("http://abs.test:13378/", "it-1", "f1"),
        )
        assertEquals(
            "http://abs.test:13378/api/items/it-1/cover",
            AbsUrls.coverUrl("http://abs.test:13378", "it-1"),
        )

        val key = AbsUrls.serverKeyFor("http://ABS.test:13378/")
        assertEquals(12, key.length)
        assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
        // Normalization — case + trailing slash don't fork the fingerprint.
        assertEquals(key, AbsUrls.serverKeyFor("http://abs.test:13378"))
    }
}
