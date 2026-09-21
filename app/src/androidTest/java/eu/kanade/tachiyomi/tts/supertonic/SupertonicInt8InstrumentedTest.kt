package eu.kanade.tachiyomi.tts.supertonic

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.supertonic.SupertonicEngine
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase-05B on-device proof (validation rows V1/V2): the production
 * [SupertonicEngine] loads the sherpa int8 bundle inside Miko's own process
 * under raw onnxruntime-android, every graph exposes the input names the
 * pipeline feeds, and end-to-end synthesis produces 44.1kHz PCM.
 *
 * Bundle staging (adb):
 *   adb push <extracted sherpa-onnx-supertonic-3-tts-int8-*>/. \
 *       /sdcard/Android/data/xyz.jmir.miko.dev/files/engines/supertonic-3-int8/
 *
 * The test copies the staging dir into `filesDir/engines/supertonic-3-int8/`
 * (the path the production catalog resolves) in [stage] and removes it in
 * [unstage].
 *
 * Instrumentation args (for the 05B-05 V6 A/B):
 *   -e steps <n>     denoise steps (default 5 — sherpa's proven default)
 *   -e threads <n>   ORT intra-op thread override (default: autodetect)
 *
 *   adb shell am instrument -w \
 *     xyz.jmir.miko.dev.test/androidx.test.runner.AndroidJUnitRunner \
 *     -e class eu.kanade.tachiyomi.tts.supertonic.SupertonicInt8InstrumentedTest \
 *     [-e steps 5] [-e threads 2]
 *
 * WAVs + `rtf-int8.json` land in the staging dir for `adb pull`.
 */
@RunWith(AndroidJUnit4::class)
class SupertonicInt8InstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val stagingDir get() =
        File(context.getExternalFilesDir(null), "engines/supertonic-3-int8")
    private val engineDir get() =
        File(context.filesDir, "engines/supertonic-3-int8")

    private val steps: Int by lazy {
        InstrumentationRegistry.getArguments().getString("steps")?.toIntOrNull() ?: 5
    }
    private val threads: Int? by lazy {
        InstrumentationRegistry.getArguments().getString("threads")?.toIntOrNull()
    }

    @Before
    fun stage() {
        require(stagingDir.isDirectory) {
            "Staging dir missing: ${stagingDir.absolutePath} — " +
                "adb push the sherpa-onnx-supertonic-3-tts-int8 bundle contents there first"
        }
        engineDir.deleteRecursively()
        stagingDir.copyRecursively(engineDir, overwrite = true)
    }

    @After
    fun unstage() {
        engineDir.deleteRecursively()
    }

    private fun newEngine() = SupertonicEngine(
        context,
        Injekt.get<NovelTtsPreferences>(),
        threadOverride = threads,
    )

    /** V1 — every int8 graph accepts the spike's fed input names. */
    @Test
    fun int8SessionsExposeExpectedInputNames() {
        runBlocking {
        val loadStart = System.currentTimeMillis()
        val engine = newEngine()
        engine.ensureLoaded()
        val loadMs = System.currentTimeMillis() - loadStart
        Log.i(TAG, "engine load: ${loadMs}ms, sampleRate=${engine.sampleRate}")

        val names = engine.sessionInputNames()
        assertInputs("duration_predictor", names["duration_predictor"], DP_INPUTS)
        assertInputs("text_encoder", names["text_encoder"], TEXT_ENC_INPUTS)
        assertInputs("vector_estimator", names["vector_estimator"], VECTOR_EST_INPUTS)
        assertInputs("vocoder", names["vocoder"], VOCODER_INPUTS)

        engine.release()
        }
    }

    /** V2 — end-to-end synthesis on int8 weights, RTF measured at the loaded rate. */
    @Test
    fun synthesizeEndToEnd() {
        runBlocking {
        val engine = newEngine()
        val loadStart = System.currentTimeMillis()
        engine.ensureLoaded()
        val loadMs = System.currentTimeMillis() - loadStart
        Log.i(
            TAG,
            "engine load: ${loadMs}ms, sampleRate=${engine.sampleRate}, " +
                "steps=$steps, threads=${threads ?: "auto"}",
        )

        val results = mutableListOf<JsonObject>()
        paragraphs.forEachIndexed { idx, text ->
            val t0 = System.currentTimeMillis()
            val chunks = engine.synthesizeStream(text, "supertonic:F5", steps, 1.0f).toList()
            val elapsedMs = System.currentTimeMillis() - t0

            val total = chunks.sumOf { it.pcm.size }
            val pcm = ShortArray(total)
            var pos = 0
            for (c in chunks) {
                c.pcm.copyInto(pcm, pos)
                pos += c.pcm.size
            }
            assertTrue("paragraph $idx produced no audio", pcm.isNotEmpty())

            val audioSec = pcm.size.toFloat() / engine.sampleRate
            val rtf = elapsedMs / 1000f / audioSec

            val wavFile = File(stagingDir, "out_int8_F5_p$idx.wav")
            writeWav(wavFile, pcm, engine.sampleRate)
            wavFile.setReadable(true, false)

            Log.i(
                TAG,
                "int8 F5 p$idx: ${chunks.size} chunk(s), ${elapsedMs}ms synth, " +
                    "${"%.2f".format(audioSec)}s audio, RTF=${"%.3f".format(rtf)}",
            )
            results.add(
                buildJsonObject {
                    put("voice", JsonPrimitive("F5"))
                    put("paragraph", JsonPrimitive(idx))
                    put("chunks", JsonPrimitive(chunks.size))
                    put("steps", JsonPrimitive(steps))
                    put("threads", JsonPrimitive(threads ?: 0))
                    put("load_ms", JsonPrimitive(loadMs))
                    put("elapsed_ms", JsonPrimitive(elapsedMs))
                    put("audio_sec", JsonPrimitive(audioSec.toDouble()))
                    put("rtf", JsonPrimitive(rtf.toDouble()))
                },
            )
            // Smoke bound — the precise number is logged for the 05B-05 V6
            // A/B, not gated here beyond faster-than-realtime.
            assertTrue("int8 RTF $rtf >= 1.0 on paragraph $idx", rtf < 1.0f)
        }

        File(stagingDir, "rtf-int8.json").let {
            it.writeText(JsonArray(results).toString())
            it.setReadable(true, false)
        }
        engine.release()
        Log.i(TAG, "done — results in ${stagingDir.absolutePath}")
        }
    }

    private fun assertInputs(graph: String, actual: Set<String>?, required: Set<String>) {
        assertTrue(
            "$graph session missing (not loaded?)",
            actual != null && actual.isNotEmpty(),
        )
        val missing = required - actual.orEmpty()
        assertTrue(
            "$graph inputNames missing $missing (has ${actual.orEmpty().sorted()})",
            missing.isEmpty(),
        )
    }

    private fun writeWav(file: File, pcm: ShortArray, sampleRate: Int) {
        val dataSize = pcm.size * 2
        RandomAccessFile(file, "rw").use { f ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // PCM chunk size
            header.putShort(1) // PCM format
            header.putShort(1) // mono
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2) // byte rate
            header.putShort(2) // block align
            header.putShort(16) // bits per sample
            header.put("data".toByteArray())
            header.putInt(dataSize)
            f.write(header.array())
            val data = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) data.putShort(s)
            f.write(data.array())
        }
    }

    companion object {
        private const val TAG = "SupertonicInt8"

        private val DP_INPUTS = setOf("text_ids", "style_dp", "text_mask")
        private val TEXT_ENC_INPUTS = setOf("text_ids", "style_ttl", "text_mask")
        private val VECTOR_EST_INPUTS = setOf(
            "noisy_latent", "text_emb", "style_ttl", "latent_mask",
            "text_mask", "current_step", "total_step",
        )
        private val VOCODER_INPUTS = setOf("latent")

        private val paragraphs = listOf(
            "The sunless human was a master of many things but more than anything else he was a master " +
                "of war. He had fought in a hundred battles and walked out of each one alive earning a " +
                "terrifying reputation in the process.",
            "Rain fell on the city like a curse. Neon signs bled into the puddles, painting the streets " +
                "in colors that had no name. Somewhere in the maze of alleys, a shadow moved that did " +
                "not belong to anyone.",
            "He drew his sword slowly, letting the rasp of steel speak for him. The corridor fell " +
                "silent. Every soldier in the room understood what was about to happen, and none of " +
                "them moved to stop it.",
        )
    }
}
