package eu.kanade.tachiyomi.ui.reader.novel.tts.pocket

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JVM coverage for [PocketAudio] (WAV parse / resample / clipping
 * normalization) and [NpyReader] (bos_before_voice.npy v1 format).
 * The WAV builder mirrors TtsPlaybackRegressionTest's synthetic RIFF.
 */
class PocketAudioNpyTest {

    @TempDir
    lateinit var dir: File

    private fun wav(
        samples: ShortArray,
        channels: Int = 1,
        sampleRate: Int = 48000,
        bits: Int = 16,
        extraChunk: Boolean = false,
    ): ByteArray {
        val dataBytes = samples.size * bits / 8
        val size = 12 + 8 + 16 + (if (extraChunk) 8 + 2 else 0) + 8 + dataBytes
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(size - 8)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * bits / 8)
            putShort((channels * bits / 8).toShort())
            putShort(bits.toShort())
            if (extraChunk) {
                put("JUNK".toByteArray())
                putInt(1) // odd-sized chunk exercises the pad byte
                put(0x55)
                put(0x00) // pad to even
            }
            put("data".toByteArray())
            putInt(dataBytes)
            for (s in samples) {
                if (bits == 16) putShort(s) else put(s.toByte())
            }
        }.array()
    }

    private fun writeWav(bytes: ByteArray): File =
        File(dir, "sample.wav").apply { writeBytes(bytes) }

    @Test
    fun `readWav parses mono 16-bit PCM into normalized floats`() {
        val pcm = shortArrayOf(0, 16384, -16384, 32767)
        val wav = PocketAudio.readWav(writeWav(wav(pcm)))
        wav.sampleRate shouldBe 48000
        wav.samples.size shouldBe 4
        wav.samples[0] shouldBe 0f
        wav.samples[1] shouldBe (16384f / 32768f)
        wav.samples[2] shouldBe (-16384f / 32768f)
        wav.samples[3] shouldBe (32767f / 32768f)
    }

    @Test
    fun `readWav mixes stereo to mono by channel average`() {
        // L = 32767, R = -32767 → average 0; L = 16384, R = 16384 → 16384.
        val pcm = shortArrayOf(32767, -32767, 16384, 16384)
        val wav = PocketAudio.readWav(writeWav(wav(pcm, channels = 2)))
        wav.samples.size shouldBe 2
        wav.samples[0] shouldBe (0f plusOrMinus 1e-6f)
        wav.samples[1] shouldBe (16384f / 32768f plusOrMinus 1e-6f)
    }

    @Test
    fun `readWav skips unrelated chunks before data`() {
        val pcm = shortArrayOf(1000)
        val wav = PocketAudio.readWav(writeWav(wav(pcm, extraChunk = true)))
        wav.samples.toList() shouldBe listOf(1000f / 32768f)
    }

    @Test
    fun `readWav rejects non-RIFF input`() {
        shouldThrow<IllegalArgumentException> {
            PocketAudio.readWav(writeWav(ByteArray(64) { 0x7F }))
        }
    }

    @Test
    fun `readWav rejects non-16-bit samples`() {
        shouldThrow<IllegalArgumentException> {
            PocketAudio.readWav(writeWav(wav(shortArrayOf(0), bits = 8)))
        }
    }

    @Test
    fun `resample returns input unchanged when rates match`() {
        val pcm = floatArrayOf(1f, 2f, 3f)
        PocketAudio.resample(pcm, 24000, 24000) shouldBe pcm
    }

    @Test
    fun `resample 2-to-1 uses the averaging decimator`() {
        val out = PocketAudio.resample(floatArrayOf(1f, 0f, -1f, 0f, 0.5f, 0.5f), 48000, 24000)
        out.toList() shouldBe listOf(0.5f, -0.5f, 0.5f)
    }

    @Test
    fun `resample arbitrary ratio falls back to linear interpolation`() {
        // ratio = 1.5 → out[1] = pcm[1]*(1-0.5) + pcm[2]*0.5 = 0.5
        val out = PocketAudio.resample(floatArrayOf(0f, 1f, 0f), 48000, 32000)
        out.size shouldBe 2
        out[0] shouldBe 0f
        out[1] shouldBe (0.5f plusOrMinus 1e-6f)
    }

    @Test
    fun `normalizeIfClipping is a no-op at or below peak 1`() {
        val pcm = floatArrayOf(0.5f, -0.99f, 1f)
        val out = PocketAudio.normalizeIfClipping(pcm)
        out.toList() shouldBe listOf(0.5f, -0.99f, 1f)
    }

    @Test
    fun `normalizeIfClipping divides by peak when any sample exceeds 1`() {
        val out = PocketAudio.normalizeIfClipping(floatArrayOf(2f, -4f, 1f))
        out[0] shouldBe (0.5f plusOrMinus 1e-6f)
        out[1] shouldBe (-1f plusOrMinus 1e-6f)
        out[2] shouldBe (0.25f plusOrMinus 1e-6f)
    }

    // -- NpyReader ------------------------------------------------------------

    /**
     * Hand-built .npy v1.0: magic + version + u16 headerLen + padded
     * python-dict header + little-endian float32 payload.
     */
    private fun npy(shape: IntArray, floats: FloatArray): ByteArray {
        val shapeStr = if (shape.size == 1) "(${shape[0]},)" else shape.joinToString(", ", "(", ")")
        val headerDict = "{'descr': '<f4', 'fortran_order': False, 'shape': $shapeStr, }"
        // Pad so (10-byte preamble + header) is a multiple of 64, ending in \n.
        var headerLen = headerDict.length + 1
        while ((10 + headerLen) % 64 != 0) headerLen++
        val header = headerDict.padEnd(headerLen - 1, ' ') + "\n"

        val out = ByteBuffer.allocate(10 + headerLen + floats.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        out.put(0x93.toByte())
        out.put("NUMPY".toByteArray())
        out.put(1) // major
        out.put(0) // minor
        out.putShort(headerLen.toShort())
        out.put(header.toByteArray(Charsets.US_ASCII))
        for (f in floats) out.putFloat(f)
        return out.array()
    }

    @Test
    fun `npy reader parses a v1 float32 C-order file`() {
        val file = File(dir, "bos.npy").apply {
            writeBytes(npy(intArrayOf(1, 2), floatArrayOf(1.5f, -2.25f)))
        }
        val npy = NpyReader.readFloat32(file)
        npy.shape.toList() shouldBe listOf(1, 2)
        npy.data.toList() shouldBe listOf(1.5f, -2.25f)
    }

    @Test
    fun `npy reader rejects a non-float32 dtype`() {
        val file = File(dir, "bad.npy").apply {
            val bytes = npy(intArrayOf(1), floatArrayOf(0f))
            // Rewrite the descr inside the header.
            val text = String(bytes, Charsets.US_ASCII).replace("<f4", "<i8")
            writeBytes(text.toByteArray(Charsets.US_ASCII))
        }
        shouldThrow<IllegalArgumentException> { NpyReader.readFloat32(file) }
    }

    @Test
    fun `npy reader rejects missing magic`() {
        val file = File(dir, "bad.npy").apply { writeBytes(ByteArray(64) { 0x20 }) }
        shouldThrow<IllegalArgumentException> { NpyReader.readFloat32(file) }
    }

    @Test
    fun `npy reader rejects a truncated payload`() {
        val file = File(dir, "short.npy").apply {
            writeBytes(npy(intArrayOf(4), floatArrayOf(1f, 2f)).dropLast(4).toByteArray())
        }
        shouldThrow<IllegalArgumentException> { NpyReader.readFloat32(file) }
    }
}
