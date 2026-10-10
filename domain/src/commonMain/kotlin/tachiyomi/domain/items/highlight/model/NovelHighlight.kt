package tachiyomi.domain.items.highlight.model

data class NovelHighlight(
    val id: Long,
    val novelId: Long,
    val chapterId: Long,
    val selectedText: String,
    val color: String?,
    val note: String?,
    val paragraphIndex: Int,
    val timestamp: Long,
) {
    companion object {
        fun create() = NovelHighlight(
            id = -1L,
            novelId = -1L,
            chapterId = -1L,
            selectedText = "",
            color = null,
            note = null,
            paragraphIndex = 0,
            timestamp = 0L,
        )
    }
}
