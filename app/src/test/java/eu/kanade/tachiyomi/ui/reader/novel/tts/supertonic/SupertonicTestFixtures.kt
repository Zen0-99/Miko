package eu.kanade.tachiyomi.ui.reader.novel.tts.supertonic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Synthetic-fixture builders for the sherpa `.bin` formats — shared by
 * SupertonicBinFormatsTest, SupertonicVoiceCatalogTest and
 * SupertonicTtsEngineTest so the byte layout lives in exactly one place.
 */
internal object SupertonicTestFixtures {

    /** What [writeVoiceBin] laid down, for assertions. */
    class VoiceBinExpectation(
        val ttlDims: LongArray,
        val dpDims: LongArray,
        val ttlData: FloatArray,
        val dpData: FloatArray,
    ) {
        val count: Int get() = ttlDims[0].toInt()
        val ttlRowLen: Int get() = (ttlDims[1] * ttlDims[2]).toInt()
        val dpRowLen: Int get() = (dpDims[1] * dpDims[2]).toInt()

        fun ttlRow(sid: Int): FloatArray =
            ttlData.copyOfRange(sid * ttlRowLen, (sid + 1) * ttlRowLen)

        fun dpRow(sid: Int): FloatArray =
            dpData.copyOfRange(sid * dpRowLen, (sid + 1) * dpRowLen)
    }

    /**
     * Write a synthetic `voice.bin`: 48-byte int64 header
     * `[n, t1, t2][n, p1, p2]`, then `n*t1*t2` floats then `n*p1*p2`
     * floats, each block patterned `i * 0.25f`. LE throughout.
     */
    fun writeVoiceBin(
        file: File,
        n: Int,
        ttlD1: Int = 2,
        ttlD2: Int = 3,
        dpD1: Int = 1,
        dpD2: Int = 4,
    ): VoiceBinExpectation {
        val ttlDims = longArrayOf(n.toLong(), ttlD1.toLong(), ttlD2.toLong())
        val dpDims = longArrayOf(n.toLong(), dpD1.toLong(), dpD2.toLong())
        val ttlData = FloatArray(n * ttlD1 * ttlD2) { it * 0.25f }
        val dpData = FloatArray(n * dpD1 * dpD2) { it * 0.25f }
        val bytes = ByteBuffer.allocate(48 + (ttlData.size + dpData.size) * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        ttlDims.forEach { bytes.putLong(it) }
        dpDims.forEach { bytes.putLong(it) }
        val floats = bytes.asFloatBuffer()
        floats.put(ttlData)
        floats.put(dpData)
        file.writeBytes(bytes.array())
        return VoiceBinExpectation(ttlDims, dpDims, ttlData, dpData)
    }

    /** Write a synthetic `unicode_indexer.bin`: LE int32 array `0,7,14,…` by default. */
    fun writeIndexerBin(file: File, values: IntArray = IntArray(16) { it * 7 }): IntArray {
        val bytes = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { bytes.putInt(it) }
        file.writeBytes(bytes.array())
        return values
    }

    /**
     * Write the spike-era `voice_styles/<name>.json` equivalent of a
     * `voice.bin` row: `{style_ttl:{dims:[1,d1,d2],data:[[[…]]]},style_dp:{…}}`.
     */
    fun writeVoiceStyleJson(
        file: File,
        ttlData: FloatArray,
        ttlD1: Int,
        ttlD2: Int,
        dpData: FloatArray,
        dpD1: Int,
        dpD2: Int,
    ) {
        fun tensor(data: FloatArray, d1: Int, d2: Int): JsonObject {
            require(data.size == d1 * d2)
            val rows = (0 until d1).map { r ->
                JsonArray((0 until d2).map { c -> JsonPrimitive(data[r * d2 + c].toDouble()) })
            }
            return JsonObject(
                mapOf(
                    "dims" to JsonArray(listOf(1, d1, d2).map { JsonPrimitive(it) }),
                    "data" to JsonArray(listOf(JsonArray(rows))),
                ),
            )
        }
        val root: JsonElement = buildJsonObject {
            put("style_ttl", tensor(ttlData, ttlD1, ttlD2))
            put("style_dp", tensor(dpData, dpD1, dpD2))
        }
        file.writeText(root.toString())
    }
}
