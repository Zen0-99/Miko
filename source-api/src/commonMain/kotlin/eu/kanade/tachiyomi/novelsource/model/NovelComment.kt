package eu.kanade.tachiyomi.novelsource.model

/**
 * Represents a comment on a chapter.
 *
 * @property id Unique comment identifier from the source.
 * @property userName Display name of the commenter.
 * @property avatarUrl Optional avatar image URL.
 * @property content Comment body (HTML or plain text).
 * @property likes Number of upvotes/likes.
 * @property dislikes Number of downvotes/dislikes (0 if not supported).
 * @property replyCount Number of direct replies.
 * @property date Timestamp of the comment (epoch millis), 0 if unknown.
 * @property replies Nested replies (for threaded comment systems).
 */
data class NovelComment(
    val id: String,
    val userName: String,
    val avatarUrl: String? = null,
    val content: String,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val replyCount: Int = 0,
    val date: Long = 0L,
    val replies: List<NovelComment> = emptyList(),
)
