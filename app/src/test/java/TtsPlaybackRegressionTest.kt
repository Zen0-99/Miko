import android.content.Context
import eu.kanade.tachiyomi.ui.reader.novel.tts.AndroidTtsEngine
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TtsPlaybackRegressionTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `reads little endian mono PCM WAV`() {
        readWav(wav(channels = 1)) shouldBe listOf(44, 16, 24000, 1, 16)
    }

    @Test
    fun `reads stereo PCM and skips odd sized chunks`() {
        readWav(wav(channels = 2, extraChunk = true)) shouldBe listOf(54, 16, 24000, 2, 16)
    }

    @Test
    fun `rejects unsupported sample encoding`() {
        readWav(wav(bits = 8)) shouldBe null
    }

    @Test
    fun `rejects truncated data`() {
        readWav(wav().dropLast(2).toByteArray()) shouldBe null
    }

    private fun readWav(bytes: ByteArray): List<Int>? {
        val file = File(directory, "sample.wav").apply { writeBytes(bytes) }
        val engine = AndroidTtsEngine(mockk<Context>())
        val method = AndroidTtsEngine::class.java.getDeclaredMethod("readWav", File::class.java)
        method.isAccessible = true
        val result = method.invoke(engine, file) ?: return null
        return listOf("dataOffset", "dataSize", "sampleRate", "channels", "bitsPerSample").map {
            result.javaClass.getDeclaredField(it).apply { isAccessible = true }.getInt(result)
        }
    }

    private fun wav(channels: Int = 1, bits: Int = 16, extraChunk: Boolean = false): ByteArray {
        val size = 60 + if (extraChunk) 10 else 0
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(size - 8)
            put("WAVEfmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(24000)
            putInt(24000 * channels * bits / 8)
            putShort((channels * bits / 8).toShort())
            putShort(bits.toShort())
            if (extraChunk) {
                put("JUNK".toByteArray())
                putInt(1)
                putShort(0)
            }
            put("data".toByteArray())
            putInt(16)
            put(ByteArray(16))
        }.array()
    }
}
