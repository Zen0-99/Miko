package eu.kanade.tachiyomi.source.novel

data class NovelPluginImagePayload(
    val bytes: ByteArray,
    val mimeType: String,
    val cacheKey: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NovelPluginImagePayload) return false
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType && cacheKey == other.cacheKey
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (cacheKey?.hashCode() ?: 0)
        return result
    }
}

interface NovelPluginImageSource {
    suspend fun fetchImage(imageRef: String): NovelPluginImagePayload?
}
