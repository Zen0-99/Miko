package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * JVM unit tests for [NovelParagraphSegmenter] — the shared chapter-HTML
 * segmenter extracted from `NovelReaderScreenModel` (plan 05-01, Task 1).
 *
 * The segmenter must produce the SAME paragraph list the reader produces:
 * `Jsoup.parse` → remove `style, script, noscript` → select `p` → skip blank
 * inner HTML; no-`<p>` fallback splits `body().text()` on
 * `\n\n+|\r\n\r\n+`. The renderer's manifest paragraph indexing is built on
 * [NovelParagraphSegmenter.paragraphTexts], so drift from these semantics is
 * a manifest/highlight-misalignment bug (RESEARCH §6).
 *
 * `renderParagraph`/`paragraphTexts` are exercised only on tag-free content
 * here — the `Html.fromHtml` path is Android-side (asserted indirectly by
 * reader parity; a JVM stub would throw "not mocked").
 */
class NovelParagraphSegmenterTest {

    @Test
    fun `segment returns inner html of p elements in document order`() {
        val html = """
            <html><body>
            <p>First paragraph.</p>
            <p>Second <b>bold</b> paragraph.</p>
            <p>Third paragraph.</p>
            </body></html>
        """.trimIndent()

        NovelParagraphSegmenter.segmentParagraphHtml(html) shouldBe listOf(
            "First paragraph.",
            "Second <b>bold</b> paragraph.",
            "Third paragraph.",
        )
    }

    @Test
    fun `segment skips blank p elements and strips style script noscript`() {
        val html = """
            <html><head>
            <style>p { color: red }</style>
            <script>var marker = "&lt;p&gt;fake&lt;/p&gt;";</script>
            </head><body>
            <noscript>not real content</noscript>
            <p>Real one.</p>
            <p>   </p>
            <p>
            </p>
            <p>Real two.</p>
            </body></html>
        """.trimIndent()

        NovelParagraphSegmenter.segmentParagraphHtml(html) shouldBe listOf(
            "Real one.",
            "Real two.",
        )
    }

    @Test
    fun `segment keeps nested tags inside paragraph inner html`() {
        val html = """<p>He said <a href="x">"hello"</a> — <em>loudly</em>.</p>"""

        NovelParagraphSegmenter.segmentParagraphHtml(html) shouldBe listOf(
            """He said <a href="x">"hello"</a> — <em>loudly</em>.""",
        )
    }

    @Test
    fun `segment without p tags falls back to double-newline body split`() {
        // Jsoup's body().text() collapses whitespace EXCEPT inside <pre>
        // (preserveWhitespace) — a <pre> block is the only fixture shape that
        // reaches the \n\n split with real newlines.
        val html = "<html><body><pre>Block one.\n\nBlock two.\r\n\r\nBlock three.</pre></body></html>"

        NovelParagraphSegmenter.segmentParagraphHtml(html) shouldBe listOf(
            "Block one.",
            "Block two.",
            "Block three.",
        )
    }

    @Test
    fun `segment without p tags collapses plain-text whitespace to one paragraph`() {
        // Documents the existing reader semantics: body().text() normalizes
        // \n\n runs to single spaces, so the split yields one paragraph.
        val html = "First block.\n\nSecond block."

        NovelParagraphSegmenter.segmentParagraphHtml(html) shouldBe listOf(
            "First block. Second block.",
        )
    }

    @Test
    fun `segment of empty or whitespace html returns empty list`() {
        NovelParagraphSegmenter.segmentParagraphHtml("") shouldBe emptyList()
        NovelParagraphSegmenter.segmentParagraphHtml("<html><body></body></html>") shouldBe emptyList()
        NovelParagraphSegmenter.segmentParagraphHtml("<p></p><p> </p>") shouldBe emptyList()
    }

    @Test
    fun `paragraphTexts returns rendered plain text with blanks filtered`() {
        val html = """
            <html><body>
            <p>Alpha.</p>
            <p>   </p>
            <p>Beta.</p>
            </body></html>
        """.trimIndent()

        NovelParagraphSegmenter.paragraphTexts(html) shouldBe listOf("Alpha.", "Beta.")
    }

    @Test
    fun `paragraphTexts keeps entity-escaped text verbatim on the fast path`() {
        // Parity note: the reader's fast path returns the inner HTML string
        // unchanged when it contains no '<' — entities stay escaped.
        NovelParagraphSegmenter.paragraphTexts("<p>a &amp; b</p>") shouldBe listOf("a &amp; b")
    }

    @Test
    fun `renderParagraph returns the raw string when html has no tag`() {
        val rendered = NovelParagraphSegmenter.renderParagraph("plain words")
        rendered.toString() shouldBe "plain words"
    }
}
