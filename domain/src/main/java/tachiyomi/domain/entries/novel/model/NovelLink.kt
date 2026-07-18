package tachiyomi.domain.entries.novel.model

/**
 * Represents a link between novel entries from different sources.
 *
 * Linked novels share the same [linkedId] and form a cluster. One novel
 * in the cluster is the [isPrimary] entry — its metadata (title, cover,
 * description) is used for display, while chapters are merged from all
 * linked sources with deduplication by chapter number.
 *
 * [extensionType] indicates whether the source is an APK extension ("apk")
 * or a JS plugin ("js"). This affects comments routing — only APK sources
 * support comments.
 */
data class NovelLink(
    val id: Long,
    val linkedId: Long,
    val novelId: Long,
    val sourceId: Long,
    val isPrimary: Boolean,
    val extensionType: String,
)
