package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.data.mikodb.CanonicalChapterMap.CanonChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `canon:{workId}:{seq}` urls encode the chapter's merge position, so a
 * re-canonize that inserts or reorders carrier feeds renumbers every
 * shifted row. `realignUpdates` re-points existing rows to their new
 * seq in place — keeping the row id (and its `lastPageRead`, history,
 * bookmark links) — instead of letting `SyncChaptersWithSource` delete
 * and re-add them.
 */
class CanonicalChapterSyncTest {

    private val workId = "mk:mb:1"

    private fun canon(seq: Int, number: Double, label: String = "Chapter $number") = CanonChapter(
        seq = seq,
        number = number,
        part = null,
        volume = null,
        kind = ChapterNumberParser.Kind.CHAPTER,
        label = label,
        confidence = "high",
        sources = emptyList(),
        refs = emptyMap(),
    )

    /** Bind rows to canon by chapter number parsed from the row name. */
    private fun bindByNumber(
        name: String,
        canon: List<CanonChapter>,
    ): CanonChapter? {
        val number = name.removePrefix("Chapter ").toDoubleOrNull() ?: return null
        return canon.firstOrNull { it.number == number }
    }

    @Test
    fun `stale seq urls are re-pointed to the current canon position`() {
        // Carrier insert pushed "Chapter 2" from seq 5 to seq 1.
        val canon = listOf(canon(seq = 0, number = 1.0), canon(seq = 1, number = 2.0))
        val rows = listOf(Triple(7L, "canon:$workId:5", "Chapter 2"))
        assertEquals(
            listOf(7L to "canon:$workId:1"),
            CanonicalChapterSync.realignUpdates(canon, rows, workId, ::bindByNumber),
        )
    }

    @Test
    fun `rows already at their canon seq emit nothing`() {
        val canon = listOf(canon(seq = 0, number = 1.0), canon(seq = 1, number = 2.0))
        val rows = listOf(
            Triple(1L, "canon:$workId:0", "Chapter 1"),
            Triple(2L, "canon:$workId:1", "Chapter 2"),
        )
        assertTrue(CanonicalChapterSync.realignUpdates(canon, rows, workId, ::bindByNumber).isEmpty())
    }

    @Test
    fun `rows with no canon counterpart keep their url`() {
        val canon = listOf(canon(seq = 0, number = 1.0))
        val rows = listOf(Triple(3L, "canon:$workId:9", "Special One-Shot"))
        assertTrue(CanonicalChapterSync.realignUpdates(canon, rows, workId, ::bindByNumber).isEmpty())
    }

    @Test
    fun `two stale rows cannot claim the same canon url`() {
        val canon = listOf(canon(seq = 1, number = 2.0))
        val rows = listOf(
            Triple(4L, "canon:$workId:5", "Chapter 2"),
            Triple(5L, "canon:$workId:8", "Chapter 2"),
        )
        assertEquals(
            listOf(4L to "canon:$workId:1"),
            CanonicalChapterSync.realignUpdates(canon, rows, workId, ::bindByNumber),
        )
    }

    @Test
    fun `empty canon sequence realigns nothing`() {
        val rows = listOf(Triple(6L, "canon:$workId:5", "Chapter 2"))
        assertTrue(
            CanonicalChapterSync.realignUpdates(emptyList(), rows, workId, ::bindByNumber).isEmpty(),
        )
    }
}
