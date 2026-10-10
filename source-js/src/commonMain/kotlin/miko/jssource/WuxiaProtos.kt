package miko.jssource

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoType

/**
 * Wuxia gRPC-web protobuf schemas — ported verbatim from
 * NovelJsRuntimeFactory. kotlinx-serialization-protobuf is KMP so these
 * encode/decode identically on Android and iOS.
 */
@Serializable
data class WuxiaGetNovelRequest(
    @ProtoNumber(2) val slug: String,
)

@Serializable
data class WuxiaGetChapterListRequest(
    @ProtoNumber(1) val novelId: Int,
)

@Serializable
data class WuxiaGetChapterRequest(
    @ProtoNumber(1) val chapterProperty: WuxiaGetChapterByProperty? = null,
)

@Serializable
data class WuxiaGetChapterByProperty(
    @ProtoNumber(2) val slugs: WuxiaByNovelAndChapterSlug? = null,
)

@Serializable
data class WuxiaByNovelAndChapterSlug(
    @ProtoNumber(1) val novelSlug: String,
    @ProtoNumber(2) val chapterSlug: String,
)

@Serializable
data class WuxiaGetNovelResponse(
    @ProtoNumber(1) val item: WuxiaNovelItem? = null,
)

@Serializable
data class WuxiaGetNovelResponseWithoutKarma(
    @ProtoNumber(1) val item: WuxiaNovelItemWithoutKarma? = null,
)

@Serializable
data class WuxiaGetChapterListResponse(
    @ProtoNumber(1) val items: List<WuxiaChapterGroupItem> = emptyList(),
)

@Serializable
data class WuxiaGetChapterResponse(
    @ProtoNumber(1) val item: WuxiaChapterItem? = null,
)

@Serializable
data class WuxiaNovelItem(
    @ProtoNumber(1) val id: Int? = null,
    @ProtoNumber(2) val name: String? = null,
    @ProtoNumber(3) val slug: String? = null,
    @ProtoNumber(4) val status: Int? = null,
    @ProtoNumber(8) val description: WuxiaStringValue? = null,
    @ProtoNumber(9) val synopsis: WuxiaStringValue? = null,
    @ProtoNumber(10) val coverUrl: WuxiaStringValue? = null,
    @ProtoNumber(13) val authorName: WuxiaStringValue? = null,
    @ProtoNumber(14) val karmaInfo: WuxiaNovelKarmaInfo? = null,
    @ProtoNumber(16) val genres: List<String> = emptyList(),
)

@Serializable
data class WuxiaNovelItemWithoutKarma(
    @ProtoNumber(1) val id: Int? = null,
    @ProtoNumber(2) val name: String? = null,
    @ProtoNumber(3) val slug: String? = null,
    @ProtoNumber(4) val status: Int? = null,
    @ProtoNumber(8) val description: WuxiaStringValue? = null,
    @ProtoNumber(9) val synopsis: WuxiaStringValue? = null,
    @ProtoNumber(10) val coverUrl: WuxiaStringValue? = null,
    @ProtoNumber(13) val authorName: WuxiaStringValue? = null,
    @ProtoNumber(16) val genres: List<String> = emptyList(),
) {
    fun toWuxiaNovelItem(): WuxiaNovelItem {
        return WuxiaNovelItem(
            id = id,
            name = name,
            slug = slug,
            status = status,
            description = description,
            synopsis = synopsis,
            coverUrl = coverUrl,
            authorName = authorName,
            karmaInfo = null,
            genres = genres,
        )
    }
}

@Serializable
data class WuxiaNovelKarmaInfo(
    @ProtoNumber(3) val maxFreeChapter: WuxiaDecimalValue? = null,
)

@Serializable
data class WuxiaChapterGroupItem(
    @ProtoNumber(2) val title: String? = null,
    @ProtoNumber(6) val chapterList: List<WuxiaChapterItem> = emptyList(),
)

@Serializable
data class WuxiaChapterItem(
    @ProtoNumber(2) val name: String? = null,
    @ProtoNumber(3) val slug: String? = null,
    @ProtoNumber(4) val number: WuxiaDecimalValue? = null,
    @ProtoNumber(5) val content: WuxiaStringValue? = null,
    @ProtoNumber(16) val relatedUserInfo: WuxiaRelatedChapterUserInfo? = null,
    @ProtoNumber(17) val offset: Int? = null,
    @ProtoNumber(18) val publishedAt: WuxiaTimestamp? = null,
)

@Serializable
data class WuxiaRelatedChapterUserInfo(
    @ProtoNumber(1) val isChapterUnlocked: WuxiaBoolValue? = null,
)

@Serializable
data class WuxiaStringValue(
    @ProtoNumber(1) val value: String? = null,
)

@Serializable
data class WuxiaBoolValue(
    @ProtoNumber(1) val value: Boolean? = null,
)

@Serializable
data class WuxiaDecimalValue(
    @ProtoNumber(1) val units: Long? = null,
    @ProtoType(ProtoIntegerType.FIXED)
    @ProtoNumber(2) val nanos: Int? = null,
)

