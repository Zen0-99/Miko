package eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** JVM tests for [SleepTimer] expiry semantics. */
class SleepTimerTest {

    @Test
    fun `off never pauses`() {
        SleepTimer.Off.shouldPause(Long.MAX_VALUE) shouldBe false
        SleepTimer.Off.pauseOnChapterEnd() shouldBe false
    }

    @Test
    fun `minutes timer pauses at and past the deadline`() {
        val timer = SleepTimer.armMinutes(15, nowMs = 1_000L)

        timer.shouldPause(1_000L) shouldBe false
        timer.shouldPause(1_000L + 15 * 60_000L - 1) shouldBe false
        timer.shouldPause(1_000L + 15 * 60_000L) shouldBe true
        timer.shouldPause(1_000L + 15 * 60_000L + 1) shouldBe true
    }

    @Test
    fun `minutes timer ignores chapter end`() {
        SleepTimer.armMinutes(5, nowMs = 0L).pauseOnChapterEnd() shouldBe false
    }

    @Test
    fun `end of chapter pauses on track end but never on elapsed time`() {
        SleepTimer.EndOfChapter.pauseOnChapterEnd() shouldBe true
        SleepTimer.EndOfChapter.shouldPause(Long.MAX_VALUE) shouldBe false
    }

    @Test
    fun `remaining counts down to the deadline`() {
        val timer = SleepTimer.armMinutes(5, nowMs = 0L)
        timer.remainingMs(60_000L) shouldBe 4 * 60_000L
    }

    @Test
    fun `presets match the locked UI choices`() {
        SleepTimer.MINUTE_PRESETS shouldBe listOf(5, 15, 30, 45, 60)
    }
}
