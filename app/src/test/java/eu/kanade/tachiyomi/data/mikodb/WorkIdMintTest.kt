package eu.kanade.tachiyomi.data.mikodb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkIdMintTest {

    @Test
    fun `mint is deterministic`() {
        val a = WorkIdMint.mint("Saga (2012-)", "other", "Brian K. Vaughan", 2012)
        val b = WorkIdMint.mint("Saga (2012-)", "other", "Brian K. Vaughan", 2012)
        assertEquals(a, b)
        assertTrue(a.startsWith("mk:m:"))
        assertEquals(69, a.length) // "mk:m:" + 64 hex
    }

    @Test
    fun `optional parts only hash when present`() {
        val without = WorkIdMint.mint("Saga", "other")
        val withAuthor = WorkIdMint.mint("Saga", "other", "Brian K. Vaughan")
        val withYear = WorkIdMint.mint("Saga", "other", null, 2012)
        assertNotEquals(without, withAuthor)
        assertNotEquals(without, withYear)
        assertNotEquals(withAuthor, withYear)
    }

    @Test
    fun `blank author is ignored`() {
        assertEquals(
            WorkIdMint.mint("Saga", "other"),
            WorkIdMint.mint("Saga", "other", "  "),
        )
    }

    @Test
    fun `title normalization applies before hashing`() {
        assertEquals(
            WorkIdMint.mint("Saga (2012-)", "other"),
            WorkIdMint.mint("  SAGA   (2012-) ", "other"),
        )
    }

    @Test
    fun `different types mint different ids`() {
        assertNotEquals(
            WorkIdMint.mint("Naruto", "manga"),
            WorkIdMint.mint("Naruto", "novel"),
        )
    }
}
