package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.SynthAudio
import eu.kanade.tachiyomi.ui.reader.novel.tts.supertonic.SupertonicTestFixtures
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * JVM unit tests for [SupertonicTtsEngine] — the `TtsEngine` adapter over
 * `supertonic/SupertonicEngine` (phase-05B plan 01). Mirrors
 * [PocketTtsEngineTest] test-for-test; the deltas under test are the 3-arg
 * `streamFactory` (text, voiceId, nativeSpeed), namespaced voice
 * resolution, and the native-speed rate path (residual-only resample).
 *
 * The engine's two Android-facing seams are injected: `streamFactory`
 * supplies fake `Flow<SynthAudio>` streams and `pcmSinkFactory` supplies a
 * [RecordingSink], so the whole prefetch/drain contract runs on the bare JVM
 * with no `Context`, no ORT, and no `AudioTrack`. Work is scheduled on a
 * [StandardTestDispatcher] bound to [runTest]'s scheduler, so
 * `advanceUntilIdle`/`runCurrent` step the engine deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SupertonicTtsEngineTest {

    private fun chunk(size: Int, fill: Int = 7): SynthAudio =
        SynthAudio(ShortArray(size) { (it * fill % 1000).toShort() }, sampleRate = 44100)

    /**
     * Builds a Context whose filesDir holds a synthetic `voice.bin` under
     * `engines/supertonic-3-int8/` — enough for
     * `SupertonicVoiceCatalog.resolveInstalledVoice`'s existence check.
     * A relaxed-mock filesDir NPEs here: `File(File, String)` reads
     * `parent.path` as a field, which a constructor-bypassing mockk instance
     * leaves null.
     */
    private fun contextWithVoices(voiceCount: Int = 10): Context {
        val dir = createTempDirectory().toFile()
        val engineDir = File(dir, "engines/supertonic-3-int8")
        engineDir.mkdirs()
        SupertonicTestFixtures.writeVoiceBin(File(engineDir, "voice.bin"), n = voiceCount)
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns dir
        return context
    }

    private data class StreamCall(val text: String, val voice: String, val speed: Float)

    private fun makeEngine(
        dispatcher: CoroutineDispatcher,
        flows: ArrayDeque<Flow<SynthAudio>>,
        sink: RecordingSink,
        calls: MutableList<StreamCall>,
        context: Context = contextWithVoices(),
    ): SupertonicTtsEngine = SupertonicTtsEngine(
        context = context,
        preferences = NovelTtsPreferences(FakePreferenceStore()),
        streamFactory = { text, voice, speed ->
            calls += StreamCall(text, voice, speed)
            flows.removeFirst()
        },
        pcmSinkFactory = { sink },
        engineLoader = {},
        dispatcher = dispatcher,
    )

    private fun events(): MutableList<String> = mutableListOf()

    private fun callback(events: MutableList<String>) = object : TtsCallback {
        override fun onStart(utteranceId: String) {
            events += "start:$utteranceId"
        }

        override fun onDone(utteranceId: String) {
            events += "done:$utteranceId"
        }

        override fun onError(utteranceId: String, errorMessage: String) {
            events += "error:$utteranceId"
        }
    }

    // -- prefetch → speak drain ----------------------------------------------

    @Test
    fun `prefetch then speak drains buffered chunks in order`() = runTest {
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(flowOf(chunk(100), chunk(200)))),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.prefetch("hello world")
        testScheduler.advanceUntilIdle()
        // Buffered, not played — nothing written until speak.
        sink.written.shouldBeEmpty()

        engine.speak("hello world", "u1", flush = false, callback = cb)
        testScheduler.advanceUntilIdle()

        sink.written.map { it.size } shouldBe listOf(100, 200)
        calls.size shouldBe 1 // only the prefetch opened a stream
        events shouldBe listOf("start:u1", "write:100", "write:200", "done:u1")
    }

    @Test
    fun `prefetch then speak with flush still drains the matching buffer`() = runTest {
        // Regression guard: every serial-path speak carries flush=true
        // (supportsQueueing=false). If flush cleared the matching entry,
        // synth-ahead would be dead code.
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(flowOf(chunk(120), chunk(80)))),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.prefetch("next paragraph")
        testScheduler.advanceUntilIdle()
        engine.speak("next paragraph", "u2", flush = true, callback = cb)
        testScheduler.advanceUntilIdle()

        sink.written.map { it.size } shouldBe listOf(120, 80)
        calls.size shouldBe 1
        events shouldBe listOf("start:u2", "write:120", "write:80", "done:u2")
    }

    @Test
    fun `speak drains a partially-buffered prefetch then continues the live flow`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gated: Flow<SynthAudio> = flow {
            emit(chunk(50))
            gate.await()
            emit(chunk(70))
        }
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(gated)),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.prefetch("para two")
        testScheduler.runCurrent()
        sink.written.shouldBeEmpty() // collected into the buffer, not played

        engine.speak("para two", "u3", flush = true, callback = cb)
        testScheduler.runCurrent()
        sink.written.map { it.size } shouldBe listOf(50) // buffered prefix drained

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        sink.written.map { it.size } shouldBe listOf(50, 70) // live tail followed
        calls.size shouldBe 1
        events shouldBe listOf("start:u3", "write:50", "write:70", "done:u3")
    }

    @Test
    fun `speak for different text under flush discards the stale prefetch`() = runTest {
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(flowOf(chunk(111)), flowOf(chunk(222)))),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.prefetch("stale text")
        testScheduler.advanceUntilIdle()
        engine.speak("fresh text", "u4", flush = true, callback = cb)
        testScheduler.advanceUntilIdle()

        sink.written.map { it.size } shouldBe listOf(222)
        calls.size shouldBe 2 // prefetch stream + fresh speak stream
        events shouldBe listOf("start:u4", "write:222", "done:u4")
    }

    // -- invalidation ---------------------------------------------------------

    @Test
    fun `stop clears a pending prefetch so a later speak streams fresh`() = runTest {
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(flowOf(chunk(90)), flowOf(chunk(60)))),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.prefetch("same words")
        testScheduler.advanceUntilIdle()
        engine.stop()

        engine.speak("same words", "u5", flush = true, callback = cb)
        testScheduler.advanceUntilIdle()

        calls.size shouldBe 2 // prefetch stream was discarded; speak re-streamed
        sink.written.map { it.size } shouldBe listOf(60)
        events shouldBe listOf("start:u5", "write:60", "done:u5")
    }

    // -- callback protocol ----------------------------------------------------

    @Test
    fun `blank text short-circuits to onDone with no writes`() = runTest {
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(flowOf(chunk(10)))),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.speak("   ", "u6", flush = true, callback = cb)
        testScheduler.advanceUntilIdle()

        sink.written.shouldBeEmpty()
        calls.shouldBeEmpty()
        events shouldBe listOf("start:u6", "done:u6")
    }

    @Test
    fun `stream failure reports onError`() = runTest {
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val failing: Flow<SynthAudio> = flow { throw RuntimeException("boom") }
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(failing)),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.speak("bad text", "u7", flush = true, callback = cb)
        testScheduler.advanceUntilIdle()

        events shouldBe listOf("start:u7", "error:u7")
    }

    // -- rate / pitch ----------------------------------------------------------

    @Test
    fun `speech rate 2 rides native speed and leaves PCM unresampled`() = runTest {
        // Supertonic's native `speed` param covers 0.5–2.0 — rate 2.0 must
        // reach the stream as speed=2.0 with NO residual resample (unlike
        // Pocket, which halves the PCM post-hoc).
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(flowOf(chunk(480), chunk(480)))),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.setSpeechRate(2.0f)
        engine.speak("fast talk", "u8", flush = true, callback = cb)
        testScheduler.advanceUntilIdle()

        calls.size shouldBe 1
        calls.last().speed shouldBe 2.0f
        sink.written.sumOf { it.size } shouldBe 960 // unresampled
    }

    @Test
    fun `speech rate above 2 clamps native speed and resamples the residual`() = runTest {
        // rate 3.0 → native speed 2.0 into the model, residual 1.5 through
        // resampleLinear (PB-4) — floor(480 / 1.5) = 320 samples out.
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(flowOf(chunk(480)))),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.setSpeechRate(3.0f)
        engine.speak("very fast talk", "u9", flush = true, callback = cb)
        testScheduler.advanceUntilIdle()

        calls.last().speed shouldBe 2.0f
        sink.written.sumOf { it.size } shouldBe 320
    }

    @Test
    fun `pitch 1 writes unmodified samples`() = runTest {
        val source = chunk(64, fill = 13)
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(flowOf(source))),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.speak("plain", "u10", flush = true, callback = cb)
        testScheduler.advanceUntilIdle()

        calls.last().speed shouldBe 1.0f
        sink.written.size shouldBe 1
        sink.written[0].contentEquals(source.pcm) shouldBe true
    }

    // -- pause / resume ---------------------------------------------------------

    @Test
    fun `pause and resume forward to the active sink`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gated: Flow<SynthAudio> = flow {
            emit(chunk(40))
            gate.await()
            emit(chunk(40))
        }
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(gated)),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.speak("pausable", "u11", flush = true, callback = cb)
        testScheduler.runCurrent()

        engine.pause()
        sink.pauseCalls shouldBe 1
        engine.resume()
        sink.resumeCalls shouldBe 1

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        sink.written.map { it.size } shouldBe listOf(40, 40)
        events.last() shouldBe "done:u11"
    }

    @Test
    fun `pause mid-stream suspends writes and resume continues without restarting synthesis`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source: Flow<SynthAudio> = flow {
            emit(chunk(40))
            gate.await()
            emit(chunk(40))
        }
        val sink = PausingRecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(source)),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.speak("pausable", "u12", flush = true, callback = cb)
        testScheduler.runCurrent()

        engine.pause()
        gate.complete(Unit)
        testScheduler.runCurrent()

        // The second chunk's write stays suspended while paused: no error,
        // no re-collection, nothing dropped.
        calls.size shouldBe 1
        sink.written.size shouldBe 1
        events.none { it.startsWith("error:") } shouldBe true

        engine.resume()
        testScheduler.advanceUntilIdle()

        calls.size shouldBe 1
        sink.written.map { it.size } shouldBe listOf(40, 40)
        events.last() shouldBe "done:u12"
    }

    // -- voice resolution -------------------------------------------------------

    @Test
    fun `stale pocket voice name falls back to the namespaced default`() = runTest {
        // R6: a Pocket-era voice pref (e.g. "eponine") carried into the
        // shared pref must resolve to supertonic:F5, never reach the engine.
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(flowOf(chunk(40)))),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.setVoice("eponine")
        engine.speak("hello", "u13", flush = true, callback = cb)
        testScheduler.advanceUntilIdle()

        calls.last().voice shouldBe SupertonicVoiceCatalog.DEFAULT_VOICE
        calls.last().voice shouldBe "supertonic:F5"
        events shouldBe listOf("start:u13", "write:40", "done:u13")
    }

    @Test
    fun `installed voice resolves to the namespaced form passed to the stream`() = runTest {
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(flowOf(chunk(40)))),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.setVoice("supertonic:M3")
        engine.speak("hello", "u14", flush = true, callback = cb)
        testScheduler.advanceUntilIdle()

        calls.last().voice shouldBe "supertonic:M3"
        events shouldBe listOf("start:u14", "write:40", "done:u14")
    }

    @Test
    fun `bare style name resolves to its namespaced form`() = runTest {
        val sink = RecordingSink(events())
        val calls = mutableListOf<StreamCall>()
        val engine = makeEngine(
            StandardTestDispatcher(testScheduler),
            ArrayDeque(listOf(flowOf(chunk(40)))),
            sink,
            calls,
        )
        val events = sink.events
        val cb = callback(events)

        engine.setVoice("M3")
        engine.speak("hello", "u15", flush = true, callback = cb)
        testScheduler.advanceUntilIdle()

        calls.last().voice shouldBe "supertonic:M3"
    }

    // -- fakes ------------------------------------------------------------------

    /** Records writes/events in call order; pause/resume/release counted. */
    private open class RecordingSink(val events: MutableList<String>) : PcmSink {
        val written = mutableListOf<ShortArray>()
        var pauseCalls = 0
        var resumeCalls = 0
        var releaseCalls = 0

        override suspend fun write(chunk: ShortArray) {
            written += chunk
            events += "write:${chunk.size}"
        }

        override fun pause() {
            pauseCalls++
        }

        override fun resume() {
            resumeCalls++
        }

        override fun release() {
            releaseCalls++
        }
    }

    /**
     * [RecordingSink] that mirrors `AudioTrackPcmSink`'s contract — [write]
     * suspends while paused instead of writing into a stalled track.
     */
    private class PausingRecordingSink(events: MutableList<String>) : RecordingSink(events) {
        @Volatile private var sinkPaused = false

        override suspend fun write(chunk: ShortArray) {
            while (sinkPaused) {
                delay(5)
            }
            super.write(chunk)
        }

        override fun pause() {
            sinkPaused = true
            super.pause()
        }

        override fun resume() {
            sinkPaused = false
            super.resume()
        }
    }

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

        override fun changes(): Flow<String?> = kotlinx.coroutines.flow.emptyFlow()
    }
}
