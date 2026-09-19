package eu.kanade.tachiyomi.ui.reader.novel.tts.pocket

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Tests for [PocketTokenizer] — the pure-Kotlin SentencePiece Unigram
 * port (Viterbi segmentation + protobuf wire parse + byte fallback).
 *
 * Most tests run against a tiny synthetic `tokenizer.model` built in
 * memory (real protobuf wire format, ~264 pieces), so coverage does not
 * depend on the optional bundle fixture. The fixture-gated test at the
 * bottom exercises the real Kyutai model when
 * `src/test/resources/pocket/tokenizer.model` exists (emitted by
 * `tools/build-pocket-bundle.py --emit-fixtures`).
 */
class PocketTokenizerTest {

    @TempDir
    lateinit var dir: File

    /**
     * Piece ids in the synthetic model:
     *   0 `<unk>` (UNKNOWN), 1 `<s>` (CONTROL), 2 `</s>` (CONTROL),
     *   3 `▁` (word boundary), 4-7 h/e/l/o, 8..263 `<0x00>`..`<0xFF>` (BYTE).
     */
    private fun syntheticTokenizer(): PocketTokenizer {
        val pieces = mutableListOf<Triple<String, Float, Int>>()
        pieces += Triple("<unk>", -10f, 2)
        pieces += Triple("<s>", 0f, 3)
        pieces += Triple("</s>", 0f, 3)
        pieces += Triple("▁", -1f, 1)
        pieces += Triple("h", -1f, 1)
        pieces += Triple("e", -1f, 1)
        pieces += Triple("l", -1f, 1)
        pieces += Triple("o", -1f, 1)
        for (b in 0..255) {
            pieces += Triple("<0x%02X>".format(b), -20f, 6)
        }
        val file = File(dir, "tokenizer.model").apply { writeBytes(modelBytes(pieces)) }
        return PocketTokenizer.load(file)
    }

    @Test
    fun `encode produces piece ids for known text`() {
        val tokenizer = syntheticTokenizer()
        // "hello" → "▁hello" — boundary + one piece per letter.
        tokenizer.encode("hello") shouldBe intArrayOf(3, 4, 5, 6, 6, 7)
        tokenizer.vocabSize shouldBe 264
    }

    @Test
    fun `encode empty string returns empty`() {
        syntheticTokenizer().encode("") shouldBe intArrayOf()
    }

    @Test
    fun `unmapped characters fall back to byte pieces`() {
        val tokenizer = syntheticTokenizer()
        // 'é' (U+00E9) is not a piece — UTF-8 encodes to C3 A9, so the
        // byte fallback emits the <0xC3> / <0xA9> piece ids (8 + byte).
        tokenizer.encode("hé") shouldBe intArrayOf(3, 4, 8 + 0xC3, 8 + 0xA9)
    }

    @Test
    fun `empty model file fails fast`() {
        val file = File(dir, "empty.model").apply { writeBytes(ByteArray(0)) }
        shouldThrow<IllegalArgumentException> { PocketTokenizer.load(file) }
    }

    @Test
    fun `real tokenizer model encodes ascii text when fixture present`() {
        // Emitted by tools/build-pocket-bundle.py --emit-fixtures app/src/test/resources/pocket
        val fixture = File("src/test/resources/pocket/tokenizer.model")
        Assumptions.assumeTrue(fixture.isFile) { "no tokenizer.model fixture — skipping" }
        val tokenizer = PocketTokenizer.load(fixture)
        tokenizer.vocabSize shouldBeGreaterThan 1000
        tokenizer.encode("Hello world.").isNotEmpty() shouldBe true
        tokenizer.encode("").size shouldBe 0
    }

    // -- synthetic SentencePiece model builder --------------------------------

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v == 0L) {
                out.write(b)
                return
            }
            out.write(b or 0x80)
        }
    }

    /**
     * Encode a ModelProto containing only `pieces` entries (tag 1,
     * length-delimited). Each SentencePiece sub-message carries `piece`
     * (tag 1, string), `score` (tag 2, fixed32) and `type` (tag 3, varint)
     * — exactly the fields [PocketTokenizer.load] reads.
     */
    private fun modelBytes(pieces: List<Triple<String, Float, Int>>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((piece, score, type) in pieces) {
            val sub = ByteArrayOutputStream()
            sub.write(0x0A) // field 1, wire 2
            val pb = piece.toByteArray(Charsets.UTF_8)
            writeVarint(sub, pb.size.toLong())
            sub.write(pb)
            sub.write(0x15) // field 2, wire 5
            val bits = score.toBits()
            for (i in 0 until 4) sub.write((bits ushr (i * 8)) and 0xFF)
            sub.write(0x18) // field 3, wire 0
            writeVarint(sub, type.toLong())
            val subBytes = sub.toByteArray()
            out.write(0x0A) // field 1, wire 2
            writeVarint(out, subBytes.size.toLong())
            out.write(subBytes)
        }
        return out.toByteArray()
    }
}
