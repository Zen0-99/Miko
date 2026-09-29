package eu.kanade.tachiyomi.ui.reader.novel.tts.pocket

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File

/**
 * Parsed representation of `bundle.json` for a Pocket TTS install.
 *
 * The bundle contains static metadata (sample rate, frame rate, latent
 * dim, predefined voices) AND two state manifests describing the
 * shape/dtype/fill policy of every persistent state tensor for
 * `flow_lm_main` (18 entries, the transformer's KV cache) and
 * `mimi_decoder` (56 entries, the streaming Mimi codec's conv + attention
 * buffers). We drive state initialization off these manifests rather than
 * hard-coding them — both because the manifest is the source of truth
 * for fill policies (cache slots want NaN, offset trackers want zeros,
 * bool flags want ones, etc.) and because future bundles might add or
 * reshape state entries.
 *
 * Ported from Marmalade's `engine/pocket/PocketBundle.kt`. Where the
 * upstream used `org.json` (Android-only), this port reads via
 * kotlinx-serialization's `JsonObject` accessors so it stays JVM-testable
 * — kotlinx-serialization is already a Miko dependency.
 */
data class PocketBundle(
    /** PCM sample rate emitted by `mimi_decoder`. 24 kHz for english_2026-04. */
    val sampleRate: Int,
    /** Latent frame rate (Hz). 12.5 for english_2026-04. */
    val frameRate: Double,
    /** Samples per latent frame. 1920 for english_2026-04 (= sampleRate / frameRate). */
    val samplesPerFrame: Int,
    /** Latent dim (sequence channel). 32. */
    val latentDim: Int,
    /** Conditioning dim (transformer model dim). 1024. */
    val conditioningDim: Int,
    /**
     * Maximum tokens per chunk before the upstream chunker splits the input.
     * 50 for english_2026-04.
     */
    val maxTokenPerChunk: Int,
    /**
     * When true, prepend the learned `bos_before_voice` 1024-d embedding to
     * the voice embedding along the time axis before voice conditioning.
     * `english_2026-04` sets this to true. Skipping the prepend is a real
     * quality regression.
     */
    val insertBosBeforeVoice: Boolean,
    /** When true, replace `;` with `, ` before tokenizing. */
    val removeSemicolons: Boolean,
    /** When true, prepend 8 spaces to inputs with <5 words. */
    val padWithSpacesForShortInputs: Boolean,
    /**
     * Optional model recommendation for frames-to-generate after the EOS
     * logit threshold first fires. When null (english_2026-04's case),
     * fall back to 3 frames (or 5 for ≤4-word inputs per Python's heuristic).
     */
    val modelRecommendedFramesAfterEos: Int?,
    /** Display names of voices bundled in the `voices/` subdir as `<name>.wav`. */
    val predefinedVoices: List<String>,
    /**
     * ONNX filenames to load for each of the 5 graphs. Bundles built before
     * the "mixed" variant omitted this block; in that case defaults preserve
     * the original `*_int8.onnx` convention so existing installs keep working
     * without re-download. The mixed bundle declares this block explicitly
     * with fp32 names for everything except `flow_lm_main`, which stays int8
     * (the selective-quant strategy from upstream PR #147).
     */
    val onnxFiles: OnnxFiles,
    /**
     * Diagnostic label for the bundle's quantization strategy. Defaults
     * to `"int8"` (the original all-quantized bundle). The mixed bundle
     * sets `"mixed-fp32-mimi"`. Surfaced in logs at engine load so device
     * A/B traces are self-describing.
     */
    val quantizationVariant: String,
    /**
     * Per-tensor state spec for the `flow_lm_main` state slots
     * (`state_0`..`state_17` / `out_state_0`..`out_state_17`).
     */
    val flowLmStateManifest: List<StateSpec>,
    /**
     * Per-tensor state spec for the `mimi_decoder` state slots
     * (`state_0`..`state_55` / `out_state_0`..`out_state_55`).
     */
    val mimiStateManifest: List<StateSpec>,
) {
    /**
     * Filenames for the 5 ONNX graphs the engine loads. Naming is variant-
     * specific — e.g. the original int8 bundle uses `*_int8.onnx`, the
     * mixed bundle uses clean fp32 names for everything except `flow_lm_main`.
     */
    data class OnnxFiles(
        val textConditioner: String,
        val mimiEncoder: String,
        val mimiDecoder: String,
        val flowLmMain: String,
        val flowLmFlow: String,
    )

    /**
     * A single state tensor's shape/dtype/fill spec. Drives both the
     * initial value (`fill` → see [StateFill]) and the wire shape passed
     * to ONNX Runtime when calling the session.
     */
    data class StateSpec(
        val index: Int,
        val inputName: String,
        val outputName: String,
        val dtype: StateDtype,
        val fill: StateFill,
        /**
         * Static shape from the manifest. Dimensions of `0` are intentional —
         * they denote empty/length-tracker tensors (e.g. `current_end`
         * starts at shape `[0]` and the model uses it as a position counter).
         */
        val shape: LongArray,
    ) {
        // LongArray needs explicit content equality for data-class semantics.
        override fun equals(other: Any?): Boolean =
            other is StateSpec &&
                index == other.index &&
                inputName == other.inputName &&
                outputName == other.outputName &&
                dtype == other.dtype &&
                fill == other.fill &&
                shape.contentEquals(other.shape)

        override fun hashCode(): Int =
            31 *
                (
                    31 * (31 * (31 * (31 * index + inputName.hashCode()) + outputName.hashCode()) + dtype.hashCode()) +
                        fill.hashCode()
                    ) +
                shape.contentHashCode()
    }

    enum class StateDtype { FLOAT32, INT64, BOOL }

    /**
     * Fill policy from `bundle.json`. Each kind maps to a different
     * initialization at engine reset time:
     *  - `NAN`: float NaN (used for KV caches; the model masks reads to
     *    the valid prefix so NaN never reaches softmax).
     *  - `ZEROS`: numeric zero (offset trackers, conv stream buffers).
     *  - `ONES`: one (the bool "first" flags in streaming convolutions —
     *    stored as `0x01` bytes for the BOOL dtype).
     *  - `EMPTY`: a literally zero-element tensor; the model uses these
     *    as length sentinels (`current_end[0]`, `previous[1,128,0]`).
     */
    enum class StateFill { NAN, ZEROS, ONES, EMPTY }

    companion object {

        /**
         * Parse a bundle.json file from disk. Throws [IllegalStateException]
         * with the bundle-relative path on any structural failure — the
         * caller (engine load / installer verifyLayout) surfaces that as an
         * engine-init failure so the UI can flag a corrupt install.
         */
        fun load(file: File): PocketBundle {
            val text = file.readText(Charsets.UTF_8)
            val json = try {
                Json.parseToJsonElement(text).jsonObject
            } catch (t: Throwable) {
                throw IllegalStateException("bundle.json is not valid JSON at $file", t)
            }
            return try {
                PocketBundle(
                    sampleRate = json.reqInt("sample_rate"),
                    frameRate = json.reqDouble("frame_rate"),
                    samplesPerFrame = json.reqInt("samples_per_frame"),
                    latentDim = json.reqInt("latent_dim"),
                    conditioningDim = json.reqInt("conditioning_dim"),
                    maxTokenPerChunk = json.reqInt("max_token_per_chunk"),
                    insertBosBeforeVoice = json.optBool("insert_bos_before_voice"),
                    removeSemicolons = json.optBool("remove_semicolons"),
                    padWithSpacesForShortInputs = json.optBool("pad_with_spaces_for_short_inputs"),
                    modelRecommendedFramesAfterEos = json.optIntOrNull("model_recommended_frames_after_eos"),
                    predefinedVoices = json.req("predefined_voices").jsonArray.map {
                        it.jsonPrimitive.content
                    },
                    onnxFiles = parseOnnxFiles(json["onnx_files"] as? JsonObject),
                    quantizationVariant = (json["quantization_variant"] as? JsonPrimitive)
                        ?.takeIf { it.isString }?.content ?: "int8",
                    flowLmStateManifest = parseManifest(json.req("flow_lm_state_manifest").jsonArray),
                    mimiStateManifest = parseManifest(json.req("mimi_state_manifest").jsonArray),
                )
            } catch (t: IllegalStateException) {
                throw t
            } catch (t: Throwable) {
                throw IllegalStateException("bundle.json has missing or malformed fields at $file", t)
            }
        }

        /**
         * Parse the optional `onnx_files` block. Pre-mixed bundles omit it;
         * we fall back to the original `*_int8.onnx` filenames so existing
         * installs still load without redownload.
         */
        private fun parseOnnxFiles(obj: JsonObject?): OnnxFiles {
            if (obj == null) {
                return OnnxFiles(
                    textConditioner = "text_conditioner_int8.onnx",
                    mimiEncoder = "mimi_encoder_int8.onnx",
                    mimiDecoder = "mimi_decoder_int8.onnx",
                    flowLmMain = "flow_lm_main_int8.onnx",
                    flowLmFlow = "flow_lm_flow_int8.onnx",
                )
            }
            return OnnxFiles(
                textConditioner = obj.req("text_conditioner").jsonPrimitive.content,
                mimiEncoder = obj.req("mimi_encoder").jsonPrimitive.content,
                mimiDecoder = obj.req("mimi_decoder").jsonPrimitive.content,
                flowLmMain = obj.req("flow_lm_main").jsonPrimitive.content,
                flowLmFlow = obj.req("flow_lm_flow").jsonPrimitive.content,
            )
        }

        private fun parseManifest(arr: JsonArray): List<StateSpec> {
            return arr.mapIndexed { i, el ->
                val entry = el.jsonObject
                val shape = entry.req("shape").jsonArray
                    .map { it.jsonPrimitive.long }
                    .toLongArray()
                StateSpec(
                    index = entry.reqInt("index"),
                    inputName = entry.req("input_name").jsonPrimitive.content,
                    outputName = entry.req("output_name").jsonPrimitive.content,
                    dtype = when (val s = entry.req("dtype").jsonPrimitive.content) {
                        "float32" -> StateDtype.FLOAT32
                        "int64" -> StateDtype.INT64
                        "bool" -> StateDtype.BOOL
                        else -> throw IllegalStateException("Unknown manifest dtype '$s' at index $i")
                    },
                    fill = when (val s = entry.req("fill").jsonPrimitive.content) {
                        "nan" -> StateFill.NAN
                        "zeros" -> StateFill.ZEROS
                        "ones" -> StateFill.ONES
                        "empty" -> StateFill.EMPTY
                        else -> throw IllegalStateException("Unknown manifest fill '$s' at index $i")
                    },
                    shape = shape,
                )
            }
        }

        private fun JsonObject.req(name: String): JsonElement =
            this[name] ?: throw IllegalStateException("bundle.json: missing field '$name'")

        private fun JsonObject.reqInt(name: String): Int =
            req(name).jsonPrimitive.int

        private fun JsonObject.reqDouble(name: String): Double =
            req(name).jsonPrimitive.double

        private fun JsonObject.optBool(name: String, default: Boolean = false): Boolean =
            (this[name] as? JsonPrimitive)?.booleanOrNull ?: default

        private fun JsonObject.optIntOrNull(name: String): Int? =
            (this[name] as? JsonPrimitive)?.intOrNull
    }
}
