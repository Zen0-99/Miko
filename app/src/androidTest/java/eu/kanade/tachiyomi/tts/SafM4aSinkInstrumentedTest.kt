package eu.kanade.tachiyomi.tts

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.ui.reader.novel.tts.render.ChapterAudioManifest
import eu.kanade.tachiyomi.ui.reader.novel.tts.render.Mp4AtomReader
import eu.kanade.tachiyomi.ui.reader.novel.tts.render.SafM4aSink
import eu.kanade.tachiyomi.ui.reader.novel.tts.render.decodeManifest
import eu.kanade.tachiyomi.ui.reader.novel.tts.render.encode
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase-05B on-device proof (validation row V7): [SafM4aSink] parameterized
 * at 44100 Hz produces a `MediaExtractor`-decodable AAC-LC `.m4a` reporting
 * sample-rate 44100 — the Supertonic render rate (D-07). A 24000 Hz control
 * proves the constructor parameter drives the codec format, not a constant.
 * Both runs also verify the manifest `uuid` atom round-trips through
 * [Mp4AtomReader] at each rate.
 *
 *   adb shell am instrument -w \
 *     -e class eu.kanade.tachiyomi.tts.SafM4aSinkInstrumentedTest \
 *     xyz.jmir.miko.dev.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class SafM4aSinkInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dirs = mutableListOf<File>()

    @After
    fun cleanup() {
        dirs.forEach { it.deleteRecursively() }
        dirs.clear()
    }

    /** V7 — Supertonic rate: 1 s of PCM at 44100 → decodable AAC-LC @ 44100. */
    @Test
    fun encodeDecode44100() = encodeDecodeRoundTrip(44100)

    /** Control — Pocket rate: same flow at 24000 must report 24000. */
    @Test
    fun encodeDecode24000() = encodeDecodeRoundTrip(24000)

    private fun encodeDecodeRoundTrip(sampleRate: Int) {
        val dirFile = File(context.cacheDir, "sink-test-${System.nanoTime()}")
            .apply { mkdirs() }
            .also { dirs += it }
        val audioDir = UniFile.fromFile(dirFile)!!
        val finalName = "t-$sampleRate.m4a"

        val manifest = ChapterAudioManifest(
            voice = "test-voice",
            engine = "test-engine-$sampleRate",
            chapterTitle = "Sink Test",
            sampleRate = sampleRate,
            speechRate = 1.0f,
            pitch = 1.0f,
            durationMs = 1000L,
            paragraphOffsetsMs = listOf(0L),
            paragraphTexts = listOf("Sine."),
            text = "Sine.",
        )

        runBlocking {
            val sink = SafM4aSink(context, audioDir, finalName, sampleRate)
            sink.write(sinePcm(sampleRate, seconds = 1.0))
            sink.finish(manifest.encode())
        }

        val file = File(dirFile, finalName)
        assertTrue("rendered file missing: ${file.absolutePath}", file.isFile && file.length() > 0)

        assertExtractorAndDecode(file, sampleRate)
        assertManifestAtom(file, manifest)
    }

    /** Extractor asserts the audio track's rate/mime; decoder drains ≥ ~0.9 s of PCM. */
    private fun assertExtractorAndDecode(file: File, sampleRate: Int) {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIndex = i
                format = f
                break
            }
        }
        assertTrue("no audio/* track in ${file.name}", trackIndex >= 0)
        format!!
        assertEquals(sampleRate, format.getInteger(MediaFormat.KEY_SAMPLE_RATE))

        extractor.selectTrack(trackIndex)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        try {
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var decodedSamples = 0L
            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    decodedSamples += info.size / 2 // PCM16 mono output
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            assertTrue(
                "decoded only ${decodedSamples} samples at $sampleRate Hz",
                decodedSamples >= (sampleRate * 0.9).toLong(),
            )
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            extractor.release()
        }
    }

    /** The manifest `uuid` atom survives the encode and round-trips the sampleRate. */
    private fun assertManifestAtom(file: File, manifest: ChapterAudioManifest) {
        val json = FileChannel.open(file.toPath(), StandardOpenOption.READ).use {
            Mp4AtomReader.read(it)
        }
        assertNotNull("manifest atom missing from ${file.name}", json)
        assertEquals(manifest, decodeManifest(json!!))
        assertEquals(manifest.sampleRate, decodeManifest(json)!!.sampleRate)
    }

    private fun sinePcm(sampleRate: Int, seconds: Double): ShortArray {
        val len = (sampleRate * seconds).toInt()
        return ShortArray(len) { i ->
            (sin(2.0 * PI * 440.0 * i / sampleRate) * 12000).toInt().toShort()
        }
    }
}
