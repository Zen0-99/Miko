package eu.kanade.tachiyomi.data.mikodb

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CanonicalChapterMapTest {

    private val map = CanonicalChapterMap(api = mockk())

    private fun canon(
        seq: Int,
        number: Double?,
        part: Int? = null,
        kind: ChapterNumberParser.Kind = ChapterNumberParser.Kind.CHAPTER,
    ) = CanonicalChapterMap.CanonChapter(
        seq = seq, number = number, part = part, volume = null,
        kind = kind, label = "", confidence = "verified",
        sources = listOf("a", "b"), refs = emptyMap(),
    )

    private val list = listOf(
        canon(0, null, kind = ChapterNumberParser.Kind.PROLOGUE),
        canon(1, 1.0),
        canon(2, 2.0),
        canon(3, 30.0, part = 1),
        canon(4, 30.0, part = 2),
        canon(5, 142.5),
        canon(6, 55.0, kind = ChapterNumberParser.Kind.EXTRA),
    )

    @Test
    fun `source chapter name binds to canonical row`() {
        assertEquals(1.0, map.bind("Ch. 01", list)?.number)
        map.bind("Chapter 30 Part 2", list).let {
            assertEquals(30.0, it?.number)
            assertEquals(2, it?.part)
        }
        assertEquals(142.5, map.bind("Chap. 142.5", list)?.number)
    }

    @Test
    fun `word names and letter suffixes bind`() {
        assertEquals(1.0, map.bind("Chapter One", list)?.number)
        assertEquals(2, map.bind("30b", list)?.part)
        assertEquals(1, map.bind("Chapter 30a", list)?.part)
    }

    @Test
    fun `unknown names never bind`() {
        assertNull(map.bind("Cahapteare1", list))
        assertNull(map.bind("afkj23ds", list))
        assertNull(map.bind("", list))
    }

    @Test
    fun `unmatched number returns null`() {
        assertNull(map.bind("Chapter 999", list))
    }

    @Test
    fun `kind-mismatched number falls back to number match`() {
        // canon only has 55 as an extra; a source calling it "Chapter 55"
        // still binds by number+part.
        val bound = map.bind("Chapter 55", list)
        assertEquals(ChapterNumberParser.Kind.EXTRA, bound?.kind)
        assertEquals(55.0, bound?.number)
    }

    @Test
    fun `prologue binds without a number`() {
        assertEquals(ChapterNumberParser.Kind.PROLOGUE, map.bind("Prologue", list)?.kind)
    }
}
