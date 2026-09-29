package eu.kanade.tachiyomi.tts.supertonic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.FloatBuffer

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

    /**
     * LiteRT arm — converted vector_estimator on the Mali GPU delegate.
     * TFLite is NHWC: ONNX [1,144,L] becomes [1,L,144]; masks [1,L,1];
     * scalars unchanged. Push the converted models first:
     *   adb push vector_estimator_float16.tflite \
     *     /sdcard/Android/data/xyz.jmir.miko.dev/files/gpu-bench/
     */
    @Test
    fun benchmarkVectorEstimatorLiteRtGpu() {
        // fp32 model — the GPU delegate converts to fp16 internally; the
        // raw fp16 export fails at CONCAT prepare (Float16 not a valid
        // kernel input type).
        val tfl = benchDir.resolve("vector_estimator_float32.tflite")
        if (!tfl.isFile) {
            report("litert-fp16-gpu: model missing at ${tfl.absolutePath}")
            return
        }
        val latLen = 320
        val txtLen = 128

        val gpuOpts = org.tensorflow.lite.InterpreterApi.Options().apply {
            addDelegate(org.tensorflow.lite.gpu.GpuDelegate())
        }
        val cpuOpts = org.tensorflow.lite.InterpreterApi.Options().apply {
            setNumThreads(2)
        }

        for ((name, opts) in listOf("litert-gpu" to gpuOpts, "litert-cpu" to cpuOpts)) {
            val interp = try {
                org.tensorflow.lite.InterpreterApi.create(tfl, opts)
            } catch (t: Throwable) {
                report("$name: create FAILED — ${t.message}")
                continue
            }
            try {
                val rng = java.util.Random(42)
                val noise = Array(1) { Array(latLen) { FloatArray(144) { rng.nextFloat() } } }
                val emb = Array(1) { Array(txtLen) { FloatArray(256) { rng.nextFloat() * 0.3f } } }
                val sty = Array(1) { Array(256) { FloatArray(50) { rng.nextFloat() * 0.3f } } }
                val lMask = Array(1) { Array(latLen) { floatArrayOf(1f) } }
                val tMask = Array(1) { Array(txtLen) { floatArrayOf(1f) } }
                val step = floatArrayOf(2f)
                val total = floatArrayOf(5f)
                val out = Array(1) { Array(144) { FloatArray(latLen) } }

                fun once() {
                    val inputs = mapOf(
                        "noisy_latent" to noise,
                        "text_emb" to emb,
                        "style_ttl" to sty,
                        "latent_mask" to lMask,
                        "text_mask" to tMask,
                        "current_step" to step,
                        "total_step" to total,
                    )
                    interp.runForMultipleInputsOutputs(
                        inputs.values.toTypedArray(),
                        mapOf(0 to out),
                    )
                }

                // NOTE: input ORDER must match the model's signature order —
                // runForMultipleInputsOutputs takes positional args.
                once()
                once() // warmup + delegate compile
                val times = LongArray(10)
                for (i in times.indices) {
                    val t0 = System.nanoTime()
                    once()
                    times[i] = System.nanoTime() - t0
                }
                report(
                    "$name: avg=${"%.0f".format(times.average() / 1e6)}ms " +
                        "min=${"%.0f".format(times.min() / 1e6)}ms " +
                        "(x5 = ${"%.0f".format(times.average() * 5 / 1e6)}ms/chunk)",
                )
            } catch (t: Throwable) {
                report("$name: run FAILED — ${t.message}")
            } finally {
                interp.close()
            }
        }
        report("litert bench complete")
    }

    /**
     * soniqo's first-party LiteRT export (HF: soniqo/Supertonic-3-LiteRT) —
     * re-authored via litert_torch, not onnx2tf, so its graph may delegate
     * where our converted one refused. Fixed signature: latent [1,144,64],
     * text [1,256,128]; positional inputs in interpreter order.
     */
    @Test
    fun benchmarkSoniqoLiteRtGpu() {
        val tfl = benchDir.resolve("soniqo_vector_estimator.tflite")
        if (!tfl.isFile) {
            report("soniqo: model missing at ${tfl.absolutePath}")
            return
        }
        val gpuOpts = org.tensorflow.lite.InterpreterApi.Options().apply {
            addDelegate(org.tensorflow.lite.gpu.GpuDelegate())
        }
        val cpuOpts = org.tensorflow.lite.InterpreterApi.Options().apply {
            setNumThreads(2)
        }
        for ((name, opts) in listOf("soniqo-gpu" to gpuOpts, "soniqo-cpu" to cpuOpts)) {
            val interp = try {
                org.tensorflow.lite.InterpreterApi.create(tfl, opts)
            } catch (t: Throwable) {
                report("$name: create FAILED — ${t.message?.take(300)}")
                continue
            }
            try {
                // Positional inputs matching interpreter detail order —
                // shapes fixed by the export: [1],[1,50,256],[1,1,64],
                // [1,144,64],[1],[1,1,128],[1,256,128].
                val shapes = interp.inputTensorCount.let { n ->
                    (0 until n).map { interp.getInputTensor(it).shape() }
                }
                report("$name inputs: ${shapes.map { it.toList() }}")
                val inputs = shapes.map { s ->
                    Array(s[0]) { FloatArray(s.drop(1).fold(1, Int::times)) { 0.5f } }
                        .let { if (s.size == 1) it[0] else it }
                        .let { arr ->
                            when (s.size) {
                                1 -> floatArrayOf(0.5f)
                                3 -> Array(s[0]) { Array(s[1]) { FloatArray(s[2]) { 0.5f } } }
                                4 -> Array(s[0]) { Array(s[1]) { Array(s[2]) { FloatArray(s[3]) { 0.5f } } } }
                                else -> arr
                            }
                        }
                }.toTypedArray()
                val out = Array(1) { Array(144) { FloatArray(64) } }

                fun once() = interp.runForMultipleInputsOutputs(inputs, mapOf(0 to out))
                once()
                once()
                val times = LongArray(10)
                for (i in times.indices) {
                    val t0 = System.nanoTime()
                    once()
                    times[i] = System.nanoTime() - t0
                }
                report(
                    "$name: avg=${"%.0f".format(times.average() / 1e6)}ms " +
                        "min=${"%.0f".format(times.min() / 1e6)}ms " +
                        "(x5 = ${"%.0f".format(times.average() * 5 / 1e6)}ms/chunk)",
                )
            } catch (t: Throwable) {
                report("$name: run FAILED — ${t.message?.take(300)}")
            } finally {
                interp.close()
            }
        }
        report("soniqo bench complete")
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
                env,
                FloatBuffer.wrap(latent),
                longArrayOf(1, 144, latLen.toLong()),
            ),
            "text_emb" to OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(textEmb),
                longArrayOf(1, 256, 64),
            ),
            "style_ttl" to OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(style),
                longArrayOf(1, 50, 256),
            ),
            "latent_mask" to OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(latMask),
                longArrayOf(1, 1, latLen.toLong()),
            ),
            "text_mask" to OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(txtMask),
                longArrayOf(1, 1, 64),
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
