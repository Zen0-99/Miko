package eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing

import eu.kanade.tachiyomi.ui.reader.novel.tts.PocketEngineCatalog
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Merged port of marmalade-tts-android's preprocessing test suite:
 * PreprocessorTest + EmojiProsodyTest + ProsodyApplierTest +
 * EngineProfilesTest, retargeted at the Pocket engine profile (the only
 * engine Miko installs). JUnit5 + Kotest assertions per Miko convention.
 *
 * Pure JVM — number spell-out is hand-rolled (not ICU-backed) and
 * ProsodyApplier works on raw ShortArrays, so no Android runtime needed.
 */
class PocketPreprocessingTest {

    private val preprocessor = Preprocessor()

    /** Convenience: run with every rule enabled (the pocket default). */
    private fun all(text: String): String =
        preprocessor.apply(text, EngineProfiles.defaultsFor("pocket-tts-en-v2026_04"))

    /** Convenience: run with one rule only. */
    private fun only(rule: String, text: String): String =
        preprocessor.apply(text, setOf(rule))

    // ── whitespace collapse (newline-preserving) ─────────────────────

    @Test
    fun `whitespace horizontal runs collapse to one space`() {
        preprocessor.apply("a  \t b   c", emptySet()) shouldBe "a b c"
    }

    @Test
    fun `whitespace single newlines survive`() {
        // TextChunker splits clauses on \n+ and Pocket's line-break rewrite
        // turns newlines into sentence pauses — the collapse must not eat them.
        preprocessor.apply("Title  \n  Body   text", emptySet()) shouldBe "Title\nBody text"
    }

    @Test
    fun `whitespace paragraph breaks normalize to double newline`() {
        // \n\s*\n is TextChunker's paragraph boundary.
        preprocessor.apply("Para one. \n \n\n  Para two.", emptySet()) shouldBe
            "Para one.\n\nPara two."
    }

    // ── terminal punctuation (CJK + Spanish aware) ──────────────────────

    @Test
    fun `terminal appends period when missing`() {
        only("terminal_punctuation", "hello") shouldBe "hello."
    }

    @Test
    fun `terminal leaves ascii punctuation alone`() {
        only("terminal_punctuation", "hello!") shouldBe "hello!"
        only("terminal_punctuation", "really?") shouldBe "really?"
    }

    @Test
    fun `terminal recognizes japanese enders no spurious period`() {
        // Japanese ？ / 。 / ！ already terminate — must NOT get an ASCII "."
        // appended.
        only("terminal_punctuation", "元気ですか？") shouldBe "元気ですか？"
        only("terminal_punctuation", "こんにちは。") shouldBe "こんにちは。"
        only("terminal_punctuation", "すごい！") shouldBe "すごい！"
    }

    @Test
    fun `terminal spanish inverted marks survive end still recognized`() {
        // ¿ is sentence-INITIAL (not terminal); the sentence still ends in
        // ASCII ? so no spurious period, and ¿ is preserved.
        only("terminal_punctuation", "¿Cómo estás?") shouldBe "¿Cómo estás?"
        only("terminal_punctuation", "¡Hola!") shouldBe "¡Hola!"
    }

    // ── currency ────────────────────────────────────────────────────

    @Test
    fun `currency dollars integer`() {
        only("currency", "\$100") shouldBe "100 dollars"
    }

    @Test
    fun `currency one dollar is singular`() {
        only("currency", "\$1") shouldBe "1 dollar"
    }

    @Test
    fun `currency dollars and cents`() {
        only("currency", "\$3.50") shouldBe "3 dollars and 50 cents"
    }

    @Test
    fun `currency pounds and pennies`() {
        // British "penny" pluralises irregularly to "pennies".
        only("currency", "£3.50") shouldBe "3 pounds and 50 pennies"
    }

    @Test
    fun `currency overflowing amount is left unchanged`() {
        // >19 digits overflows Long. Must pass through untouched instead
        // of throwing NumberFormatException out of the synthesis pipeline.
        val whale = "\$99999999999999999999"
        only("currency", whale) shouldBe whale
        val whaleCents = "\$99999999999999999999.99"
        only("currency", whaleCents) shouldBe whaleCents
    }

