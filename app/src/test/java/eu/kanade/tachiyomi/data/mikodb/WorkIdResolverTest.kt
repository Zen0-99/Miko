package eu.kanade.tachiyomi.data.mikodb

import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.novel.AudioChapterSchemes
import eu.kanade.tachiyomi.source.novel.abs.AbsClient
import eu.kanade.tachiyomi.source.novel.abs.AbsNovelSource
import eu.kanade.tachiyomi.source.novel.abs.AbsUrls
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkIdResolverTest {

    private fun extract(url: String) = WorkIdResolver.extractExternalIds(url)

    /** SharedPreferences stub — AbsClient only reads getString
     *  (same shape as AbsNovelSourceTest's FakePrefs). */
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

    private fun absSourceAt(baseUrl: String): AbsNovelSource = AbsNovelSource(
        AbsClient(
            FakePrefs(
                mapOf(
                    "abs_base_url" to baseUrl,
                    "__PRIVATE_abs_api_key" to "tok-test",
                ),
            ),
        ),
    )

    @Test
    fun `extracts mangadex uuid`() {
        assertEquals(
            listOf("mangadex" to "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"),
            extract("https://api.mangadex.org/manga/a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"),
        )
    }

    @Test
    fun `extracts webtoons title_no`() {
        assertEquals(
            listOf("webtoons" to "402"),
            extract("https://www.webtoons.com/en/fantasy/tower-of-god/list?title_no=402"),
        )
    }

    @Test
    fun `extracts mal numeric id`() {
        assertEquals(
            listOf("my_anime_list" to "656"),
            extract("https://myanimelist.net/manga/656/Vagabond"),
        )
    }

    @Test
    fun `extracts mangaupdates slug`() {
        assertEquals(
            listOf("manga_updates" to "rlpe3ta"),
            extract("https://www.mangaupdates.com/series/rlpe3ta/shadow-slave"),
        )
    }

    @Test
    fun `extracts novelupdates slug`() {
        assertEquals(
            listOf("novelupdates" to "shadow-slave"),
            extract("https://www.novelupdates.com/series/shadow-slave/"),
        )
    }

    @Test
    fun `extracts royalroad fiction id`() {
        assertEquals(
            listOf("royalroad" to "21220"),
            extract("https://www.royalroad.com/fiction/21220/mother-of-learning"),
        )
    }

    @Test
    fun `extracts imdb id from cinemeta scheme urls`() {
        // cinemeta: is an app-internal scheme — parsed before the http bail.
        assertEquals(
            listOf("imdb" to "tt0903747"),
            extract("cinemeta:series:tt0903747"),
        )
        assertEquals(
            listOf("imdb" to "tt0903747"),
            extract("cinemeta:movie:tt0903747"),
        )
        assertEquals(
            listOf("imdb" to "tt0903747"),
            extract("cinemeta:episode:tt0903747:1:1"),
        )
    }

    @Test
    fun `extracts hs slug from hentaistream provider urls`() {
        assertEquals(
            listOf("hs" to "some-show-episode-3"),
            extract("https://hentaisea.com/watch/some-show-episode-3/"),
        )
    }

    @Test
    fun `extracts openlibrary work id`() {
        assertEquals(
            listOf("openlibrary" to "OL45883W"),
            extract("https://openlibrary.org/works/OL45883W/Dune"),
        )
        // Edition keys (…M) are not work identity — ignored.
        assertTrue(extract("https://openlibrary.org/books/OL27918581M.json").isEmpty())
    }

    @Test
    fun `cinemetaRef parses the url type and ttid`() {
        assertEquals("series" to "tt0903747", WorkIdResolver.cinemetaRef("cinemeta:series:tt0903747"))
        assertEquals("movie" to "tt0113243", WorkIdResolver.cinemetaRef("cinemeta:movie:tt0113243"))
        // Episode urls count as series for the kind_hint fetch order.
        assertEquals("series" to "tt0903747", WorkIdResolver.cinemetaRef("cinemeta:episode:tt0903747:1:1"))
        assertEquals(null, WorkIdResolver.cinemetaRef("https://example.com/x"))
    }

    @Test
    fun `shows allowedTypes accepts the shows kind vocab`() {
        assertEquals(setOf("anime", "movie", "series"), WorkIdResolver.allowedTypes("shows"))
    }

    @Test
    fun `books allowedTypes accepts the books kind vocab`() {
        assertEquals(setOf("book", "novel", "audiobook"), WorkIdResolver.allowedTypes("books"))
        // Alias window closed (15-06): legacy 'novel' key falls to the
        // manga fallback like any unknown key.
        assertEquals(
            setOf("manga", "manhwa", "manhua", "comic", "webtoon", "oel", "other"),
            WorkIdResolver.allowedTypes("novel"),
        )
        assertEquals(
            setOf("manga", "manhwa", "manhua", "comic", "webtoon", "oel", "other"),
            WorkIdResolver.allowedTypes("manga"),
        )
    }

    @Test
    fun `resolve request sends kindHint as kind_hint on the wire`() {
        val body = Json.encodeToString(
            WorkIdentityApi.ResolveRequest.serializer(),
            WorkIdentityApi.ResolveRequest(
                title = "Dune",
                medium = "books",
                kindHint = "book",
            ),
        )
        assertTrue("\"kind_hint\":\"book\"" in body)
        assertFalse("\"kind\":" in body.replace("\"kind_hint\"", "\"\""))
        assertTrue("\"medium\":\"books\"" in body)
    }

    @Test
    fun `work dto parses kind nsfw medium and content_rating`() {
        val dto = Json.decodeFromString(
            WorkIdentityApi.WorkDto.serializer(),
            """{"miko_work_id":"mk:mb:1","title":"T","type":"manhwa","kind":"manhwa",""" +
                """"medium":"manga","nsfw":true,"content_rating":"pornographic"}""",
        )
        assertEquals("manhwa", dto.kind)
        assertEquals("manga", dto.medium)
        assertTrue(dto.nsfw)
        assertEquals("pornographic", dto.content_rating)
    }

    @Test
    fun `mediumToType gives shows mints a carrier-disjoint kind`() {
        assertEquals("movie", WorkIdResolver.mediumToType("shows", isBook = false, kindHint = "movie"))
        assertEquals("series", WorkIdResolver.mediumToType("shows", isBook = false, kindHint = null))
        assertEquals("manga", WorkIdResolver.mediumToType("manga", isBook = false, kindHint = null))
        assertEquals("novel", WorkIdResolver.mediumToType("books", isBook = false, kindHint = null))
        // Legacy 'novel' key is no longer recognized (alias window closed).
        assertEquals("manga", WorkIdResolver.mediumToType("novel", isBook = false, kindHint = null))
        assertEquals("book", WorkIdResolver.mediumToType("manga", isBook = true, kindHint = null))
        // Carrier-disjoint: a shows mk:m: mint never collides with a
        // same-titled manga mint.
        assertTrue(
            WorkIdMint.mint("Same Title", "series") != WorkIdMint.mint("Same Title", "manga"),
        )
    }

    // ---- 22-06 (decision B): audiobook provider ids + kind -----------

    @Test
    fun `providerIdForNovelUrl emits librivox and ia pairs`() {
        assertEquals(
            "librivox" to "15147",
            AudioChapterSchemes.providerIdForNovelUrl("lv://book/15147"),
        )
        assertEquals(
            "ia" to "sherlock_001",
            AudioChapterSchemes.providerIdForNovelUrl("ia://item/sherlock_001"),
        )
        // Trailing segments tolerated like the per-source helpers.
        assertEquals(
            "librivox" to "15147",
            AudioChapterSchemes.providerIdForNovelUrl("lv://book/15147/extra"),
        )
    }

    @Test
    fun `providerIdForNovelUrl scopes abs ids to the configured server`() {
        val source = absSourceAt("http://abs.test:13378")
        val key = AbsUrls.serverKeyFor("http://abs.test:13378")
        assertEquals(
            "abs" to "$key:it-1",
            AudioChapterSchemes.providerIdForNovelUrl("abs://item/it-1", source),
        )
        // Base-url normalization: case + trailing slash don't fork the key.
        assertEquals(
            "abs" to "$key:it-1",
            AudioChapterSchemes.providerIdForNovelUrl(
                "abs://item/it-1",
                absSourceAt("HTTP://ABS.TEST:13378/"),
            ),
        )
        // A different server mints a different external id.
        val other = AudioChapterSchemes.providerIdForNovelUrl(
            "abs://item/it-1",
            absSourceAt("http://other.test:13378"),
        )
        assertTrue(other != null && other.second != "$key:it-1")
        // Unconfigured/no source → no pair (the fingerprint would hash
        // an empty base url and collide across unconfigured devices).
        assertEquals(null, AudioChapterSchemes.providerIdForNovelUrl("abs://item/it-1"))
    }

    @Test
    fun `providerIdForNovelUrl rejects chapter urls and non-audio urls`() {
        // Track/file urls are chapter-level identity — never external ids.
        assertEquals(null, AudioChapterSchemes.providerIdForNovelUrl("abs://track/it-1/1001"))
        assertEquals(null, AudioChapterSchemes.providerIdForNovelUrl("lv://track/iaid/01.mp3"))
        assertEquals(null, AudioChapterSchemes.providerIdForNovelUrl("lv://file/http%3A%2F%2Fx"))
        assertEquals(null, AudioChapterSchemes.providerIdForNovelUrl("ia://track/id/f.mp3"))
        // Non-audio urls emit nothing.
        assertEquals(null, AudioChapterSchemes.providerIdForNovelUrl("https://example.com/x"))
        assertEquals(null, AudioChapterSchemes.providerIdForNovelUrl(""))
        assertEquals(null, AudioChapterSchemes.providerIdForNovelUrl("miko:novel:mk:m:abc"))
    }

    @Test
    fun `novelUrlForProviderId maps pairs back to source entries`() {
        assertEquals(
            -2L to "lv://book/15147",
            AudioChapterSchemes.novelUrlForProviderId("librivox", "15147"),
        )
        assertEquals(
            -3L to "ia://item/sherlock_001",
            AudioChapterSchemes.novelUrlForProviderId("ia", "sherlock_001"),
        )
        assertEquals(
            -1L to "abs://item/it-1",
            AudioChapterSchemes.novelUrlForProviderId("abs", "a1b2c3d4e5f6:it-1"),
        )
        assertEquals(null, AudioChapterSchemes.novelUrlForProviderId("mangadex", "x"))
    }

    @Test
    fun `mediumToType emits audiobook for audio candidates only`() {
        assertEquals(
            "audiobook",
            WorkIdResolver.mediumToType("books", isBook = false, kindHint = null, isAudiobook = true),
        )
        // The flag beats isBook — an audio entry is never a published-book
        // work even if the source flags both.
        assertEquals(
            "audiobook",
            WorkIdResolver.mediumToType("books", isBook = true, kindHint = null, isAudiobook = true),
        )
        // Ordinary text candidates are unchanged (regression).
        assertEquals(
            "novel",
            WorkIdResolver.mediumToType("books", isBook = false, kindHint = null, isAudiobook = false),
        )
        assertEquals(
            "book",
            WorkIdResolver.mediumToType("books", isBook = true, kindHint = null, isAudiobook = false),
        )
        // The flag is books-scoped — it can't leak the kind onto manga.
        assertEquals(
            "manga",
            WorkIdResolver.mediumToType("manga", isBook = false, kindHint = null, isAudiobook = true),
        )
    }

    @Test
    fun `audiobook kind participates in the mk m mint hash`() {
        // Decision B: same title+author, different kind → different work.
        assertTrue(
            WorkIdMint.mint("Dune", "audiobook", "Frank Herbert") !=
                WorkIdMint.mint("Dune", "novel", "Frank Herbert"),
        )
        // Deterministic: identical inputs mint identical ids (re-mint
        // after the 22-06 flag flip converges onto this id).
        assertEquals(
            WorkIdMint.mint("Dune", "audiobook", "Frank Herbert"),
            WorkIdMint.mint("Dune", "audiobook", "Frank Herbert"),
        )
    }

    @Test
    fun `resolve request emits audio provider and external_id on the wire`() {
        val body = Json.encodeToString(
            WorkIdentityApi.ResolveRequest.serializer(),
            WorkIdentityApi.ResolveRequest(
                provider = "librivox",
                externalId = "15147",
                title = "Dune",
                medium = "books",
                kindHint = "audiobook",
            ),
        )
        assertTrue("\"provider\":\"librivox\"" in body)
        assertTrue("\"external_id\":\"15147\"" in body)
        assertTrue("\"kind_hint\":\"audiobook\"" in body)
        assertTrue("\"medium\":\"books\"" in body)
    }

    @Test
    fun `work dto parses audiobook kind and audio external ids`() {
        val dto = Json.decodeFromString(
            WorkIdentityApi.WorkDto.serializer(),
            """{"miko_work_id":"mk:audio:librivox:15147","title":"Dune",""" +
                """"kind":"audiobook","medium":"books",""" +
                """"external_ids":{"librivox":"15147"}}""",
        )
        assertEquals("audiobook", dto.kind)
        assertEquals("books", dto.medium)
        assertEquals("15147", dto.external_ids["librivox"])
    }

    @Test
    fun `ignores non-http and unknown urls`() {
        assertTrue(extract("gallery/1234").isEmpty())
        assertTrue(extract("https://readcomicsonline.ru/comic/saga-2012").isEmpty())
        assertTrue(extract("").isEmpty())
    }

    @Test
    fun `isbnFromGenre mines isbn tokens`() {
        assertEquals(
            "9780441172719",
            WorkIdResolver.isbnFromGenre(listOf("epub", "english", "1965", "isbn:9780441172719")),
        )
        assertEquals(
            "044117271X",
            WorkIdResolver.isbnFromGenre(listOf("isbn:0-441-17271-X")),
        )
    }

    @Test
    fun `isbnFromGenre ignores non-isbn tokens`() {
        assertEquals(null, WorkIdResolver.isbnFromGenre(listOf("epub", "english", "1965")))
        assertEquals(null, WorkIdResolver.isbnFromGenre(null))
        assertEquals(null, WorkIdResolver.isbnFromGenre(listOf("isbn:notanisbn")))
    }

    @Test
    fun `normalizeIsbn validates shape`() {
        assertEquals("9780441172719", WorkIdResolver.normalizeIsbn("978-0-441-17271-9"))
        assertEquals("044117271X", WorkIdResolver.normalizeIsbn("0-441-17271-X"))
        assertEquals(null, WorkIdResolver.normalizeIsbn("12345"))
        assertEquals(null, WorkIdResolver.normalizeIsbn("abc"))
    }
}
