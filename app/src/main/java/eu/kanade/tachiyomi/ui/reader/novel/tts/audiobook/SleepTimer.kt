package eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook

/**
 * Audiobook sleep timer state (D-14/PB-5). Pure value type — the controller
 * feeds it clock ticks and player events; it only answers "should playback
 * pause now".
 *
 * - [Off]: never pauses.
 * - [Minutes]: pauses once the wall-clock deadline is reached (playback time
 *   is irrelevant — a paused player still hits its deadline).
 * - [EndOfChapter]: pauses when the current chapter's playback ends instead
 *   of auto-advancing to the next track.
 */
sealed interface SleepTimer {

    /** True when the timer wants playback paused at wall-clock [nowMs]. */
    fun shouldPause(nowMs: Long): Boolean = false

    /** True when reaching the end of the current track should pause playback. */
    fun pauseOnChapterEnd(): Boolean = false

    data object Off : SleepTimer

    /** [presetMinutes] remembers which preset armed it — for UI row selection. */
    data class Minutes(val deadlineMs: Long, val presetMinutes: Int) : SleepTimer {
        override fun shouldPause(nowMs: Long): Boolean = nowMs >= deadlineMs

        /** Milliseconds remaining — negative once expired. For UI countdowns. */
        fun remainingMs(nowMs: Long): Long = deadlineMs - nowMs
    }

    data object EndOfChapter : SleepTimer {
        override fun pauseOnChapterEnd(): Boolean = true
    }

    companion object {
        /** Preset minute choices surfaced by the player UI. */
        val MINUTE_PRESETS = listOf(5, 15, 30, 45, 60)

        /** Arm a minutes timer relative to [nowMs]. */
        fun armMinutes(minutes: Int, nowMs: Long): Minutes =
            Minutes(nowMs + minutes * 60_000L, minutes)
    }
}
