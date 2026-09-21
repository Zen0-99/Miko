package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import eu.kanade.tachiyomi.ui.reader.novel.tts.render.ChapterAudioManifest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Pure-JVM paragraph↔millisecond math for rendered-audio read-along (D-19).
 *
 * Wraps the manifest's `paragraphOffsetsMs`/`durationMs`: [paragraphIndexAt]
 * maps a playback position to the paragraph it falls inside (binary-search
 * floor, clamped to the last index so positions past EOF resolve to the final
 * paragraph), and [isParagraphFinished] reports when a position has crossed
 * the paragraph's end boundary — the next paragraph's start offset, or
 * `durationMs` for the last one. Both are monotonic in position, which is
 * what lets the engine's ~100ms poller fire `onDone` exactly once.
 *
 * [indexFromUtterance] mirrors `TtsController`'s `tts_{gen}_{idx}` utterance
 * contract — it extracts the paragraph index tail; generation matching stays
 * the controller's job (`paragraphIndexFor`, TtsController).
 */
internal class RenderedAudioTracker(
    val offsetsMs: List<Long>,
    val durationMs: Long,
) {

    /** Paragraph index containing [positionMs] — floor over [offsetsMs]. */
    fun paragraphIndexAt(positionMs: Long): Int {
        if (offsetsMs.isEmpty()) return 0
        val result = offsetsMs.binarySearch(positionMs)
        val floor = if (result >= 0) result else -result - 2
        return floor.coerceIn(0, offsetsMs.size - 1)
    }

    /**
     * True once [positionMs] has reached paragraph [idx]'s end — the next
     * offset, or [durationMs] for the last paragraph.
     */
    fun isParagraphFinished(idx: Int, positionMs: Long): Boolean {
        val nextOffset = offsetsMs.getOrNull(idx + 1) ?: durationMs
        return positionMs >= nextOffset
    }

    /** Paragraph index encoded in a `tts_{gen}_{idx}` utterance id, else null. */
    fun indexFromUtterance(utteranceId: String): Int? {
        val parts = utteranceId.split('_')
        if (parts.size != 3 || parts[0] != "tts") return null
        return parts[2].toIntOrNull()
    }
}

/**
 * [TtsEngine] adapter over ExoPlayer for chapters with rendered audio (D-19).
 *
 * The manifest is already validated before this engine is constructed, so
 * [speak] ignores `text` entirely — it parses the paragraph index out of the
 * `tts_{gen}_{idx}` utterance id, seeks to `paragraphOffsetsMs[idx]`, plays,
 * and a ~100ms position poller fires [TtsCallback.onDone] when
 * `currentPosition` crosses the paragraph's end boundary. The poller stops
 * there — the controller issues the next [speak] itself, so next/previous/
 * pause/reposition semantics come free and are identical to live synthesis.
 * `Player.STATE_ENDED` flushes a pending `onDone` so the controller's
 * onComplete chain still runs on the last paragraph.
 *
 * Serial engine: [supportsQueueing] is false and [supportsPause] is true —
 * same shape as [PocketTtsEngine]. [prefetch] stays the inherited no-op
 * (file playback has zero synthesis latency). [setSpeechRate]/[setPitch] map
 * to `setPlaybackParameters`, a relative multiplier on top of the rate/pitch
 * baked into the PCM at render time (SD-1). [setVoice]/[setLanguage] are
 * no-ops; [getAvailableVoices] reports the manifest's recorded voice.
 *
 * Test seams: [tracker], [playerFactory], and [dispatcher] are injectable so
 * the offset math and poller contract run on the bare JVM.
 */