    // ── percentage ──────────────────────────────────────────────────

    @Test
    fun `percentage integer`() {
        only("percentage", "50%") shouldBe "50 percent"
    }

    @Test
    fun `percentage decimal`() {
        only("percentage", "99.9%") shouldBe "99.9 percent"
    }

    // ── ordinal ─────────────────────────────────────────────────────

    @Test
    fun `ordinal first`() {
        only("ordinal", "1st") shouldBe "first"
    }

    @Test
    fun `ordinal twenty third`() {
        only("ordinal", "23rd") shouldBe "twenty-third"
    }

    // ── number (with year protection) ───────────────────────────────

    @Test
    fun `number small integer`() {
        only("number", "42") shouldBe "forty-two"
    }

    @Test
    fun `number year left as digits`() {
        // Year-protection: 4-digit numbers 1900-2099 stay as digits.
        only("number", "1985") shouldBe "1985"
    }

    @Test
    fun `number year band boundary`() {
        only("number", "2099") shouldBe "2099"
        // 1899 is outside the protection band → verbalize as cardinal.
        (only("number", "1899") != "1899") shouldBe true
    }

    @Test
    fun `number decimal pointwise`() {
        only("number", "99.5") shouldBe "ninety-nine point five"
    }

    // ── abbreviation ────────────────────────────────────────────────

    @Test
    fun `abbreviation doctor`() {
        only("abbreviation", "Dr. Smith") shouldBe "doctor Smith"
    }

    @Test
    fun `abbreviation etcetera`() {
        only("abbreviation", "etc.").trim() shouldBe "et cetera"
    }

    @Test
    fun `abbreviation does not match mid word`() {
        // Regression: dropping the \b before the short-abbreviation group
        // made "test." match "st." → "te saint", swallowing the period.
        val sentence = "Hello from the smoke test. This is a second sentence."
        only("abbreviation", sentence) shouldBe sentence
        only("abbreviation", "They left. Then came back.") shouldBe
            "They left. Then came back."
    }

    @Test
    fun `abbreviation saint still expands at word start`() {
        only("abbreviation", "St. Louis") shouldBe "saint Louis"
    }

    @Test
    fun `abbreviation dot separated acronym`() {
        // U.S.A. → U S A (dot-separated branch).
        only("abbreviation", "U.S.A.") shouldBe "U S A"
    }

    // ── email ───────────────────────────────────────────────────────

    @Test
    fun `email simple`() {
        only("email", "alice@example.com") shouldBe "alice at example dot com"
    }

    @Test
    fun `email multipart domain`() {
        only("email", "bob@mail.example.co.uk") shouldBe
            "bob at mail dot example dot co dot uk"
    }

    // ── url ─────────────────────────────────────────────────────────

    @Test
    fun `url https with path`() {
        only("url", "https://example.com/foo/bar") shouldBe "example dot com"
    }

    @Test
    fun `url strips www`() {
        only("url", "https://www.example.com") shouldBe "example dot com"
    }

    // ── html ────────────────────────────────────────────────────────

    @Test
    fun `html strips tags and decodes entities`() {
        only("html", "<p>Hello &amp; welcome</p>") shouldBe "Hello & welcome"
    }

    @Test
    fun `html decodes named entities`() {
        only("html", "1 &lt; 2 &amp; 3 &gt; 0") shouldBe "1 < 2 & 3 > 0"
    }

    // ── markdown ────────────────────────────────────────────────────

    @Test
    fun `markdown strips bold and italic`() {
        only("markdown", "**Hello** *world*!") shouldBe "Hello world!"
    }

    @Test
    fun `markdown strips link keeps anchor text`() {
        only("markdown", "[Click here](https://example.com) for more") shouldBe
            "Click here for more"
    }

    // ── emoji rule ──────────────────────────────────────────────────

    @Test
    fun `emoji rule strips with whitespace collapse`() {
        only("emoji", "Hello 🤣 world") shouldBe "Hello world"
    }

