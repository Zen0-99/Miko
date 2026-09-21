package eu.kanade.tachiyomi.ui.reader.novel.tts.supertonic

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure-JVM readers for the sherpa-onnx Supertonic-3 bundle's packed binary
 * files (`voice.bin`, `unicode_indexer.bin`). sherpa ships only these `.bin`
 * forms in the int8 tarball — no `voice_styles/<name>.json` /
 * `unicode_indexer.json` — so the engine parses them directly rather than
 * repacking the bundle (phase-05B D-03 / RESEARCH rec 2, F4).
 *
 * Deliberately ORT-free: parsers return plain arrays and let the engine wrap
 * them in `OnnxTensor`, so the byte-format contract is unit-testable without
 * the ORT native library (validation rows V3/V4).
 *
 * `voice.bin` layout (little-endian, from sherpa's
 * `scripts/supertonic/generate_voices_bin.py`):
 *
 *   int64 ttl_dims[3] = [n, d1, d2]   ─┐ 48-byte header
 *   int64 dp_dims[3]  = [n, d1, d2]   ─┘
 *   float32 ttl block: n × ttl_d1 × ttl_d2
 *   float32 dp block:  n × dp_d1 × dp_d2
 *
 * `unicode_indexer.bin` is the JSON int array written as raw C-order int32
 * (LE), nothing else (`generate_indexer_bin.py`).
 */
object VoiceBinReader {

    /**
     * Parsed `voice.bin`: voice-count-major float blocks plus their dims.
     * [ttlData] holds `n × ttlDims[1] × ttlDims[2]` floats (row-major);
     * [dpData] the same for the duration-predictor style block.
     */
    class VoiceBinData(
        val ttlDims: LongArray,
        val dpDims: LongArray,
        val ttlData: FloatArray,
        val dpData: FloatArray,
    ) {
        /** Number of voice styles in the file (`dims[0]`). */
        val count: Int get() = ttlDims[0].toInt()

        /**
         * Row [sid] of the ttl block as `(flat floats, shape)` where shape
         * is `[1, d1, d2]` — the same leading-1 batch shape the fp32
         * `voice_styles/<name>.json` files carry.
         */
        fun ttlSlice(sid: Int): Pair<FloatArray, LongArray> =
            slice(sid, ttlDims, ttlData, "ttl")

        /** Row [sid] of the dp block; see [ttlSlice]. */
        fun dpSlice(sid: Int): Pair<FloatArray, LongArray> =
            slice(sid, dpDims, dpData, "dp")

        private fun slice(
            sid: Int,
            dims: LongArray,
            data: FloatArray,
            block: String,
        ): Pair<FloatArray, LongArray> {
            require(sid in 0 until count) {
                "voice.bin sid $sid out of range (count=$count)"
            }
            val rowLen = (dims[1] * dims[2]).toInt()
            val out = FloatArray(rowLen)
            System.arraycopy(data, sid * rowLen, out, 0, rowLen)
            return out to longArrayOf(1, dims[1], dims[2])
        }

        init {
            require(ttlDims.size == 3 && dpDims.size == 3) {
                "voice.bin dims must be rank-3 (ttl=${ttlDims.toList()}, dp=${dpDims.toList()})"
            }
            require(ttlDims[0] == dpDims[0]) {
                "voice.bin ttl/dp voice counts differ (${ttlDims[0]} vs ${dpDims[0]})"
            }
        }
    }

    /**
     * Full parse: header + both float blocks, strictly length-checked —
     * a truncated or oversized file throws [IOException] rather than
     * producing partial tensors (threat T-05B-01-01).
     */
    fun parse(file: File): VoiceBinData {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < HEADER_BYTES) {
            throw IOException("voice.bin too short for header: ${bytes.size} bytes")
        }
        val ttlDims = LongArray(3) { buf.long }
        val dpDims = LongArray(3) { buf.long }
        for (d in ttlDims + dpDims) {
            if (d <= 0) throw IOException("voice.bin non-positive dim in header")
        }
        val n = ttlDims[0]
        val ttlFloats = n * ttlDims[1] * ttlDims[2]
        val dpFloats = n * dpDims[1] * dpDims[2]
        val expected = HEADER_BYTES + (ttlFloats + dpFloats) * 4
        if (bytes.size.toLong() != expected) {
            throw IOException(
                "voice.bin length ${bytes.size} != header-declared $expected " +
                    "(ttl=${ttlDims.toList()}, dp=${dpDims.toList()})",
            )
        }
        val floats = buf.asFloatBuffer()
        val ttlData = FloatArray(ttlFloats.toInt()).also { floats.get(it) }
        val dpData = FloatArray(dpFloats.toInt()).also { floats.get(it) }
        return VoiceBinData(ttlDims, dpDims, ttlData, dpData)
    }

    /**
     * Header-only read for [eu.kanade.tachiyomi.ui.reader.novel.tts.SupertonicVoiceCatalog.discoverVoices] —
     * returns the voice count without loading the float payload.
     */
    fun readCount(file: File): Int {
        file.inputStream().use { input ->
            val header = ByteArray(HEADER_BYTES)
            var read = 0
            while (read < HEADER_BYTES) {
                val n = input.read(header, read, HEADER_BYTES - read)
                if (n < 0) break
                read += n
            }
            if (read < HEADER_BYTES) {
                throw IOException("voice.bin too short for header: $read bytes")
            }
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val ttlDims = LongArray(3) { buf.long }
            val dpDims = LongArray(3) { buf.long }
            for (d in ttlDims + dpDims) {
                if (d <= 0) throw IOException("voice.bin non-positive dim in header")
            }
            if (ttlDims[0] != dpDims[0]) {
                throw IOException("voice.bin ttl/dp voice counts differ")
            }
            return ttlDims[0].toInt()
        }
    }

    private const val HEADER_BYTES = 6 * 8
}

/**
 * `unicode_indexer.bin`: raw little-endian int32 array (one entry per
 * Unicode codepoint; index = codepoint, value = model token id). The engine
 * widens to `Long` at lookup time — text_ids is an int64 tensor input.
 */
object IndexerBinReader {

    fun parse(file: File): IntArray {
        val bytes = file.readBytes()
        if (bytes.size % 4 != 0) {
            throw IOException("unicode_indexer.bin length ${bytes.size} not a multiple of 4")
        }
        val out = IntArray(bytes.size / 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(out)
        return out
    }
}