class RenderedAudioEngine internal constructor(
    private val context: Context,
    private val audioUri: Uri,
    private val manifest: ChapterAudioManifest,
    internal val tracker: RenderedAudioTracker = RenderedAudioTracker(
        manifest.paragraphOffsetsMs,
        manifest.durationMs,
    ),
    internal val playerFactory: (Context) -> ExoPlayer = { ExoPlayer.Builder(it).build() },
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : TtsEngine {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var player: ExoPlayer? = null

    @Volatile private var ready = false
    @Volatile private var currentIdx = -1
    @Volatile private var currentUtteranceId: String? = null
    @Volatile private var currentCallback: TtsCallback? = null
    private var pollerJob: Job? = null
    private var initErrorHandler: ((String) -> Unit)? = null

    private var currentRate = 1.0f
    private var currentPitch = 1.0f

    override val isReady: Boolean
        get() = ready

    // File playback is serial: the controller chains utterances via onDone,
    // and prefetch() is a no-op — there is nothing to synthesize ahead.
    override val supportsQueueing: Boolean
        get() = false

    // ExoPlayer pause keeps playback position — a real pause.
    override val supportsPause: Boolean
        get() = true

    override fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        if (ready) {
            onReady()
            return
        }
        initErrorHandler = onError
        val exoPlayer = playerFactory(context)
        player = exoPlayer
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            // READY re-fires after every seek/buffer cycle —
                            // without this guard each speak()→seekTo() round-
                            // trips through the caller's onReady→beginReading
                            // and loops forever (~40 audio-focus requests/s).
                            if (!ready) {
                                ready = true
                                onReady()
                            }
                        }
                        // EOF: fire the pending onDone so the controller's
                        // onComplete chain runs for the last paragraph.
                        Player.STATE_ENDED -> finishCurrentUtterance()
                        else -> {}
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    onPlayerFailure(error.message ?: "Rendered audio playback failed")
                }
            },
        )
        exoPlayer.setMediaItem(MediaItem.fromUri(audioUri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = false
    }

    override fun speak(text: String, utteranceId: String, flush: Boolean, callback: TtsCallback?) {
        val exoPlayer = player
        if (exoPlayer == null || !ready) {
            callback?.onError(utteranceId, "Rendered audio engine not ready")
            return
        }
        val idx = tracker.indexFromUtterance(utteranceId)
        if (idx == null || idx !in manifest.paragraphOffsetsMs.indices) {
            callback?.onError(utteranceId, "Rendered audio: no paragraph for $utteranceId")
            return
        }
        pollerJob?.cancel()
        currentIdx = idx
        currentUtteranceId = utteranceId
        currentCallback = callback
        exoPlayer.seekTo(manifest.paragraphOffsetsMs[idx])
        exoPlayer.play()
        callback?.onStart(utteranceId)
        startPoller(exoPlayer)
    }

    override fun pause() {
        player?.pause()
    }

    override fun resume() {
        player?.play()
    }

    override fun stop() {
        pollerJob?.cancel()
        pollerJob = null
        currentCallback = null
        currentUtteranceId = null
        player?.let {
            it.pause()
            it.seekTo(0)
        }
    }

    override fun setSpeechRate(rate: Float) {
        currentRate = rate
        applyPlaybackParameters()
    }

    override fun setPitch(pitch: Float) {
        currentPitch = pitch
        applyPlaybackParameters()
    }

    override fun setVoice(voiceName: String) {
        // Voice is baked into the rendered file — nothing to switch.
    }

    override fun setLanguage(languageCode: String) {
        // Rendered audio is fixed content — language is a no-op.
    }

    override fun getAvailableVoices(): List<TtsVoice> {
        return listOf(TtsVoice(manifest.voice, manifest.voice, "en", isNeural = true))
    }

    override fun shutdown() {
        pollerJob?.cancel()
        scope.cancel()
        player?.release()
        player = null
        ready = false
    }

    /**
     * Paragraph under the playhead right now — lets the highlight stay glued
     * to the file even if speak/onDone cadence drifts (e.g. after a seek).
     */
    fun currentParagraphIndex(): Int =
        tracker.paragraphIndexAt(player?.currentPosition ?: 0L)

    private fun startPoller(exoPlayer: ExoPlayer) {
        pollerJob?.cancel()
        pollerJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (tracker.isParagraphFinished(currentIdx, exoPlayer.currentPosition)) {
                    finishCurrentUtterance()
                    break
                }
            }
        }
    }

    /**
     * Fire `onDone` for the in-flight utterance exactly once — callback and
     * id are cleared before invoking so a STATE_ENDED racing the poller
     * can't double-fire.
     */
    private fun finishCurrentUtterance() {
        pollerJob?.cancel()
        pollerJob = null
        val callback = currentCallback
        val utteranceId = currentUtteranceId
        currentCallback = null
        currentUtteranceId = null
        if (callback != null && utteranceId != null) {
            callback.onDone(utteranceId)
        }
    }

    private fun onPlayerFailure(message: String) {
        if (!ready) {
            initErrorHandler?.invoke(message)
            return
        }
        pollerJob?.cancel()
        val callback = currentCallback
        val utteranceId = currentUtteranceId
        currentCallback = null
        currentUtteranceId = null
        if (callback != null && utteranceId != null) {
            callback.onError(utteranceId, message)
        } else {
            logcat(LogPriority.ERROR) { "RenderedAudioEngine: $message" }
        }
    }

    private fun applyPlaybackParameters() {
        // PlaybackParameters multiply the rate/pitch baked into the PCM at
        // render time (SD-1) — never baked again.
        player?.setPlaybackParameters(PlaybackParameters(currentRate, currentPitch))
    }

    private companion object {
        const val POLL_INTERVAL_MS = 100L
    }
}
