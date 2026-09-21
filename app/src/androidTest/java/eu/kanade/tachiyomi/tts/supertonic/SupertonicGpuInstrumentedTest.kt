package eu.kanade.tachiyomi.tts.supertonic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.FloatBuffer
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spike: GPU/NPU feasibility on-device (the "2-minute chapter" question).
 *
 * Times the vector_estimator — ~85% of render wall-clock — under each
 * execution provider ONNX Runtime Android can reach:
 *   - CPU EP (production baseline: int8, 2 threads)
 *   - XNNPACK EP (CPU SIMD — free arm, already supported by the engine)
 *   - NNAPI EP (int8 + fp32) — the only path that can land on the
 *     Mali-G715 GPU or Edge TPU. QDQ-int8 support is partial; fp32 with
 *     fp16 enabled is the realistic GPU arm.
 *
 * The int8 model is read from the production engine dir; the fp32 model
 * must be pushed manually:
 *   adb push vector_estimator.onnx \
 *     /sdcard/Android/data/xyz.jmir.miko.dev/files/gpu-bench/
 *
 * Run:
 *   adb shell am instrument -w \
 *     xyz.jmir.miko.dev.test/androidx.test.runner.AndroidJUnitRunner \
 *     -e class eu.kanade.tachiyomi.tts.supertonic.SupertonicGpuInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class SupertonicGpuInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val int8Dir get() = File(context.filesDir, "engines/supertonic-3-int8")
    private val benchDir get() =
        File(context.getExternalFilesDir(null), "gpu-bench")

    @Test
    fun benchmarkVectorEstimatorAcrossProviders() {
        val env = OrtEnvironment.getEnvironment()
        val latLen = 287 // ~20s chunk — the production chunk size

        val latent = FloatArray(144 * latLen) { (it % 97) * 0.01f - 0.5f }
        val textEmb = FloatArray(256 * 64) { (it % 89) * 0.01f - 0.4f }
        val style = FloatArray(50 * 256) { (it % 83) * 0.01f - 0.4f }
        val latMask = FloatArray(latLen) { 1f }
        val txtMask = FloatArray(64) { 1f }

        // int8 VE also lives in gpu-bench — the production dir can be
        // wiped by sibling tests' unstage() during a full-suite run.
        val int8 = benchDir.resolve("vector_estimator.int8.onnx")
        val arms = mutableListOf<Pair<String, () -> OrtSession?>>()
        arms += "cpu-int8-t2" to {
            mkSession(int8) { it.setIntraOpNumThreads(2) }
        }
        arms += "xnnpack-int8" to {
            mkSession(int8) {
                it.setIntraOpNumThreads(2)
                it.addXnnpack(mapOf("intra_op_num_threads" to "2"))
            }
        }
        arms += "nnapi-int8" to {
            mkSession(int8) { it.addNnapi() }
        }
        val fp32 = benchDir.resolve("vector_estimator.onnx")
        if (fp32.isFile) {
            arms += "nnapi-fp32" to { mkSession(fp32) { it.addNnapi() } }
            arms += "cpu-fp32-t2" to {
                mkSession(fp32) { it.setIntraOpNumThreads(2) }
            }
        } else {
            Log.w(TAG, "fp32 model missing at ${fp32.absolutePath} — skipping fp32 arms")
        }

        for ((name, factory) in arms) {
            val session = try {
                factory()
            } catch (t: Throwable) {
                report("$name: session create FAILED — ${t.message}")
                null
            } ?: continue

            try {
                bench(env, session, name, latent, textEmb, style, latMask, txtMask, latLen)
            } catch (t: Throwable) {
                report("$name: run FAILED — ${t.message}")
            } finally {
                session.close()
            }
        }
        Log.i(TAG, "bench complete")
    }

    /** Logcat rotates under device noise — mirror every line to a file too. */
    private fun report(line: String) {
        Log.i(TAG, line)
        benchDir.mkdirs()
        benchDir.resolve("results.txt").appendText("$line\n")
        benchDir.resolve("results.txt").setReadable(true, false)
    }

    private fun mkSession(model: File, opts: (OrtSession.SessionOptions) -> Unit): OrtSession? {
        if (!model.isFile) {
            report("${model.name}: missing at ${model.absolutePath}")
            return null
        }
        val so = OrtSession.SessionOptions()
        opts(so)
        val t0 = System.currentTimeMillis()
        val s = OrtEnvironment.getEnvironment().createSession(model.absolutePath, so)
        report("${model.name} session: ${System.currentTimeMillis() - t0}ms")
        return s
    }

    private fun bench(
        env: OrtEnvironment,
        session: OrtSession,
        name: String,
        latent: FloatArray,
        textEmb: FloatArray,
        style: FloatArray,
        latMask: FloatArray,
        txtMask: FloatArray,
        latLen: Int,
    ) {
        fun inputs(step: Float) = mapOf(
            "noisy_latent" to OnnxTensor.createTensor(
                env, FloatBuffer.wrap(latent), longArrayOf(1, 144, latLen.toLong()),
            ),
            "text_emb" to OnnxTensor.createTensor(
                env, FloatBuffer.wrap(textEmb), longArrayOf(1, 256, 64),
            ),
            "style_ttl" to OnnxTensor.createTensor(
                env, FloatBuffer.wrap(style), longArrayOf(1, 50, 256),
            ),
            "latent_mask" to OnnxTensor.createTensor(
                env, FloatBuffer.wrap(latMask), longArrayOf(1, 1, latLen.toLong()),
            ),
            "text_mask" to OnnxTensor.createTensor(
                env, FloatBuffer.wrap(txtMask), longArrayOf(1, 1, 64),
            ),
            "current_step" to OnnxTensor.createTensor(env, floatArrayOf(step)),
            "total_step" to OnnxTensor.createTensor(env, floatArrayOf(5f)),
        )

        fun once(step: Float) {
            val ins = inputs(step)
            try {
                session.run(ins).close()
            } finally {
                ins.values.forEach { it.close() }
            }
        }

        repeat(2) { once(it.toFloat()) } // warmup
        val times = LongArray(10)
        for (i in times.indices) {
            val t0 = System.nanoTime()
            once((i % 5).toFloat())
            times[i] = System.nanoTime() - t0
        }
        val avg = times.average() / 1e6
        val min = times.min() / 1e6
        report(
            "$name: avg=${"%.0f".format(avg)}ms min=${"%.0f".format(min)}ms " +
                "(x5 steps = ${"%.0f".format(avg * 5)}ms/chunk)",
        )
    }

    companion object {
        private const val TAG = "GpuBench"
    }
}
