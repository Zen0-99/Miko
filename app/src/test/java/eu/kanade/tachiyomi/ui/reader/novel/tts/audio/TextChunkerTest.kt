package eu.kanade.tachiyomi.ui.reader.novel.tts.audio

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Tests for [TextChunker], ported from marmalade-tts-android's
 * TextChunkerTest — CJK sentence-splitting plus regression coverage that
 * the ASCII behaviour is unchanged, the minChars merge path, the
 * first-chunk exemption (TTFA), and terminalMarksOnly mode.
 */
class TextChunkerTest {

    // -- CJK splitting -------------------------------------------------------

    @Test
    fun `japanese sentences split on ideographic stop no whitespace`() {
        // 。 takes no following space in Japanese — must still split.
        val chunks = TextChunker.chunk(
            text = "これはテストです。元気ですか。さようなら。",
            maxChars = 255,
            packSentences = false,
            sentenceOnly = true,
            allowWordSplits = false,
        )
        chunks shouldBe listOf("これはテストです。", "元気ですか。", "さようなら。")
    }

    @Test
    fun `japanese fullwidth question and bang split`() {
        val chunks = TextChunker.chunk(
            text = "元気ですか？はい！",
            maxChars = 255,
            packSentences = false,
            sentenceOnly = true,
            allowWordSplits = false,
        )
        chunks shouldBe listOf("元気ですか？", "はい！")
    }

    @Test
    fun `japanese comma does not split`() {
        // 、 is a clause comma, not a sentence end — stays in one chunk
        // (mirrors ASCII comma behaviour in sentenceOnly mode).
        val chunks = TextChunker.chunk(
            text = "こんにちは、元気ですか。",
            maxChars = 255,
            packSentences = false,
            sentenceOnly = true,
            allowWordSplits = false,
        )
        chunks shouldBe listOf("こんにちは、元気ですか。")
    }

    // -- ASCII regression -----------------------------------------------------

    @Test
    fun `english still splits on period space`() {
        val chunks = TextChunker.chunk(
            text = "First sentence. Second sentence. Third one.",
            maxChars = 255,
            packSentences = false,
            sentenceOnly = true,
            allowWordSplits = false,
        )
        chunks shouldBe listOf("First sentence.", "Second sentence.", "Third one.")
    }

    @Test
    fun `short single sentence is one chunk`() {
        TextChunker.chunk("Hello there.", maxChars = 255) shouldBe listOf("Hello there.")
    }

    @Test
    fun `blank input is empty`() {
        TextChunker.chunk("   ", maxChars = 255).isEmpty() shouldBe true
    }

    // -- minChars merge + first-chunk exemption (TTFA) ------------------------

    @Test
    fun `minChars merges tiny sentences`() {
        val chunks = TextChunker.chunk(
            text = "Yes. Sure thing. Absolutely, whenever you like it best.",
            maxChars = 255,
            packSentences = false,
            sentenceOnly = true,
            allowWordSplits = false,
            minChars = 20,
        )
        chunks shouldBe listOf("Yes. Sure thing. Absolutely, whenever you like it best.")
    }

    @Test
    fun `exempt first emits short opening sentence alone`() {
        val chunks = TextChunker.chunk(
            text = "Yes. Sure thing. Absolutely, whenever you like it best.",
            maxChars = 255,
            packSentences = false,
            sentenceOnly = true,
            allowWordSplits = false,
            minChars = 20,
            minCharsExemptFirst = true,
        )
        chunks shouldBe listOf("Yes.", "Sure thing. Absolutely, whenever you like it best.")
    }

    @Test
    fun `exempt first applies only to first paragraph`() {
        val chunks = TextChunker.chunk(
            text = "Hi. Second sentence here padding along.\n\nOk. Later paragraph sentence padding.",
            // Force past the single-chunk fast path so paragraphs split.
            maxChars = 60,
            packSentences = false,
            sentenceOnly = true,
            allowWordSplits = false,
            minChars = 20,
            minCharsExemptFirst = true,
        )
        chunks shouldBe listOf(
            "Hi.",
            "Second sentence here padding along.",
            "Ok. Later paragraph sentence padding.",
        )
    }

    @Test
    fun `exempt first is no op when first sentence already long`() {
        val long = "This opening sentence is comfortably longer than the merge floor."
        val chunks = TextChunker.chunk(
            text = "$long Short tail.",
            maxChars = 255,
            packSentences = false,
            sentenceOnly = true,
            allowWordSplits = false,
            minChars = 20,
            minCharsExemptFirst = true,
        )
        chunks shouldBe listOf(long, "Short tail.")
    }

    // -- terminalMarksOnly (per-sentence mode) --------------------------------

    @Test
    fun `terminal marks only keeps colon and semicolon in sentence`() {
        // Rows are computed per sentence; a `:`/`;` split would compute
        // them on fragments and re-register mid-sentence.
        val chunks = TextChunker.chunk(
            text = "Look at the panel: is it blinking; or not? All good.",
            maxChars = 255,
            packSentences = false,
            allowWordSplits = false,
            terminalMarksOnly = true,
        )
        chunks shouldBe listOf("Look at the panel: is it blinking; or not?", "All good.")
    }

    @Test
    fun `terminal marks only splits every sentence`() {
        val chunks = TextChunker.chunk(
            text = "Stop! Don't touch that wire. Take three steps back, slowly.",
            maxChars = 255,
            packSentences = false,
            allowWordSplits = false,
            terminalMarksOnly = true,
        )
        chunks shouldBe listOf("Stop!", "Don't touch that wire.", "Take three steps back, slowly.")
    }

    @Test
    fun `terminal marks only splits on newlines`() {
        val chunks = TextChunker.chunk(
            text = "First line\nsecond line",
            maxChars = 255,
            packSentences = false,
            allowWordSplits = false,
            terminalMarksOnly = true,
        )
        chunks shouldBe listOf("First line", "second line")
    }

    @Test
    fun `terminal marks only keeps mark inside closing quote attached`() {
        // '?" she asked.' — the mark sits inside the quotes, so the
        // quoted sentence stays with its attribution.
        val chunks = TextChunker.chunk(
            text = "\"Where did you put the keys?\" she asked. He shrugged.",
            maxChars = 255,
            packSentences = false,
            allowWordSplits = false,
            terminalMarksOnly = true,
        )
        chunks shouldBe listOf("\"Where did you put the keys?\" she asked.", "He shrugged.")
    }
}
