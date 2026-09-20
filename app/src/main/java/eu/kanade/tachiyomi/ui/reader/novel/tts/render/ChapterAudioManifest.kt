package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manifest embedded in a rendered chapter `.m4a` as a trailing top-level
 * `uuid` MP4 atom (D-06). Self-contained: the file carries its own
 * paragraph→millisecond offsets plus the full chapter text, so playback and
 * read-along survive moves/exports/leaving the app — no sidecar.
 *
 * The format is the contract Phase 6's player and this phase's read-along
 * file playback parse — field names/semantics are frozen by already-rendered
 * files.
 *
 * @property version schema version; bump on incompatible changes.
 * @property voice the RESOLVED Pocket voice name used at render time
 *                 ([eu.kanade.tachiyomi.ui.reader.novel.tts.PocketVoiceCatalog.resolveInstalledVoice]
 *                 output — post-fallback, never the raw pref).
 * @property engine engine identifier ([PocketEngine.ENGINE_NAME]).
 * @property chapterTitle display title of the rendered chapter.
 * @property sampleRate PCM rate the audio was rendered at (24 kHz native).
 * @property speechRate rate baked into the PCM at render time (SD-1);
 *                      playback-speed controls apply on top as a multiplier.
 * @property pitch pitch baked into the PCM at render time (SD-1).
 * @property durationMs total rendered audio duration in milliseconds
 *                      (total shaped samples ÷ sampleRate × 1000).
 * @property paragraphOffsetsMs offset of paragraph i in milliseconds —
 *                             cumulative shaped-sample count ÷ sampleRate.
 *                             Same indexing as [paragraphTexts] and the
 *                             reader's blank-filtered paragraph list.
 * @property paragraphTexts rendered plain-text paragraphs, for manifest↔
 *                        reader validation before read-along file playback.
 * @property text full chapter plain text (paragraphs joined).
 */
@Serializable
data class ChapterAudioManifest(
    val version: Int = 1,
    val voice: String,
    val engine: String,
    val chapterTitle: String,
    val sampleRate: Int = 24000,
    val speechRate: Float,
    val pitch: Float,
    val durationMs: Long,
    val paragraphOffsetsMs: List<Long>,
    val paragraphTexts: List<String>,
    val text: String,
)

/**
 * Private codec instance — deliberately NOT the Injekt `Json` singleton:
 * `ignoreUnknownKeys` is a hard requirement here (Phase 6 may add fields to
 * manifests written by future versions; older readers must still parse).
 * `encodeDefaults` keeps optional fields explicit on the wire.
 */
private val manifestJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Serialise this manifest for embedding as the `uuid` atom payload. */
fun ChapterAudioManifest.encode(): String = manifestJson.encodeToString(this)

/**
 * Decode a manifest JSON payload. Missing, malformed, or schema-incompatible
 * input returns null — never throws — so a corrupt/tampered atom degrades to
 * "no manifest" and the file still plays as plain audio (RESEARCH §2).
 */
fun decodeManifest(json: String): ChapterAudioManifest? {
    return try {
        manifestJson.decodeFromString<ChapterAudioManifest>(json)
    } catch (e: Exception) {
        null
    }
}
