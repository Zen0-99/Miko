package eu.kanade.tachiyomi.ui.reader.novel.tts

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * JVM unit tests for [RenderedAudioTracker] — the pure-JVM half of
 * [RenderedAudioEngine] (plan 05-03, D-19): the paragraph↔millisecond math
 * that drives highlights when read-along plays a rendered `.m4a` instead of
 * live synthesis.
 *
 * Covered contract:
 *  - `paragraphIndexAt` is a binary-search floor over `offsetsMs`, clamped to
 *    the last index (positions past EOF still resolve to the final paragraph).
 *  - `isParagraphFinished(idx, pos)` is `pos >= offsets[idx+1]`, with the last
 *    paragraph bounded by `durationMs` — the poller fires `onDone` on the
 *    first crossing and stops, so the finish signal must be monotonic.
 *  - `indexFromUtterance` parses the `tts_{gen}_{idx}` tail the controller
 *    assigns (`paragraphIndexFor`/`utteranceId`, TtsController) and rejects
 *    malformed ids; generation matching stays the controller's job.
 */
class RenderedAudioTrackerTest {

    private val offsets = listOf(0L, 1_000L, 2_500L, 4_000L)
    private val tracker = RenderedAudioTracker(offsetsMs = offsets, durationMs = 5_000L)

    // -- paragraphIndexAt -----------------------------------------------------

    @Test
    fun `paragraphIndexAt returns 0 at position 0`() {
        tracker.paragraphIndexAt(0) shouldBe 0
    }

    @Test
    fun `paragraphIndexAt returns the floor index between offsets`() {
        tracker.paragraphIndexAt(999) shouldBe 0
        tracker.paragraphIndexAt(1_500) shouldBe 1
        tracker.paragraphIndexAt(3_999) shouldBe 2
    }

    @Test
    fun `paragraphIndexAt lands exactly on an offset boundary`() {
        tracker.paragraphIndexAt(1_000) shouldBe 1
        tracker.paragraphIndexAt(2_500) shouldBe 2
        tracker.paragraphIndexAt(4_000) shouldBe 3
    }

    @Test
    fun `paragraphIndexAt clamps to the last index at and past EOF`() {
        tracker.paragraphIndexAt(4_999) shouldBe 3
        tracker.paragraphIndexAt(5_000) shouldBe 3
        tracker.paragraphIndexAt(999_999) shouldBe 3
    }

    @Test
    fun `paragraphIndexAt clamps positions before the first offset to 0`() {
        tracker.paragraphIndexAt(-50) shouldBe 0
        RenderedAudioTracker(listOf(500L, 1_000L), durationMs = 2_000L)
            .paragraphIndexAt(0) shouldBe 0
    }

    @Test
    fun `paragraphIndexAt on empty offsets returns 0`() {
        RenderedAudioTracker(emptyList(), durationMs = 1_000L)
            .paragraphIndexAt(123) shouldBe 0
    }

    // -- isParagraphFinished --------------------------------------------------

    @Test
    fun `isParagraphFinished is false below the next offset and true at it`() {
        tracker.isParagraphFinished(0, 999) shouldBe false
        tracker.isParagraphFinished(0, 1_000) shouldBe true
        tracker.isParagraphFinished(0, 1_500) shouldBe true
    }

    @Test
    fun `isParagraphFinished for the last paragraph is bounded by durationMs`() {
        tracker.isParagraphFinished(3, 4_999) shouldBe false
        tracker.isParagraphFinished(3, 5_000) shouldBe true
        tracker.isParagraphFinished(3, 5_001) shouldBe true
    }

    @Test
    fun `paragraph finish fires exactly once under a 100ms poll cadence`() {
        // Mirrors the engine's poller: position marches in ~100ms ticks past
        // the boundary; the consumer stops polling on the first "finished"
        // report — so the finish must be observable exactly once.
        var fired = 0
        var position = 0L
        while (position <= 2_000L) {
            if (tracker.isParagraphFinished(0, position)) {
                fired++
                break
            }
            position += 100
        }
        fired shouldBe 1
        // Monotonic contract: once finished it stays finished, so no later
        // poll can "un-finish" and re-fire.
        tracker.isParagraphFinished(0, position) shouldBe true
    }

    // -- indexFromUtterance ---------------------------------------------------

    @Test
    fun `indexFromUtterance parses the tts-gen-idx tail`() {
        tracker.indexFromUtterance("tts_3_7") shouldBe 7
        tracker.indexFromUtterance("tts_0_0") shouldBe 0
        tracker.indexFromUtterance("tts_12_42") shouldBe 42
    }

    @Test
    fun `indexFromUtterance returns null for malformed ids`() {
        tracker.indexFromUtterance("") shouldBe null
        tracker.indexFromUtterance("hello") shouldBe null
        tracker.indexFromUtterance("tts_3") shouldBe null
        tracker.indexFromUtterance("tts_3_x") shouldBe null
        tracker.indexFromUtterance("tts_3_7_extra") shouldBe null
        tracker.indexFromUtterance("x_3_7") shouldBe null
    }
}
