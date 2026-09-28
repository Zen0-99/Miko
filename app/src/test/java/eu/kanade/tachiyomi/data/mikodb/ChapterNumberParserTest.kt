package eu.kanade.tachiyomi.data.mikodb

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChapterNumberParserTest {

    @Serializable
    data class Vector(
        val `in`: String,
        val number: Double? = null,
        val volume: Double? = null,
        val part: Int? = null,
        val kind: String,
    )

    private val vectors: List<Vector> = Json.decodeFromString(
        checkNotNull(javaClass.classLoader?.getResource("chapter_parser_golden.json"))
            .readText(),
    )

    @Test
    fun `golden vectors all pass`() {
        val failures = vectors.mapNotNull { v ->
            val p = ChapterNumberParser.parse(v.`in`)
            val expected = ChapterNumberParser.Kind.entries.first { it.wire == v.kind }
            if (p.number != v.number ||
                p.volume != v.volume ||
                p.part != v.part ||
                p.kind != expected
            ) {
                "${v.`in`}: got ${p.number}/${p.volume}/${p.part}/${p.kind} " +
                    "want ${v.number}/${v.volume}/${v.part}/${v.kind}"
            } else {
                null
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `wordToNumber handles compound forms`() {
        assertEquals(23, ChapterNumberParser.wordToNumber(listOf("twenty", "three"))?.first)
        assertEquals(105, ChapterNumberParser.wordToNumber(listOf("one", "hundred", "and", "five"))?.first)
        assertEquals(100, ChapterNumberParser.wordToNumber(listOf("a", "hundred"))?.first)
        assertEquals(null, ChapterNumberParser.wordToNumber(listOf("the", "beginning")))
    }

    @Test
    fun `romanToInt handles part positions`() {
        assertEquals(2, ChapterNumberParser.romanToInt("ii"))
        assertEquals(4, ChapterNumberParser.romanToInt("iv"))
        assertEquals(14, ChapterNumberParser.romanToInt("xiv"))
        assertEquals(null, ChapterNumberParser.romanToInt("xyz"))
    }
}
