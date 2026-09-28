package eu.kanade.domain.ui.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CarrierTest {

    @Test
    fun `medium keys match the worker contract`() {
        assertEquals("shows", Carrier.SHOWS.medium)
        assertEquals("manga", Carrier.MANGA.medium)
        assertEquals("books", Carrier.BOOKS.medium)
        assertEquals(setOf("shows", "manga", "books"), Carrier.mediums)
    }

    @Test
    fun `fromMedium parses carrier keys`() {
        assertEquals(Carrier.SHOWS, Carrier.fromMedium("shows"))
        assertEquals(Carrier.MANGA, Carrier.fromMedium("manga"))
        assertEquals(Carrier.BOOKS, Carrier.fromMedium("books"))
    }

    @Test
    fun `fromMedium honors the legacy alias window`() {
        assertEquals(Carrier.SHOWS, Carrier.fromMedium("anime"))
        assertEquals(Carrier.BOOKS, Carrier.fromMedium("novel"))
        assertEquals(Carrier.SHOWS, Carrier.fromMedium(" Anime "))
    }

    @Test
    fun `fromMedium rejects unknown and null`() {
        assertNull(Carrier.fromMedium(null))
        assertNull(Carrier.fromMedium(""))
        assertNull(Carrier.fromMedium("comic"))
    }
}
