package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.novel.NovelDownloader
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Encode-side seam of the chapter render (plan 05-01): the renderer feeds
 * shaped mono PCM16 at the render profile's sample rate via [write]; the
 * sink owns the AAC encode, the MP4 mux, and the SAF tmp→final rename.
 *
 * The three call sites form one transaction:
 *   write(chunk) * → finish(manifestJson)   commits: EOS → drain →
 *                                          muxer.stop (writes moov) → uuid
 *                                          atom → rename tmp to final.
 *                   abort()                 rolls back: teardown WITHOUT
 *                                          muxer.stop and tmp doc deleted —
 *                                          a stopped muxer writes a valid
 *                                          moov, which would leave a
 *                                          valid-looking partial file.
 */
internal interface ChapterAudioSink {

    /** Encode + mux one PCM16 chunk. Suspends on encoder back-pressure. */
    suspend fun write(chunk: ShortArray)

    /**
     * Queue EOS, drain the encoder, stop/release codec+muxer, append the
     * manifest `uuid` atom, then rename the tmp document to its final name.
     */
    suspend fun finish(manifestJson: String)

    /** Tear down without muxer.stop() and delete the tmp document. */
    suspend fun abort()
}

/**
 * Production sink: MediaCodec AAC-LC → MediaMuxer → SAF document.
 *
 * Pipeline (plan 05-01, D-01/D-02/D-03/D-06):
 *  - tmp SAF doc named `<finalName>_tmp` in [audioDir] so a crashed/killed
 *    render never leaves a valid-looking final-named file behind.
 *  - `MediaMuxer` over the SAF `ParcelFileDescriptor` ("rw"). The muxer
 *    dup()s the fd at construction, so our descriptor closes immediately.
 *    Providers whose fd isn't seekable (MediaMuxer needs lseek for the
 *    moov write at stop()) are detected UP FRONT via `Os.lseek(SEEK_CUR)`
 *    — waiting for stop() would fail after the whole encode (RESEARCH §3
 *    risk 8). Fallback: mux into `cacheDir`, stream-copy into the SAF doc
 *    at [finish].
 *  - AAC: `audio/mp4a-latm`, [sampleRate] Hz, mono, AACObjectLC, 96 kbps,
 *    PCM16 input (D-02; KEY_PCM_ENCODING hints the encoder to accept
 *    16-bit PCM directly on API 24+).
 *  - Muxer starts exactly once, on the encoder's INFO_OUTPUT_FORMAT_CHANGED.
 *  - Presentation timestamps derive from cumulative submitted samples
 *    (`totalSamples * 1_000_000 / sampleRate`), never the wall clock.
 *
 * @property sampleRate PCM rate of the incoming chunks — set from the
 *           render profile's `sampleRate` (24 kHz pocket / 44.1 kHz
 *           supertonic); drives the MediaFormat and all pts math (PB-18).
 */
