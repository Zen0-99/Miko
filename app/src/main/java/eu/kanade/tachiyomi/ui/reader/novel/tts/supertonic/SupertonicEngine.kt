package eu.kanade.tachiyomi.ui.reader.novel.tts.supertonic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.SupertonicEngineCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.SupertonicVoiceCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.EngineNotInstalledException
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.SynthAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Supertonic-3 TTS engine — Supertone's flow-matching TTS running directly
 * on `com.microsoft.onnxruntime:onnxruntime-android` (phase-05B D-02).
 *
 * Promoted from the spike-002 `androidTest` port of
 * `supertone-inc/supertonic`'s `java/Helper.java` — the same 4-session
 * pipeline (duration_predictor → text_encoder → vector_estimator ×steps →
 * vocoder) with the production adaptations:
 *
 *  - **Bundle shape (PB-1, D-03):** per-file `*.int8.onnx` first, `*.onnx`
 *    fallback — loads sherpa's int8 release AND the fp32 HF-style layout
 *    without a flag. `voice.bin` / `unicode_indexer.bin` are parsed by
 *    [VoiceBinReader] / [IndexerBinReader]; when absent the fp32 layout's
 *    `voice_styles/<name>.json` / `unicode_indexer.json` are used instead (PB-2).
 *  - **`tts.json` fields** (`ae.sample_rate`, `ae.base_chunk_size`,
 *    `ttl.chunk_compress_factor`, `ttl.latent_dim`) are read from the
 *    loaded bundle, never hardcoded — sherpa's int8 config differs from
 *    the fp32 spike's. JSON goes through kotlinx-serialization (org.json
 *    is absent from the JVM test classpath — the PocketBundle precedent).
 *  - **Perf (PB-7 / RESEARCH F6):** constant denoise inputs (`text_emb`,
 *    `style_ttl`, `latent_mask`, `text_mask`, `total_step`) are hoisted
 *    out of the step loop and `xt` stays a flat FloatArray + shape — the
 *    spike re-created four tensors and re-flattened nested arrays per step.
 *  - **Cancellation (PB-6 / R5):** `ensureActive` per emitted chunk and per
 *    denoise step, plus a [releaseRequested] flag checked per step so
 *    [release] can't hang behind an in-flight synth.
 *
 * Thread-safety mirrors PocketEngine: [ensureLoaded] is gated by [loadLock]
 * with `env`-last publish; every [synthesizeStream] holds the companion
 * [synthLock] for the whole utterance so read-along and renderer instances
 * serialize process-wide (PocketEngine.kt:2093 precedent).
 */
