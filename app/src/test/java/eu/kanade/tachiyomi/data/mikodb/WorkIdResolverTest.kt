package eu.kanade.tachiyomi.data.mikodb

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkIdResolverTest {

    private fun extract(url: String) = WorkIdResolver.extractExternalIds(url)

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
        assertEquals(setOf("book", "novel"), WorkIdResolver.allowedTypes("books"))
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
