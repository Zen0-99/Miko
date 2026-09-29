package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.data.mikodb.CanonicalChapterMap.CanonChapter
import eu.kanade.tachiyomi.data.mikodb.ChapterNumberParser.Kind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CanonicalChapterSyncTest {

    private fun canon(
        seq: Int,
        number: Double?,
        label: String,
        kind: Kind = Kind.CHAPTER,
        refs: Map<String, String> = emptyMap(),
        dateUpload: Long? = null,
    ) = CanonChapter(
        seq = seq,
        number = number,
        part = null,
        volume = null,
        kind = kind,
        label = label,
        confidence = "verified",
        sources = refs.keys.toList(),
        refs = refs,
        dateUpload = dateUpload,
    )

    @Test
    fun `canon rows become newest-first chapters with stable urls`() {
        val rows = listOf(
            canon(0, null, "Prologue", Kind.PROLOGUE),
            canon(1, 1.0, "Chapter 1"),
            canon(2, 2.0, "Chapter 2"),
        )
        val out = CanonicalChapterSync.toSChapters(rows, "mk:mb:1")
        // Newest first, like a real source feed.
        assertEquals("Chapter 2", out[0].name)
        assertEquals("Chapter 1", out[1].name)
        assertEquals("Prologue", out[2].name)
        // Stable canon urls — never source urls.
        assertEquals("canon:mk:mb:1:2", out[0].url)
        assertEquals("canon:mk:mb:1:0", out[2].url)
        assertEquals(2.0f, out[0].chapter_number)
        assertEquals(0.0f, out[2].chapter_number) // unnumbered kind
    }

    @Test
    fun `scanlator stays null — carrier refs never surface as package names`() {
        val row = canon(
            1, 30.0, "Chapter 30",
            refs = mapOf(
                "en.mangadex" to "/ch/a",
                "en.asura" to "/ch/b",
            ),
        )
        val out = CanonicalChapterSync.toSChapters(listOf(row), "mk:mb:1")
        assertNull(out[0].scanlator)
    }

    @Test
    fun `carrier upload date flows through to the row`() {
        val row = canon(1, 1.0, "Chapter 1", dateUpload = 1_700_000_000_000L)
        val out = CanonicalChapterSync.toSChapters(listOf(row), "mk:mb:1")
        assertEquals(1_700_000_000_000L, out[0].date_upload)
    }

    @Test
    fun `missing carrier date stays zero — card shows N-A, not now`() {
        val row = canon(1, 1.0, "Chapter 1")
        val out = CanonicalChapterSync.toSChapters(listOf(row), "mk:mb:1")
        assertEquals(0L, out[0].date_upload)
    }

    @Test
    fun `redundant trailing chapter marker is stripped when it repeats the number`() {
        assertEquals(
            "Ep. 1 - Beginning of the End",
            CanonicalChapterSync.normalizeLabel("Ep. 1 - Beginning of the End (ch. 1)", 1.0),
        )
        assertEquals(
            "Chapter 30",
            CanonicalChapterSync.normalizeLabel("Chapter 30 [chapter 30]", 30.0),
        )
    }

    @Test
    fun `marker with a different number is real information and kept`() {
        // Source numbering offset — the parenthetical isn't redundant.
        val label = "Episode 5 (ch. 12)"
        assertEquals(label, CanonicalChapterSync.normalizeLabel(label, 5.0))
    }

    @Test
    fun `unnumbered rows keep their labels untouched`() {
        assertEquals(
            "Omake (ch. 99)",
            CanonicalChapterSync.normalizeLabel("Omake (ch. 99)", null),
        )
        // Empty-after-strip labels keep the original rather than going blank.
        assertEquals("(ch. 7)", CanonicalChapterSync.normalizeLabel("(ch. 7)", 7.0))
    }

    @Test
    fun `unnumbered extras keep their labels`() {
        val rows = listOf(
            canon(9, null, "Omake", Kind.EXTRA),
            canon(10, null, "Afterword", Kind.AFTERWORD),
        )
        val out = CanonicalChapterSync.toSChapters(rows, "mk:mb:1")
        assertTrue(out.all { it.chapter_number == 0.0f })
        assertEquals("Afterword", out[0].name) // newest-first
    }

    @Test
    fun `canon thread id round-trips a numbered row`() {
        // The materialized chapter feeds straight into ChapterBinder —
        // "manga:mk:mb:1:c30" is the shared thread regardless of carrier.
        val row = canon(1, 30.0, "Chapter 30", refs = mapOf("en.asura" to "/x"))
        val ch = CanonicalChapterSync.toSChapters(listOf(row), "mk:mb:1")[0]
        val thread = eu.kanade.tachiyomi.data.comments.ChapterBinder
            .bind(eu.kanade.domain.ui.model.Carrier.MANGA, "mk:mb:1", ch.name, ch.chapter_number)
        assertEquals("manga:mk:mb:1:c30", thread)
    }
}
