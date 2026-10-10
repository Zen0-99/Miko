package miko.jssource

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

/**
 * Stable source IDs for JS novel plugins — MUST stay identical to Android's
 * `NovelPluginId.toSourceId` (SHA-256(pluginId) -> first 8 bytes big-endian ->
 * masked positive Long) or library entries/backups resolve different sources
 * per platform.
 */
object NovelPluginId {

    @OptIn(ExperimentalForeignApi::class)
    fun toSourceId(pluginId: String): Long {
        val input = pluginId.encodeToByteArray()
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
        input.usePinned { pinned ->
            digest.usePinned { out ->
                CC_SHA256(
                    pinned.addressOf(0),
                    input.size.toUInt(),
                    out.addressOf(0),
                )
            }
        }
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (digest[i].toLong() and 0xFF)
        }
        return value and Long.MAX_VALUE
    }
}
