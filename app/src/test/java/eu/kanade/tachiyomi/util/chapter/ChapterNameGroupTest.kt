package eu.kanade.tachiyomi.util.chapter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChapterNameGroupTest {

    @Test
    fun `all chapters marked produces sorted groups`() {
        val groups = ChapterNameGroup.computeGroups(
            listOf("Vol.2 Ch.10", "Vol.1 Ch.1", "Vol.1 Ch.2", "Vol.10 Ch.90"),
        )
        assertEquals(listOf("Volume 1", "Volume 2", "Volume 10"), groups)
    }

    @Test
    fun `a single unmarked chapter disables grouping`() {
        // The Shadow-Slave-style regression: one "Vol.1" straggler used to
        // strand that chapter in its own pill while the rest fell into a
        // catch-all bucket — grouping must be all-or-nothing.
        val groups = ChapterNameGroup.computeGroups(
            listOf("Vol.1 Ch.5", "Chapter 1", "Chapter 2", "Chapter 3"),
        )
        assertEquals(emptyList<String>(), groups)
    }

    @Test
    fun `empty list produces no groups`() {
        assertEquals(emptyList<String>(), ChapterNameGroup.computeGroups(emptyList()))
    }

    @Test
    fun `uniform single group still returns the label`() {
        val groups = ChapterNameGroup.computeGroups(
            listOf("Book 1 - Part A", "Book 1 - Part B"),
        )
        assertEquals(listOf("Book 1"), groups)
    }

    @Test
    fun `mixed nouns group independently when fully covered`() {
        val groups = ChapterNameGroup.computeGroups(
            listOf("Season 2 Ep 3", "Season 1 Ep 1", "Season 2 Ep 4"),
        )
        assertEquals(listOf("Season 1", "Season 2"), groups)
    }
}