internal class SafM4aSink(
    private val context: Context,
    private val audioDir: UniFile,
    private val finalName: String,
    private val sampleRate: Int,
) : ChapterAudioSink {

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var tmpDoc: UniFile? = null
    private var cacheFallbackFile: File? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var totalSamplesSubmitted = 0L
    private val bufferInfo = MediaCodec.BufferInfo()

    override suspend fun write(chunk: ShortArray) {
        if (chunk.isEmpty()) return
        ensurePipeline()
        val enc = encoder ?: throw IOException("AAC encoder not started")

        // PCM16 → little-endian bytes (MediaCodec accepts both, but LE is
        // the platform-native layout every encoder honors).
        val data = ByteArray(chunk.size * BYTES_PER_SAMPLE)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(chunk)

        var offset = 0
        while (offset < data.size) {
            currentCoroutineContext().ensureActive()
            val inputIdx = enc.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inputIdx >= 0) {
                val input = enc.getInputBuffer(inputIdx) ?: continue
                val n = minOf(input.remaining(), data.size - offset)
                input.put(data, offset, n)
                // Timestamp of the FIRST sample in this queued buffer —
                // cumulative samples submitted so far.
                val ptsUs = totalSamplesSubmitted * 1_000_000L / sampleRate
                enc.queueInputBuffer(inputIdx, 0, n, ptsUs, 0)
                offset += n
                totalSamplesSubmitted += n / BYTES_PER_SAMPLE
            }
            drainEncoder(endOfStream = false)
        }
    }

    override suspend fun finish(manifestJson: String) {
        ensurePipeline() // zero-PCM renders still produce a valid (empty) file
        val enc = encoder ?: throw IOException("AAC encoder not started")

        // Queue EOS, then drain until the encoder reports it.
        while (true) {
            currentCoroutineContext().ensureActive()
            val inputIdx = enc.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inputIdx >= 0) {
                enc.queueInputBuffer(
                    inputIdx,
                    0,
                    0,
                    totalSamplesSubmitted * 1_000_000L / sampleRate,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                break
            }
            drainEncoder(endOfStream = false)
        }
        drainEncoder(endOfStream = true)

        runCatching { enc.stop() }
        runCatching { enc.release() }
        encoder = null

        val mux = muxer
        muxer = null
        try {
            if (muxerStarted) {
                // Writes the moov box — the point of no return for "valid file".
                mux?.stop()
            }
        } finally {
            mux?.release()
            muxerStarted = false
        }

        val tmp = tmpDoc ?: throw IOException("tmp document missing at finish")
        val payload = manifestJson.toByteArray(Charsets.UTF_8)

        val cacheFile = cacheFallbackFile
        if (cacheFile != null) {
            // Non-seekable SAF fd fallback: muxed bytes live in cacheDir —
            // stream them into the SAF doc, then append the atom.
            tmp.openOutputStream().use { out ->
                cacheFile.inputStream().use { it.copyTo(out) }
            }
            cacheFile.delete()
        }
        cacheFallbackFile = null

        // Manifest lands AFTER moov, at EOF (muxer output is moov-last).
        Mp4AtomAppender.append(context, tmp.uri, payload)

        audioDir.findFile(finalName)?.delete()
        if (!tmp.renameTo(finalName)) {
            throw IOException("rename ${tmp.name} -> $finalName failed")
        }
        tmpDoc = null
    }

    override suspend fun abort() {
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        encoder = null
        // Deliberately NO muxer.stop(): stopping writes the moov box and
        // would leave a valid-looking partial .m4a behind.
        runCatching { muxer?.release() }
        muxer = null
        muxerStarted = false
        runCatching { tmpDoc?.delete() }
        tmpDoc = null
        cacheFallbackFile?.delete()
        cacheFallbackFile = null
    }

    // -- pipeline ---------------------------------------------------------------

    /**
     * Lazily create the tmp SAF doc + muxer + encoder on first use so an
     * aborted-before-first-write render has nothing to clean up.
     */
    private fun ensurePipeline() {
        if (encoder != null) return

        val tmp = audioDir.createFile(finalName + NovelDownloader.TMP_DIR_SUFFIX)
            ?: throw IOException("cannot create ${finalName}${NovelDownloader.TMP_DIR_SUFFIX} in $audioDir")
        tmpDoc = tmp

        muxer = openMuxer(tmp)

        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            1,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
            setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        }
        try {
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            encoder = enc
        } catch (t: Throwable) {
            // AAC at the render rate is specced but not guaranteed on every
            // vendor codec — surface it cleanly; the on-device checkpoint
            // (SafM4aSinkInstrumentedTest, V7) verifies both 24 kHz and
            // 44.1 kHz on real hardware.
            throw IOException("AAC encoder setup failed at $sampleRate Hz", t)
        }
    }

    private fun openMuxer(tmp: UniFile): MediaMuxer {
        val pfd: ParcelFileDescriptor = runCatching {
            context.contentResolver.openFileDescriptor(tmp.uri, "rw")
        }.getOrNull()
            ?: return fallbackMuxer()

        // MediaMuxer dup()s the fd in nativeSetup — ours closes immediately.
        pfd.use { descriptor ->
            val seekable = runCatching {
                Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_CUR)
            }.isSuccess
            if (!seekable) return fallbackMuxer()
            return MediaMuxer(descriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
    }

    /**
     * SAF fd not writable/seekable (exotic provider): mux into cacheDir and
     * stream-copy into the SAF doc at [finish] (RESEARCH §3, risk 8).
     */
    private fun fallbackMuxer(): MediaMuxer {
        logcat(LogPriority.WARN) { "SAF fd not seekable — muxing via cacheDir fallback" }
        val cacheFile = File(context.cacheDir, "chapter_audio_mux/${finalName.hashCode()}.m4a").apply {
            parentFile?.mkdirs()
            delete()
        }
        cacheFallbackFile = cacheFile
        return MediaMuxer(cacheFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /**
     * Non-blocking output drain during [write]; blocking loop (until the EOS
     * output flag) when [endOfStream]. The muxer starts exactly once, on the
     * encoder's output-format change.
     */
    private fun drainEncoder(endOfStream: Boolean) {
        val enc = encoder ?: return
        val mux = muxer ?: return
        while (true) {
            val outIdx = enc.dequeueOutputBuffer(
                bufferInfo,
                if (endOfStream) OUTPUT_TIMEOUT_US else 0L,
            )
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = mux.addTrack(enc.outputFormat)
                        mux.start()
                        muxerStarted = true
                    }
                }
                outIdx < 0 -> {
                    if (!endOfStream) return
                    // keep waiting for the EOS-flagged output buffer
                }
                else -> {
                    val buf = enc.getOutputBuffer(outIdx)
                    if (buf != null && muxerStarted && bufferInfo.size > 0) {
                        mux.writeSampleData(trackIndex, buf, bufferInfo)
                    }
                    enc.releaseOutputBuffer(outIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val AAC_BIT_RATE = 96_000 // D-02
        const val INPUT_TIMEOUT_US = 10_000L
        const val OUTPUT_TIMEOUT_US = 10_000L
    }
}
