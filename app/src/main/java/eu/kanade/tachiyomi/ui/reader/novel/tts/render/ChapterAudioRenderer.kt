package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelParagraphSegmenter
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.PitchResampler
import eu.kanade.tachiyomi.ui.reader.novel.tts.audio.SynthesisPipeline
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.SynthAudio
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.ProsodyApplier
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.ProsodyHint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Render failure — IO/codec/engine errors surface as this, never raw. */
class RenderException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Chapter audiobook renderer (plan 05-01, Task 2 tracer).
 *
 * Pipeline: downloaded chapter text → [NovelParagraphSegmenter.paragraphTexts]
 * (the SAME segmentation the reader uses — manifest indices never drift) →
 * per-paragraph `SynthesisPipeline.prepare` → the profile's `streamFactory`
 * (the selected engine's `synthesizeStream` in production) → per-chunk
 * shaping (shapeChunk → resampleLinear(bakeRate)
 * → PitchResampler) → PCM16 scratch append → [ChapterAudioSink]
 * (AAC-LC 96 kbps / mono `.m4a` at the profile's sample rate) → manifest
 * `uuid` atom → rename.
 *
 * Engine selection (D-07, R4): the renderer is engine-agnostic — a
 * per-render [EngineRenderProfile] snapshot of `pref_novel_tts_engine`
 * (`profileFactory`) carries the sample rate, the native-vs-baked rate
 * split, voice resolution, preprocessing rules, and the stream factory.
 * The snapshot is taken ONCE at render start: a mid-render pref flip does
 * not tear down in-flight work.
 *
 * Scratch checkpoint (D-10): shaped PCM16 appends to
 * `cacheDir/chapter_audio/<chapterId>.pcm` and a `.idx` sidecar records the
 * cumulative sample count after each finished paragraph (8-byte BE longs).
 * [resumeFromParagraph] skips synthesis for already-checkpointed paragraphs;
 * the encode pass always re-reads the whole scratch, so a resumed file is
 * byte-identical to a from-scratch one. The scratch is session-scoped —
 * cancellation keeps it, success deletes it, and [abandon] deletes it when
 * the caller doesn't want the partial render resumed (D-11).
 *
 * Rate/pitch/voice are read ONCE at render start — a mid-render preference
 * change must not split the file across two regimes (SD-1).
 *
 * Test seams (research pattern 9): [profileFactory], [sinkFactory],
 * [chapterTextLoader], and [scratchDir] are injectable so JVM tests
 * exercise everything except the Android media/ORT stack.
 */
class ChapterAudioRenderer internal constructor(
    private val context: Context,
    private val provider: NovelDownloadProvider = Injekt.get(),
    private val preferences: NovelTtsPreferences = Injekt.get(),
    internal val profileFactory: (engineType: String) -> EngineRenderProfile =
        { RenderProfiles.forEngine(it, context, preferences) },
    internal val sinkFactory: (audioDir: UniFile?, finalName: String, sampleRate: Int) -> ChapterAudioSink =
        { dir, name, rate ->
            SafM4aSink(
                context,
                dir ?: throw RenderException("downloads directory unavailable — cannot render audio"),
                name,
                rate,
            )
        },
    internal val chapterTextLoader: suspend (novel: Novel, chapter: NovelChapter, source: NovelSource) -> String? =
        { novel, chapter, source ->
            provider.findChapterDir(chapter.name, chapter.scanlator, novel.title, source)
                ?.openInputStream()
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?.takeIf { it.isNotBlank() }
        },
    internal val scratchDir: File = File(context.cacheDir, "chapter_audio"),
) {

    /**
     * The most recent render's profile (PB-16): a mid-render pref flip
     * means the NEXT render's `ensureLoaded` runs on its own profile, so
     * [releaseEngine] tracks and releases only the last-used engine —
     * that covers the "leave reader" cleanup the queue manager needs.
     */
    private var lastProfile: EngineRenderProfile? = null

    /**
     * Render one downloaded chapter to `.m4a`. Returns the manifest that was
     * embedded in the file.
     *
     * @param resumeFromParagraph skip synthesis for paragraphs `< N` and
     *        rebuild their offsets from the `.idx` sidecar (same-session
     *        resume — D-10). A missing/corrupt checkpoint restarts at zero.
     * @param onProgress (completedParagraphCount, totalParagraphs) after
     *        each paragraph — indices are absolute, so a resumed render
     *        resumes mid-range.
     * @throws RenderException on empty input or IO/codec/engine failure
     * @throws CancellationException on cancel — scratch retained for resume
     */
    suspend fun renderChapter(
        novel: Novel,
        chapter: NovelChapter,
        source: NovelSource,
        resumeFromParagraph: Int = 0,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): ChapterAudioManifest {
        val html = chapterTextLoader(novel, chapter, source)
            ?: throw RenderException("chapter text unavailable for '${chapter.name}'")
        val paragraphs = NovelParagraphSegmenter.paragraphTexts(html)
        if (paragraphs.isEmpty()) {
            throw RenderException("no speakable paragraphs in '${chapter.name}'")
        }

        // Render-time settings snapshot — read once, before the first chunk
        // (SD-1). The engine profile is likewise a snapshot of the engine
        // pref (R4): a mid-render flip does NOT tear down this render.
        // Voice resolves against the installed bundle WITHOUT mutating the
        // preference.
        val profile = profileFactory(preferences.engineType().get()).also { lastProfile = it }
        profile.ensureLoaded()
        val voice = profile.resolveVoice(preferences.voiceName().get())
        val rate = preferences.speechRate().get()
        val pitch = preferences.pitch().get()
        val enabledRules = profile.preprocessingRules
        val bakeRate = profile.bakeRateFor(rate)

        scratchDir.mkdirs()
        val scratch = File(scratchDir, "${chapter.id}.pcm")
        val index = File(scratchDir, "${chapter.id}.idx")

        // cumulativeBefore[i] = shaped samples preceding paragraph i.
        val cumulativeBefore = LongArray(paragraphs.size)
        var cumulativeSamples = 0L
        var effectiveResume = resumeFromParagraph.coerceIn(0, paragraphs.size)
        if (effectiveResume == 0) {
            scratch.delete()
            index.delete()
        } else {
            val stored = readScratchIndex(index)
            if (!scratch.isFile || stored == null || stored.size < effectiveResume) {
                logcat(LogPriority.WARN) {
                    "resume requested at $effectiveResume but checkpoint is missing/corrupt — restarting"
                }
                effectiveResume = 0
                scratch.delete()
                index.delete()
            } else {
                for (i in 1 until effectiveResume) cumulativeBefore[i] = stored[i - 1]
                cumulativeSamples = stored[effectiveResume - 1]
            }
        }

        var sink: ChapterAudioSink? = null
        try {
            // -- synthesis phase: per-paragraph → shaped PCM → scratch ------
            for (i in effectiveResume until paragraphs.size) {
                currentCoroutineContext().ensureActive()
                cumulativeBefore[i] = cumulativeSamples
                val prepared = SynthesisPipeline.prepare(paragraphs[i], enabledRules)
                if (prepared != null) {
                    profile.streamFactory(
                        prepared.engineText,
                        voice,
                        profile.nativeSpeedFor(rate),
                    ).collect { audio ->
                        currentCoroutineContext().ensureActive()
                        val shaped = shapeForRender(audio, prepared.hint, bakeRate, pitch)
                        if (shaped.isNotEmpty()) {
                            scratch.appendBytes(shaped.toLittleEndianBytes())
                            cumulativeSamples += shaped.size
                        }
                    }
                }
                // Boundary checkpoint — lets a resume rebuild offsets without
                // re-synthesising earlier paragraphs.
                index.appendBytes(
                    ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                        .putLong(cumulativeSamples).array(),
                )
                onProgress(i + 1, paragraphs.size)
            }

            val manifest = ChapterAudioManifest(
                voice = voice,
                engine = profile.engineName,
                chapterTitle = chapter.name,
                sampleRate = profile.sampleRate,
                speechRate = rate,
                pitch = pitch,
                durationMs = cumulativeSamples * 1000L / profile.sampleRate,
                paragraphOffsetsMs = List(paragraphs.size) {
                    cumulativeBefore[it] * 1000L / profile.sampleRate
                },
                paragraphTexts = paragraphs,
                text = paragraphs.joinToString("\n\n"),
            )

            // -- encode phase: scratch → AAC/m4a → manifest atom → rename ---
            sink = sinkFactory(
                provider.getOrCreateAudioDir(novel.title, source),
                provider.getAudioFileName(chapter.name, chapter.scanlator),
                profile.sampleRate,
            )
            encodeScratch(scratch, sink)
            sink.finish(manifest.encode())

            scratch.delete()
            index.delete()
            return manifest
        } catch (t: CancellationException) {
            // D-10/D-11: tmp doc torn down via abort() (never muxer.stop()),
            // PCM scratch KEPT for same-session resume.
            sink?.abort()
            throw t
        } catch (t: RenderException) {
            sink?.abort()
            throw t
        } catch (t: Throwable) {
            sink?.abort()
            throw RenderException("render failed for '${chapter.name}': ${t.message}", t)
        }
    }

    /**
     * Drop the scratch checkpoint for [chapterId] outright — the "don't
     * resume this render" path (D-11 abandon semantics).
     */
    fun abandon(chapterId: Long) {
        File(scratchDir, "$chapterId.pcm").delete()
        File(scratchDir, "$chapterId.idx").delete()
    }

    /** Release the engine — the queue manager (05-02) calls this on drain. */
    fun releaseEngine() {
        lastProfile?.releaseEngine()
    }

    // -- internals --------------------------------------------------------------

    /**
     * Canonical per-chunk shaping, mirroring `PocketTtsEngine.shape` (keep in
     * sync): emotion prosody → baked speech rate → pitch. The rate split is
     * the profile's (D-07): `bakeRate` is the residue the engine did NOT
     * apply natively — Pocket bakes the whole rate, Supertonic bakes
     * `rate / native` so 1.0 skips the resample entirely (PB-17). Pitch is
     * always baked via [PitchResampler] for both engines.
     */
    private fun shapeForRender(audio: SynthAudio, hint: ProsodyHint, bakeRate: Float, pitch: Float): ShortArray {
        var pcm = SynthesisPipeline.shapeChunk(audio.pcm, audio.sampleRate, hint)
        if (abs(bakeRate - 1f) > 0.01f) {
            pcm = ProsodyApplier.resampleLinear(pcm, bakeRate)
        }
        if (abs(pitch - 1f) > 0.001f) {
            val floats = FloatArray(pcm.size) { pcm[it].toFloat() }
            val shifted = PitchResampler.resample(floats, pitch)
            pcm = ShortArray(shifted.size) { i ->
                shifted[i].toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
        return pcm
    }

    /** Stream the PCM16 scratch into the sink in bounded chunks. */
    private suspend fun encodeScratch(scratch: File, sink: ChapterAudioSink) {
        if (!scratch.isFile) return
        scratch.inputStream().buffered().use { input ->
            val raw = ByteArray(ENCODE_CHUNK_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                var filled = 0
                while (filled < raw.size) {
                    val n = input.read(raw, filled, raw.size - filled)
                    if (n < 0) break
                    filled += n
                }
                if (filled == 0) return
                val samples = filled / BYTES_PER_SAMPLE
                val chunk = ShortArray(samples)
                ByteBuffer.wrap(raw, 0, samples * BYTES_PER_SAMPLE)
                    .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(chunk)
                sink.write(chunk)
                if (filled < raw.size) return // EOF tail
            }
        }
    }

    /**
     * `.idx` sidecar: one big-endian long per finished paragraph — the
     * cumulative shaped-sample count after it. `null` on absent/corrupt.
     */
    private fun readScratchIndex(index: File): LongArray? {
        if (!index.isFile) return null
        val bytes = index.readBytes()
        if (bytes.isEmpty() || bytes.size % 8 != 0) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return LongArray(bytes.size / 8) { buf.long }
    }

    private fun ShortArray.toLittleEndianBytes(): ByteArray {
        val out = ByteArray(size * BYTES_PER_SAMPLE)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(this)
        return out
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val ENCODE_CHUNK_BYTES = 64 * 1024
    }
}
