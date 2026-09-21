package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import android.content.Context
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.PocketVoiceCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.SupertonicEngineCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.SupertonicVoiceCatalog
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.PocketEngine
import eu.kanade.tachiyomi.ui.reader.novel.tts.pocket.SynthAudio
import eu.kanade.tachiyomi.ui.reader.novel.tts.preprocessing.EngineProfiles
import eu.kanade.tachiyomi.ui.reader.novel.tts.supertonic.SupertonicEngine
import kotlinx.coroutines.flow.Flow

// -----------------------------------------------------------------------------
// Data flow
// -----------------------------------------------------------------------------
//   ChapterAudioRenderer.renderChapter
//     │
//     ├── RenderProfiles.forEngine(pref_novel_tts_engine snapshot)
//     │       "supertonic" → supertonic profile (44.1 kHz, native speed)
//     │       else         → pocket profile   (24 kHz, bake-in resample)
//     │
//     ▼
//   EngineRenderProfile — engineName + sampleRate → manifest/sink,
//   nativeSpeedFor/bakeRateFor → rate split, resolveVoice → catalog
//   fallback, streamFactory → engine.synthesizeStream
// -----------------------------------------------------------------------------

/**
 * The only engine coupling the chapter renderer needs (PB-14, D-07): one
 * small record of lambdas over a concrete engine instance. A plain class,
 * not an interface — the `EngineSpec` precedent (EngineInstaller.kt:301):
 * lambdas over subtypes are all the polymorphism a two-engine world needs.
 *
 * Rate split semantics (D-07):
 *  - `nativeSpeedFor(rate)` is the speed handed to the engine's stream
 *    call — Pocket ignores speed entirely (native=1, always); Supertonic
 *    rides its duration-divide parameter inside `0.5..2`.
 *  - `bakeRateFor(rate)` is the residual baked into the rendered PCM via
 *    `ProsodyApplier.resampleLinear` — Pocket bakes the whole rate (its
 *    engine ignores speed), Supertonic bakes `rate / native` so renders
 *    reuse the same audio the read-along path produces (1.0 in-range →
 *    resample skipped entirely).
 *
 * @property engineName Manifest `engine` field (`PocketEngine.ENGINE_NAME`
 *           / `SupertonicEngineCatalog.ENGINE_NAME`).
 * @property sampleRate PCM rate renders are produced at — drives the sink's
 *           MediaFormat, the manifest `sampleRate`, and all
 *           durationMs/paragraphOffsetsMs math.
 * @property preprocessingRules Enabled preprocessing-rule names for
 *           `SynthesisPipeline.prepare` (engine-specific defaults).
 * @property streamFactory One engine synthesis stream per prepared
 *           paragraph: (engineText, resolvedVoice, nativeSpeed).
 */
internal class EngineRenderProfile(
    val engineName: String,
    val sampleRate: Int,
    val nativeSpeedFor: (rate: Float) -> Float,
    val bakeRateFor: (rate: Float) -> Float,
    val ensureLoaded: suspend () -> Unit,
    val releaseEngine: () -> Unit,
    val resolveVoice: (requested: String) -> String,
    val preprocessingRules: Set<String>,
    val streamFactory: (text: String, voiceId: String, speed: Float) -> Flow<SynthAudio>,
)

/**
 * Per-render profile dispatch (PB-15): the renderer's snapshot of
 * `pref_novel_tts_engine` selects the profile once at render start.
 *
 * `"supertonic"` maps to the Supertonic profile; EVERYTHING else —
 * `"pocket"`, `"android"`, `"neural"`, unknown values — maps to the Pocket
 * profile, byte-identical to the pre-profile behavior which always
 * rendered via [PocketEngine] regardless of the read-along engine pref.
 */
internal object RenderProfiles {

    fun forEngine(
        engineType: String,
        context: Context,
        preferences: NovelTtsPreferences,
    ): EngineRenderProfile = when (engineType) {
        "supertonic" -> supertonic(context, preferences)
        else -> pocket(context, preferences)
    }

    /**
     * Pocket profile — reproduces the original render path exactly
     * (D-08/D-09): 24 kHz, engine speed ignored (native=1), the full
     * speech rate baked via `resampleLinear`, KITTEN_DEFAULTS rules,
     * `synthesizeStream(text, voice, 1.0f, null)`.
     */
    private fun pocket(context: Context, preferences: NovelTtsPreferences): EngineRenderProfile {
        val engine = PocketEngine(context, preferences)
        return EngineRenderProfile(
            engineName = PocketEngine.ENGINE_NAME,
            sampleRate = PocketVoiceCatalog.SAMPLE_RATE,
            nativeSpeedFor = { 1f },
            bakeRateFor = { it },
            ensureLoaded = engine::ensureLoaded,
            releaseEngine = engine::release,
            resolveVoice = { PocketVoiceCatalog.resolveInstalledVoice(it, context.filesDir) },
            preprocessingRules = EngineProfiles.defaultsFor(PocketEngine.ENGINE_NAME),
            streamFactory = { text, voice, _ -> engine.synthesizeStream(text, voice, 1.0f, null) },
        )
    }

    /**
     * Supertonic profile (D-07): 44.1 kHz native — zero resampling in the
     * codec path. Rate rides the model's duration-divide parameter inside
     * `0.5..2`; out-of-range residue is baked (`rate / native`). Denoise
     * steps come from `supertonicRenderNumSteps()` at stream time — the
     * render-specific knob (default 8, spike-003 ear test) rather than the
     * live-TTS `supertonicNumSteps`.
     */
    private fun supertonic(context: Context, preferences: NovelTtsPreferences): EngineRenderProfile {
        val engine = SupertonicEngine(context, preferences)
        return EngineRenderProfile(
            engineName = SupertonicEngine.ENGINE_NAME,
            sampleRate = SupertonicEngineCatalog.SAMPLE_RATE,
            nativeSpeedFor = { it.coerceIn(0.5f, 2f) },
            bakeRateFor = { rate -> rate / rate.coerceIn(0.5f, 2f) },
            ensureLoaded = engine::ensureLoaded,
            releaseEngine = engine::release,
            resolveVoice = { SupertonicVoiceCatalog.resolveInstalledVoice(it, context.filesDir) },
            preprocessingRules = EngineProfiles.defaultsFor(SupertonicEngine.ENGINE_NAME),
            streamFactory = { text, voice, speed ->
                engine.synthesizeStream(text, voice, preferences.supertonicRenderNumSteps().get(), speed)
            },
        )
    }
}
