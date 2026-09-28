package eu.kanade.tachiyomi.data.mikodb

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorksBrowseApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `browse response parses works and paging`() {
        val parsed = json.decodeFromString(
            WorksBrowseApi.BrowseResponse.serializer(),
            """{"works":[{"miko_work_id":"mk:mb:42","kind":"manga","title":"Solo Leveling",""" +
                """"author":"Chugong","cover_url":"https://cdn.example.com/covers/42.jpg","rating":91.5,""" +
                """"popularity":3,"year":2018,"nsfw":false,"genres":["Action","Fantasy"]}],""" +
                """"page":2,"has_more":true}""",
        )
        assertEquals(2, parsed.page)
        assertTrue(parsed.has_more)
        val work = parsed.works.single()
        assertEquals("mk:mb:42", work.miko_work_id)
        assertEquals("manga", work.kind)
        assertEquals("Solo Leveling", work.title)
        assertEquals("Chugong", work.author)
        assertEquals("https://cdn.example.com/covers/42.jpg", work.cover_url)
        assertEquals(91.5, work.rating)
        assertEquals(3L, work.popularity)
        assertEquals(2018, work.year)
        assertFalse(work.nsfw)
        assertEquals(listOf("Action", "Fantasy"), work.genres)
    }

    @Test
    fun `missing optional fields default safely`() {
        // Honest-null mints carry kind=null; old rows may lack cover/rating/year.
        val parsed = json.decodeFromString(
            WorksBrowseApi.BrowseResponse.serializer(),
            """{"works":[{"miko_work_id":"mk:ol:OL1W","title":"Dune"}],"page":1,"has_more":false}""",
        )
        val work = parsed.works.single()
        assertNull(work.kind)
        assertNull(work.cover_url)
        assertNull(work.rating)
        assertNull(work.popularity)
        assertNull(work.year)
        assertFalse(work.nsfw)
        assertFalse(parsed.has_more)
    }

    @Test
    fun `empty page parses`() {
        val parsed = json.decodeFromString(
            WorksBrowseApi.BrowseResponse.serializer(),
            """{"works":[],"page":7,"has_more":false}""",
        )
        assertTrue(parsed.works.isEmpty())
        assertEquals(7, parsed.page)
    }
}
