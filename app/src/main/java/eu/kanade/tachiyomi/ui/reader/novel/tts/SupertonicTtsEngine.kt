package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.audio.SynthesisPipeline
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.SynthAudio
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.EngineProfiles
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.ProsodyApplier
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.ProsodyHint
import eu.kanade.tachiyomi.ui.reader.novel.tts.supertonic.SupertonicEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.math.abs

/**
 * [TtsEngine] adapter over the ported Supertonic-3 engine (phase-05B D-01).
 * A near-verbatim mirror of [PocketTtsEngine] — same prefetch/drain
 * protocol, same sink, same callback contract — with three deltas:
 *
 *  1. **Rate rides the model's native `speed` parameter** (RESEARCH rec 5):
 *     `speak`/`prefetch` pass `nativeSpeed = currentRate.coerceIn(0.5f, 2f)`
 *     into the stream — a duration divide, no pitch shift — and [shape]
 *     only runs `resampleLinear` for the residual `currentRate / nativeSpeed`
 *     (the 2.0–4.0 slider tail the model can't cover). Pitch still rides
 *     the float-domain [PitchResampler].
 *  2. **Voices are namespaced** (`supertonic:F5`) via
 *     [SupertonicVoiceCatalog.resolveInstalledVoice] — a stale Pocket pref
 *     name resolves to the F5 default instead of erroring (R6).
 *  3. **The sink is created after [SupertonicEngine.ensureLoaded]** in the
 *     playback job so `AudioTrackPcmSink(engine.sampleRate)` sees the
 *     loaded `tts.json` rate, not the catalog constant.
 *
 * See [PocketTtsEngine]'s class doc for the streaming/prefetch architecture
 * notes — they apply unchanged.
 */