@Serializable
data class WuxiaTimestamp(
    @ProtoNumber(1) val seconds: Long? = null,
    @ProtoNumber(2) val nanos: Int? = null,
)

/**
 * gRPC-web framing + Wuxia encode/decode — ported verbatim from
 * NovelJsRuntimeFactory; pure ByteArray math, engine-neutral.
 */
internal object GrpcWeb {
    private val proto = ProtoBuf
    private val json = kotlinx.serialization.json.Json

    fun encodeRequest(config: JsProtoFetchConfig): ByteArray {
        return when (config.requestType) {
            "GetNovelRequest" -> {
                val slug = config.requestData.jsonObject.stringValue("slug")
                    ?: error("Missing slug for GetNovelRequest")
                proto.encodeToByteArray(WuxiaGetNovelRequest.serializer(), WuxiaGetNovelRequest(slug = slug))
            }
            "GetChapterListRequest" -> {
                val novelId = config.requestData.jsonObject.intValue("novelId")
                    ?: error("Missing novelId for GetChapterListRequest")
                proto.encodeToByteArray(
                    WuxiaGetChapterListRequest.serializer(),
                    WuxiaGetChapterListRequest(novelId = novelId),
                )
            }
            "GetChapterRequest" -> {
                val requestData = config.requestData.jsonObject
                val chapterProperty = requestData["chapterProperty"]?.jsonObject
                    ?: error("Missing chapterProperty for GetChapterRequest")
                val slugs = chapterProperty["slugs"]?.jsonObject
                    ?: error("Missing chapterProperty.slugs for GetChapterRequest")
                val novelSlug = slugs.stringValue("novelSlug")
                    ?: error("Missing chapterProperty.slugs.novelSlug")
                val chapterSlug = slugs.stringValue("chapterSlug")
                    ?: error("Missing chapterProperty.slugs.chapterSlug")
                proto.encodeToByteArray(
                    WuxiaGetChapterRequest.serializer(),
                    WuxiaGetChapterRequest(
                        chapterProperty = WuxiaGetChapterByProperty(
                            slugs = WuxiaByNovelAndChapterSlug(
                                novelSlug = novelSlug,
                                chapterSlug = chapterSlug,
                            ),
                        ),
                    ),
                )
            }
            else -> error("Unsupported proto request type: ${config.requestType}")
        }
    }

    fun decodeResponse(responseType: String, responsePayload: ByteArray): String {
        return when (responseType) {
            "GetNovelResponse" -> {
                val decoded = decodeGetNovelResponse(responsePayload)
                json.encodeToString(WuxiaGetNovelResponse.serializer(), decoded)
            }
            "GetChapterListResponse" -> {
                val decoded = if (responsePayload.isEmpty()) {
                    WuxiaGetChapterListResponse()
                } else {
                    proto.decodeFromByteArray(WuxiaGetChapterListResponse.serializer(), responsePayload)
                }
                json.encodeToString(WuxiaGetChapterListResponse.serializer(), decoded)
            }
            "GetChapterResponse" -> {
                val decoded = if (responsePayload.isEmpty()) {
                    WuxiaGetChapterResponse()
                } else {
                    proto.decodeFromByteArray(WuxiaGetChapterResponse.serializer(), responsePayload)
                }
                json.encodeToString(WuxiaGetChapterResponse.serializer(), decoded)
            }
            else -> error("Unsupported proto response type: $responseType")
        }
    }

    private fun decodeGetNovelResponse(responsePayload: ByteArray): WuxiaGetNovelResponse {
        if (responsePayload.isEmpty()) return WuxiaGetNovelResponse()
        val decoded = runCatching {
            proto.decodeFromByteArray(WuxiaGetNovelResponse.serializer(), responsePayload)
        }
        if (decoded.isSuccess) return decoded.getOrThrow()
        val fallback = runCatching {
            proto.decodeFromByteArray(WuxiaGetNovelResponseWithoutKarma.serializer(), responsePayload)
        }
        if (fallback.isSuccess) {
            return WuxiaGetNovelResponse(item = fallback.getOrThrow().item?.toWuxiaNovelItem())
        }
        throw decoded.exceptionOrNull() ?: fallback.exceptionOrNull() ?: error("Failed to decode GetNovelResponse")
    }

    fun frameMessage(message: ByteArray): ByteArray {
        val framed = ByteArray(5 + message.size)
        framed[0] = 0
        writeInt32BigEndian(framed, 1, message.size)
        message.copyInto(framed, destinationOffset = 5)
        return framed
    }

    fun extractPayload(body: ByteArray): ByteArray {
        var offset = 0
        while (offset + 5 <= body.size) {
            val flag = body[offset].toInt() and 0xFF
            val frameLength = readInt32BigEndian(body, offset + 1)
            val start = offset + 5
            val end = start + frameLength
            if (frameLength < 0 || end > body.size) break
            if ((flag and 0x80) == 0) {
                return body.copyOfRange(start, end)
            }
            offset = end
        }
        return ByteArray(0)
    }

    private fun readInt32BigEndian(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun writeInt32BigEndian(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value ushr 24) and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 3] = (value and 0xFF).toByte()
    }

    private fun JsonObject.stringValue(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.intValue(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
}