    // ── math, ampersand, hashtag ────────────────────────────────────

    @Test
    fun `math plus between spaces`() {
        only("math", "2 + 2") shouldBe "2 plus 2"
    }

    @Test
    fun `ampersand with spaces`() {
        only("ampersand", "you & me") shouldBe "you and me"
    }

    @Test
    fun `hashtag number`() {
        only("hashtag", "#100") shouldBe "number 100"
    }

    @Test
    fun `hashtag word`() {
        only("hashtag", "#hello") shouldBe "hashtag hello"
    }

    // ── composition ─────────────────────────────────────────────────

    @Test
    fun `composition realistic paragraph`() {
        val input = "Send me \$100 by Friday — see https://example.com 🤣"
        val out = all(input)
        out shouldContain "dollars"
        out shouldContain "example dot com"
        out shouldNotContain "🤣"
        out shouldNotContain "  "
    }

    @Test
    fun `composition empty set is identity modulo whitespace`() {
        val s = "Plain text, nothing to see here."
        preprocessor.apply(s, emptySet()) shouldBe s
    }

    @Test
    fun `composition collapses internal whitespace`() {
        preprocessor.apply("a   b\tc", emptySet()) shouldBe "a b c"
    }

    @Test
    fun `composition unknown rule names ignored`() {
        preprocessor.apply("no change", setOf("nope_not_a_rule")) shouldBe "no change"
    }

    // ── repeated_punctuation ────────────────────────────────────────

    @Test
    fun `repeated punct three dots collapse to ellipsis`() {
        only("repeated_punctuation", "wait...") shouldBe "wait…"
    }

    @Test
    fun `repeated punct more than three dots also collapse`() {
        only("repeated_punctuation", "wait....") shouldBe "wait…"
        only("repeated_punctuation", "wait......") shouldBe "wait…"
    }

    @Test
    fun `repeated punct two dots is not enough to collapse`() {
        only("repeated_punctuation", "wait..") shouldBe "wait.."
    }

    @Test
    fun `repeated punct bangs collapse to one`() {
        only("repeated_punctuation", "wow!!") shouldBe "wow!"
        only("repeated_punctuation", "wow!!!!!!") shouldBe "wow!"
    }

    @Test
    fun `repeated punct questions collapse to one`() {
        only("repeated_punctuation", "really??") shouldBe "really?"
        only("repeated_punctuation", "really??????") shouldBe "really?"
    }

    @Test
    fun `repeated punct mixed rhetorical is preserved`() {
        only("repeated_punctuation", "really?!") shouldBe "really?!"
        only("repeated_punctuation", "wait!?") shouldBe "wait!?"
    }

    @Test
    fun `repeated punct runs across sentence each handled`() {
        only("repeated_punctuation", "First... second!!! third??") shouldBe
            "First… second! third?"
    }

    @Test
    fun `repeated punct single characters unchanged`() {
        val s = "This is a sentence. Another! And one more?"
        only("repeated_punctuation", s) shouldBe s
    }

    // ── disabled rules are no-ops ───────────────────────────────────

    @Test
    fun `disabled currency leaves dollar amount intact`() {
        // With only `number` enabled, "$100" → "$" + verbalized "100".
        val out = only("number", "\$100")
        out shouldContain "$"
        out shouldContain "one hundred"
    }

    // ═════════════════════════════════════════════════════════════════
    // EmojiProsody
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `detect on plain text returns Neutral with zero intensity`() {
        val hint = EmojiProsody.detect("Hello")
        hint.emotion shouldBe Emotion.Neutral
        hint.intensity shouldBe 0f
    }

    @Test
    fun `detect on empty string returns Neutral with zero intensity`() {
        val hint = EmojiProsody.detect("")
        hint.emotion shouldBe Emotion.Neutral
        hint.intensity shouldBe 0f
    }

    @Test
    fun `detect ignores unrecognized emojis like phone`() {
        // 📱 is a valid emoji but is not in the emotion set.
        val hint = EmojiProsody.detect("My 📱 is dead")
        hint.emotion shouldBe Emotion.Neutral
        hint.intensity shouldBe 0f
    }

