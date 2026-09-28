package eu.kanade.tachiyomi.metadata.openlibrary

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OpenLibraryTrendingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `workIdFromKey extracts the OL work id`() {
        assertEquals("OL45883W", OpenLibraryTrending.workIdFromKey("/works/OL45883W"))
        assertNull(OpenLibraryTrending.workIdFromKey("/books/OL27918581M"))
        assertNull(OpenLibraryTrending.workIdFromKey("/authors/OL7422948A"))
        assertNull(OpenLibraryTrending.workIdFromKey(null))
        assertNull(OpenLibraryTrending.workIdFromKey(""))
    }

    @Test
    fun `coverUrl builds the b-id cover url`() {
        assertEquals(
            "https://covers.openlibrary.org/b/id/12539702-M.jpg",
            OpenLibraryTrending.coverUrl(12539702L),
        )
        assertNull(OpenLibraryTrending.coverUrl(null))
    }

    @Test
    fun `trending response maps docs to items`() {
        val parsed = json.decodeFromString(
            OpenLibraryTrending.TrendingResponse.serializer(),
            """{"query":"/trending/daily","works":[""" +
                """{"key":"/works/OL17930368W","title":"Atomic Habits",""" +
                """"author_name":["James Clear"],"first_publish_year":2016,""" +
                """"cover_i":12539702,"isbn":["9781847941831","bad"]}],"days":1,"hours":0}""",
        )
        val item = with(OpenLibraryTrending) { parsed.works.single().toItem() }!!
        assertEquals("OL17930368W", item.workKey)
        assertEquals("Atomic Habits", item.title)
        assertEquals("James Clear", item.author)
        assertEquals(2016, item.year)
        assertEquals("https://covers.openlibrary.org/b/id/12539702-M.jpg", item.coverUrl)
        assertEquals("9781847941831", item.isbn)
    }

    @Test
    fun `docs without a work key or title are dropped`() {
        val parsed = json.decodeFromString(
            OpenLibraryTrending.TrendingResponse.serializer(),
            """{"works":[""" +
                """{"key":"/books/OL1M","title":"Edition-only doc"},""" +
                """{"key":"/works/OL2W","title":""},""" +
                """{"key":"/works/OL3W","title":"Kept"}]}""",
        )
        val items = parsed.works.mapNotNull { with(OpenLibraryTrending) { it.toItem() } }
        assertEquals(listOf("OL3W"), items.map { it.workKey })
        assertNull(items.single().coverUrl)
        assertNull(items.single().author)
        assertNull(items.single().year)
        assertNull(items.single().isbn)
    }
}
