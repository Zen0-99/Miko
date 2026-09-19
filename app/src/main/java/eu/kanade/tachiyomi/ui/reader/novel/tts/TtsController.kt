package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Controller that manages TTS engine lifecycle, text queuing, and reading session.
 *
 * Supports both Android TTS (system) and Pocket TTS (neural) engines.
 * Handles paragraph-by-paragraph reading with auto-continue.
 *
 * For engines that support it ([TtsEngine.supportsQueueing] — the Android TTS
 * path), upcoming paragraphs are enqueued ahead of playback via QUEUE_ADD in a
 * rolling lookahead window. This lets the TTS service synthesize the next
 * paragraph while the current one is still being played, eliminating the
 * per-paragraph dead-air gap that a strict speak → onDone → speak chain
 * produces (the gap equals the full synthesis latency of the next paragraph).
 * Serial engines ([PocketTtsEngine]) play each speak() call immediately — for
 * them the controller synth-aheads instead: when a paragraph starts speaking,
 * [TtsEngine.prefetch] begins synthesizing the next paragraph into the
 * engine's PCM buffer so the following speak() drains it with zero wait
 * (D-06).
 */
class TtsController(
    private val context: Context,
    private val preferences: eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences,
) {

    private var engine: TtsEngine? = null
    private var paragraphs: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var isPlaying: Boolean = false
    private var onParagraphChanged: ((Int) -> Unit)? = null
    private var onRangeChanged: ((paragraphIndex: Int, start: Int, end: Int) -> Unit)? = null
    private var onComplete: (() -> Unit)? = null
    private var onPlaybackError: ((String) -> Unit)? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    val isReady: Boolean
        get() = engine?.isReady == true

    /**
     * Bumped every time the engine queue is flushed/rebuilt. Utterance IDs
     * encode it so callbacks from flushed utterances can be ignored.
     */
    private var generation: Int = 0

    /** Next paragraph index that has not been sent to the engine yet. */
    private var nextToEnqueue: Int = 0

    /**
     * True when playback was paused through the engine's real pause
     * ([TtsEngine.supportsPause]) — the engine's queue and playback position
     * are preserved and [resume] only needs to unpause it.
     */
    private var enginePaused: Boolean = false

    private var sessionCallback: TtsCallback? = null

    val isSpeaking: Boolean
        get() = isPlaying

    val currentParagraphIndex: Int
        get() = currentIndex

    /**
     * Create and initialize the appropriate TTS engine based on preferences.
     */
    fun initializeEngine(
        onReady: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val requested = preferences.engineType().get()
        // D-02: stale "neural" prefs resolve silently — to Pocket when the
        // engine bundle is installed, else to the Android fallback. This is a
        // string-level mapping; no engine class for "neural" exists anymore.
        val engineType = when (requested) {
            "neural" ->
                if (PocketEngineCatalog.isInstalled(context.filesDir)) "pocket" else "android"
            else -> requested
        }
        val engineImpl: TtsEngine = when (engineType) {
            "pocket" -> PocketTtsEngine(context, preferences)
            else -> AndroidTtsEngine(context)
        }

        engineImpl.setSpeechRate(preferences.speechRate().get())
        engineImpl.setPitch(preferences.pitch().get())

        val voice = preferences.voiceName().get()
        if (voice.isNotEmpty()) {
            engineImpl.setVoice(voice)
        }

        val lang = preferences.language().get()
        if (lang.isNotEmpty()) {
            engineImpl.setLanguage(lang)
        }

        engineImpl.initialize(
            onReady = {
                engine = engineImpl
                onReady()
            },
            onError = { error ->
                // Fall back to Android TTS if the neural engine fails
                // (bundle missing or load failure alike — Q7).
                if (engineType == "pocket") {
                    logcat(LogPriority.WARN) { "Pocket TTS failed: $error — falling back to Android TTS" }
                    val fallback = AndroidTtsEngine(context)
                    fallback.setSpeechRate(preferences.speechRate().get())
                    fallback.setPitch(preferences.pitch().get())
                    fallback.initialize(
                        onReady = {
                            engine = fallback
                            onReady()
                        },
                        onError = { fallbackError ->
                            onError(fallbackError)
                        },
                    )
                } else {
                    onError(error)
                }
            },
        )
    }

    /**
     * Start reading a list of paragraphs from [startIndex].
     */
    fun startReading(
        paragraphs: List<String>,
        startIndex: Int = 0,
        onParagraphChanged: (Int) -> Unit,
        onRangeChanged: ((paragraphIndex: Int, start: Int, end: Int) -> Unit)? = null,
        onPlaybackError: ((String) -> Unit)? = null,
        onComplete: () -> Unit,
    ) {
        this.paragraphs = paragraphs
        this.currentIndex = startIndex
        this.onParagraphChanged = onParagraphChanged
        this.onRangeChanged = onRangeChanged
        this.onComplete = onComplete
        this.onPlaybackError = onPlaybackError
        isPlaying = true
        enginePaused = false
        generation++
        sessionCallback = makeSessionCallback()
        enqueueWindow(startIndex, flushFirst = true)
    }

    private fun makeSessionCallback(): TtsCallback = object : TtsCallback {
        override fun onStart(utteranceId: String) {
            mainHandler.post {
                val index = paragraphIndexFor(utteranceId) ?: return@post
                currentIndex = index
                onParagraphChanged?.invoke(index)
            }
        }

        override fun onDone(utteranceId: String) {
            mainHandler.post {
                val index = paragraphIndexFor(utteranceId) ?: return@post
                if (index >= paragraphs.size - 1 || !preferences.autoContinue().get()) {
                    isPlaying = false
                    onComplete?.invoke()
                } else {
                    enqueueAhead(index)
                }
            }
        }

        override fun onError(utteranceId: String, errorMessage: String) {
            mainHandler.post {
                paragraphIndexFor(utteranceId) ?: return@post
                logcat(LogPriority.ERROR) { "TTS error: $errorMessage" }
                stop()
                onPlaybackError?.invoke(errorMessage)
            }
        }

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            mainHandler.post {
                val index = paragraphIndexFor(utteranceId) ?: return@post
                onRangeChanged?.invoke(index, start, end)
            }
        }
    }

    /**
     * Map an utterance ID back to its paragraph index, or null when the ID
     * belongs to a flushed (stale-generation) queue.
     */
    private fun paragraphIndexFor(utteranceId: String): Int? {
        val parts = utteranceId.split('_')
        if (parts.size != 3 || parts[0] != "tts") return null
        if (parts[1].toIntOrNull() != generation) return null
        return parts[2].toIntOrNull()
    }

    private fun utteranceId(index: Int): String = "tts_${generation}_$index"

    /**
     * Enqueue paragraphs starting at [from]. The first utterance flushes the
     * engine queue when [flushFirst] is set; the rest are appended. On
     * queue-capable engines a lookahead window of upcoming paragraphs is
     * enqueued so synthesis overlaps playback; on serial engines only [from]
     * is spoken, the next paragraph is synth-aheaded via [TtsEngine.prefetch],
     * and the chain continues via onDone.
     */
    private fun enqueueWindow(from: Int, flushFirst: Boolean) {
        val engine = engine ?: return
        if (from >= paragraphs.size) {
            isPlaying = false
            onComplete?.invoke()
            return
        }
        val canQueue = engine.supportsQueueing && preferences.autoContinue().get()
        val last = if (canQueue) {
            minOf(from + QUEUE_LOOKAHEAD, paragraphs.size - 1)
        } else {
            from
        }
        var flush = flushFirst
        for (i in from..last) {
            engine.speak(
                text = paragraphs[i],
                utteranceId = utteranceId(i),
                flush = flush,
                callback = sessionCallback,
            )
            flush = false
        }
        // D-06 synth-ahead: serial engines can't queue, so once the window's
        // first paragraph starts speaking, begin synthesizing the next one
        // into the engine's PCM buffer — its speak() drains it instead of
        // cold-starting synthesis (no default-op harm on queue-capable
        // engines; this branch never runs for them).
        if (!canQueue && preferences.autoContinue().get() && last + 1 < paragraphs.size) {
            engine.prefetch(paragraphs[last + 1])
        }
        nextToEnqueue = last + 1
    }

    /**
     * Keep the engine's queue topped up after [doneIndex] finished. On
     * queue-capable engines the following paragraphs are already queued —
     * only the window tail needs extending. On serial engines this is where
     * the next paragraph actually gets spoken.
     */
    private fun enqueueAhead(doneIndex: Int) {
        val engine = engine ?: return
        if (engine.supportsQueueing) {
            if (nextToEnqueue < paragraphs.size) {
                engine.speak(
                    text = paragraphs[nextToEnqueue],
                    utteranceId = utteranceId(nextToEnqueue),
                    flush = false,
                    callback = sessionCallback,
                )
                nextToEnqueue++
            }
        } else {
            val next = doneIndex + 1
            if (next < paragraphs.size) {
                currentIndex = next
                onParagraphChanged?.invoke(next)
                engine.speak(
                    text = paragraphs[next],
                    utteranceId = utteranceId(next),
                    flush = true,
                    callback = sessionCallback,
                )
                // D-06: keep the pipeline hot — the just-started speak drains
                // its prefetch buffer; start buffering the paragraph after it.
                if (preferences.autoContinue().get() && next + 1 < paragraphs.size) {
                    engine.prefetch(paragraphs[next + 1])
                }
            }
        }
    }

    fun pause() {
        if (!isPlaying) return
        isPlaying = false
        if (engine?.supportsPause == true) {
            // Real pause: engine keeps its queue and playback position, so
            // resume() is instant and continues mid-word. Generation stays —
            // queued utterance callbacks must not be treated as stale.
            enginePaused = true
            engine?.pause()
        } else {
            generation++
            engine?.pause()
        }
    }

    fun resume() {
        if (!isPlaying && currentIndex < paragraphs.size) {
            isPlaying = true
            if (enginePaused) {
                enginePaused = false
                engine?.resume()
            } else {
                generation++
                enqueueWindow(currentIndex, flushFirst = true)
            }
        }
    }

    fun next() {
        if (currentIndex < paragraphs.size - 1) {
            val target = currentIndex + 1
            repositionTo(target)
        }
    }

    fun previous() {
        if (currentIndex > 0) {
            val target = currentIndex - 1
            repositionTo(target)
        }
    }

    private fun repositionTo(target: Int) {
        currentIndex = target
        onParagraphChanged?.invoke(target)
        if (enginePaused) {
            // Drop the engine's stale queue — resume() will rebuild from
            // the new position instead of replaying the old utterances.
            enginePaused = false
            generation++
            engine?.stop()
        }
        if (isPlaying) {
            generation++
            enqueueWindow(target, flushFirst = true)
        }
    }

    fun stop() {
        isPlaying = false
        enginePaused = false
        generation++
        engine?.stop()
    }

    fun setSpeechRate(rate: Float) {
        preferences.speechRate().set(rate)
        engine?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        preferences.pitch().set(pitch)
        engine?.setPitch(pitch)
    }

    fun getAvailableVoices(): List<TtsVoice> {
        return engine?.getAvailableVoices() ?: emptyList()
    }

    fun setVoice(voiceName: String) {
        preferences.voiceName().set(voiceName)
        engine?.setVoice(voiceName)
    }

    fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
    }

    companion object {
        /** Paragraphs queued ahead of the currently speaking one. */
        private const val QUEUE_LOOKAHEAD = 6
    }
}
