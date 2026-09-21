package eu.kanade.tachiyomi.ui.reader.novel.tts.supertonic

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Byte-format contract tests for the sherpa `.bin` parsers (validation
 * rows V3/V4, JVM half — the byte layout is the claim; ORT tensor
 * wrapping is the engine's job and is exercised on-device).
 */
class SupertonicBinFormatsTest {

    @TempDir
    lateinit var dir: File

    // ---- voice.bin -------------------------------------------------------

    @Test
    fun `voice bin parses header dims and slices per-sid rows`() {
        val file = File(dir, "voice.bin")
        val expected = SupertonicTestFixtures.writeVoiceBin(
            file, n = 2, ttlD1 = 2, ttlD2 = 3, dpD1 = 1, dpD2 = 4,
        )

        val data = VoiceBinReader.parse(file)
        data.ttlDims.toList() shouldBe listOf(2L, 2L, 3L)
        data.dpDims.toList() shouldBe listOf(2L, 1L, 4L)
        data.count shouldBe 2
        data.ttlData.toList() shouldBe expected.ttlData.toList()
        data.dpData.toList() shouldBe expected.dpData.toList()

        val (ttl0, ttlShape0) = data.ttlSlice(0)
        ttl0.toList() shouldBe expected.ttlRow(0).toList()
        ttlShape0.toList() shouldBe listOf(1L, 2L, 3L)

        val (ttl1, ttlShape1) = data.ttlSlice(1)
        ttl1.toList() shouldBe expected.ttlRow(1).toList()
        ttlShape1.toList() shouldBe listOf(1L, 2L, 3L)

        val (dp1, dpShape1) = data.dpSlice(1)
        dp1.toList() shouldBe expected.dpRow(1).toList()
        dpShape1.toList() shouldBe listOf(1L, 1L, 4L)
    }

    @Test
    fun `voice bin slice equals the equivalent voice_styles json payload`() {
        // V3: a voice.bin row must be identical to the matching
        // voice_styles/<name>.json payload the fp32 layout ships.
        val bin = File(dir, "voice.bin")
        val expected = SupertonicTestFixtures.writeVoiceBin(
            bin, n = 10, ttlD1 = 4, ttlD2 = 5, dpD1 = 2, dpD2 = 7,
        )
        val sid = 4 // F5

        val styleJson = File(dir, "F5.json")
        SupertonicTestFixtures.writeVoiceStyleJson(
            styleJson,
            ttlData = expected.ttlRow(sid), ttlD1 = 4, ttlD2 = 5,
            dpData = expected.dpRow(sid), dpD1 = 2, dpD2 = 7,
        )

        // Flatten the JSON the same way the engine's fp32 fallback does.
        val root = Json.parseToJsonElement(styleJson.readText()).jsonObject
        fun flatten(key: String): Pair<FloatArray, LongArray> {
            val node = root.getValue(key).jsonObject
            val dims = node.getValue("dims").jsonArray
            val shape = longArrayOf(
                dims[0].jsonPrimitive.int.toLong(),
                dims[1].jsonPrimitive.int.toLong(),
                dims[2].jsonPrimitive.int.toLong(),
            )
            val flat = FloatArray((shape[0] * shape[1] * shape[2]).toInt())
            var idx = 0
            for (b in node.getValue("data").jsonArray) {
                for (r in b.jsonArray) {
                    for (v in r.jsonArray) {
                        flat[idx++] = v.jsonPrimitive.float
                    }
                }
            }
            return flat to shape
        }
        val (jsonTtl, jsonTtlShape) = flatten("style_ttl")
        val (jsonDp, jsonDpShape) = flatten("style_dp")

        val (binTtl, binTtlShape) = VoiceBinReader.parse(bin).ttlSlice(sid)
        val (binDp, binDpShape) = VoiceBinReader.parse(bin).dpSlice(sid)

        binTtlShape.toList() shouldBe jsonTtlShape.toList()
        binTtl.toList() shouldBe jsonTtl.toList()
        binDpShape.toList() shouldBe jsonDpShape.toList()
        binDp.toList() shouldBe jsonDp.toList()
    }

    @Test
    fun `voice bin sid out of range throws`() {
        val file = File(dir, "voice.bin")
        SupertonicTestFixtures.writeVoiceBin(file, n = 2)
        val data = VoiceBinReader.parse(file)
        shouldThrow<IllegalArgumentException> { data.ttlSlice(2) }
        shouldThrow<IllegalArgumentException> { data.ttlSlice(-1) }
    }

    @Test
    fun `truncated voice bin throws IOException`() {
        val file = File(dir, "voice.bin")
        SupertonicTestFixtures.writeVoiceBin(file, n = 2)
        file.writeBytes(file.readBytes().copyOf(file.length().toInt() - 8))
        shouldThrow<IOException> { VoiceBinReader.parse(file) }
    }

    @Test
    fun `voice bin shorter than header throws IOException`() {
        val file = File(dir, "voice.bin")
        file.writeBytes(ByteArray(24))
        shouldThrow<IOException> { VoiceBinReader.parse(file) }
    }

    @Test
    fun `voice bin with non-positive dims throws IOException`() {
        val file = File(dir, "voice.bin")
        val buf = java.nio.ByteBuffer.allocate(48 + 16)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        // ttl dims [2, 0, 3] — non-positive middle dim
        buf.putLong(2); buf.putLong(0); buf.putLong(3)
        buf.putLong(2); buf.putLong(1); buf.putLong(4)
        file.writeBytes(buf.array())
        shouldThrow<IOException> { VoiceBinReader.parse(file) }
    }

    @Test
    fun `voice bin length mismatch throws IOException`() {
        val file = File(dir, "voice.bin")
        SupertonicTestFixtures.writeVoiceBin(file, n = 2)
        // Append 4 bytes — header-declared length no longer matches.
        file.appendBytes(ByteArray(4))
        shouldThrow<IOException> { VoiceBinReader.parse(file) }
    }

    @Test
    fun `voice bin readCount reads the header only`() {
        val file = File(dir, "voice.bin")
        SupertonicTestFixtures.writeVoiceBin(file, n = 7)
        VoiceBinReader.readCount(file) shouldBe 7

        val corrupt = File(dir, "corrupt.bin")
        corrupt.writeBytes(ByteArray(10))
        shouldThrow<IOException> { VoiceBinReader.readCount(corrupt) }
    }

    // ---- unicode_indexer.bin ----------------------------------------------

    @Test
    fun `indexer bin parses as raw little-endian int32`() {
        val file = File(dir, "unicode_indexer.bin")
        val values = intArrayOf(0, 1, 4, 9, -3, Int.MAX_VALUE, Int.MIN_VALUE, 65535)
        SupertonicTestFixtures.writeIndexerBin(file, values)
        IndexerBinReader.parse(file).toList() shouldBe values.toList()
    }

    @Test
    fun `indexer bin equals the equivalent json array`() {
        // V4: bin and the fp32 layout's unicode_indexer.json carry the
        // same codepoint→token-id array.
        val values = IntArray(256) { it * 3 - 100 }
        val bin = File(dir, "unicode_indexer.bin")
        SupertonicTestFixtures.writeIndexerBin(bin, values)

        val json = File(dir, "unicode_indexer.json")
        json.writeText(values.joinToString(",", "[", "]"))
        val fromJson = Json.parseToJsonElement(json.readText()).jsonArray
            .map { it.jsonPrimitive.int }
            .toIntArray()

        IndexerBinReader.parse(bin).toList() shouldBe fromJson.toList()
    }

    @Test
    fun `indexer bin non-multiple-of-4 length throws IOException`() {
        val file = File(dir, "unicode_indexer.bin")
        file.writeBytes(ByteArray(10))
        shouldThrow<IOException> { IndexerBinReader.parse(file) }
    }
}
