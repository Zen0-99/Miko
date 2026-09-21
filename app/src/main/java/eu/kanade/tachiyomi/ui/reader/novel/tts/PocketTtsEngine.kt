package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.audio.SynthesisPipeline
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.PocketEngine
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.SynthAudio
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.EngineProfiles
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.ProsodyApplier
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.ProsodyHint
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * [TtsEngine] adapter over the ported Pocket engine (D-05/D-06).
 *
 * `speak()` collects [PocketEngine.synthesizeStream] and writes each emitted
 * chunk to an `AudioTrack` `MODE_STREAM` sink as it arrives — never the
 * synth-then-play pattern that produced the paragraph-boundary silence this
 * phase exists to fix. Per chunk the canonical chain runs:
 * `shapeChunk` (emotion prosody) → `resampleLinear` (speech rate — Pocket's
 * stream ignores its `speed` arg) → [PitchResampler] (float domain) →
 * PCM16 sink write. [SynthesisPipeline.prepare] runs once per [speak] text
 * (emoji detect → rules → strip).
 *
 * Synth-ahead (D-06): [prefetch] starts collecting the stream for upcoming
 * text into a channel-backed PCM buffer. A later [speak] of the same text
 * drains the buffered chunks first, then keeps receiving the still-in-flight
 * stream — the prefetch is consumed exactly once. Entries are keyed by
 * [normalizedKey] and invalidated by [stop] or by a flushing [speak] for a
 * different text (a flushing speak for the SAME text still drains its entry —
 * every serial-path speak carries `flush=true`, so clearing on match would
 * make synth-ahead dead code).
 *
 * [supportsQueueing] is false (serial onDone-chained playback, same as the
 * previous neural path); [supportsPause] is true (AudioTrack pause/play).
 * `onRangeStart` is never emitted — parity with the previous neural path;
 * word highlight stays in estimated mode.
 *
 * Test seams (research pattern 9 — fun-interface testability): the internal
 * constructor accepts a [streamFactory] and [pcmSinkFactory] so JVM tests can
 * drive the prefetch/drain contract with fake flows and a recording sink, no
 * `Context`/ORT/`AudioTrack` needed. [dispatcher] is the playback/prefetch
 * scope's dispatcher — inject a test dispatcher to step work deterministically.
 */
