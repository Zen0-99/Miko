package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import android.content.Context
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.PocketVoiceCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.PocketEngine
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.SynthAudio
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter

/**
 * JVM unit tests for [ChapterAudioRenderer] — the end-to-end render slice
 * (plan 05-01, Task 2 tracer).
 *
 * The Android-facing seams are injected: `streamFactory` supplies fake
 * `Flow<SynthAudio>` per paragraph, `sinkFactory` supplies a recording fake
 * that runs the manifest through the REAL [Mp4AtomAppender] onto a temp
 * file, `chapterTextLoader` returns fixture HTML, `scratchDir` is a temp
 * dir, and `engine` is a relaxed mock (never loads ORT). Everything else —
 * the real [eu.kanade.tachiyomi.ui.reader.novel.NovelParagraphSegmenter],
 * offset math, PCM scratch checkpointing, the rate/pitch bake chain, and the
 * manifest codec — is production code.
 *
 * Covered contract (05-01 must-haves):
 *  - paragraphs segmented identically to the reader's blank-filtered list
 *  - per-paragraph PCM16 scratch append; `paragraphOffsetsMs[i]` = cumulative
 *    shaped samples before paragraph i × 1000 / 24000
 *  - speechRate/pitch baked via shapeChunk→resampleLinear→PitchResampler and
 *    recorded in the manifest (SD-1)
 *  - finish() commits via the sink; manifest atom appended + readable
 *  - cancel mid-render → sink.abort(), scratch retained, CancellationException
 *    rethrown; abandon() deletes the scratch (D-10/D-11/SD-2)
 *  - resumeFromParagraph=N skips synthesis for paragraphs < N, preserves the
 *    existing scratch bytes, and keeps paragraph offsets intact
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChapterAudioRendererTest {

    private fun chunk(size: Int, fill: Int = 7): SynthAudio =
        SynthAudio(ShortArray(size) { (it * fill % 1000).toShort() }, sampleRate = 24000)

    private val novel = Novel.create().copy(id = 7L, title = "Test Novel")
    private val chapter = NovelChapter.create().copy(id = 42L, novelId = 7L, name = "Chapter 1")
    private val source = mockk<NovelSource>(relaxed = true)

    /** Fake MP4 prefix the recording sink writes before the manifest atom. */
    private fun fakeMp4Prefix(): ByteArray {
        fun atom(fourcc: String, payload: ByteArray): ByteArray {
            val buf = ByteBuffer.allocate(8 + payload.size)
            buf.putInt(8 + payload.size)
            buf.put(fourcc.toByteArray(Charsets.US_ASCII))
            buf.put(payload)
            return buf.array()
        }
        return atom("ftyp", "M4A ".toByteArray()) + atom("mdat", ByteArray(16)) +
            atom("moov", ByteArray(8))
    }

    /**
     * Recording [ChapterAudioSink]: captures PCM chunks, and on [finish]
     * writes a fake moov-last MP4 to [outFile] then appends the manifest JSON
     * through the real [Mp4AtomAppender] — the same contract [SafM4aSink]
     * fulfills on device.
     */
    private class RecordingSink(private val outFile: File, private val mp4Prefix: ByteArray) : ChapterAudioSink {
        val written = mutableListOf<ShortArray>()
        var finishedJson: String? = null
        var abortCalls = 0
        var writeGate: CompletableDeferred<Unit>? = null

        override suspend fun write(chunk: ShortArray) {
            writeGate?.await()
            written += chunk
        }

        override suspend fun finish(manifestJson: String) {
            finishedJson = manifestJson
            outFile.writeBytes(mp4Prefix)
            Mp4AtomAppender.append(outFile, manifestJson.toByteArray(Charsets.UTF_8))
        }

        override suspend fun abort() {
            abortCalls++
        }
    }

    private fun readAtomJson(file: File): String? =
        FileChannel.open(file.toPath(), StandardOpenOption.READ).use { Mp4AtomReader.read(it) }

    private fun contextWithVoices(vararg voiceNames: String): Context {
        val dir = createTempDirectory().toFile()
        val voicesDir = File(dir, "engines/pocket-tts-en-v2026_04/voices")
        voicesDir.mkdirs()
        voiceNames.forEach { File(voicesDir, "$it.wav").writeBytes(byteArrayOf(0)) }
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns dir
        return context
    }

    private fun makeRenderer(
        scratchDir: File,
        sink: RecordingSink,
        html: String,
        streamFactory: (text: String, voiceId: String) -> Flow<SynthAudio>,
        preferences: NovelTtsPreferences = NovelTtsPreferences(FakePreferenceStore()),
        context: Context = contextWithVoices("eponine"),
    ): ChapterAudioRenderer = ChapterAudioRenderer(
        context = context,
        provider = mockk<NovelDownloadProvider>(relaxed = true).also {
            every { it.getAudioFileName("Chapter 1", null) } returns "Chapter 1.m4a"
        },
        preferences = preferences,
        engine = mockk<PocketEngine>(relaxed = true),
        streamFactory = streamFactory,
        sinkFactory = { sink },
        chapterTextLoader = { _, _, _ -> html },
        scratchDir = scratchDir,
    )

    private fun scratchFile(scratchDir: File) = File(scratchDir, "${chapter.id}.pcm")

    // -- happy path -------------------------------------------------------------

    @Test
    fun `render writes per-paragraph PCM, computes offsets, and embeds a readable manifest`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        val outFile = createTempFile(suffix = ".m4a").toFile()
        val sink = RecordingSink(outFile, fakeMp4Prefix())
        val calls = mutableListOf<Pair<String, String>>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Alpha.</p><p>Beta.</p>",
            streamFactory = { text, voice ->
                calls += text to voice
                if (text.contains("Alpha")) flowOf(chunk(480), chunk(480)) else flowOf(chunk(240))
            },
        )

        val manifest = renderer.renderChapter(novel, chapter, source) { c, t -> progress += c to t }

        // Two stream calls, resolved voice.
        calls.size shouldBe 2
        calls.all { it.second == "eponine" } shouldBe true

        // 960 samples before paragraph 2 → 40000 ms at 24 kHz.
        manifest.paragraphOffsetsMs shouldBe listOf(0L, 960L * 1000L / 24000L)
        manifest.paragraphTexts shouldBe listOf("Alpha.", "Beta.")
        manifest.text shouldBe "Alpha.\n\nBeta."
        manifest.voice shouldBe "eponine"
        manifest.engine shouldBe "pocket-tts-en-v2026_04"
        manifest.sampleRate shouldBe 24000
        manifest.speechRate shouldBe 1.0f
        manifest.durationMs shouldBe 1200L * 1000L / 24000L

        // Sink received all 1200 shaped samples; finish ran once; no abort.
        sink.written.sumOf { it.size } shouldBe 1200
        sink.finishedJson.shouldNotBeNull()
        sink.abortCalls shouldBe 0

        // The atom on the produced file decodes to the same manifest.
        decodeManifest(readAtomJson(outFile)!!) shouldBe manifest

        // Scratch + index cleaned up after a successful render.
        scratchFile(scratchDir).exists() shouldBe false
        File(scratchDir, "${chapter.id}.idx").exists() shouldBe false

        progress shouldBe listOf(1 to 2, 2 to 2)
    }

    @Test
    fun `speech rate 2 halves the rendered PCM and is recorded in the manifest`() = runTest {
        val prefs = NovelTtsPreferences(FakePreferenceStore())
        prefs.speechRate().set(2.0f)
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Fast.</p>",
            streamFactory = { _, _ -> flowOf(chunk(480)) },
            preferences = prefs,
        )

        val manifest = renderer.renderChapter(novel, chapter, source)

        sink.written.sumOf { it.size } shouldBe 240 // resampleLinear halves
        manifest.speechRate shouldBe 2.0f
    }

    // -- cancellation / resume (D-10, SD-2) --------------------------------------

    @Test
    fun `cancel during synthesis retains the scratch checkpoint and rethrows`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        val gate = CompletableDeferred<Unit>()
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Alpha.</p><p>Beta.</p>",
            streamFactory = { text, _ ->
                if (text.contains("Alpha")) {
                    flowOf(chunk(480))
                } else {
                    flow { emit(chunk(10)); gate.await(); emit(chunk(10)) }
                }
            },
        )

        val job = async { renderer.renderChapter(novel, chapter, source) }
        testScheduler.runCurrent()
        job.cancelAndJoin()

        job.isCancelled shouldBe true
        scratchFile(scratchDir).length() shouldBe 480L * 2 // paragraph 1's PCM retained
        sink.abortCalls shouldBe 0 // sink never created during the synth phase

        // abandon() removes the checkpoint outright (D-11 cancel semantics).
        renderer.abandon(chapter.id)
        scratchFile(scratchDir).exists() shouldBe false
        File(scratchDir, "${chapter.id}.idx").exists() shouldBe false
    }

    @Test
    fun `cancel during encode aborts the sink and retains the scratch`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        sink.writeGate = CompletableDeferred()
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Alpha.</p>",
            streamFactory = { _, _ -> flowOf(chunk(480)) },
        )

        val job = async { renderer.renderChapter(novel, chapter, source) }
        testScheduler.runCurrent()
        job.cancelAndJoin()

        sink.abortCalls shouldBe 1
        sink.finishedJson shouldBe null
        scratchFile(scratchDir).exists() shouldBe true
    }

    @Test
    fun `resumeFromParagraph skips earlier synthesis and preserves offsets`() = runTest {
        val scratchDir = createTempDirectory().toFile()

        // Stage a checkpoint: render paragraph 1, cancel inside paragraph 2.
        val gate = CompletableDeferred<Unit>()
        val firstSink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        val first = makeRenderer(
            scratchDir = scratchDir,
            sink = firstSink,
            html = "<p>Alpha.</p><p>Beta.</p>",
            streamFactory = { text, _ ->
                if (text.contains("Alpha")) flowOf(chunk(480)) else flow { gate.await() }
            },
        )
        val job = async { first.renderChapter(novel, chapter, source) }
        testScheduler.runCurrent()
        job.cancelAndJoin()
        scratchFile(scratchDir).length() shouldBe 480L * 2

        // Resume — a fresh renderer instance over the same scratchDir.
        val calls = mutableListOf<Pair<String, String>>()
        val outFile = createTempFile(suffix = ".m4a").toFile()
        val secondSink = RecordingSink(outFile, fakeMp4Prefix())
        val second = makeRenderer(
            scratchDir = scratchDir,
            sink = secondSink,
            html = "<p>Alpha.</p><p>Beta.</p>",
            streamFactory = { text, voice -> calls += text to voice; flowOf(chunk(240)) },
        )
        val manifest = second.renderChapter(novel, chapter, source, resumeFromParagraph = 1)

        calls.size shouldBe 1
        calls[0].first.contains("Beta") shouldBe true
        // Paragraph 1 offset preserved from the checkpoint (480 samples → 20000ms).
        manifest.paragraphOffsetsMs shouldBe listOf(0L, 20000L)
        manifest.durationMs shouldBe (480 + 240) * 1000L / 24000L
        decodeManifest(readAtomJson(outFile)!!)!!.paragraphOffsetsMs shouldBe listOf(0L, 20000L)
        scratchFile(scratchDir).exists() shouldBe false
    }

    @Test
    fun `resume without a scratch checkpoint restarts from zero`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        val calls = mutableListOf<String>()
        val renderer = makeRenderer(
            scratchDir = scratchDir,
            sink = sink,
            html = "<p>Alpha.</p><p>Beta.</p>",
            streamFactory = { text, _ -> calls += text; flowOf(chunk(120)) },
        )

        renderer.renderChapter(novel, chapter, source, resumeFromParagraph = 1)

        calls.size shouldBe 2 // both paragraphs re-synthesized
    }

    @Test
    fun `chapter with no speakable paragraphs fails without touching the sink`() = runTest {
        val scratchDir = createTempDirectory().toFile()
        val sink = RecordingSink(createTempFile().toFile(), fakeMp4Prefix())
        var sinkCreated = false
        val renderer = ChapterAudioRenderer(
            context = contextWithVoices("eponine"),
            provider = mockk<NovelDownloadProvider>(relaxed = true),
            preferences = NovelTtsPreferences(FakePreferenceStore()),
            engine = mockk<PocketEngine>(relaxed = true),
            streamFactory = { _, _ -> flowOf(chunk(10)) },
            sinkFactory = { sinkCreated = true; sink },
            chapterTextLoader = { _, _, _ -> "<p>   </p><p></p>" },
            scratchDir = scratchDir,
        )

        shouldThrow<RenderException> {
            renderer.renderChapter(novel, chapter, source)
        }
        sinkCreated shouldBe false
    }

    // -- fakes -------------------------------------------------------------------

    private class FakePreference<T>(initial: T) : Preference<T> {
        private val state = MutableStateFlow(initial)

        override fun key(): String = "fake"
        override fun get(): T = state.value
        override fun set(value: T) {
            state.value = value
        }

        override fun isSet(): Boolean = true
        override fun delete() = Unit
        override fun defaultValue(): T = state.value
        override fun changes(): Flow<T> = state
        override fun stateIn(scope: CoroutineScope): StateFlow<T> = state
    }

    private class FakePreferenceStore : PreferenceStore {
        private val prefs = mutableMapOf<String, Preference<*>>()

        @Suppress("UNCHECKED_CAST")
        private fun <T> pref(key: String, defaultValue: T): Preference<T> =
            prefs.getOrPut(key) { FakePreference(defaultValue) } as Preference<T>

        override fun getString(key: String, defaultValue: String): Preference<String> =
            pref(key, defaultValue)

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            pref(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            pref(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            pref(key, defaultValue)

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            pref(key, defaultValue)

        override fun getStringSet(
            key: String,
            defaultValue: Set<String>,
        ): Preference<Set<String>> = pref(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = pref(key, defaultValue)

        override fun getAll(): Map<String, *> = emptyMap<String, Any>()
    }
}
