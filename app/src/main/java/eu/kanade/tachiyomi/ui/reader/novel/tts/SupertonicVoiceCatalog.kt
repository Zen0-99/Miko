package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.tts.supertonic.VoiceBinReader
import java.io.File
import java.io.IOException

/**
 * A selectable Supertonic voice — a row of the bundle's `voice.bin`
 * (or a `voice_styles/<style>.json` in the fp32 fallback layout).
 *
 * @property name Namespaced machinery key stored in `pref_novel_tts_voice`,
 *                e.g. `supertonic:F5` (R6 — shared pref stays unambiguous
 *                across engines).
 * @property styleName Bare style name / `voice_styles` filename stem, e.g. `F5`.
 * @property sid sherpa speaker id — the row index inside `voice.bin`.
 * @property gender "female" / "male" — the F/M letter in the style name.
 */
data class SupertonicVoice(
    val name: String,
    val styleName: String,
    val sid: Int,
    val gender: String,
)

/**
 * Static catalog of the voices inside the `supertonic-3-int8` bundle.
 *
 * sherpa packs all ten upstream voice styles into `voice.bin`; the sid order
 * is the alphabetical `*.json` sort order used by
 * `generate_voices_bin.py` — F1..F5 then M1..M5 — so **sid 4 = F5**, the
 * voice picked from the spike-001 sherpa-APK samples (F4/D-05).
 */
object SupertonicVoiceCatalog {

    const val ENGINE = SupertonicEngineCatalog.ENGINE_NAME
    const val VOICE_PREFIX = "supertonic:"

    /** Default style — F5, the user's pick from spike 001 (D-05). */
    const val DEFAULT_STYLE = "F5"
    const val DEFAULT_VOICE = "$VOICE_PREFIX$DEFAULT_STYLE"

    /** sid → style name; sid i speaks for SID_ORDER[i]. */
    val SID_ORDER = listOf("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5")

    /** All ten upstream styles as namespaced voices, in sid order. */
    val voices: List<SupertonicVoice> = SID_ORDER.mapIndexed { sid, style ->
        SupertonicVoice(
            name = "$VOICE_PREFIX$style",
            styleName = style,
            sid = sid,
            gender = if (style.startsWith("F")) "female" else "male",
        )
    }

    /** sid for a bare or namespaced style name; null when unknown. */
    fun sidFor(name: String): Int? {
        val style = name.substringAfter(':', name)
        return SID_ORDER.indexOf(style).takeIf { it >= 0 }
    }

    /**
     * Resolve a requested voice name to one the installed bundle actually
     * ships — pure lookup, no pref mutation (same contract as
     * [PocketVoiceCatalog.resolveInstalledVoice], plan 05-01 precedent).
     *
     *  - blank request → [DEFAULT_VOICE]
     *  - namespaced or bare name ∈ [SID_ORDER] whose style data exists in
     *    the installed bundle (`voice.bin` row or `voice_styles/<s>.json`)
     *    → the namespaced form
     *  - anything else (stale Pocket name like `eponine`, missing bundle)
     *    → [DEFAULT_VOICE]  (R6)
     */
    fun resolveInstalledVoice(requested: String, filesDir: File): String {
        val style = requested.substringAfter(':', requested)
        val sid = sidFor(style) ?: return DEFAULT_VOICE
        val engineDir = SupertonicEngineCatalog.engineDir(filesDir)
        val bin = File(engineDir, "voice.bin")
        if (bin.isFile) {
            val count = try {
                VoiceBinReader.readCount(bin)
            } catch (t: IOException) {
                0
            }
            if (sid < count) return "$VOICE_PREFIX$style"
        }
        if (File(engineDir, "voice_styles/$style.json").isFile) {
            return "$VOICE_PREFIX$style"
        }
        return DEFAULT_VOICE
    }

    /**
     * Voices actually usable from an installed engine dir: `voice.bin`
     * supplies [SID_ORDER].take(n) (n = header count); when the file is
     * absent the fp32 layout's `voice_styles/<name>.json` filenames are used
     * instead. Missing/corrupt data → empty list.
     */
    fun discoverVoices(engineDir: File): List<SupertonicVoice> {
        val bin = File(engineDir, "voice.bin")
        if (bin.isFile) {
            return try {
                voices.take(VoiceBinReader.readCount(bin))
            } catch (t: IOException) {
                emptyList()
            }
        }
        val stylesDir = File(engineDir, "voice_styles")
        val present = stylesDir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            .orEmpty()
        return voices.filter { it.styleName in present }
    }

    /** Human-facing picker label: `"F5 (Female)"`. */
    fun labelFor(voice: SupertonicVoice): String =
        "${voice.styleName} (${voice.gender.replaceFirstChar { it.uppercase() }})"
}
