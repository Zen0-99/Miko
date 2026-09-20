package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.PocketEngine
import java.io.File

/**
 * Static catalog of the voices inside the `pocket-tts-en-v2026_04` bundle.
 *
 * The base bundle ships the 6 commercial-safe predefined voices; upstream's
 * `cosette` (Expresso) and `jean` (EARS) are CC-BY-NC-4.0 and are deliberately
 * excluded. The Miko-hosted bundle additionally injects UK-accented VCTK
 * speakers as `voices/vctk_pNNN.wav` (phase-04 D-07) — those are discovered
 * from disk via [discoverVoices] rather than listed here, since the base
 * (fallback) bundle does not contain them.
 *
 * Pocket voices are reference WAVs in `voices/<name>.wav`; on first use the
 * engine encodes the WAV through `mimi_encoder` into a cached embedding.
 * Voices are addressed by name, not index — order is cosmetic.
 */
object PocketVoiceCatalog {

    const val ENGINE = PocketEngineCatalog.ENGINE_NAME

    /** `mimi_decoder` emits 24 kHz mono PCM (bundle.json `sample_rate`). */
    const val SAMPLE_RATE = 24000

    /**
     * Default voice — `eponine` (VCTK p262, Scottish), deliberately different
     * from upstream's alba default (phase-04 D-08).
     */
    const val DEFAULT_VOICE = "eponine"

    private val EXTRA_VOICE_WAV = Regex("vctk_(p\\d{3})\\.wav")

    /**
     * Accent labels for the curated UK extras in `tools/pocket-uk-speakers.txt`
     * (verified against VCTK `speaker-info.txt`). Unknown `vctk_*` ids fall
     * back to "British" — the build script only admits UK accents.
     */
    private val EXTRA_VOICE_ACCENTS = mapOf(
        "p225" to "English",
        "p226" to "English",
        "p227" to "English",
        "p228" to "English",
        "p229" to "English",
        "p253" to "Welsh",
    )

    /**
     * The 6 commercial-safe predefined voices, in upstream
     * `bundle.json#predefined_voices` order. Gender from F0 estimation of the
     * bundled reference WAVs (~Hz noted); names don't map reliably to the Les
     * Misérables canonical genders — trust the audio, not the name.
     */
    val baseVoices: List<PocketVoice> = listOf(
        // Alba Mackenna reference recording (not VCTK). ~133 Hz.
        PocketVoice("alba", "Alba", "male", "CC-BY-4.0"),
        // VCTK p303 — Canadian (Toronto), not UK. ~214 Hz.
        PocketVoice("azelma", "Azelma", "female", "CC-BY-4.0", accent = "Canadian"),
        // VCTK p262 — Scottish (Edinburgh). ~164 Hz. Locked default (D-08).
        PocketVoice("eponine", "Eponine", "female", "CC-BY-4.0", accent = "Scottish"),
        // VCTK p244 — English (Southern England). ~211 Hz.
        PocketVoice("fantine", "Fantine", "female", "CC-BY-4.0", accent = "English"),
        // Unmute Voice Donation Project, CC0. ~96 Hz.
        PocketVoice("javert", "Javert", "male", "CC0"),
        // Unmute Voice Donation Project, CC0. ~214 Hz.
        PocketVoice("marius", "Marius", "female", "CC0"),
    )

    /**
     * Map a `voices/<x>.wav` filename to an extra voice name:
     * `vctk_p225.wav` → `vctk_p225`. Returns null for anything else.
     */
    fun extraVoiceName(wavFileName: String): String? =
        EXTRA_VOICE_WAV.matchEntire(wavFileName)?.let { "vctk_${it.groupValues[1]}" }

    /**
     * License for a voice name. Base voices carry their catalog license;
     * `vctk_*` extras are always CC-BY-4.0 (CSTR VCTK Corpus, U. Edinburgh);
     * unknown names return "".
     */
    fun licenseFor(name: String): String = when {
        name.startsWith("vctk_") -> "CC-BY-4.0"
        else -> baseVoices.firstOrNull { it.name == name }?.license ?: ""
    }

    /**
     * Resolve a requested voice name to one the installed bundle actually
     * ships (plan 05-01: moved verbatim from `PocketTtsEngine.resolveVoice`,
     * minus the `currentVoice` memoization — a PURE lookup that never
     * mutates preferences, so the renderer can record the resolved name in
     * the manifest without side effects).
     *
     *  - blank request → [DEFAULT_VOICE]
     *  - `cloned-*` request → passes through unchanged (cloned voices live
     *    under `cloned_voices/`, resolved by the engine's own store)
     *  - `voices/<name>.wav` present in the installed bundle → the request
     *  - anything else (stale pref, missing WAV) → [DEFAULT_VOICE]
     */
    fun resolveInstalledVoice(requested: String, filesDir: File): String {
        val request = requested.ifBlank { DEFAULT_VOICE }
        val localName = request.substringAfter(':', request)
        if (localName.startsWith(PocketEngine.CLONED_VOICE_PREFIX)) return request
        val voicesDir = File(PocketEngineCatalog.engineDir(filesDir), "voices")
        return if (File(voicesDir, "$localName.wav").isFile) request else DEFAULT_VOICE
    }

    /**
     * Voices actually usable from an installed engine dir: base voices whose
     * `voices/<name>.wav` is present, plus any `voices/vctk_*.wav` extras the
     * bundle carries (Miko UK-expanded bundle only).
     */
    fun discoverVoices(engineDir: File): List<PocketVoice> {
        val voicesDir = File(engineDir, "voices")
        val present = voicesDir.listFiles()
            ?.filter { it.isFile }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
        val base = baseVoices.filter { "${it.name}.wav" in present }
        val extras = present
            .mapNotNull { extraVoiceName(it) }
            .sorted()
            .map { name ->
                PocketVoice(
                    name = name,
                    displayName = "VCTK ${name.removePrefix("vctk_")}",
                    gender = "",
                    license = licenseFor(name),
                    accent = EXTRA_VOICE_ACCENTS[name.removePrefix("vctk_")] ?: "British",
                )
            }
        return base + extras
    }

    /**
     * Human-facing picker label: `"Name (Accent)"` where an accent is
     * verified, otherwise `"Name (Male|Female)"` from the F0-estimated
     * gender. Never exposes license strings — attribution lives in
     * About → Licenses.
     */
    fun labelFor(voice: PocketVoice): String {
        val descriptor = voice.accent.ifBlank {
            voice.gender.replaceFirstChar { it.uppercase() }
        }
        return if (descriptor.isBlank()) voice.displayName else "${voice.displayName} ($descriptor)"
    }
}

/**
 * A selectable Pocket voice — a `voices/<name>.wav` reference file.
 *
 * @property name Machinery key (`voices/<name>.wav`), e.g. `eponine`,
 *                `vctk_p225`.
 * @property displayName UI label.
 * @property gender "male" / "female" / "" (extras are unannotated).
 * @property license "CC-BY-4.0" or "CC0" — every shipped voice is
 *                commercial-safe (D-07).
 * @property accent Accent label where verified ("" = unannotated).
 */
data class PocketVoice(
    val name: String,
    val displayName: String,
    val gender: String,
    val license: String,
    val accent: String = "",
)
