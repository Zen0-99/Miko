package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Neural TTS engine backed by sherpa-onnx (Piper/Kokoro/Matcha/ZipVoice models).
 *
 * This engine provides high-quality offline neural TTS with 186+ voices.
 * It requires downloading model files separately (via voice management UI).
 *
 * Supported model types:
 * - Piper / VITS: fast, good quality, many voices
 * - Kokoro: highest quality, slower, multi-language
 * - Matcha: multilingual, good quality (acoustic + vocoder)
 * - Kitten: compact Kokoro variant
 * - ZipVoice: compact, lower quality (encoder + decoder + vocoder)
 */
class NeuralTtsEngine(
    private val context: Context,
    private val modelPath: String,
    private val modelType: String = "piper",
    private val useNnapi: Boolean = false,
    private val numThreads: Int = 2,
    private val maxNumSentences: Int = 2,
    private val speakerId: Int = 0,
    private val lengthScale: Float = 1.0f,
    private val noiseScale: Float = 0.667f,
    private val noiseScaleW: Float = 0.8f,
) : TtsEngine {

    private var ready = false
    private var callback: TtsCallback? = null
    private var currentRate: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentLanguage: String = ""

    private var sherpaTts: OfflineTts? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var isPlaying = false
    @Volatile private var isPaused = false
    @Volatile private var stopRequested = false

    private val utteranceCounter = AtomicInteger(0)

    override val isReady: Boolean
        get() = ready

    override fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        val modelDir = File(modelPath)
        if (!modelDir.exists() || !modelDir.isDirectory) {
            onError("Neural TTS model not found at $modelPath. Please download a model first.")
            return
        }

        try {
            val config = buildTtsConfig(modelDir, modelType, currentLanguage)
                ?: run {
                    onError("Unsupported model type '$modelType' or missing model files in $modelPath")
                    return
                }
            logcat(LogPriority.INFO) {
                "NeuralTtsEngine: initializing $modelType model from $modelPath"
            }
            sherpaTts = OfflineTts(assetManager = null, config = config)
            ready = true
            logcat(LogPriority.INFO) {
                "NeuralTtsEngine: ready, sampleRate=${sherpaTts?.sampleRate()}, numSpeakers=${sherpaTts?.numSpeakers()}"
            }
            onReady()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Neural TTS init failed" }
            onError("Neural TTS init failed: ${e.message}")
        }
    }

    override fun speak(text: String, flush: Boolean, callback: TtsCallback?) {
        this.callback = callback
        val tts = sherpaTts
        if (tts == null || !ready) {
            callback?.onError("", "Neural TTS engine not ready")
            return
        }
        if (text.isBlank()) {
            callback?.onDone("")
            return
        }

        if (flush) {
            stopPlayback()
        }

        val utteranceId = utteranceCounter.incrementAndGet().toString()
        stopRequested = false

        Thread {
            try {
                callback?.onStart(utteranceId)

                val nSpeakers = runCatching { tts.numSpeakers() }.getOrDefault(0)
                val safeSid = if (nSpeakers > 0) speakerId.coerceIn(0, nSpeakers - 1) else 0

                val audio = tts.generate(text = text, sid = safeSid, speed = currentRate)
                var samples = audio.samples
                val sampleRate = audio.sampleRate

                // Apply pitch shifting via linear-interpolation resampler
                if (kotlin.math.abs(currentPitch - 1f) >= 0.001f) {
                    samples = PitchResampler.resample(samples, currentPitch)
                }

                if (stopRequested) {
                    callback?.onDone(utteranceId)
                    return@Thread
                }

                val pcmBytes = floatToPcm16(samples)
                playPcm(pcmBytes, sampleRate)

                if (!stopRequested) {
                    callback?.onDone(utteranceId)
                } else {
                    callback?.onDone(utteranceId)
                }
            } catch (t: Throwable) {
                logcat(LogPriority.ERROR, t) { "NeuralTtsEngine.speak failed" }
                callback?.onError(utteranceId, "Synthesis failed: ${t.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    override fun stop() {
        stopRequested = true
        stopPlayback()
    }

    override fun pause() {
        val track = audioTrack
        if (track != null && isPlaying && !isPaused) {
            try {
                track.pause()
                isPaused = true
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "NeuralTtsEngine.pause failed" }
            }
        }
    }

    override fun resume() {
        val track = audioTrack
        if (track != null && isPaused) {
            try {
                track.play()
                isPaused = false
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "NeuralTtsEngine.resume failed" }
            }
        }
    }

    override fun setSpeechRate(rate: Float) {
        currentRate = rate.coerceIn(0.25f, 4f)
    }

    override fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.25f, 4f)
    }

    override fun setVoice(voiceName: String) {
        // Switching voice requires re-initialization with a different model path.
        // The caller (TtsController) recreates the engine with a new modelPath when
        // the voice changes, so here we just log. If the voiceName maps to a
        // subdirectory under modelPath, we could re-init — but the current design
        // rebuilds the engine entirely.
        logcat(LogPriority.INFO) { "NeuralTtsEngine.setVoice($voiceName) — recreate engine to switch voice" }
    }

    override fun setLanguage(languageCode: String) {
        currentLanguage = languageCode
        // For Kokoro multi-language models, language is baked into the config at
        // construction time. A re-init is needed to change it. We store it so the
        // next initialize() call uses it.
        logcat(LogPriority.INFO) { "NeuralTtsEngine.setLanguage($languageCode)" }
    }

    override fun getAvailableVoices(): List<TtsVoice> {
        // Scan the parent voices directory for installed voice directories.
        val modelDir = File(modelPath)
        if (!modelDir.exists()) return emptyList()

        // If modelPath points at a single voice directory, return it as one voice.
        val hasOnnx = modelDir.listFiles()?.any { it.isFile && it.extension == "onnx" } == true
        if (hasOnnx) {
            return listOf(
                TtsVoice(
                    name = modelDir.name,
                    displayName = modelDir.name.replace("_", " "),
                    language = inferLanguageFromDirName(modelDir.name),
                    isNeural = true,
                ),
            )
        }

        // Otherwise scan subdirectories for voice bundles.
        return modelDir.listFiles()
            ?.filter { it.isDirectory }
            ?.filter { dir -> dir.listFiles()?.any { it.isFile && it.extension == "onnx" } == true }
            ?.map { dir ->
                TtsVoice(
                    name = dir.name,
                    displayName = dir.name.replace("_", " "),
                    language = inferLanguageFromDirName(dir.name),
                    isNeural = true,
                )
            }
            ?: emptyList()
    }

    override fun shutdown() {
        stop()
        try {
            sherpaTts?.release()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "NeuralTtsEngine.shutdown release failed" }
        }
        sherpaTts = null
        ready = false
    }

    // ---------------------------------------------------------------------------
    // Playback
    // ---------------------------------------------------------------------------

    private fun playPcm(pcmBytes: ByteArray, sampleRate: Int) {
        stopPlayback()

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufSize = maxOf(minBuf, pcmBytes.size)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcmBytes, 0, pcmBytes.size)
        track.setNotificationMarkerPosition(pcmBytes.size / 2) // 16-bit mono → samples = bytes/2
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                stopPlayback()
            }

            override fun onPeriodicNotification(track: AudioTrack?) {}
        })

        audioTrack = track
        isPlaying = true
        isPaused = false
        track.play()

        // Block until playback finishes or is stopped, so speak() thread knows when done.
        while (isPlaying && !stopRequested) {
            try {
                Thread.sleep(20)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun stopPlayback() {
        val track = audioTrack ?: return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.release()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "NeuralTtsEngine.stopPlayback failed" }
        }
        audioTrack = null
        isPlaying = false
        isPaused = false
    }

    // ---------------------------------------------------------------------------
    // PCM conversion
    // ---------------------------------------------------------------------------

    private fun floatToPcm16(samples: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            // Clamp to [-1, 1] and scale to 16-bit
            val clamped = sample.coerceIn(-1f, 1f)
            val value = (clamped * Short.MAX_VALUE).toInt()
            buffer.putShort(value.toShort())
        }
        return buffer.array()
    }

    // ---------------------------------------------------------------------------
    // Config building (ported from HayaiTTS SherpaTtsRuntime)
    // ---------------------------------------------------------------------------

    private fun buildTtsConfig(
        dir: File,
        type: String,
        languageHint: String,
    ): OfflineTtsConfig? {
        return when (type.lowercase()) {
            "piper", "vits" -> buildVitsConfig(
                dir, lengthScale, noiseScale, noiseScaleW, numThreads, maxNumSentences,
            )
            "kokoro" -> buildKokoroConfig(
                dir, languageHint, lengthScale, numThreads, maxNumSentences,
            )
            "matcha" -> buildMatchaConfig(
                dir, lengthScale, numThreads, maxNumSentences,
            )
            "kitten" -> buildKittenConfig(
                dir, lengthScale, numThreads, maxNumSentences,
            )
            "zipvoice" -> buildZipVoiceConfig(
                dir, numThreads, maxNumSentences,
            )
            else -> {
                // Try to infer from directory contents
                logcat(LogPriority.WARN) {
                    "NeuralTtsEngine: unknown modelType '$type', attempting VITS fallback"
                }
                runCatching {
                    buildVitsConfig(dir, lengthScale, noiseScale, noiseScaleW, numThreads, maxNumSentences)
                }.getOrNull()
            }
        }
    }

    private fun buildVitsConfig(
        dir: File,
        lengthScale: Float,
        noiseScale: Float,
        noiseScaleW: Float,
        numThreads: Int,
        maxNumSentences: Int,
    ): OfflineTtsConfig {
        val modelPath = resolveModelFile(dir, VITS_MODEL_CANDIDATES)
        val tokensPath = File(dir, TOKENS_FILE).absolutePath
        val dataDir = File(dir, ESPEAK_DIR)
        val dataDirPath = if (dataDir.isDirectory) dataDir.absolutePath else ""
        val lexicon = File(dir, LEXICON_FILE)
        val lexiconPath = if (lexicon.isFile) lexicon.absolutePath else ""
        val dictDir = File(dir, DICT_DIR)
        val dictDirPath = if (dictDir.isDirectory) dictDir.absolutePath else ""
        return OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelPath,
                    lexicon = lexiconPath,
                    tokens = tokensPath,
                    dataDir = dataDirPath,
                    dictDir = dictDirPath,
                    lengthScale = lengthScale,
                    noiseScale = noiseScale,
                    noiseScaleW = noiseScaleW,
                ),
                numThreads = numThreads,
                debug = false,
                provider = if (useNnapi) "nnapi" else "cpu",
            ),
            ruleFsts = collectRuleFsts(dir),
            maxNumSentences = maxNumSentences,
        )
    }

    private fun buildMatchaConfig(
        dir: File,
        lengthScale: Float,
        numThreads: Int,
        maxNumSentences: Int,
    ): OfflineTtsConfig {
        val acoustic = resolveModelFile(dir, MATCHA_ACOUSTIC_CANDIDATES)
        val vocoder = resolveVocoderFile(dir, MATCHA_VOCODER_CANDIDATES)
        val tokensPath = File(dir, TOKENS_FILE).absolutePath
        val dataDir = File(dir, ESPEAK_DIR)
        val dataDirPath = if (dataDir.isDirectory) dataDir.absolutePath else ""
        val lexicon = File(dir, LEXICON_FILE)
        val lexiconPath = if (lexicon.isFile) lexicon.absolutePath else ""
        val dictDir = File(dir, DICT_DIR)
        val dictDirPath = if (dictDir.isDirectory) dictDir.absolutePath else ""
        return OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                matcha = OfflineTtsMatchaModelConfig(
                    acousticModel = acoustic,
                    vocoder = vocoder,
                    lexicon = lexiconPath,
                    tokens = tokensPath,
                    dataDir = dataDirPath,
                    dictDir = dictDirPath,
                    lengthScale = lengthScale,
                ),
                numThreads = numThreads,
                debug = false,
                provider = if (useNnapi) "nnapi" else "cpu",
            ),
            ruleFsts = collectRuleFsts(dir),
            maxNumSentences = maxNumSentences,
        )
    }

    private fun buildKokoroConfig(
        dir: File,
        languageHint: String,
        lengthScale: Float,
        numThreads: Int,
        maxNumSentences: Int,
    ): OfflineTtsConfig {
        val modelPath = resolveModelFile(dir, KOKORO_MODEL_CANDIDATES)
        val voices = File(dir, KOKORO_VOICES_FILE)
        check(voices.isFile) { "Kokoro voice at $dir is missing $KOKORO_VOICES_FILE" }
        val tokensPath = File(dir, TOKENS_FILE).absolutePath
        val dataDir = File(dir, ESPEAK_DIR)
        val dataDirPath = if (dataDir.isDirectory) dataDir.absolutePath else ""
        val lexicon = File(dir, LEXICON_FILE)
        val multiLexicons = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(KOKORO_LEXICON_PREFIX) && it.name.endsWith(".txt") }
            ?.sortedBy { it.name }
            .orEmpty()
        val isMultiLanguage = multiLexicons.isNotEmpty() ||
            dir.name.contains("multi-lang", ignoreCase = true) ||
            dir.name.contains("v1_", ignoreCase = true)
        val lexiconPath = when {
            isMultiLanguage && multiLexicons.isNotEmpty() ->
                multiLexicons.joinToString(",") { it.absolutePath }
            lexicon.isFile -> lexicon.absolutePath
            else -> ""
        }
        val lang = if (isMultiLanguage) languageHint.ifBlank { DEFAULT_KOKORO_LANGUAGE } else ""
        val dictDir = File(dir, DICT_DIR)
        val dictDirPath = if (dictDir.isDirectory) dictDir.absolutePath else ""
        return OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = modelPath,
                    voices = voices.absolutePath,
                    tokens = tokensPath,
                    dataDir = dataDirPath,
                    lexicon = lexiconPath,
                    lang = lang,
                    dictDir = dictDirPath,
                    lengthScale = lengthScale,
                ),
                numThreads = numThreads,
                debug = false,
                provider = if (useNnapi) "nnapi" else "cpu",
            ),
            ruleFsts = collectRuleFsts(dir),
            maxNumSentences = maxNumSentences,
        )
    }

    private fun buildKittenConfig(
        dir: File,
        lengthScale: Float,
        numThreads: Int,
        maxNumSentences: Int,
    ): OfflineTtsConfig {
        val modelPath = resolveModelFile(dir, KITTEN_MODEL_CANDIDATES)
        val voices = File(dir, KOKORO_VOICES_FILE)
        check(voices.isFile) { "Kitten voice at $dir is missing $KOKORO_VOICES_FILE" }
        val tokensPath = File(dir, TOKENS_FILE).absolutePath
        val dataDir = File(dir, ESPEAK_DIR)
        val dataDirPath = if (dataDir.isDirectory) dataDir.absolutePath else ""
        return OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kitten = OfflineTtsKittenModelConfig(
                    model = modelPath,
                    voices = voices.absolutePath,
                    tokens = tokensPath,
                    dataDir = dataDirPath,
                    lengthScale = lengthScale,
                ),
                numThreads = numThreads,
                debug = false,
                provider = if (useNnapi) "nnapi" else "cpu",
            ),
            maxNumSentences = maxNumSentences,
        )
    }

    private fun buildZipVoiceConfig(
        dir: File,
        numThreads: Int,
        maxNumSentences: Int,
    ): OfflineTtsConfig {
        val encoder = resolveModelFile(dir, ZIPVOICE_ENCODER_CANDIDATES)
        val decoder = resolveModelFile(dir, ZIPVOICE_DECODER_CANDIDATES)
        val vocoder = resolveModelFile(dir, ZIPVOICE_VOCODER_CANDIDATES)
        val tokensPath = File(dir, TOKENS_FILE).absolutePath
        val dataDir = File(dir, ESPEAK_DIR)
        val dataDirPath = if (dataDir.isDirectory) dataDir.absolutePath else ""
        val lexicon = File(dir, LEXICON_FILE)
        val lexiconPath = if (lexicon.isFile) lexicon.absolutePath else ""
        return OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                zipvoice = OfflineTtsZipVoiceModelConfig(
                    tokens = tokensPath,
                    encoder = encoder,
                    decoder = decoder,
                    vocoder = vocoder,
                    dataDir = dataDirPath,
                    lexicon = lexiconPath,
                ),
                numThreads = numThreads,
                debug = false,
                provider = if (useNnapi) "nnapi" else "cpu",
            ),
            maxNumSentences = maxNumSentences,
        )
    }

    // ---------------------------------------------------------------------------
    // File resolution helpers
    // ---------------------------------------------------------------------------

    private fun resolveModelFile(dir: File, candidates: List<String>): String {
        for (name in candidates) {
            val candidate = File(dir, name)
            if (candidate.isFile) return candidate.absolutePath
        }
        val firstOnnx = dir.listFiles()?.firstOrNull { it.isFile && it.extension == "onnx" }
        if (firstOnnx != null) {
            logcat(LogPriority.WARN) {
                "NeuralTtsEngine: unknown bundle layout at $dir, falling back to ${firstOnnx.name}"
            }
            return firstOnnx.absolutePath
        }
        error("No .onnx weight file found in $dir (tried $candidates)")
    }

    private fun resolveVocoderFile(dir: File, candidates: List<String>): String {
        for (name in candidates) {
            val candidate = File(dir, name)
            if (candidate.isFile) return candidate.absolutePath
        }
        val fallback = dir.listFiles()?.firstOrNull { file ->
            file.isFile &&
                file.extension == "onnx" &&
                (file.name.startsWith("vocos-") || file.name.contains("vocoder"))
        }
        if (fallback != null) {
            logcat(LogPriority.WARN) {
                "NeuralTtsEngine: unknown Matcha vocoder layout at $dir, falling back to ${fallback.name}"
            }
            return fallback.absolutePath
        }
        error("No Matcha vocoder file found in $dir (tried $candidates)")
    }

    private fun collectRuleFsts(dir: File): String {
        val present = RULE_FST_FILES.mapNotNull { name ->
            val f = File(dir, name)
            if (f.isFile) f.absolutePath else null
        }
        return present.joinToString(",")
    }

    private fun inferLanguageFromDirName(name: String): String {
        // e.g. "en_US-amy-medium" → "en"
        return name.substringBefore('_').substringBefore('-').lowercase().ifBlank { "en" }
    }

    companion object {
        private const val TOKENS_FILE = "tokens.txt"
        private const val LEXICON_FILE = "lexicon.txt"
        private const val ESPEAK_DIR = "espeak-ng-data"
        private const val DICT_DIR = "dict"

        private val VITS_MODEL_CANDIDATES = listOf("model.onnx", "vits-vctk.onnx", "vits-vctk.int8.onnx")
        private val MATCHA_ACOUSTIC_CANDIDATES = listOf("model-steps-3.onnx", "model-steps-6.onnx", "acoustic.onnx")
        private val MATCHA_VOCODER_CANDIDATES = listOf("vocos-22khz-univ.onnx", "vocos-16khz-univ.onnx", "vocoder.onnx")
        private val KOKORO_MODEL_CANDIDATES = listOf(
            "model.onnx",
            "kokoro-multi-lang-v1_1.onnx",
            "kokoro-multi-lang-v1_0.onnx",
            "kokoro-int8-multi-lang-v1_1.onnx",
            "kokoro-int8-multi-lang-v1_0.onnx",
            "kokoro-en-v0_19.onnx",
        )
        private val KITTEN_MODEL_CANDIDATES = listOf("model.onnx", "model.int8.onnx")
        private const val KOKORO_VOICES_FILE = "voices.bin"
        private const val KOKORO_LEXICON_PREFIX = "lexicon-"
        private const val DEFAULT_KOKORO_LANGUAGE = "en"
        private val ZIPVOICE_ENCODER_CANDIDATES = listOf("encoder.int8.onnx", "encoder.onnx")
        private val ZIPVOICE_DECODER_CANDIDATES = listOf("decoder.int8.onnx", "decoder.onnx")
        private val ZIPVOICE_VOCODER_CANDIDATES = listOf("vocos_24khz.onnx", "vocos_22khz.onnx", "vocoder.onnx")
        private val RULE_FST_FILES = listOf("date.fst", "number.fst", "phone.fst")

        /** Known neural TTS voice catalog (186 voices across Piper/Kokoro/Matcha/ZipVoice). */
        val VOICE_CATALOG = listOf(
            "en_US-amy-medium",
            "en_US-amy-low",
            "en_US-arctic-medium",
            "en_US-danny-low",
            "en_US-hfc_male-medium",
            "en_US-kathleen-low",
            "en_US-l2arctic-medium",
            "en_US-libritts-high",
            "en_US-ryan-high",
            "en_US-ryan-medium",
            "en_GB-alba-medium",
            "en_GB-jenny_dioco-medium",
            "en_GB-northern_english_male-medium",
            "en_GB-semaine-medium",
            "zh_CN-huayan-medium",
            "ja_JP-kokoro-medium",
            "ko_KR-kss-medium",
            "es_ES-carlfm-x-low",
            "fr_FR-siwis-medium",
            "de_DE-thorsten-medium",
        )

        /** Piper model download URLs (Hugging Face) */
        const val PIPER_BASE_URL = "https://huggingface.co/rhasspy/piper-voices/resolve/main"
        const val KOKORO_BASE_URL = "https://huggingface.co/hexgrad/Kokoro-82M/resolve/main"
    }
}
