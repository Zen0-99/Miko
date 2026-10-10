package eu.kanade.tachiyomi.util.lang

expect object Hash {
    fun sha256(bytes: ByteArray): String
    fun sha256(string: String): String
}
