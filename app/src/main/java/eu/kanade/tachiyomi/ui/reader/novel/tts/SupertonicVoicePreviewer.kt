package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.supertonic.SupertonicEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * On-demand voice previews for the Supertonic picker — the user hears a
 * voice before committing to it. Synthesizes [SAMPLE_TEXT] through the
 * installed bundle (the real engine path, so previews match what
 * read-along produces) and streams the PCM through [AudioTrackPcmSink].
 *
 * The synthesized clip is cached per style+rate for the screen model's
 * lifetime (~350 KB each), so a second tap on the same voice is instant.
 * The engine instance is kept loaded once created — settings previews
 * cluster together, and a ~1.6 s reload per tap would feel broken.
 *
 * Synthesis shares the process-wide `synthLock` inside
 * [SupertonicEngine.synthesizeStream], so a preview can't interleave with
 * an active read-along utterance — it queues behind it.
 */
internal class SupertonicVoicePreviewer(
    private val context: Context,
    private val preferences: NovelTtsPreferences,
) {
    private var engine: SupertonicEngine? = null
    private var sink: AudioTrackPcmSink? = null
    private val cache = HashMap<String, ShortArray>()

    /**
     * Synthesize (or reuse the cached clip for) [styleName] and play it to
     * completion. [onPlaybackStart] fires once synthesis is done and PCM is
     * about to flow — lets callers distinguish "loading" from "audible".
     * Suspends until playback ends; cancellation stops audio promptly
     * (release unblocks a mid-flight sink write).
     */
    suspend fun play(styleName: String, onPlaybackStart: () -> Unit = {}) {
        logcat(LogPriority.INFO) { "VoicePreview: play($styleName)" }
        val eng = engine ?: SupertonicEngine(context, preferences).also { engine = it }
        // Cache key includes the rate — a clip synthesized at 1.0x must not
        // be replayed after the user moves the speech-rate slider.
        val speed = preferences.speechRate().get().coerceIn(0.5f, 3.0f)
        val cacheKey = "$styleName@$speed"
        val pcm = cache[cacheKey] ?: run {
            logcat(LogPriority.INFO) { "VoicePreview: synthesizing sample for $styleName" }
            val chunks = ArrayList<ShortArray>()
            eng.synthesizeStream(
                SAMPLE_TEXT,
                "${SupertonicVoiceCatalog.VOICE_PREFIX}$styleName",
                preferences.supertonicNumSteps().get(),
                speed,
            ).collect { chunks.add(it.pcm) }
            val total = chunks.sumOf { it.size }
            ShortArray(total).also { merged ->
                var offset = 0
                for (chunk in chunks) {
                    chunk.copyInto(merged, offset)
                    offset += chunk.size
                }
                cache[cacheKey] = merged
            }
        }
        currentCoroutineContext().ensureActive()
        val pcmSink = AudioTrackPcmSink(eng.sampleRate).also { sink = it }
        logcat(LogPriority.INFO) { "VoicePreview: playing ${pcm.size} samples for $styleName" }
        onPlaybackStart()
        try {
            pcmSink.write(pcm)
            pcmSink.awaitDrain()
        } finally {
            pcmSink.release()
            sink = null
        }
    }

    /** Stop any in-flight preview — releases the track so write() unblocks. */
    fun stop() {
        sink?.release()
        sink = null
    }

    /** Stop playback and unload the engine/sessions entirely. */
    fun release() {
        stop()
        try {
            engine?.release()
        } catch (t: Throwable) {
            logcat(LogPriority.WARN, t) { "SupertonicVoicePreviewer release failed" }
        }
        engine = null
        cache.clear()
    }

    private companion object {
        /** User-requested sample — a pangram exercises most phonemes. */
        const val SAMPLE_TEXT =
            "The quick brown fox jumps over the lazy dog."
    }
}