    @Test
    fun `detect single amused emoji yields intensity one third`() {
        val hint = EmojiProsody.detect("Hello 🤣")
        hint.emotion shouldBe Emotion.Amused
        abs(hint.intensity - 1f / 3f) shouldBeLessThan 1e-4f
    }

    @Test
    fun `detect single sad emoji maps to Sad`() {
        EmojiProsody.detect("I miss you 😭").emotion shouldBe Emotion.Sad
    }

    @Test
    fun `detect single angry emoji maps to Angry`() {
        EmojiProsody.detect("I am so 😡").emotion shouldBe Emotion.Angry
    }

    @Test
    fun `detect three identical emojis caps intensity at one`() {
        val hint = EmojiProsody.detect("Hello 🤣🤣🤣")
        hint.emotion shouldBe Emotion.Amused
        hint.intensity shouldBe 1f
    }

    @Test
    fun `detect more than three identical emojis stays at one`() {
        val hint = EmojiProsody.detect("Hello 🤣🤣🤣🤣🤣")
        hint.emotion shouldBe Emotion.Amused
        hint.intensity shouldBe 1f
    }

    @Test
    fun `detect tied counts last occurrence wins sad over amused`() {
        val hint = EmojiProsody.detect("Hello 🤣 then 😭")
        hint.emotion shouldBe Emotion.Sad
        abs(hint.intensity - 1f / 3f) shouldBeLessThan 1e-4f
    }

    @Test
    fun `detect three way tie picks the latest emoji angry`() {
        EmojiProsody.detect("Hello 🤣 then 😭 then 😡").emotion shouldBe Emotion.Angry
    }

    @Test
    fun `detect higher count beats latest occurrence`() {
        // Sad appears twice, amused once at the end — count beats recency.
        val hint = EmojiProsody.detect("😭 well 😭 ok 🤣")
        hint.emotion shouldBe Emotion.Sad
        abs(hint.intensity - 2f / 3f) shouldBeLessThan 1e-4f
    }

    @Test
    fun `stripEmojis removes recognized emoji and collapses whitespace`() {
        EmojiProsody.stripEmojis("Hello 🤣 world") shouldBe "Hello world"
    }

    @Test
    fun `stripEmojis leaves plain text unchanged`() {
        EmojiProsody.stripEmojis("Plain text") shouldBe "Plain text"
    }

    @Test
    fun `stripEmojis leaves unrecognized emojis alone`() {
        EmojiProsody.stripEmojis("My 📱 is dead") shouldBe "My 📱 is dead"
    }

    @Test
    fun `stripEmojis handles multiple emojis without leaving blank gaps`() {
        EmojiProsody.stripEmojis("I 😭 miss 😭 you 😭 so much") shouldBe
            "I miss you so much"
    }

    @Test
    fun `isEmotionEmoji true for recognized emoji`() {
        EmojiProsody.isEmotionEmoji("🤣") shouldBe true
    }

    @Test
    fun `isEmotionEmoji false for ordinary letter`() {
        EmojiProsody.isEmotionEmoji("a") shouldBe false
    }

    @Test
    fun `isEmotionEmoji false for non emotion emoji like phone`() {
        EmojiProsody.isEmotionEmoji("📱") shouldBe false
    }

