package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.io.path.createTempFile
import org.junit.jupiter.api.Test

/**
 * JVM unit tests for the MP4 `uuid`-atom codec ([Mp4AtomAppender] /
 * [Mp4AtomReader]) and the [ChapterAudioManifest] JSON codec (plan 05-01,
 * Task 1).
 *
 * The manifest atom is a spec-sanctioned `uuid` box appended at EOF after
 * `MediaMuxer.stop()` (which writes `moov` last — RESEARCH §2). Read-back is
 * a forward top-level atom walk; every malformed/truncated/oversized shape
 * must degrade to `null` (file still plays as plain audio).
 */
class Mp4AtomTest {

    private fun atom(fourcc: String, payload: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(8 + payload.size)
        buf.putInt(8 + payload.size)
        buf.put(fourcc.toByteArray(Charsets.US_ASCII))
        buf.put(payload)
        return buf.array()
    }

    private fun uuidAtom(payload: ByteArray, uuid: ByteArray = MIKO_MANIFEST_UUID): ByteArray {
        val buf = ByteBuffer.allocate(8 + 16 + payload.size)
        buf.putInt(8 + 16 + payload.size)
        buf.put("uuid".toByteArray(Charsets.US_ASCII))
        buf.put(uuid)
        buf.put(payload)
        return buf.array()
    }

    /** ftyp + mdat + moov — the moov-last layout MediaMuxer produces. */
    private fun fakeMp4(): ByteArray =
        atom("ftyp", "M4A ".toByteArray()) +
            atom("mdat", ByteArray(32) { 0x11 }) +
            atom("moov", ByteArray(16) { 0x22 })

    private fun tempMp4(bytes: ByteArray): File =
        createTempFile(suffix = ".m4a").toFile().apply { writeBytes(bytes) }

    private fun readManifest(file: File): String? =
        FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
            Mp4AtomReader.read(channel)
        }

    // -- append + read-back ---------------------------------------------------

    @Test
    fun `append writes uuid atom at EOF and read-back round-trips the payload`() {
        val original = fakeMp4()
        val file = tempMp4(original)
        val json = """{"version":1,"voice":"eponine","engine":"pocket-tts-en-v2026_04"}"""
        val payload = json.toByteArray(Charsets.UTF_8)

        Mp4AtomAppender.append(file, payload)

        // Atom header lands exactly at the original EOF (after moov).
        val bytes = file.readBytes()
        bytes.size shouldBe original.size + 8 + 16 + payload.size
        val headerPos = original.size
        ByteBuffer.wrap(bytes, headerPos, 8).apply { }.let { header ->
            header.int shouldBe 8 + 16 + payload.size
            String(ByteArray(4) { 0 }.also { header.get(it) }, Charsets.US_ASCII) shouldBe "uuid"
        }
        // The 16-byte Miko UUID follows the header.
        bytes.copyOfRange(headerPos + 8, headerPos + 24) shouldBe MIKO_MANIFEST_UUID

        readManifest(file) shouldBe json
    }

    @Test
    fun `read-back skips unknown top-level atoms and finds uuid anywhere`() {
        val json = """{"v":1}"""
        val file = tempMp4(
            atom("free", ByteArray(7)) + atom("mdat", ByteArray(64)) +
                uuidAtom(json.toByteArray(Charsets.UTF_8)),
        )
        readManifest(file) shouldBe json
    }

    // -- corruption tolerance ---------------------------------------------------

    @Test
    fun `read returns null when no uuid atom is present`() {
        readManifest(tempMp4(fakeMp4())).shouldBeNull()
    }

    @Test
    fun `read returns null for a tail-truncated payload`() {
        val full = fakeMp4() + uuidAtom("x".repeat(128).toByteArray(Charsets.UTF_8))
        readManifest(tempMp4(full.copyOf(full.size - 10))).shouldBeNull()
    }

    @Test
    fun `read returns null for a truncated header`() {
        val file = tempMp4(byteArrayOf(0x00, 0x00, 0x00))
        readManifest(file).shouldBeNull()
    }

    @Test
    fun `read returns null when the uuid bytes do not match`() {
        val wrongUuid = ByteArray(16) { (it + 1).toByte() }
        val file = tempMp4(fakeMp4() + uuidAtom("{}".toByteArray(), uuid = wrongUuid))
        readManifest(file).shouldBeNull()
    }

    @Test
    fun `read returns null when declared payload size exceeds the cap`() {
        // Hand-craft a uuid atom claiming a >4MiB payload (don't actually write it).
        val header = ByteBuffer.allocate(8 + 16)
        header.putInt((MAX_MANIFEST_BYTES + 8 + 16 + 1).toInt())
        header.put("uuid".toByteArray(Charsets.US_ASCII))
        header.put(MIKO_MANIFEST_UUID)
        val file = tempMp4(fakeMp4() + header.array() + ByteArray(64))
        readManifest(file).shouldBeNull()
    }

    @Test
    fun `read returns null for a malformed undersized atom`() {
        // Declared size smaller than the 8-byte header — malformed.
        val file = tempMp4(byteArrayOf(0, 0, 0, 4) + "abcd".toByteArray())
        readManifest(file).shouldBeNull()
    }

    // -- manifest codec ---------------------------------------------------------

    private fun sampleManifest() = ChapterAudioManifest(
        voice = "eponine",
        engine = "pocket-tts-en-v2026_04",
        chapterTitle = "Chapter 12 — The Cellar",
        speechRate = 1.5f,
        pitch = 0.9f,
        durationMs = 123_456L,
        paragraphOffsetsMs = listOf(0L, 8_420L, 16_900L),
        paragraphTexts = listOf("Alpha.", "Beta.", "Gamma."),
        text = "Alpha.\n\nBeta.\n\nGamma.",
    )

    @Test
    fun `manifest encode-decode round-trips every field`() {
        val manifest = sampleManifest()
        decodeManifest(manifest.encode()) shouldBe manifest
    }

    @Test
    fun `manifest decode tolerates unknown keys (forward-compat)`() {
        val manifest = sampleManifest()
        val withFutureField = manifest.encode().replaceFirst("{", """{"phaseSixField":42,""")
        decodeManifest(withFutureField) shouldBe manifest
    }

    @Test
    fun `manifest decode returns null on bad json and on missing required fields`() {
        decodeManifest("not json at all").shouldBeNull()
        decodeManifest("{}").shouldBeNull()
        decodeManifest("""{"voice":"eponine"}""").shouldBeNull()
    }

    @Test
    fun `manifest survives the atom round-trip end to end`() {
        val manifest = sampleManifest()
        val file = tempMp4(fakeMp4())
        Mp4AtomAppender.append(file, manifest.encode().toByteArray(Charsets.UTF_8))
        readManifest(file).shouldNotBeNull().let { json ->
            decodeManifest(json) shouldBe manifest
        }
    }
}
