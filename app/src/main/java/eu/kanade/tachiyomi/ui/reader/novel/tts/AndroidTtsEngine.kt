package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * TTS engine backed by Android's built-in [TextToSpeech].
 *
 * This is the default engine and works on all Android devices without
 * additional dependencies. Quality depends on the system TTS engine
 * (e.g. Google TTS, Marmalade, Samsung TTS).
 *
 * Two playback modes:
 *
 * - **Buffered (default):** each utterance is pre-rendered with
 *   [TextToSpeech.synthesizeToFile] into a temp WAV file and played back
 *   through our own [AudioTrack]. Queued utterances are synthesized ahead of
 *   playback, so inter-paragraph latency is fully hidden, and pause/resume
 *   are real — the track position is preserved instead of restarting the
 *   paragraph. Sentence-level progress is estimated from the playback head
 *   and reported via [TtsCallback.onRangeStart].
 * - **Queue (fallback):** if the system engine rejects `synthesizeToFile`,
 *   speak() falls back to [TextToSpeech.speak] with QUEUE_ADD. Pause then
 *   degrades to stop/restart.
 */
class AndroidTtsEngine(
    private val context: Context,
) : TtsEngine {

    private var tts: TextToSpeech? = null
    private var callback: TtsCallback? = null
    private var ready = false
    private var pendingRate: Float = 1.0f
    private var pendingPitch: Float = 1.0f
    private var pendingVoice: String? = null
    private var pendingLanguage: String? = null
    override val isReady: Boolean
        get() = ready && tts != null

    override val supportsQueueing: Boolean
        get() = true

    /** Real pause/resume only works in buffered mode (own AudioTrack). */
    override val supportsPause: Boolean
        get() = fileMode

    // ------------------------------------------------------------------
    // Buffered playback pipeline
    // ------------------------------------------------------------------

    private data class QueuedUtterance(
        val utteranceId: String,
        val text: String,
        val file: File,
    ) {
        val ready = CountDownLatch(1)
        @Volatile var failed: Boolean = false

        /** Sentence char ranges within [text], computed lazily on play. */
        val sentences: List<IntRange> by lazy { sentenceRanges(text) }
    }

    /** Items awaiting playback, in order. */
    private val utteranceQueue = ConcurrentLinkedQueue<QueuedUtterance>()

    /** Items currently being synthesized to file, keyed by utteranceId. */
    private val pendingSynthesis = ConcurrentHashMap<String, QueuedUtterance>()

    private val playerLock = ReentrantLock()
    private val queueSignal = playerLock.newCondition()

    @Volatile private var playerThread: Thread? = null
    @Volatile private var playerStopped = false
    @Volatile private var playerPaused = false
    @Volatile private var currentTrack: AudioTrack? = null
    @Volatile private var fileMode = true
    @Volatile private var activeUtterance: QueuedUtterance? = null

    private val cacheDir: File
        get() = File(context.cacheDir, "tts_pcm").apply { mkdirs() }

    override fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(pendingRate)
                tts?.setPitch(pendingPitch)
                pendingVoice?.let { setVoice(it) }
                pendingLanguage?.let { setLanguage(it) }
                setupProgressListener()
                onReady()
            } else {
                logcat(LogPriority.ERROR) { "Android TTS init failed: status=$status" }
                onError("TTS initialization failed (status=$status)")
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                // In file mode this fires when synthesis starts — playback
                // onStart is emitted by the player thread instead.
                if (!fileMode) callback?.onStart(utteranceId)
            }

            override fun onDone(utteranceId: String) {
                val item = pendingSynthesis.remove(utteranceId)
                if (item != null) {
                    item.ready.countDown()
                } else if (!fileMode) {
                    callback?.onDone(utteranceId)
                }
            }

            override fun onError(utteranceId: String?) {
                handleEngineError(utteranceId, "TTS error")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?, errorCode: Int) {
                handleEngineError(utteranceId, "TTS error code=$errorCode")
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                // In file mode range callbacks fire at synthesis time, which
                // races ahead of playback — the player reports its own ranges.
                if (!fileMode) callback?.onRangeStart(utteranceId ?: "", start, end, frame)
            }
        })
    }

    private fun handleEngineError(utteranceId: String?, message: String) {
        val item = utteranceId?.let { pendingSynthesis.remove(it) }
        if (item != null) {
            item.failed = true
            item.ready.countDown()
        } else if (!fileMode) {
            callback?.onError(utteranceId ?: "", message)
        }
    }

    override fun speak(text: String, utteranceId: String, flush: Boolean, callback: TtsCallback?) {
        this.callback = callback
        val engine = tts ?: return

        if (fileMode) {
            if (flush) resetPipeline()
            val item = QueuedUtterance(
                utteranceId = utteranceId,
                text = text,
                file = File.createTempFile("tts_", ".wav", cacheDir),
            )
            pendingSynthesis[utteranceId] = item
            utteranceQueue.add(item)
            ensurePlayerRunning()
            val result = engine.synthesizeToFile(text, Bundle(), item.file, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                // Engine doesn't support file synthesis — drop to speak mode.
                logcat(LogPriority.WARN) { "synthesizeToFile unsupported (code=$result), falling back to speak()" }
                item.ready.countDown()
                resetPipeline()
                fileMode = false
                engine.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, utteranceId)
            }
        } else {
            val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(text, mode, null, utteranceId)
        }
    }

    override fun stop() {
        tts?.stop()
        if (fileMode) resetPipeline()
    }

    override fun pause() {
        if (fileMode) {
            playerPaused = true
            currentTrack?.pause()
        } else {
            // Android speak() mode has no pause — caller rebuilds on resume.
            tts?.stop()
        }
    }

    override fun resume() {
        if (fileMode) {
            playerPaused = false
            currentTrack?.play()
            playerLock.withLock { queueSignal.signalAll() }
        }
    }

    override fun setSpeechRate(rate: Float) {
        pendingRate = rate
        if (ready) tts?.setSpeechRate(rate)
    }

    override fun setPitch(pitch: Float) {
        pendingPitch = pitch
        if (ready) tts?.setPitch(pitch)
    }

    override fun setVoice(voiceName: String) {
        pendingVoice = voiceName
        if (!ready) return
        val voices = tts?.voices ?: return
        val match = voices.find { it.name == voiceName }
        if (match != null) {
            tts?.voice = match
        }
    }

    override fun setLanguage(languageCode: String) {
        pendingLanguage = languageCode
        if (ready) {
            tts?.language = Locale(languageCode)
        }
    }

    override fun getAvailableVoices(): List<TtsVoice> {
        if (!ready) return emptyList()
        val voices = tts?.voices ?: return emptyList()
        return voices
            .filter { it.quality != Voice.QUALITY_VERY_LOW }
            .sortedByDescending { it.quality }
            .map { voice ->
                TtsVoice(
                    name = voice.name,
                    displayName = voice.locale.displayName,
                    language = voice.locale.language,
                    isNeural = false,
                )
            }
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        resetPipeline()
        cacheDir.deleteRecursively()
    }

    // ------------------------------------------------------------------
    // Pipeline internals
    // ------------------------------------------------------------------

    private fun ensurePlayerRunning() {
        if (playerThread?.isAlive == true) return
        playerStopped = false
        playerThread = Thread({ playerLoop() }, "TtsFilePlayer").apply {
            isDaemon = true
        }
        playerThread?.start()
        playerLock.withLock { queueSignal.signalAll() }
    }

    /**
     * Stop the player, drop every queued/pending utterance, and delete the
     * temp files. The next [speak] with a fresh queue starts a new pipeline.
     */
    private fun resetPipeline() {
        tts?.stop()
        playerLock.withLock {
            playerStopped = true
            val previousThread = playerThread
            playerThread = null
            previousThread?.interrupt()
            queueSignal.signalAll()
            releaseTrack()
            activeUtterance?.file?.delete()
            activeUtterance = null
            utteranceQueue.forEach { it.file.delete() }
            utteranceQueue.clear()
            pendingSynthesis.values.forEach {
                it.ready.countDown()
                it.file.delete()
            }
            pendingSynthesis.clear()
            playerPaused = false
        }
    }

    private fun isPlayerCancelled(): Boolean = playerStopped || Thread.currentThread() !== playerThread

    private fun releaseTrack() {
        val track = currentTrack
        currentTrack = null
        if (track != null) {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                track.release()
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "releaseTrack failed" }
            }
        }
    }

    private fun playerLoop() {
        try {
            playerLoopInner()
        } catch (t: Throwable) {
            if (!isPlayerCancelled()) {
                logcat(LogPriority.ERROR, t) { "TTS player loop crashed" }
                activeUtterance?.let { callback?.onError(it.utteranceId, "Audio playback failed: ${t.message}") }
            }
        } finally {
            playerLock.withLock {
                if (Thread.currentThread() === playerThread) releaseTrack()
            }
        }
    }

    private fun playerLoopInner() {
        // Frames written into the current AudioTrack across all items that
        // share its format — used to map playbackHeadPosition to utterance
        // boundaries for onStart/onDone/onRangeStart timing.
        var framesWritten = 0L
        var trackSampleRate = 0
        var trackChannels = 0
        var trackFrameBytes = 0

        while (!isPlayerCancelled()) {
            val item = playerLock.withLock {
                var head = utteranceQueue.peek()
                while (head == null && !isPlayerCancelled()) {
                    queueSignal.await(200, TimeUnit.MILLISECONDS)
                    head = utteranceQueue.peek()
                }
                head
            } ?: break
            if (isPlayerCancelled()) break

            // Wait for the file synthesis to finish (usually instant — files
            // are pre-rendered ahead of playback by the queue-ahead window).
            while (!item.ready.await(100, TimeUnit.MILLISECONDS)) {
                if (isPlayerCancelled()) return
            }
            playerLock.withLock {
                if (isPlayerCancelled()) return
                utteranceQueue.remove(item)
                activeUtterance = item
            }

            if (item.failed) {
                item.file.delete()
                callback?.onError(item.utteranceId, "Synthesis failed")
                continue
            }

            val wav = readWav(item.file)
            if (wav == null) {
                item.file.delete()
                callback?.onError(item.utteranceId, "Unsupported WAV format")
                continue
            }

            // (Re)create the AudioTrack only when the format changes; keeping
            // one track alive across items makes item boundaries gapless.
            val frameBytes = wav.channels * 2
            if (currentTrack == null || trackSampleRate != wav.sampleRate || trackChannels != wav.channels) {
                playerLock.withLock {
                    if (isPlayerCancelled()) return
                    releaseTrack()
                    currentTrack = newTrack(wav.sampleRate, wav.channels)
                }
                trackSampleRate = wav.sampleRate
                trackChannels = wav.channels
                trackFrameBytes = frameBytes
                framesWritten = 0
            }
            val track = currentTrack ?: run {
                item.file.delete()
                callback?.onError(item.utteranceId, "AudioTrack init failed")
                continue
            }

            // Park before starting a new item when paused mid-queue.
            awaitWhilePausedOrStopped(track)
            if (isPlayerCancelled()) return

            val itemStartFrame = framesWritten
            val itemFrames = (wav.dataSize / frameBytes).toLong()
            val itemEndFrame = itemStartFrame + itemFrames
            var startEmitted = false
            var lastSentence = -1

            var written = 0
            val buffer = ByteArray(16 * 1024)
            RandomAccessFile(item.file, "r").use { raf ->
                raf.seek(wav.dataOffset.toLong())
                while (written < wav.dataSize) {
                    if (isPlayerCancelled()) return
                    awaitWhilePausedOrStopped(track)
                    if (isPlayerCancelled()) return

                    val toRead = minOf(buffer.size, wav.dataSize - written)
                    raf.readFully(buffer, 0, toRead)
                    var offset = 0
                    while (offset < toRead && !isPlayerCancelled()) {
                        awaitWhilePausedOrStopped(track)
                        if (isPlayerCancelled()) return
                        val count = track.write(buffer, offset, toRead - offset, AudioTrack.WRITE_NON_BLOCKING)
                        check(count >= 0) { "AudioTrack write failed: $count" }
                        offset += count
                        framesWritten += count / frameBytes
                        if (count == 0) Thread.sleep(10)
                    }
                    written += offset

                    // Emit onStart as soon as the playback head reaches this
                    // item's first frame (writes run ahead of playback).
                    if (!startEmitted && track.playbackHeadPosition >= itemStartFrame) {
                        startEmitted = true
                        callback?.onStart(item.utteranceId)
                    }
                    lastSentence = emitRangeForPosition(
                        item, track, itemStartFrame, itemFrames, lastSentence,
                    )
                }
            }
            item.file.delete()

            // All bytes buffered — wait until the playback head actually
            // reaches the item end before reporting onDone, then continue to
            // the next queued file without releasing the track (gapless).
            while (!isPlayerCancelled()) {
                awaitWhilePausedOrStopped(track)
                if (isPlayerCancelled()) return
                val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                if (!startEmitted && head >= itemStartFrame) {
                    startEmitted = true
                    callback?.onStart(item.utteranceId)
                }
                if (head >= itemEndFrame) break
                lastSentence = emitRangeForPosition(
                    item, track, itemStartFrame, itemFrames, lastSentence,
                )
                Thread.sleep(20)
            }
            if (isPlayerCancelled()) return
            if (!startEmitted) callback?.onStart(item.utteranceId)
            callback?.onDone(item.utteranceId)
        }
    }

    /** Park while paused; also pauses the track. Returns when unpaused. */
    private fun awaitWhilePausedOrStopped(track: AudioTrack) {
        while (playerPaused && !isPlayerCancelled()) {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                runCatching { track.pause() }
            }
            playerLock.withLock {
                queueSignal.await(100, TimeUnit.MILLISECONDS)
            }
        }
        if (!isPlayerCancelled() && track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            runCatching { track.play() }
        }
    }

    /**
     * Report [TtsCallback.onRangeStart] when the estimated playback position
     * enters a new sentence range within [item]'s text. Returns the sentence
     * index last reported.
     */
    private fun emitRangeForPosition(
        item: QueuedUtterance,
        track: AudioTrack,
        itemStartFrame: Long,
        itemFrames: Long,
        lastSentence: Int,
    ): Int {
        if (item.sentences.size <= 1) return lastSentence
        val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        val playedFrames = (head - itemStartFrame).coerceIn(0, itemFrames)
        val charPos = if (itemFrames > 0) {
            (playedFrames * item.text.length / itemFrames).toInt()
                .coerceIn(0, item.text.length - 1)
        } else {
            0
        }
        val sentenceIndex = item.sentences.indexOfFirst { charPos < it.last + 1 }
            .let { if (it < 0) item.sentences.lastIndex else it }
        if (sentenceIndex != lastSentence) {
            val range = item.sentences[sentenceIndex]
            callback?.onRangeStart(
                item.utteranceId,
                range.first,
                (range.last + 1).coerceAtMost(item.text.length),
                playedFrames.toInt(),
            )
            return sentenceIndex
        }
        return lastSentence
    }

    private fun newTrack(sampleRate: Int, channels: Int): AudioTrack? {
        val channelMask = if (channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return runCatching {
            AudioTrack.Builder()
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
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuf, 64 * 1024))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also {
                    check(it.state == AudioTrack.STATE_INITIALIZED)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        it.setStartThresholdInFrames(1)
                    }
                    if (!playerPaused) it.play()
                }
        }.onFailure { logcat(LogPriority.ERROR, it) { "AudioTrack init failed" } }
            .getOrNull()
    }

    // ------------------------------------------------------------------
    // WAV parsing
    // ------------------------------------------------------------------

    private data class WavData(
        val dataOffset: Int,
        val dataSize: Int,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
    )

    /** Parse a RIFF/WAVE header, returning PCM data location and format. */
    private fun readWav(file: File): WavData? {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 44) return null
                val header = ByteArray(12)
                raf.readFully(header)
                if (String(header, 0, 4) != "RIFF" || String(header, 8, 4) != "WAVE") {
                    return null
                }
                var sampleRate = 0
                var channels = 0
                var bits = 0
                var dataOffset = 0
                var dataSize = 0
                // Iterate chunks: [id:4][size:4][data:size]
                while (raf.filePointer + 8 <= raf.length()) {
                    val idBytes = ByteArray(4)
                    raf.readFully(idBytes)
                    val id = String(idBytes)
                    val size = Integer.reverseBytes(raf.readInt())
                    if (size < 0 || size.toLong() > raf.length() - raf.filePointer) return null
                    if (id == "fmt ") {
                        if (size < 16) return null
                        val audioFormat = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xFFFF
                        channels = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xFFFF
                        sampleRate = Integer.reverseBytes(raf.readInt())
                        raf.skipBytes(6) // byteRate + blockAlign
                        bits = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xFFFF
                        if (audioFormat != 1) return null
                        raf.skipBytes((size - 16).coerceAtLeast(0))
                    } else if (id == "data") {
                        dataOffset = raf.filePointer.toInt()
                        dataSize = size.coerceAtMost((raf.length() - dataOffset).toInt())
                        break
                    } else {
                        raf.skipBytes(size)
                    }
                    // Chunks are word-aligned.
                    if (size % 2 == 1) raf.skipBytes(1)
                }
                if (sampleRate <= 0 || channels !in 1..2 || dataSize <= 0 || bits != 16 ||
                    dataSize % (channels * 2) != 0
                ) return null
                WavData(dataOffset, dataSize, sampleRate, channels, bits)
            }
        }.onFailure { logcat(LogPriority.WARN, it) { "readWav failed for ${file.name}" } }
            .getOrNull()
    }

    // ------------------------------------------------------------------
    // Sentence estimation
    // ------------------------------------------------------------------

    companion object {
        /** Sentence boundary: terminal punctuation + closing quotes/brackets + whitespace. */
        private val SENTENCE_END = Regex("[.!?…]+[\"'”’\\)\\]]*\\s+")

        /** Split [text] into approximate sentence ranges covering the whole string. */
        fun sentenceRanges(text: String): List<IntRange> {
            if (text.isEmpty()) return emptyList()
            val ranges = mutableListOf<IntRange>()
            var start = 0
            for (match in SENTENCE_END.findAll(text)) {
                val end = match.range.last + 1
                if (end > start) ranges.add(start until end)
                start = end
            }
            if (start < text.length) ranges.add(start until text.length)
            return ranges.ifEmpty { listOf(0 until text.length) }
        }
    }
}
