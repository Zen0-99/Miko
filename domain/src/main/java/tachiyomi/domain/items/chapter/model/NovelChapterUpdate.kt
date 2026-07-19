package tachiyomi.domain.items.chapter.model

data class NovelChapterUpdate(
    val id: Long,
    val novelId: Long? = null,
    val url: String? = null,
    val name: String? = null,
    val scanlator: String? = null,
    val read: Boolean? = null,
    val bookmark: Boolean? = null,
    val lastCharRead: Long? = null,
    val totalCharCount: Long? = null,
    val chapterNumber: Double? = null,
    val sourceOrder: Long? = null,
    val dateFetch: Long? = null,
    val dateUpload: Long? = null,
    val version: Long? = null,
)

fun NovelChapter.toNovelChapterUpdate(): NovelChapterUpdate {
    return NovelChapterUpdate(
        id,
        novelId,
        url,
        name,
        scanlator,
        read,
        bookmark,
        lastCharRead,
        totalCharCount,
        chapterNumber,
        sourceOrder,
        dateFetch,
        dateUpload,
        version,
    )
}