    @Test
    fun `isEmotionEmoji recognizes the full upstream eleven`() {
        val all = listOf("😍", "😡", "😎", "😭", "🙄", "😁", "🙂", "🤣", "😮", "😅", "🤔")
        for (e in all) {
            EmojiProsody.isEmotionEmoji(e) shouldBe true
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // ProsodyApplier — property-based, no coefficient pinning
    // ═════════════════════════════════════════════════════════════════

    private val sampleRate = 24_000

    @Test
    fun `Neutral returns the input untouched`() {
        val input = sineWave(440.0, sampleRate, durationMs = 100, amplitude = 8_000)
        val out = ProsodyApplier.apply(input, sampleRate, Emotion.Neutral)
        out.size shouldBe input.size
        for (i in input.indices) {
            (abs(out[i].toInt() - input[i].toInt()) <= 2) shouldBe true
        }
    }

    @Test
    fun `Happy raises pitch higher zero crossing density than Neutral`() {
        val input = sineWave(200.0, sampleRate, durationMs = 1000, amplitude = 16_384)
        val neutralOut = ProsodyApplier.apply(input, sampleRate, Emotion.Neutral)
        val happyOut = ProsodyApplier.apply(input, sampleRate, Emotion.Happy)

        val neutralDensity = zeroCrossingCount(neutralOut).toDouble() / neutralOut.size
        val happyDensity = zeroCrossingCount(happyOut).toDouble() / happyOut.size

        happyDensity shouldBeGreaterThan neutralDensity
    }

    @Test
    fun `Sad lowers volume vs Neutral`() {
        val input = sineWave(440.0, sampleRate, durationMs = 200, amplitude = 16_384)
        val neutralOut = ProsodyApplier.apply(input, sampleRate, Emotion.Neutral)
        val sadOut = ProsodyApplier.apply(input, sampleRate, Emotion.Sad)

        // At least 1 dB quieter (ratio < 0.89); gainDb -3 → ratio ≈ 0.708.
        (rms(sadOut) / rms(neutralOut)) shouldBeLessThan 0.89
    }

    @Test
    fun `Angry raises volume vs Neutral`() {
        val input = sineWave(440.0, sampleRate, durationMs = 200, amplitude = 16_384)
        val neutralOut = ProsodyApplier.apply(input, sampleRate, Emotion.Neutral)
        val angryOut = ProsodyApplier.apply(input, sampleRate, Emotion.Angry)

        // At least 1 dB louder (ratio > 1.12); gainDb +3 → ratio ≈ 1.413.
        (rms(angryOut) / rms(neutralOut)) shouldBeGreaterThan 1.12
    }

    @Test
    fun `Nervous adds an amplitude wobble windowed RMS std dev jumps`() {
        val input = sineWave(1_000.0, sampleRate, durationMs = 1000, amplitude = 16_384)
        val neutralOut = ProsodyApplier.apply(input, sampleRate, Emotion.Neutral)
        val nervousOut = ProsodyApplier.apply(input, sampleRate, Emotion.Nervous)

        val windowSize = 240 // 10 ms at 24 kHz
        val neutralStd = stdDevOfWindowedRms(neutralOut, windowSize)
        val nervousStd = stdDevOfWindowedRms(nervousOut, windowSize)

        neutralStd shouldBeLessThan 50.0
        nervousStd shouldBeGreaterThan 200.0
        nervousStd shouldBeGreaterThan neutralStd * 4.0
    }

    @Test
    fun `all emotions land within plus or minus 20 percent of input length`() {
        val n = 4_800
        val input = sineWave(440.0, sampleRate, durationMs = 200, amplitude = 8_000)
        input.size shouldBe n

        for (emotion in Emotion.entries) {
            val out = ProsodyApplier.apply(input, sampleRate, emotion)
            val ratio = out.size.toDouble() / n
            (ratio in 0.80..1.20) shouldBe true
        }
    }

    @Test
    fun `empty input returns empty output for every emotion`() {
        for (emotion in Emotion.entries) {
            ProsodyApplier.apply(ShortArray(0), sampleRate, emotion).size shouldBe 0
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // EngineProfiles
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `pocket profile includes every rule`() {
        // Pocket does no native text normalization upstream — its default
        // profile contains every rule offered.
        val profile = EngineProfiles.defaultsFor("pocket-tts-en-v2026_04")
        val allNames = PreprocessingRules.ALL.map { it.name }.toSet()
        profile shouldBe allNames
    }

    @Test
    fun `kitten profile includes every rule`() {
        val profile = EngineProfiles.defaultsFor("kitten-direct-v0_8")
        val allNames = PreprocessingRules.ALL.map { it.name }.toSet()
        profile shouldBe allNames
    }

    @Test
    fun `emojivoice intentionally omits emoji rule`() {
        // EmojiVoice consumes the emoji as its speaker-id signal — the
        // `emoji` rule must stay off or every utterance goes neutral.
        val profile = EngineProfiles.defaultsFor("emojivoice")
        ("emoji" in profile) shouldBe false
    }

    @Test
    fun `kokoro omits what misaki handles natively`() {
        val profile = EngineProfiles.defaultsFor("kokoro-direct-v1_0")
        ("number" in profile) shouldBe false
        ("abbreviation" in profile) shouldBe false
        ("ordinal" in profile) shouldBe false
    }

    @Test
    fun `coqui omits number but keeps abbreviation`() {
        val profile = EngineProfiles.defaultsFor("coqui")
        ("number" in profile) shouldBe false
        ("abbreviation" in profile) shouldBe true
    }

    @Test
    fun `unknown engine falls back to kitten profile`() {
        val kitten = EngineProfiles.defaultsFor("kitten-direct-v0_8")
        val unknown = EngineProfiles.defaultsFor("nonexistent-engine")
        unknown shouldBe kitten
    }

    @Test
    fun `every default profile only references real rules`() {
        val realNames = PreprocessingRules.ALL.map { it.name }.toSet()
        for ((_, profile) in EngineProfiles.DEFAULT_PROFILES) {
            for (rule in profile) {
                rule shouldBeIn realNames
            }
        }
    }

    @Test
    fun `every installable engine has a profile defined`() {
        // Miko's install catalog carries exactly one engine — it must have
        // a non-fallback profile or Settings → Text preprocessing would
        // render an empty toggle list for it.
        EngineProfiles.DEFAULT_PROFILES.containsKey(PocketEngineCatalog.POCKET_TTS_EN.name) shouldBe
            true
    }
}

// ── ProsodyApplier helpers (file-private top-level funs) ──────────────

private fun sineWave(
    freqHz: Double,
    sampleRate: Int,
    durationMs: Int,
    amplitude: Int,
): ShortArray {
    val n = (sampleRate.toLong() * durationMs / 1000L).toInt()
    val out = ShortArray(n)
    val twoPiF = 2.0 * PI * freqHz
    for (i in 0 until n) {
        val t = i.toDouble() / sampleRate
        out[i] = (amplitude * sin(twoPiF * t)).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
    return out
}

private fun rms(pcm: ShortArray): Double {
    if (pcm.isEmpty()) return 0.0
    var acc = 0.0
    for (v in pcm) {
        val f = v.toDouble()
        acc += f * f
    }
    return sqrt(acc / pcm.size)
}

/**
 * Count sign changes through zero. A sample exactly at 0 is treated as
 * non-negative, so the boundary 0→-1 counts once and -1→0 counts once.
 */
private fun zeroCrossingCount(pcm: ShortArray): Int {
    if (pcm.size < 2) return 0
    var count = 0
    var prevSign = if (pcm[0] >= 0) 1 else -1
    for (i in 1 until pcm.size) {
        val sign = if (pcm[i] >= 0) 1 else -1
        if (sign != prevSign) {
            count++
            prevSign = sign
        }
    }
    return count
}

/**
 * Split [pcm] into non-overlapping windows of [windowSize] samples,
 * compute each window's RMS, and return the population standard deviation
 * of those RMS values. Used to detect envelope wobble (tremolo).
 */
private fun stdDevOfWindowedRms(pcm: ShortArray, windowSize: Int): Double {
    val nWindows = pcm.size / windowSize
    if (nWindows < 2) return 0.0
    val rmsValues = DoubleArray(nWindows)
    for (w in 0 until nWindows) {
        val start = w * windowSize
        var acc = 0.0
        for (i in 0 until windowSize) {
            val f = pcm[start + i].toDouble()
            acc += f * f
        }
        rmsValues[w] = sqrt(acc / windowSize)
    }
    var mean = 0.0
    for (v in rmsValues) mean += v
    mean /= nWindows
    var variance = 0.0
    for (v in rmsValues) {
        val d = v - mean
        variance += d * d
    }
    return sqrt(variance / nWindows)
}