class SupertonicEngine(
    private val context: Context,
    private val preferences: NovelTtsPreferences,
    internal val threadOverride: Int? = null,
) {

    /** Voice style pair as ORT tensors (ttl = text-encoder/denoise cond, dp = duration-predictor cond). */
    class Style(val ttlTensor: OnnxTensor, val dpTensor: OnnxTensor)

    /**
     * [env] is [doLoad]'s publish-last marker — the same field
     * [ensureLoaded]'s unlocked fast path treats as "fully loaded"
     * (PocketEngine.kt:229-234 pattern).
     */
    val isLoaded: Boolean
        get() = env != null

    /**
     * Native output rate — the loaded `tts.json` `ae.sample_rate` while
     * loaded, else the catalog constant. `SupertonicTtsEngine` builds its
     * `AudioTrackPcmSink` AFTER [ensureLoaded] so this reflects the real
     * bundle config, not the nominal 44100.
     */
    val sampleRate: Int
        get() = loadedSampleRate ?: SupertonicEngineCatalog.SAMPLE_RATE

    private val engineDir: File get() = SupertonicEngineCatalog.engineDir(context.filesDir)

    private val loadLock = Mutex()

    /**
     * Raised by [release] so an in-flight synth (which holds [synthLock]
     * for the whole utterance) aborts at its next per-step check instead
     * of making release wait out the full denoise loop.
     */
    private val releaseRequested = AtomicBoolean(false)

    // -- live state (null while not loaded) ----------------------------------

    @Volatile private var env: OrtEnvironment? = null
    private var loadedSampleRate: Int? = null
    private var baseChunkSize: Int = 0
    private var chunkCompress: Int = 0
    private var latentDim: Int = 0
    private var textProcessor: UnicodeProcessor? = null
    private var voiceBinData: VoiceBinReader.VoiceBinData? = null

    private var dpSession: OrtSession? = null
    private var textEncSession: OrtSession? = null
    private var vectorEstSession: OrtSession? = null
    private var vocoderSession: OrtSession? = null

    /** Lazily-created style tensors, keyed by bare style name ("F5"). */
    private val styleCache = HashMap<String, Style>()

    /** Per-label record of which execution provider ended up active (load-time selection log). */
    private val xnnpackOutcome: MutableMap<String, String> = LinkedHashMap()

    fun isInstalled(): Boolean = SupertonicEngineCatalog.isInstalled(context.filesDir)

    suspend fun ensureLoaded() {
        if (env != null) return
        loadLock.withLock {
            if (env != null) return
            if (!isInstalled()) throw EngineNotInstalledException(ENGINE_NAME)
            logcat(LogPriority.INFO) { "Loading Supertonic TTS bundle from $engineDir …" }
            val t0 = System.currentTimeMillis()
            // Thread resolution (PB-5): an explicit override (instrumented-test
            // A/B knob) wins, then the shared ORT manual pref, else 2 — the
            // V6 device matrix showed 2 threads beating perf-core autodetect
            // by ~20% on Tensor G3 (little-core contention), matching sherpa's
            // own 2-thread default.
            val manualThreads: Int? = preferences.pocketIntraOpThreads().get().takeIf { it > 0 }
            val threadCount = threadOverride
                ?: manualThreads
                ?: DEFAULT_THREADS
            val xnnpackMode = preferences.pocketXnnpackMode().get().lowercase()
            try {
                doLoad(threadCount, xnnpackMode)
                val elapsed = System.currentTimeMillis() - t0
                logcat(LogPriority.INFO) { "Supertonic TTS loaded in $elapsed ms" }
            } catch (t: Throwable) {
                // Best-effort cleanup so a half-loaded engine doesn't
                // leak across retries.
                releaseInternal()
                throw IllegalStateException("Failed to load Supertonic TTS: ${t.message}", t)
            }
        }
    }

    private fun doLoad(intraOpThreads: Int, xnnpackMode: String) {
        // Build everything against the local [ort]; publish to [env] only
        // at the END (PocketEngine's publish-last pattern — an early
        // publish let a concurrent first synth see a half-loaded engine).
        val ort = OrtEnvironment.getEnvironment()

        // tts.json — loader metadata, read from the bundle (the sherpa
        // int8 config differs from the fp32 spike's: never hardcode).
        val ttsJson = Json.parseToJsonElement(File(engineDir, "tts.json").readText()).jsonObject
        val ae = ttsJson.getValue("ae").jsonObject
        val ttl = ttsJson.getValue("ttl").jsonObject
        loadedSampleRate = ae.getValue("sample_rate").jsonPrimitive.int
        baseChunkSize = ae.getValue("base_chunk_size").jsonPrimitive.int
        chunkCompress = ttl.getValue("chunk_compress_factor").jsonPrimitive.int
        latentDim = ttl.getValue("latent_dim").jsonPrimitive.int

        // Unicode indexer: sherpa's packed int32 .bin, else the fp32
        // layout's JSON array (PB-2).
        val indexerBin = File(engineDir, "unicode_indexer.bin")
        val indexer: IntArray = if (indexerBin.isFile) {
            IndexerBinReader.parse(indexerBin)
        } else {
            Json.parseToJsonElement(File(engineDir, "unicode_indexer.json").readText())
                .jsonArray
                .map { it.jsonPrimitive.int }
                .toIntArray()
        }
        textProcessor = UnicodeProcessor(indexer)

        // Voice styles: sherpa's voice.bin (parsed, tensors created lazily
        // per voice in [styleFor]), else fp32 voice_styles/<name>.json (PB-2).
        val voiceBin = File(engineDir, "voice.bin")
        voiceBinData = if (voiceBin.isFile) VoiceBinReader.parse(voiceBin) else null

        logcat(LogPriority.INFO) { "ORT intraOpNumThreads=$intraOpThreads (manual setting or autodetect)" }
        xnnpackOutcome.clear()
        val opts = buildSessionOptions(intraOpThreads, label = "main", xnnpackMode = xnnpackMode)
        // ORT-1.26-android SIGSEGVs partitioning the sherpa int8 vocoder for
        // XNNPACK at session-create time (null deref inside libonnxruntime,
        // reproducer: load vocoder.int8.onnx with addXnnpack on Pixel 8 Pro).
        // The other three graphs partition fine, so only the vocoder gets a
        // CPU-EP session regardless of pocketXnnpackMode.
        val vocoderOpts = buildSessionOptions(intraOpThreads, label = "vocoder", xnnpackMode = "off")
        xnnpackOutcome["vocoder"] = "cpu(int8+xnnpack segv workaround)"

        dpSession = createSession(ort, opts, "duration_predictor")
        textEncSession = createSession(ort, opts, "text_encoder")
        vectorEstSession = createSession(ort, opts, "vector_estimator")
        vocoderSession = createSession(ort, vocoderOpts, "vocoder")

        logcat(LogPriority.INFO) {
            "Supertonic load selection: intraOpThreads=$intraOpThreads " +
                "xnnpackMode=$xnnpackMode ep=$xnnpackOutcome " +
                "sampleRate=$loadedSampleRate voices=${voiceBinData?.count ?: 0}"
        }

        // Publish only after every field a synth path touches is non-null.
        env = ort
    }

    /**
     * Per-session OrtSession.SessionOptions — mirrors PocketEngine's
     * `buildPocketSessionOptions` (PocketEngine.kt:413-475): ALL_OPT +
     * memory-pattern on, XNNPACK per the shared `pocketXnnpackMode` knob
     * with `allow_spinning=0` so ORT's pool doesn't fight XNNPACK's.
     */
    private fun buildSessionOptions(
        intraOpThreads: Int,
        label: String,
        xnnpackMode: String,
    ): OrtSession.SessionOptions {
        return OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(intraOpThreads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setMemoryPatternOptimization(true)
            if (VERBOSE_ORT_DIAGNOSTIC) {
                setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_VERBOSE)
                setSessionLogVerbosityLevel(0)
                logcat(LogPriority.WARN) { "ORT verbose diagnostic ENABLED for $label — disable after capture" }
            }
            if (xnnpackMode == "off") {
                xnnpackOutcome[label] = "off(pref)"
                logcat(LogPriority.INFO) { "XNNPACK EP disabled by preference for $label" }
            } else {
                if (xnnpackMode == "on") {
                    logcat(LogPriority.INFO) { "XNNPACK EP forced on for $label (pocketXnnpackMode=on)" }
                }
                try {
                    addXnnpack(mapOf("intra_op_num_threads" to intraOpThreads.toString()))
                    // XNNPACK has its own pthread pool; ORT's intra-op pool
                    // with spinning enabled fights it for cores — disable
                    // spinning so ORT threads park instead of busy-waiting.
                    addConfigEntry("session.intra_op.allow_spinning", "0")
                    xnnpackOutcome[label] = "xnnpack"
                    logcat(LogPriority.INFO) {
                        "XNNPACK EP enabled for $label (intra_op_num_threads=$intraOpThreads, allow_spinning=0)"
                    }
                } catch (t: Throwable) {
                    xnnpackOutcome[label] = "cpu(fallback)"
                    logcat(LogPriority.WARN, t) { "XNNPACK EP unavailable for $label; using CPU EP only" }
                }
            }
        }
    }

    /**
     * Load an ONNX session for [baseName], taking `<baseName>.int8.onnx`
     * when present and `<baseName>.onnx` otherwise (PB-1 — per-file, not
     * per-bundle). Applies the ARMv7 byte-array workaround on 32-bit
     * devices (PocketEngine.kt:524-552 verbatim): ARMv7 traps SIGBUS on
     * misaligned int8 tensor loads under mmap; a heap ByteArray dodges it.
     */
    private fun createSession(
        ort: OrtEnvironment,
        opts: OrtSession.SessionOptions,
        baseName: String,
    ): OrtSession {
        val int8 = File(engineDir, "$baseName.int8.onnx")
        val file = if (int8.isFile) int8 else File(engineDir, "$baseName.onnx")
        return if (is32BitArm()) {
            ort.createSession(file.readBytes(), opts)
        } else {
            ort.createSession(file.absolutePath, opts)
        }
    }

    private fun is32BitArm(): Boolean {
        val primary = Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        return primary == "armeabi-v7a" || primary == "armeabi"
    }

    /**
     * Test seam (validation row V1): the real `session.inputNames` of each
     * loaded graph, keyed by graph name. The instrumented test asserts each
     * set covers the names the spike feeds.
     */
    internal fun sessionInputNames(): Map<String, Set<String>> = mapOf(
        "duration_predictor" to (dpSession?.inputNames ?: emptySet()),
        "text_encoder" to (textEncSession?.inputNames ?: emptySet()),
        "vector_estimator" to (vectorEstSession?.inputNames ?: emptySet()),
        "vocoder" to (vocoderSession?.inputNames ?: emptySet()),
    )

    /**
     * Streaming synthesis: emits one [SynthAudio] per [chunkText] chunk
     * (≤300 chars), PCM16 mono at [sampleRate], with 0.3 s of silence
     * prepended to every chunk after the first. [speed] rides the model's
     * native duration-divide parameter (no pitch shift — RESEARCH rec 5);
     * [totalStep] is the denoise step count (sherpa default 5).
     *
     * The whole utterance holds the companion [synthLock] — process-shared
     * so read-along and renderer engine instances serialize (PB-6).
     */
    fun synthesizeStream(
        text: String,
        voiceId: String,
        totalStep: Int,
        speed: Float,
    ): Flow<SynthAudio> = channelFlow {
        ensureLoaded()
        val rate = sampleRate
        synthLock.withLock {
            val style = styleFor(voiceId.substringAfter(':', voiceId))
            val silence = ShortArray((INTER_CHUNK_SILENCE_SEC * rate).toInt())
            var first = true
            for (chunk in chunkText(text)) {
                coroutineContext.ensureActive()
                check(!releaseRequested.get()) { "Supertonic engine released mid-synthesis" }
                val wav = infer(chunk, style, totalStep, speed)
                var pcm = ShortArray(wav.size) { i ->
                    (wav[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }
                if (!first) pcm = silence + pcm
                first = false
                send(SynthAudio(pcm, rate))
            }
        }
    }.flowOn(Dispatchers.Default)

    /**
     * One chunk through the full graph chain. Suspend so the denoise loop
     * can `ensureActive` per step — ORT `run` calls aren't interruptible
     * mid-graph, so cancellation granularity is the step boundary (R5).
     *
     * PB-7: `text_emb`, `style_ttl`, `latent_mask`, `text_mask` and
     * `total_step` tensors are created once and reused across all steps;
     * `xt` stays a flat FloatArray — each step writes the estimator output
     * back into it via the output tensor's FloatBuffer (no nested-array
     * flattening, no per-step tensor churn).
     */
    private suspend fun infer(
        text: String,
        style: Style,
        totalStep: Int,
        speed: Float,
    ): FloatArray {
        val ort = env ?: error("Supertonic engine not loaded")
        val dp = dpSession ?: error("duration_predictor session missing")
        val textEnc = textEncSession ?: error("text_encoder session missing")
        val vectorEst = vectorEstSession ?: error("vector_estimator session missing")
        val vocoder = vocoderSession ?: error("vocoder session missing")
        val processor = textProcessor ?: error("text processor missing")

        val (textIds, textMask) = processor.process(text, LANG)
        val textIdsTensor =
            createLongTensor(textIds, longArrayOf(1, textIds.size.toLong()))
        val textMaskTensor =
            createFloatTensor(textMask, longArrayOf(1, 1, textMask.size.toLong()))
        try {
            // Duration prediction — duration[0] seconds, divided by the
            // native speed parameter (tempo change without pitch shift).
            val duration: FloatArray
            val dpResult = dp.run(
                mapOf(
                    "text_ids" to textIdsTensor,
                    "style_dp" to style.dpTensor,
                    "text_mask" to textMaskTensor,
                ),
            )
            try {
                when (val v = dpResult.get(0).value) {
                    is Array<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        duration = (v as Array<FloatArray>)[0]
                    }
                    else -> duration = v as FloatArray
                }
            } finally {
                dpResult.close()
            }
            duration[0] /= speed
            val wavLen = (duration[0] * sampleRate).toInt()

            // Text encoding — the output tensor stays alive across the
            // whole denoise loop as the `text_emb` conditioning input.
            val textEncResult = textEnc.run(
                mapOf(
                    "text_ids" to textIdsTensor,
                    "style_ttl" to style.ttlTensor,
                    "text_mask" to textMaskTensor,
                ),
            )
            val textEmbTensor = textEncResult.get(0) as OnnxTensor

            val latent = sampleNoisyLatent(duration[0])
            val latentMaskTensor =
                OnnxTensor.createTensor(ort, FloatBuffer.wrap(latent.mask), latent.maskShape)
            val totalStepTensor =
                OnnxTensor.createTensor(ort, floatArrayOf(totalStep.toFloat()))
            try {
                for (step in 0 until totalStep) {
                    coroutineContext.ensureActive()
                    check(!releaseRequested.get()) { "Supertonic engine released mid-synthesis" }
                    val currentStepTensor =
                        OnnxTensor.createTensor(ort, floatArrayOf(step.toFloat()))
                    val noisyLatentTensor =
                        OnnxTensor.createTensor(ort, FloatBuffer.wrap(latent.xt), latent.xtShape)
                    val veResult = vectorEst.run(
                        mapOf(
                            "noisy_latent" to noisyLatentTensor,
                            "text_emb" to textEmbTensor,
                            "style_ttl" to style.ttlTensor,
                            "latent_mask" to latentMaskTensor,
                            "text_mask" to textMaskTensor,
                            "current_step" to currentStepTensor,
                            "total_step" to totalStepTensor,
                        ),
                    )
                    // Read the refined latent straight back into the flat
                    // array — avoids the spike's nested-array re-flatten
                    // per step.
                    (veResult.get(0) as OnnxTensor).floatBuffer.get(latent.xt)
                    currentStepTensor.close()
                    noisyLatentTensor.close()
                    veResult.close()
                }
            } finally {
                latentMaskTensor.close()
                totalStepTensor.close()
                textEncResult.close()
            }

            val latentTensor =
                OnnxTensor.createTensor(ort, FloatBuffer.wrap(latent.xt), latent.xtShape)
            val wav: FloatArray
            val vocoderResult = vocoder.run(mapOf("latent" to latentTensor))
            try {
                @Suppress("UNCHECKED_CAST")
                val wavBatch = vocoderResult.get(0).value as Array<FloatArray>
                wav = wavBatch[0]
            } finally {
                vocoderResult.close()
            }
            latentTensor.close()

            return FloatArray(minOf(wavLen, wav.size)) { wav[it] }
        } finally {
            textIdsTensor.close()
            textMaskTensor.close()
        }
    }

    /**
     * Flat-layout noisy latent + mask for a single-utterance batch.
     *
     * `latentLen = ceil(wavLen / (baseChunkSize * chunkCompress))` latent
     * frames of `latentDim * chunkCompress` channels. The mask is all-ones
     * for a batch of one (`ceil(wavLen/chunkSize) == latentLen` — the
     * mask only differentiates rows when batching), so the spike's
     * noise×mask multiply is a no-op and is skipped.
     */
    private fun sampleNoisyLatent(durationSec: Float): SampledLatent {
        val wavLen = (durationSec * sampleRate).toLong().coerceAtLeast(1)
        val chunkSize = baseChunkSize * chunkCompress
        val latentLen = ((wavLen + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
        val latDim = latentDim * chunkCompress
        val rng = Random.Default
        val xt = FloatArray(latDim * latentLen)
        // Box-Muller gaussian noise — same sampler as upstream Helper.java.
        for (i in xt.indices) {
            val u1 = max(1e-10, rng.nextDouble())
            val u2 = rng.nextDouble()
            xt[i] = (sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)).toFloat()
        }
        return SampledLatent(
            xt = xt,
            xtShape = longArrayOf(1, latDim.toLong(), latentLen.toLong()),
            mask = FloatArray(latentLen) { 1f },
            maskShape = longArrayOf(1, 1, latentLen.toLong()),
        )
    }

    private class SampledLatent(
        val xt: FloatArray,
        val xtShape: LongArray,
        val mask: FloatArray,
        val maskShape: LongArray,
    )

    /**
     * Style tensors for [styleName] ("F5" — the `supertonic:` prefix is
     * stripped by the caller). Lazily created and cached (~300 KB of
     * floats per voice); called under [synthLock]. voice.bin supplies the
     * sid row; the fp32 layout falls back to `voice_styles/<name>.json`.
     */
    private fun styleFor(styleName: String): Style {
        styleCache[styleName]?.let { return it }
        val ort = env ?: error("Supertonic engine not loaded")
        val bin = voiceBinData
        val style = if (bin != null) {
            val sid = SupertonicVoiceCatalog.sidFor(styleName)
                ?: throw IllegalArgumentException("Unknown Supertonic voice '$styleName'")
            val (ttlFlat, ttlShape) = bin.ttlSlice(sid)
            val (dpFlat, dpShape) = bin.dpSlice(sid)
            Style(
                OnnxTensor.createTensor(ort, FloatBuffer.wrap(ttlFlat), ttlShape),
                OnnxTensor.createTensor(ort, FloatBuffer.wrap(dpFlat), dpShape),
            )
        } else {
            loadVoiceStyle(File(engineDir, "voice_styles/$styleName.json"), ort)
        }
        styleCache[styleName] = style
        return style
    }

    /**
     * fp32-layout fallback (PB-2): the spike's `voice_styles/<name>.json`
     * format — `{style_ttl: {dims:[1,d1,d2], data:[[[…]]]}, style_dp:{…}}`
     * — parsed via kotlinx-serialization (org.json is JVM-test-absent).
     */
    private fun loadVoiceStyle(file: File, ort: OrtEnvironment): Style {
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        fun flatten(key: String): Pair<FloatArray, LongArray> {
            val node = root.getValue(key).jsonObject
            val dims = node.getValue("dims").jsonArray
            val shape = longArrayOf(
                dims[0].jsonPrimitive.long,
                dims[1].jsonPrimitive.long,
                dims[2].jsonPrimitive.long,
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
        val (ttlFlat, ttlShape) = flatten("style_ttl")
        val (dpFlat, dpShape) = flatten("style_dp")
        return Style(
            OnnxTensor.createTensor(ort, FloatBuffer.wrap(ttlFlat), ttlShape),
            OnnxTensor.createTensor(ort, FloatBuffer.wrap(dpFlat), dpShape),
        )
    }

    private fun createFloatTensor(array: FloatArray, shape: LongArray): OnnxTensor {
        val ort = env ?: error("Supertonic engine not loaded")
        return OnnxTensor.createTensor(ort, FloatBuffer.wrap(array), shape)
    }

    private fun createLongTensor(array: LongArray, shape: LongArray): OnnxTensor {
        val ort = env ?: error("Supertonic engine not loaded")
        return OnnxTensor.createTensor(ort, LongBuffer.wrap(array), shape)
    }

    fun release() {
        try {
            // Closing a session another thread is mid-run() on is a native
            // SIGSEGV, so release must exclude BOTH a concurrent load
            // (loadLock) and a concurrent synth (synthLock) — PocketEngine
            // :1080-1103 pattern verbatim. [releaseRequested] lets the
            // denoise loop abort within ~one step instead of waiting out
            // the whole utterance. Lock order can't deadlock: this is the
            // only place both locks are held at once.
            releaseRequested.set(true)
            try {
                kotlinx.coroutines.runBlocking {
                    synthLock.withLock { loadLock.withLock { releaseInternal() } }
                }
            } finally {
                releaseRequested.set(false)
            }
        } catch (t: Throwable) {
            logcat(LogPriority.WARN) { "release() ignored failure: ${t.message}" }
        }
    }

    private fun releaseInternal() {
        listOf(dpSession, textEncSession, vectorEstSession, vocoderSession).forEach { s ->
            try {
                s?.close()
            } catch (_: Throwable) {}
        }
        dpSession = null
        textEncSession = null
        vectorEstSession = null
        vocoderSession = null
        styleCache.values.forEach { style ->
            try {
                style.ttlTensor.close()
            } catch (_: Throwable) {}
            try {
                style.dpTensor.close()
            } catch (_: Throwable) {}
        }
        styleCache.clear()
        voiceBinData = null
        textProcessor = null
        loadedSampleRate = null
        // OrtEnvironment is process-scoped; don't close it.
        env = null
    }

    /**
     * Text normalization + unicode-indexer lookup — port of the spike's
     * `UnicodeProcessor` (itself a port of upstream Helper.java), with the
     * indexer as a plain [IntArray] so both `unicode_indexer.bin` and the
     * fp32 layout's JSON array feed it. Values widen to Long at lookup —
     * `text_ids` is an int64 tensor input.
     */
    class UnicodeProcessor(private val indexer: IntArray) {

        /**
         * [text] → `(textIds, textMask)` for a batch of one: textIds[T]
         * is the codepoint→indexer lookup, textMask[T] is all-ones (the
         * mask only differentiates rows when batching).
         */
        fun process(text: String, lang: String): Pair<LongArray, FloatArray> {
            val vals = preprocess(text, lang).codePoints().toArray()
            val textIds = LongArray(vals.size) { i ->
                val cp = vals[i]
                if (cp < indexer.size) indexer[cp].toLong() else 0
            }
            val textMask = FloatArray(vals.size) { 1f }
            return textIds to textMask
        }

        private fun preprocess(text: String, lang: String): String {
            var t = Normalizer.normalize(text, Normalizer.Form.NFKD)
            t = removeEmojis(t)
            mapOf(
                "–" to "-", "‑" to "-", "—" to "-", "_" to " ",
                "“" to "\"", "”" to "\"", "‘" to "'", "’" to "'",
                "´" to "'", "`" to "'", "[" to " ", "]" to " ",
                "|" to " ", "/" to " ", "#" to " ", "→" to " ", "←" to " ",
            ).forEach { (k, v) -> t = t.replace(k, v) }
            t = t.replace(Regex("[♥☆♡©\\\\]"), "")
            t = t.replace("@", " at ")
                .replace("e.g.,", "for example, ")
                .replace("i.e.,", "that is, ")
            t = t.replace(" ,", ",").replace(" .", ".").replace(" !", "!")
                .replace(" ?", "?").replace(" ;", ";").replace(" :", ":")
                .replace(" '", "'")
            while (t.contains("\"\"")) t = t.replace("\"\"", "\"")
            while (t.contains("''")) t = t.replace("''", "'")
            while (t.contains("``")) t = t.replace("``", "`")
            t = t.replace(Regex("\\s+"), " ").trim()
            if (!t.matches(Regex(".*[.!?;:,'\"“”‘’)\\]}…。」』】〉》›»]$"))) t += "."
            return "<$lang>$t</$lang>"
        }

        private fun removeEmojis(text: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < text.length) {
                val cp = if (Character.isHighSurrogate(text[i]) &&
                    i + 1 < text.length &&
                    Character.isLowSurrogate(text[i + 1])
                ) {
                    Character.codePointAt(text, i).also { i++ }
                } else {
                    text[i].code
                }
                val isEmoji = (cp in 0x1F600..0x1F64F) ||
                    (cp in 0x1F300..0x1F5FF) ||
                    (cp in 0x1F680..0x1F6FF) ||
                    (cp in 0x1F700..0x1F77F) ||
                    (cp in 0x1F780..0x1F7FF) ||
                    (cp in 0x1F800..0x1F8FF) ||
                    (cp in 0x1F900..0x1F9FF) ||
                    (cp in 0x1FA00..0x1FA6F) ||
                    (cp in 0x1FA70..0x1FAFF) ||
                    (cp in 0x2600..0x26FF) ||
                    (cp in 0x2700..0x27BF) ||
                    (cp in 0x1F1E6..0x1F1FF)
                if (!isEmoji) {
                    if (cp > 0xFFFF) sb.append(String(Character.toChars(cp))) else sb.append(cp.toChar())
                }
                i++
            }
            return sb.toString()
        }
    }

    companion object {
        const val ENGINE_NAME = SupertonicEngineCatalog.ENGINE_NAME

        /**
         * Process-shared synthesis lock (PocketEngine.kt:2093 precedent):
         * EVERY SupertonicEngine instance — live read-along AND the chapter
         * renderer's — funnels through this one Mutex so concurrent synths
         * can't double-book the ORT session CPU budget. Held per
         * [synthesizeStream] call, not per chapter.
         */
        private val synthLock = Mutex()

        /** Bundle is English (`<en>` tags) — non-en setLanguage is stored but ignored (PB-8). */
        private const val LANG = "en"

        private const val MAX_CHUNK_LENGTH = 300
        private const val INTER_CHUNK_SILENCE_SEC = 0.3f

        /**
         * Default ORT intra-op threads when neither the instrumented-test
         * override nor the shared manual pref is set. V6 device matrix
         * (Pixel 8 Pro): 2 threads ≈ RTF 0.49 vs autodetect(8) ≈ 0.59 —
         * sherpa-onnx ships the same 2-thread default.
         */
        private const val DEFAULT_THREADS = 2

        /** Flip to capture ORT node placement in logcat (`adb logcat -s onnxruntime`). */
        private const val VERBOSE_ORT_DIAGNOSTIC = false

        private val ABBREVIATIONS = listOf(
            "Dr.", "Mr.", "Mrs.", "Ms.", "Prof.", "Sr.", "Jr.",
            "St.", "Ave.", "Rd.", "Blvd.", "Dept.", "Inc.", "Ltd.",
            "Co.", "Corp.", "etc.", "vs.", "i.e.", "e.g.", "Ph.D.",
        )

        /**
         * Upstream Helper.java's chunker, verbatim: split on blank lines,
         * then sentence boundaries (abbreviation-aware), then commas, then
         * words — no chunk exceeds [maxLen] chars unless a single word does.
         */
        fun chunkText(text: String, maxLen: Int = MAX_CHUNK_LENGTH): List<String> {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return listOf("")
            val chunks = mutableListOf<String>()
            for (para in trimmed.split(Regex("\\n\\s*\\n"))) {
                val p = para.trim()
                if (p.isEmpty()) continue
                if (p.length <= maxLen) {
                    chunks.add(p)
                    continue
                }
                val sentences = splitSentences(p)
                val current = StringBuilder()
                for (sentence in sentences) {
                    val s = sentence.trim()
                    if (s.isEmpty()) continue
                    if (s.length > maxLen) {
                        if (current.isNotEmpty()) {
                            chunks.add(current.toString().trim())
                            current.setLength(0)
                        }
                        for (part in s.split(",")) {
                            val pt = part.trim()
                            if (pt.isEmpty()) continue
                            if (pt.length > maxLen) {
                                val wordChunk = StringBuilder()
                                for (word in pt.split(Regex("\\s+"))) {
                                    if (wordChunk.length + word.length + 1 > maxLen && wordChunk.isNotEmpty()) {
                                        chunks.add(wordChunk.toString().trim())
                                        wordChunk.setLength(0)
                                    }
                                    if (wordChunk.isNotEmpty()) wordChunk.append(" ")
                                    wordChunk.append(word)
                                }
                                if (wordChunk.isNotEmpty()) chunks.add(wordChunk.toString().trim())
                            } else {
                                if (current.length + pt.length + 1 > maxLen && current.isNotEmpty()) {
                                    chunks.add(current.toString().trim())
                                    current.setLength(0)
                                }
                                if (current.isNotEmpty()) current.append(", ")
                                current.append(pt)
                            }
                        }
                        continue
                    }
                    if (current.length + s.length + 1 > maxLen && current.isNotEmpty()) {
                        chunks.add(current.toString().trim())
                        current.setLength(0)
                    }
                    if (current.isNotEmpty()) current.append(" ")
                    current.append(s)
                }
                if (current.isNotEmpty()) chunks.add(current.toString().trim())
            }
            return chunks.ifEmpty { listOf("") }
        }

        private fun splitSentences(text: String): List<String> {
            val abbrev = ABBREVIATIONS.joinToString("|") { Regex.escape(it) }
            val pattern = Regex("(?<!(?:$abbrev))(?<=[.!?])\\s+")
            return pattern.split(text).toList()
        }
    }
}
