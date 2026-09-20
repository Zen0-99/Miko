package eu.kanade.tachiyomi.ui.reader.novel

import android.os.Build
import android.text.Html
import android.text.Spanned
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag

/**
 * Shared chapter-HTML → paragraph segmentation, extracted verbatim from
 * `NovelReaderScreenModel.parseHtmlToParagraphs` /
 * `parseHtmlToParagraphsProgressive` (plan 05-01).
 *
 * Both the reader and the chapter-audio renderer consume this object so the
 * manifest's `paragraphTexts`/`paragraphOffsetsMs` indexing can never drift
 * from the reader's highlight indexing (RESEARCH §6, risk 5):
 *
 * - The reader needs each paragraph's inner HTML (`[segmentParagraphHtml]`)
 *   rendered to a `Spanned` (`[renderParagraph]`) so it can keep its
 *   `TextItem.Paragraph` char-index math.
 * - The renderer needs the blank-filtered plain-text list
 *   (`[paragraphTexts]`) — identical to the list `startTtsPlayback` feeds
 *   the TTS service.
 *
 * [segmentParagraphHtml] is pure Jsoup (JVM-testable). [renderParagraph]
 * keeps the reader's fast path — inner HTML without a `<` is returned as the
 * raw `String` (a `CharSequence`), never touching `Html.fromHtml`; the
 * tag-bearing path is Android-side only.
 */
object NovelParagraphSegmenter {

    /**
     * Parse chapter HTML into the inner-HTML string of each paragraph
     * element, in document order.
     *
     * Semantics preserved from the reader:
     * - `style`, `script`, `noscript` subtrees are removed first (they
     *   produce stray "-->" artifacts and phantom gaps otherwise).
     * - `<p>` elements are the paragraphs; an entry whose inner HTML is
     *   blank is skipped.
     * - Fallback when no `<p>` exists: `body().text()` (Jsoup-normalised
     *   whitespace — preserved only inside `<pre>`-style nodes) split on
     *   `\n\n+|\r\n\r\n+`, each piece trimmed and non-blank.
     */
    fun segmentParagraphHtml(html: String): List<String> {
        val document = Jsoup.parse(html)
        document.select("style, script, noscript").remove()

        val paragraphs = document.select("p")

        // Fallback: if no <p> tags, split body text by double-newlines.
        val elementsToProcess: List<Element> = if (paragraphs.isEmpty()) {
            val bodyText = document.body().text()
            if (bodyText.isNotBlank()) {
                bodyText.split(Regex("\n\n+|\r\n\r\n+"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { line ->
                        Element(Tag.valueOf("p"), "").apply { html(line) }
                    }
            } else {
                emptyList()
            }
        } else {
            paragraphs
        }

        return elementsToProcess
            .map { it.html() }
            .filter { it.isNotBlank() }
    }

    /**
     * Render one paragraph's inner HTML to display text.
     *
     * Fast path: inner HTML containing no `<` is already plain text — return
     * the raw string instead of paying for `Html.fromHtml` (the majority of
     * novel paragraphs; the reader measured a 5-10x parse saving). The
     * tag-bearing path delegates to `Html.fromHtml` and returns its `Spanned`.
     */
    fun renderParagraph(paragraphHtml: String): CharSequence {
        if (!paragraphHtml.contains('<')) {
            return paragraphHtml
        }
        return renderHtml(paragraphHtml)
    }

    /**
     * The renderer's paragraph source — and the manifest's `paragraphTexts`
     * source: segment → render → drop blank renders → plain `String`s.
     * Matches the reader's TTS list (`TextItem.Paragraph` filtered by
     * `text.isNotBlank()`, `.toString()`ed).
     */
    fun paragraphTexts(html: String): List<String> =
        segmentParagraphHtml(html)
            .map { renderParagraph(it) }
            .filter { it.isNotBlank() }
            .map { it.toString() }

    @Suppress("DEPRECATION")
    private fun renderHtml(source: String): Spanned {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT)
        } else {
            Html.fromHtml(source)
        }
    }
}
