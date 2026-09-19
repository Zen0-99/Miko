package eu.kanade.tachiyomi.ui.reader.novel.tts.audio

import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.EmojiProsody
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.Emotion
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.Preprocessor
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.ProsodyApplier
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.ProsodyHint

// -----------------------------------------------------------------------------
// Shared synthesis pipeline — streaming-adapted port of marmalade-tts-android
// `audio/SynthesisPipeline.kt`.
//
// Upstream wrapped a one-shot `synthesize()` call inside the chain and
// returned a `PipelineResult` sealed class. Miko's Pocket engine streams
// chunks, so the chain is split at the synthesis seam:
//
//   Canonical chain (upstream order preserved):
//     1. EmojiProsody.detect(rawText)                  ──► ProsodyHint
//     2. Preprocessor.apply(rawText, rules)            ──► normalised text
//     3. EmojiProsody.stripEmojis(normalised)          ──► engine-safe text
//     4. engine.synthesizeStream(text, ...)            ──► Flow<SynthAudio>
//        (lives inside the engine — NOT this module)
//     5. ProsodyApplier.apply(pcm, sr, hint.emotion)   ──► emotion-shaped PCM
//        (runs per emitted chunk, via shapeChunk())
//     6. EffectChain.applyChain(pcm, sr, effectBlocks) ──► effect-shaped PCM
//        DEFERRED — Marmalade's EffectChain/StreamingEffectChain is a
//        deferred feature (voice-effects presets); intentionally not ported.
//
//   Steps 1-3 run once per input text via [prepare]; step 5 runs per
//   synthesized chunk via [shapeChunk]. Callers stay in charge of
//   dispatchers and the engine call itself.
// -----------------------------------------------------------------------------

/**
 * Result of [SynthesisPipeline.prepare]: the engine-safe text plus the
 * prosody hint extracted from the raw (pre-strip) input.
 */
data class PreparedText(
    /** Preprocessed, emoji-stripped text to feed the engine. */
    val engineText: String,
    /** Emotion/intensity detected from the raw text, for [SynthesisPipeline.shapeChunk]. */
    val hint: ProsodyHint,
)

object SynthesisPipeline {

    /**
     * Run chain steps 1-3: emoji-prosody detect → preprocessing rules →
     * emotion-emoji strip. Returns null when the input collapses to
     * nothing speakable (blank text, or emoji-only text after stripping)
     * — the upstream `PipelineResult.Empty` case. Callers should skip
     * the engine entirely on null.
     *
     * Emoji prosody is computed off the *raw* text (the emojis are the
     * signal). The text fed to the engine then goes through:
     *   1. User-configured text preprocessing (currency, numbers,
     *      abbreviations, … per the per-engine enabled-rules set).
     *   2. Emotion-emoji stripping — a safety net for the emojis in the
     *      EmojiProsody set, in case the user disabled the preprocessing
     *      `emoji` rule.
     */
    fun prepare(
        rawText: String,
        enabledRules: Set<String>,
        preprocessor: Preprocessor = Preprocessor(),
    ): PreparedText? {
        if (rawText.isBlank()) return null
        val hint = EmojiProsody.detect(rawText)
        val preprocessed = preprocessor.apply(rawText, enabledRules)
        val text = EmojiProsody.stripEmojis(preprocessed)
        if (text.isBlank()) return null
        return PreparedText(engineText = text, hint = hint)
    }

    /**
     * Run chain step 5 on one synthesized chunk: apply the detected
     * emotion's prosody curve to [pcm] (mono PCM16 at [sampleRate] Hz).
     * Identity for Neutral hints — safe to call unconditionally on every
     * emitted chunk.
     */
    fun shapeChunk(pcm: ShortArray, sampleRate: Int, hint: ProsodyHint): ShortArray =
        if (hint.emotion == Emotion.Neutral) {
            pcm
        } else {
            ProsodyApplier.apply(pcm, sampleRate, hint.emotion)
        }
}
