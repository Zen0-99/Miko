package eu.kanade.tachiyomi.util.lang

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.runBlocking

actual object Hash {

    private val chars = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f',
    )

    private val sha256Hasher get() = CryptographyProvider.Default.get(SHA256).hasher()

    actual fun sha256(bytes: ByteArray): String {
        return encodeHex(runBlocking { sha256Hasher.hash(bytes) })
    }

    actual fun sha256(string: String): String {
        return sha256(string.encodeToByteArray())
    }

    private fun encodeHex(data: ByteArray): String {
        val l = data.size
        val out = CharArray(l shl 1)
        var i = 0
        var j = 0
        while (i < l) {
            out[j++] = chars[(240 and data[i].toInt()).ushr(4)]
            out[j++] = chars[15 and data[i].toInt()]
            i++
        }
        return out.concatToString()
    }
}
