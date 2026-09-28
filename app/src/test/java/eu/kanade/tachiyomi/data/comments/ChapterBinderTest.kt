package eu.kanade.tachiyomi.data.comments

import eu.kanade.domain.ui.model.Carrier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChapterBinderTest {

    private val work = "mk:mb:531343"

    @Test
    fun `ungrouped chapter produces c-prefixed id`() {
        assertEquals(
            "manga:$work:c142",
            ChapterBinder.bind(Carrier.MANGA, work, "Chapter 142", 142f),
        )
    }

    @Test
    fun `volume group produces v-prefixed id`() {
        assertEquals(
            "manga:$work:v4c30",
            ChapterBinder.bind(Carrier.MANGA, work, "Vol.4 Ch.30: Title", 30f),
        )
    }

    @Test
    fun `book group produces b-prefixed id`() {
        assertEquals(
            "books:$work:b2c7",
            ChapterBinder.bind(Carrier.BOOKS, work, "Book 2 - Chapter 7", 7f),
        )
    }

    @Test
    fun `arc and act map to a and t prefixes`() {
        assertEquals("a3", ChapterBinder.groupCode("Arc 3"))
        assertEquals("t1", ChapterBinder.groupCode("Act 1"))
        assertEquals("p2", ChapterBinder.groupCode("Part 2"))
        assertEquals("s5", ChapterBinder.groupCode("Season 5"))
    }

    @Test
    fun `float numbers normalize`() {
        assertEquals(
            "manga:$work:c142",
            ChapterBinder.bind(Carrier.MANGA, work, "Chapter 142", 142.0f),
        )
        assertEquals(
            "manga:$work:c142.5",
            ChapterBinder.bind(Carrier.MANGA, work, "Chapter 142.5", 142.5f),
        )
    }

    @Test
    fun `zero chapter number falls back to name parsing`() {
        assertEquals(
            "manga:$work:c9",
            ChapterBinder.bind(Carrier.MANGA, work, "Chapter 9 - The Beginning", 0f),
        )
    }

    @Test
    fun `unresolvable chapter returns null`() {
        assertNull(ChapterBinder.bind(Carrier.MANGA, work, "Prologue", 0f))
        assertNull(ChapterBinder.bind(Carrier.MANGA, work, "Extra", -1f))
    }

    @Test
    fun `episode binding produces e-prefixed id`() {
        assertEquals(
            "shows:mk:imdb:tt0944947:e5",
            ChapterBinder.bindEpisode("mk:imdb:tt0944947", 5f),
        )
    }

    @Test
    fun `produced ids satisfy worker thread regex`() {
        val re = Regex("^(shows|manga|books):[^\\s]{1,200}$")
        val ids = listOfNotNull(
            ChapterBinder.bind(Carrier.MANGA, work, "Vol.4 Ch.30", 30f),
            ChapterBinder.bind(Carrier.BOOKS, work, "Chapter 7", 7f),
            ChapterBinder.bindEpisode("mk:imdb:tt0944947", 12.5f),
        )
        ids.forEach { assert(it.matches(re)) { "bad thread id: $it" } }
    }
}

class ChapterFingerprintTest {

    @Test
    fun `fingerprint is order independent`() {
        val a = ChapterFingerprint.of(
            listOf("Chapter 1" to 1f, "Chapter 2" to 2f, "Chapter 3" to 3f),
        )
        val b = ChapterFingerprint.of(
            listOf("Chapter 3" to 3f, "Chapter 1" to 1f, "Chapter 2" to 2f),
        )
        assertEquals(a.namesHash, b.namesHash)
        assertEquals(listOf(1f, 2f, 3f), b.numbers)
        assertEquals(3, b.count)
    }

    @Test
    fun `non-positive numbers resolve via name and unresolvable drop`() {
        val fp = ChapterFingerprint.of(
            listOf(
                "Chapter 1" to 0f, // resolves to 1 via name
                "Prologue" to 0f, // dropped
                "Chapter 5" to 5f,
            ),
        )
        assertEquals(2, fp.count)
        assertEquals(listOf(1f, 5f), fp.numbers)
    }

    @Test
    fun `different lists produce different hashes`() {
        val a = ChapterFingerprint.of(listOf("Chapter 1" to 1f))
        val b = ChapterFingerprint.of(listOf("Chapter 2" to 2f))
        assert(a.namesHash != b.namesHash)
        assertEquals(16, a.namesHash.length)
    }
}
