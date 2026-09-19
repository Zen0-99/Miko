package eu.kanade.tachiyomi.ui.reader.novel.tts.pocket

/**
 * Synthesised audio emitted by [PocketEngine.synthesizeStream] and returned
 * by [PocketEngine.synthesize].
 *
 * `pcm` is 16-bit signed PCM, mono, in little-endian native order.
 * The engine produces `FloatArray` in -1..1 internally; it clamps and
 * converts to `ShortArray` here so the AudioTrack consumer has no
 * further work to do.
 *
 * Ported from marmalade-tts-android `engine/EngineTypes.kt` (only this
 * type — the rest of that file is Marmalade-specific surface).
 */
data class SynthAudio(val pcm: ShortArray, val sampleRate: Int) {

    // Generated equality so test fixtures can compare audio buffers directly.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SynthAudio) return false
        return sampleRate == other.sampleRate && pcm.contentEquals(other.pcm)
    }

    override fun hashCode(): Int =
        31 * pcm.contentHashCode() + sampleRate
}