class PocketTtsEngine internal constructor(
    private val context: Context,
    preferences: NovelTtsPreferences,
    streamFactory: ((text: String, voiceId: String) -> Flow<SynthAudio>)? = null,
    pcmSinkFactory: (() -> PcmSink)? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TtsEngine {

    private val engine = PocketEngine(context, preferences)

    /** Stream source — real engine stream in production, fake flow in tests. */
    private val streamSource: (text: String, voiceId: String) -> Flow<SynthAudio> =
        streamFactory ?: { text, voice -> engine.synthesizeStream(text, voice, 1.0f, null) }

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
    @Volatile private var currentVoice = PocketVoiceCatalog.DEFAULT_VOICE
    @Volatile private var pauseRequested = false
    @Volatile private var currentLanguage = ""

    override val isReady: Boolean
        get() = engine.isLoaded

    // Pocket plays each speak() immediately; callers chain via onDone and
    // prefetch() covers the inter-paragraph synthesis gap.
    override val supportsQueueing: Boolean
        get() = false

    // AudioTrack.pause() keeps playback position — a real pause.
    override val supportsPause: Boolean
        get() = true

    override fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        // Engine load (~1-2s incl. warmup) is absorbed here — no separate UI
        // state; the controller hears about failure via onError → fallback.
        scope.launch {
            runCatching { engine.ensureLoaded() }
                .fold(
                    onSuccess = { onReady() },
                    onFailure = { onError(it.message ?: "Pocket load failed") },
                )
        }
    }

    override fun speak(text: String, utteranceId: String, flush: Boolean, callback: TtsCallback?) {
        val key = normalizedKey(text)

        // DRAIN BEFORE CLEAR: resolve the prefetch entry matching this speak's
        // text BEFORE flush semantics discard anything. Every serial-path
        // speak arrives with flush=true (supportsQueueing=false), so if flush
        // cleared the matching entry the D-06 synth-ahead would be dead code —
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

        val prepared = SynthesisPipeline.prepare(text, EngineProfiles.defaultsFor(PocketEngine.ENGINE_NAME))
        if (prepared == null) {
            // Nothing speakable after preprocessing — the empty case.
            matching?.cancel()
            callback?.onDone(utteranceId)
            return
        }

        playbackJob = scope.launch {
            val sink = sinkSource()
            currentSink = sink
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
                    streamSource(prepared.engineText, resolveVoice()).collect { audio ->
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
                    logcat(LogPriority.ERROR, t) { "PocketTtsEngine.speak failed" }
                    callback?.onError(utteranceId, t.message ?: "Pocket synthesis failed")
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
        val prepared = SynthesisPipeline.prepare(text, EngineProfiles.defaultsFor(PocketEngine.ENGINE_NAME))
            ?: return
        synchronized(prefetchLock) {
            if (prefetches.containsKey(key)) return
            // UNLIMITED: the point of synth-ahead is to run synthesis ahead of
            // playback, and a key only ever holds one paragraph's chunks
            // (bounded by design; invalidated on stop/flush-mismatch).
            val channel = Channel<SynthAudio>(capacity = Channel.UNLIMITED)
            val job = scope.launch {
                try {
                    streamSource(prepared.engineText, resolveVoice()).collect { channel.send(it) }
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
        currentVoice = voiceName.ifBlank { PocketVoiceCatalog.DEFAULT_VOICE }
    }

    override fun setLanguage(languageCode: String) {
        // English-only engine — Android TTS covers other languages. Stored so
        // a future multi-lang bundle could pick it up; otherwise a no-op.
        currentLanguage = languageCode
        logcat(LogPriority.INFO) { "PocketTtsEngine.setLanguage($languageCode) — English-only, ignored" }
    }

    override fun getAvailableVoices(): List<TtsVoice> {
        val engineDir = PocketEngineCatalog.engineDir(context.filesDir)
        return PocketVoiceCatalog.discoverVoices(engineDir).map { voice ->
            TtsVoice(
                name = voice.name,
                displayName = voice.displayName,
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
     * Canonical per-chunk chain: emotion prosody → speech rate → pitch.
     *
     * Rate: Pocket's stream ignores `speed`, so the rate slider applies as a
     * post-shift resample. `resampleLinear(pcm, ratio)` reads `ratio` input
     * samples per output sample — ratio = rate shortens (speeds up) the
     * chunk, which is what the slider means. (The plan text's `1f / rate`
     * form would invert the control — 2.0 would double the length; the
     * plan's own behaviour spec, "rate 2.0 → ~half the PCM length", is what
     * this implements.) Pitch rides the existing float-domain
     * [PitchResampler]; both shifts change pitch with tempo, an accepted
     * tradeoff (research Q1).
     */
    private fun shape(audio: SynthAudio, hint: ProsodyHint): ShortArray {
        var pcm = SynthesisPipeline.shapeChunk(audio.pcm, audio.sampleRate, hint)
        if (abs(currentRate - 1f) > 0.01f) {
            pcm = ProsodyApplier.resampleLinear(pcm, currentRate)
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
     * ships. A stale pref (e.g. a sherpa-era `vits-piper-*` name, or a
     * voice absent from the active bundle) would otherwise surface as
     * PocketEngine's "Voice WAV missing" check and kill playback — anything
     * unrecognized falls back to the default voice instead. `cloned-*`
     * voices live under `cloned_voices/`, not `voices/`, and pass through
     * for the engine's own store to resolve.
     */
    private fun resolveVoice(): String {
        val requested = currentVoice.ifBlank { PocketVoiceCatalog.DEFAULT_VOICE }
        val resolved = PocketVoiceCatalog.resolveInstalledVoice(requested, context.filesDir)
        if (resolved != requested) {
            logcat(LogPriority.WARN) {
                "Pocket voice '$requested' not in the installed bundle — using $resolved"
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

/**
 * PCM output sink shared by [PocketTtsEngine] and [SupertonicTtsEngine].
 * `fun interface` so tests inject a recording fake via SAM conversion or a
 * tiny class; production uses [AudioTrackPcmSink]. [write] is the sole
 * abstract method — the lifecycle hooks have no-op defaults.
 */
internal fun interface PcmSink {
    /** Write one chunk of mono PCM16 samples (blocks until consumed). */
    suspend fun write(chunk: ShortArray)

    /** Pause playback, preserving position. */
    fun pause() {}

    /** Resume a paused sink. */
    fun resume() {}

    /** Wait until everything written has been played out. */
    suspend fun awaitDrain() {}

    /** Stop playback and free the underlying track. */
    fun release() {}
}

/**
 * [PcmSink] over an `AudioTrack` in `MODE_STREAM` — the same construction the
 * previous neural engine used (USAGE_MEDIA + CONTENT_TYPE_SPEECH + PCM_16BIT +
 * mono + `maxOf(minBuf, 8192)` buffer + LOW_LATENCY), adapted for streaming:
 * the track is created lazily on the first [write], [write] blocks while the
 * HAL buffer is full (that's the playback pacing), and [awaitDrain] polls
 * `playbackHeadPosition` to drain like the old playPcm loop. [release]
 * unblocks a mid-flight [write] so a cancelled playback job unwinds promptly.
 */
internal class AudioTrackPcmSink(private val sampleRate: Int) : PcmSink {

    private var track: AudioTrack? = null
    private var samplesWritten = 0
    @Volatile private var paused = false

    private fun ensureTrack(): AudioTrack {
        track?.let { return it }
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        // MODE_STREAM (not MODE_STATIC — it glitches on long utterances).
        // Deep buffer: ~5s of PCM. The old maxOf(minBuf, 8192) held ~170ms,
        // so any inter-chunk synthesis stall longer than that starved the
        // track (underrun → Android disabled+restarted it). With Pocket
        // producing at roughly realtime, a deep buffer is what lets
        // prefetched audio actually bank ahead of the playback head.
        // Pause stays instant — pause() halts output regardless of how
        // much is buffered.
        val bufSize = maxOf(minBuf, sampleRate * 2 * STREAM_BUFFER_SECONDS)
        val newTrack = AudioTrack.Builder()
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
            .setTransferMode(AudioTrack.MODE_STREAM)
            // No LOW_LATENCY flag — it pins a small HAL-buffer fast path,
            // defeating the deep buffer above. TTS playback is not
            // latency-sensitive.
            .build()
        // If pause() landed during synthesis, start already-paused —
        // MODE_STREAM buffers writes before play(), so resume() picks up
        // seamlessly.
        if (paused) {
            newTrack.pause()
        } else {
            newTrack.play()
        }
        track = newTrack
        return newTrack
    }

    override suspend fun write(chunk: ShortArray) {
        if (chunk.isEmpty()) return
        val t = ensureTrack()
        var offset = 0
        var stalls = 0
        while (offset < chunk.size) {
            currentCoroutineContext().ensureActive()
            // A paused MODE_STREAM track doesn't drain its HAL buffer, and a
            // write blocked in native when pause() lands surfaces as a 0
            // return — previously thrown as fatal, which aborted the whole
            // utterance and forced a full re-synth on resume. Wait the pause
            // out instead; cancellation still unwinds promptly via
            // ensureActive + release() unblocking the native write.
            while (paused) {
                delay(PAUSE_POLL_MS)
                currentCoroutineContext().ensureActive()
            }
            val n = t.write(chunk, offset, chunk.size - offset)
            when {
                n > 0 -> {
                    offset += n
                    stalls = 0
                }
                n == 0 -> {
                    // Transient: pause delivered mid-write, or the track's
                    // buffer momentarily reported no space. Bounded retry —
                    // ~1s of continuous stalls is a real failure.
                    if (++stalls > MAX_WRITE_STALLS) {
                        throw IllegalStateException("AudioTrack.write stalled (paused=$paused)")
                    }
                    delay(WRITE_STALL_RETRY_MS)
                }
                else -> throw IllegalStateException("AudioTrack.write returned $n")
            }
        }
        samplesWritten += chunk.size
    }

    override fun pause() {
        paused = true
        val t = track
        if (t != null && t.playState == AudioTrack.PLAYSTATE_PLAYING) {
            try {
                t.pause()
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "AudioTrackPcmSink.pause failed" }
            }
        }
    }

    override fun resume() {
        paused = false
        val t = track
        if (t != null && t.playState == AudioTrack.PLAYSTATE_PAUSED) {
            try {
                t.play()
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "AudioTrackPcmSink.resume failed" }
            }
        }
    }

    override suspend fun awaitDrain() {
        val t = track ?: return
        while (
            currentCoroutineContext().isActive &&
            t.playbackHeadPosition < samplesWritten
        ) {
            // Keep waiting while PAUSED — draining resumes on resume(). The
            // old playState==PLAYING condition exited early on pause, fired
            // onDone, and let the next flush release the track with buffered
            // audio still unplayed.
            if (t.playState == AudioTrack.PLAYSTATE_STOPPED) break
            delay(10)
        }
    }

    override fun release() {
        val t = track ?: return
        try {
            if (t.playState != AudioTrack.PLAYSTATE_STOPPED) {
                t.stop()
            }
        } catch (_: Throwable) {
        }
        try {
            t.release()
        } catch (_: Throwable) {
        }
        track = null
    }

    private companion object {
        const val PAUSE_POLL_MS = 20L
        const val WRITE_STALL_RETRY_MS = 20L
        const val MAX_WRITE_STALLS = 50
        const val STREAM_BUFFER_SECONDS = 5
    }
}
