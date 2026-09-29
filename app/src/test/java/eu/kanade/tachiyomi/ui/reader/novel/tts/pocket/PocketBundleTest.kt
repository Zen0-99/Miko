package eu.kanade.tachiyomi.ui.reader.novel.tts.pocket

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PocketBundleTest {

    @TempDir
    lateinit var dir: File

    private fun load(json: String): PocketBundle {
        val file = File(dir, "bundle.json").apply { writeText(json) }
        return PocketBundle.load(file)
    }

    private fun syntheticJson(
        onnxFilesBlock: String? = null,
        modelRecommendedFramesAfterEos: String = "null",
    ): String {
        val onnx = onnxFilesBlock?.let { "\"onnx_files\": $it," } ?: ""
        return """
        {
          "sample_rate": 24000,
          "frame_rate": 12.5,
          "samples_per_frame": 1920,
          "latent_dim": 32,
          "conditioning_dim": 1024,
          "max_token_per_chunk": 50,
          "insert_bos_before_voice": true,
          "remove_semicolons": true,
          "pad_with_spaces_for_short_inputs": true,
          "model_recommended_frames_after_eos": $modelRecommendedFramesAfterEos,
          "predefined_voices": ["alba", "azelma", "eponine", "fantine", "javert", "marius"],
          $onnx
          "quantization_variant": "mixed-fp32-mimi",
          "flow_lm_state_manifest": [
            {"index": 0, "input_name": "state_0", "output_name": "out_state_0",
             "dtype": "float32", "fill": "nan", "shape": [1, 32, 8, 128]},
            {"index": 1, "input_name": "state_1", "output_name": "out_state_1",
             "dtype": "int64", "fill": "zeros", "shape": [1]}
          ],
          "mimi_state_manifest": [
            {"index": 0, "input_name": "state_0", "output_name": "out_state_0",
             "dtype": "float32", "fill": "zeros", "shape": [1, 64, 4]},
            {"index": 1, "input_name": "state_1", "output_name": "out_state_1",
             "dtype": "bool", "fill": "ones", "shape": [1]},
            {"index": 2, "input_name": "state_2", "output_name": "out_state_2",
             "dtype": "float32", "fill": "empty", "shape": [0]}
          ]
        }
        """.trimIndent()
    }

    private val mixedOnnxBlock = """
        {
          "text_conditioner": "text_conditioner.onnx",
          "mimi_encoder": "mimi_encoder.onnx",
          "mimi_decoder": "mimi_decoder.onnx",
          "flow_lm_main": "flow_lm_main_int8.onnx",
          "flow_lm_flow": "flow_lm_flow.onnx"
        }
    """.trimIndent()

    @Test
    fun `parses a full synthetic bundle`() {
        val bundle = load(syntheticJson(onnxFilesBlock = mixedOnnxBlock))
        bundle.sampleRate shouldBe 24000
        bundle.frameRate shouldBe 12.5
        bundle.samplesPerFrame shouldBe 1920
        bundle.latentDim shouldBe 32
        bundle.conditioningDim shouldBe 1024
        bundle.maxTokenPerChunk shouldBe 50
        bundle.insertBosBeforeVoice shouldBe true
        bundle.removeSemicolons shouldBe true
        bundle.padWithSpacesForShortInputs shouldBe true
        bundle.modelRecommendedFramesAfterEos shouldBe null
        bundle.predefinedVoices shouldBe
            listOf("alba", "azelma", "eponine", "fantine", "javert", "marius")
        bundle.quantizationVariant shouldBe "mixed-fp32-mimi"
        bundle.onnxFiles shouldBe PocketBundle.OnnxFiles(
            textConditioner = "text_conditioner.onnx",
            mimiEncoder = "mimi_encoder.onnx",
            mimiDecoder = "mimi_decoder.onnx",
            flowLmMain = "flow_lm_main_int8.onnx",
            flowLmFlow = "flow_lm_flow.onnx",
        )
    }

    @Test
    fun `state manifests parse into dtype fill and shape`() {
        val bundle = load(syntheticJson())
        bundle.flowLmStateManifest.size shouldBe 2
        bundle.mimiStateManifest.size shouldBe 3

        val kv = bundle.flowLmStateManifest[0]
        kv.index shouldBe 0
        kv.inputName shouldBe "state_0"
        kv.outputName shouldBe "out_state_0"
        kv.dtype shouldBe PocketBundle.StateDtype.FLOAT32
        kv.fill shouldBe PocketBundle.StateFill.NAN
        kv.shape shouldBe longArrayOf(1, 32, 8, 128)

        bundle.flowLmStateManifest[1].dtype shouldBe PocketBundle.StateDtype.INT64
        bundle.mimiStateManifest[1].let {
            it.dtype shouldBe PocketBundle.StateDtype.BOOL
            it.fill shouldBe PocketBundle.StateFill.ONES
        }
        bundle.mimiStateManifest[2].let {
            it.fill shouldBe PocketBundle.StateFill.EMPTY
            it.shape shouldBe longArrayOf(0)
        }
    }

    @Test
    fun `missing onnx_files falls back to int8 defaults`() {
        val bundle = load(syntheticJson(onnxFilesBlock = null))
        bundle.onnxFiles shouldBe PocketBundle.OnnxFiles(
            textConditioner = "text_conditioner_int8.onnx",
            mimiEncoder = "mimi_encoder_int8.onnx",
            mimiDecoder = "mimi_decoder_int8.onnx",
            flowLmMain = "flow_lm_main_int8.onnx",
            flowLmFlow = "flow_lm_flow_int8.onnx",
        )
    }

    @Test
    fun `optional fields default when absent`() {
        // Absent optional flags + absent quantization_variant.
        val json = """
        {
          "sample_rate": 24000, "frame_rate": 12.5, "samples_per_frame": 1920,
          "latent_dim": 32, "conditioning_dim": 1024, "max_token_per_chunk": 50,
          "predefined_voices": ["eponine"],
          "flow_lm_state_manifest": [], "mimi_state_manifest": []
        }
        """.trimIndent()
        val bundle = load(json)
        bundle.insertBosBeforeVoice shouldBe false
        bundle.removeSemicolons shouldBe false
        bundle.padWithSpacesForShortInputs shouldBe false
        bundle.modelRecommendedFramesAfterEos shouldBe null
        bundle.quantizationVariant shouldBe "int8"
    }

    @Test
    fun `reads model_recommended_frames_after_eos when present`() {
        val bundle = load(syntheticJson(modelRecommendedFramesAfterEos = "4"))
        bundle.modelRecommendedFramesAfterEos shouldBe 4
    }

    @Test
    fun `malformed json throws IllegalStateException`() {
        shouldThrow<IllegalStateException> {
            load("{ this is not json")
        }
    }

    @Test
    fun `missing required field throws IllegalStateException`() {
        shouldThrow<IllegalStateException> {
            load("""{"sample_rate": 24000}""")
        }
    }

    @Test
    fun `unknown manifest dtype throws IllegalStateException`() {
        val json = syntheticJson().replace("\"dtype\": \"float32\"", "\"dtype\": \"bfloat16\"")
        shouldThrow<IllegalStateException> { load(json) }
    }

    @Test
    fun `real bundle fixture parses when present`() {
        // Emitted by tools/build-pocket-bundle.py --emit-fixtures app/src/test/resources/pocket
        val fixture = File("src/test/resources/pocket/bundle.json")
        Assumptions.assumeTrue(fixture.isFile) { "no bundle.json fixture — skipping" }
        val bundle = PocketBundle.load(fixture)
        bundle.sampleRate shouldBe 24000
        bundle.predefinedVoices.isEmpty() shouldBe false
    }
}