class SupertonicTtsEngine internal constructor(
    private val context: Context,
    preferences: NovelTtsPreferences,
    streamFactory: ((text: String, voiceId: String, speed: Float) -> Flow<SynthAudio>)? = null,
    pcmSinkFactory: (() -> PcmSink)? = null,
    engineLoader: (suspend () -> Unit)? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TtsEngine {

    private val engine = SupertonicEngine(context, preferences)

    /**
     * Engine-load seam — [SupertonicEngine.ensureLoaded] in production; JVM
     * tests inject a no-op since the playback job must call it before
     * creating the sink (the sink's AudioTrack rate is the loaded
     * `tts.json` truth, not the catalog constant).
     */
    private val ensureEngineLoaded: suspend () -> Unit =
        engineLoader ?: { engine.ensureLoaded() }

    /** Stream source — real engine stream in production, fake flow in tests. */
    private val streamSource: (text: String, voiceId: String, speed: Float) -> Flow<SynthAudio> =
        streamFactory ?: { text, voice, speed ->
            engine.synthesizeStream(text, voice, preferences.supertonicNumSteps().get(), speed)
        }

    /** PCM sink — AudioTrack-backed in production, recording fake in tests. */
    private val sinkSource: () -> PcmSink =
        pcmSinkFactory ?: { AudioTrackPcmSink(engine.sampleRate) }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val prefetchLock = Any()
    private val prefetches = LinkedHashMap<String, PrefetchEntry>()

    @Volatile private var playbackJob: Job? = null

    @Volatile private var currentSink: PcmSink? = null

    @Volatile private var currentRate = 1.0f

    @Volatile private var currentPitch = 1.0f

    @Volatile private var currentVoice = SupertonicVoiceCatalog.DEFAULT_VOICE

    @Volatile private var pauseRequested = false

    @Volatile private var currentLanguage = ""

    override val isReady: Boolean
        get() = engine.isLoaded

    // Supertonic plays each speak() immediately; callers chain via onDone and
    // prefetch() covers the inter-paragraph synthesis gap.
    override val supportsQueueing: Boolean
        get() = false

    // AudioTrack.pause() keeps playback position — a real pause.
    override val supportsPause: Boolean
        get() = true

    override fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        // Engine load (~1-2s) is absorbed here — no separate UI
        // state; the controller hears about failure via onError → fallback.
        scope.launch {
            runCatching { ensureEngineLoaded() }
                .fold(
                    onSuccess = { onReady() },
                    onFailure = { onError(it.message ?: "Supertonic load failed") },
                )
        }
    }

    override fun speak(text: String, utteranceId: String, flush: Boolean, callback: TtsCallback?) {
        val key = normalizedKey(text)

        // DRAIN BEFORE CLEAR: resolve the prefetch entry matching this speak's
        // text BEFORE flush semantics discard anything. Every serial-path
        // speak arrives with flush=true (supportsQueueing=false), so if flush
        // cleared the matching entry the synth-ahead would be dead code —
        // flush discards only entries keyed by OTHER texts.
        val matching: PrefetchEntry?
        synchronized(prefetchLock) {
            matching = prefetches.remove(key)
            if (flush) {
                val stale = prefetches.values.toList()
                prefetches.clear()
                for (entry in stale) {
                    entry.cancel()
                }
            }
        }
        if (flush) {
            playbackJob?.cancel()
            currentSink?.release()
            currentSink = null
        }

        callback?.onStart(utteranceId)

        val prepared = SynthesisPipeline.prepare(
            text,
            EngineProfiles.defaultsFor(SupertonicEngine.ENGINE_NAME),
        )
        if (prepared == null) {
            // Nothing speakable after preprocessing — the empty case.
            matching?.cancel()
            callback?.onDone(utteranceId)
            return
        }

        // Rate rides Supertonic's native `speed` param (duration divide, no
        // pitch shift); the model clamps 0.5–2.0, the residual is handled in
        // [shape] via resampleLinear.
        val nativeSpeed = currentRate.coerceIn(0.5f, 2f)

        playbackJob = scope.launch {
            val sink = try {
                // Load BEFORE building the sink so AudioTrackPcmSink sees the
                // loaded tts.json sample rate, not the catalog constant.
                ensureEngineLoaded()
                sinkSource().also { currentSink = it }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (coroutineContext.isActive) {
                    logcat(LogPriority.ERROR, t) { "SupertonicTtsEngine.speak failed" }
                    callback?.onError(utteranceId, t.message ?: "Supertonic synthesis failed")
                }
                matching?.cancel()
                return@launch
            }
            if (pauseRequested) sink.pause()
            try {
                if (matching != null) {
                    // Drain the synth-ahead buffer first; the channel keeps
                    // delivering the still-in-flight stream afterwards, so the
                    // prefetched paragraph plays with zero synth wait.
                    for (audio in matching.channel) {
                        coroutineContext.ensureActive()
                        sink.write(shape(audio, prepared.hint))
                    }
                } else {
                    streamSource(prepared.engineText, resolveVoice(), nativeSpeed).collect { audio ->
                        coroutineContext.ensureActive()
                        sink.write(shape(audio, prepared.hint))
                    }
                }
                sink.awaitDrain()
                callback?.onDone(utteranceId)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                // A cancelled job can surface a released-sink failure from
                // write(); only report real errors while still active.
                if (coroutineContext.isActive) {
                    logcat(LogPriority.ERROR, t) { "SupertonicTtsEngine.speak failed" }
                    callback?.onError(utteranceId, t.message ?: "Supertonic synthesis failed")
                }
            } finally {
                matching?.cancel()
                sink.release()
                if (currentSink === sink) currentSink = null
            }
        }
    }

    override fun prefetch(text: String) {
        val key = normalizedKey(text)
        val prepared = SynthesisPipeline.prepare(
            text,
            EngineProfiles.defaultsFor(SupertonicEngine.ENGINE_NAME),
        )
            ?: return
        synchronized(prefetchLock) {
            if (prefetches.containsKey(key)) return
            // UNLIMITED: the point of synth-ahead is to run synthesis ahead of
            // playback, and a key only ever holds one paragraph's chunks
            // (bounded by design; invalidated on stop/flush-mismatch).
            val channel = Channel<SynthAudio>(capacity = Channel.UNLIMITED)
            val job = scope.launch {
                try {
                    streamSource(
                        prepared.engineText,
                        resolveVoice(),
                        currentRate.coerceIn(0.5f, 2f),
                    ).collect { channel.send(it) }
                    channel.close()
                } catch (t: Throwable) {
                    channel.close(t)
                }
            }
            prefetches[key] = PrefetchEntry(job, channel)
        }
    }

    override fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        synchronized(prefetchLock) {
            val all = prefetches.values.toList()
            prefetches.clear()
            for (entry in all) {
                entry.cancel()
            }
        }
        currentSink?.release()
        currentSink = null
        pauseRequested = false
    }

    override fun pause() {
        // The flag lands unconditionally — if pause arrives during synthesis
        // (no sink/track yet), the playback job starts the sink paused.
        pauseRequested = true
        currentSink?.pause()
    }

    override fun resume() {
        pauseRequested = false
        currentSink?.resume()
    }

    override fun setSpeechRate(rate: Float) {
        currentRate = rate.coerceIn(0.25f, 4f)
    }

    override fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.25f, 4f)
    }

    override fun setVoice(voiceName: String) {
        currentVoice = voiceName.ifBlank { SupertonicVoiceCatalog.DEFAULT_VOICE }
    }

    override fun setLanguage(languageCode: String) {
        // English-only bundle (<en> tags) — Android TTS covers other
        // languages. Stored so a future multi-lang bundle could pick it
        // up; otherwise a no-op (PB-8, Pocket parity).
        currentLanguage = languageCode
        if (!languageCode.startsWith("en")) {
            logcat(LogPriority.INFO) {
                "SupertonicTtsEngine.setLanguage($languageCode) — English-only, ignored"
            }
        }
    }

    override fun getAvailableVoices(): List<TtsVoice> {
        val engineDir = SupertonicEngineCatalog.engineDir(context.filesDir)
        return SupertonicVoiceCatalog.discoverVoices(engineDir).map { voice ->
            TtsVoice(
                name = voice.name,
                displayName = SupertonicVoiceCatalog.labelFor(voice),
                language = "en",
                isNeural = true,
            )
        }
    }

    override fun shutdown() {
        stop()
        engine.release()
        scope.cancel()
    }

    /**
     * Join in-flight playback + prefetch work. Test hook — production callers
     * never need it (the callback protocol reports completion).
     */
    internal suspend fun awaitIdle() {
        playbackJob?.join()
        val prefetchJobs = synchronized(prefetchLock) { prefetches.values.map { it.job } }
        prefetchJobs.forEach { it.join() }
    }

    // -- per-chunk shaping -----------------------------------------------------

    /**
     * Canonical per-chunk chain: emotion prosody → residual rate → pitch.
     *
     * Rate: [speak] already fed `currentRate.coerceIn(0.5f, 2f)` to the
     * model's native `speed` input (tempo change without pitch shift —
     * strictly better than a post-resample). Only the residual
     * `currentRate / nativeSpeed` — non-1.0 when the slider exceeds the
     * model's 0.5–2.0 clamp — goes through `resampleLinear` (PB-4).
     * `resampleLinear(pcm, ratio)` reads `ratio` input samples per output
     * sample, so `ratio = residual > 1` shortens (speeds up) the chunk.
     * Pitch rides the existing float-domain [PitchResampler].
     */
    private fun shape(audio: SynthAudio, hint: ProsodyHint): ShortArray {
        var pcm = SynthesisPipeline.shapeChunk(audio.pcm, audio.sampleRate, hint)
        val residual = currentRate / currentRate.coerceIn(0.5f, 2f)
        if (abs(residual - 1f) > 0.01f) {
            pcm = ProsodyApplier.resampleLinear(pcm, residual)
        }
        if (abs(currentPitch - 1f) > 0.001f) {
            val floats = FloatArray(pcm.size) { pcm[it].toFloat() }
            val shifted = PitchResampler.resample(floats, currentPitch)
            pcm = ShortArray(shifted.size) { i ->
                shifted[i].toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
        return pcm
    }

    /**
     * Resolve the requested voice to one the installed bundle actually
     * ships. A stale pref (e.g. a Pocket-era `eponine`) would otherwise
     * surface as the engine's unknown-voice failure and kill playback —
     * anything unrecognized falls back to `supertonic:F5` instead (R6).
     * The resolved namespaced name flows to the engine, which strips the
     * prefix for the sid lookup.
     */
    private fun resolveVoice(): String {
        val requested = currentVoice.ifBlank { SupertonicVoiceCatalog.DEFAULT_VOICE }
        val resolved = SupertonicVoiceCatalog.resolveInstalledVoice(requested, context.filesDir)
        if (resolved != requested) {
            logcat(LogPriority.WARN) {
                "Supertonic voice '$requested' not in the installed bundle — using $resolved"
            }
        }
        // Persist the resolution for this engine instance so the file check +
        // warning don't repeat on every paragraph's speak/prefetch call.
        currentVoice = resolved
        return resolved
    }

    private fun normalizedKey(text: String): String = text.trim().replace(WHITESPACE, " ")

    /**
     * A synth-ahead entry: [job] collects the stream into [channel]
     * (unlimited capacity, bounded by one paragraph per key). A matching
     * speak() drains the channel — buffered chunks first, then live tail.
     */
    private class PrefetchEntry(
        val job: Job,
        val channel: Channel<SynthAudio>,
    ) {
        fun cancel() {
            job.cancel()
            channel.cancel()
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
