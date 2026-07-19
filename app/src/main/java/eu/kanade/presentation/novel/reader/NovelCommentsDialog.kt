package eu.kanade.presentation.novel.reader

import android.text.Html
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.novelsource.model.NovelComment
import java.text.DateFormat
import java.util.Date

/**
 * Centered dialog showing chapter comments.
 * Comments display: avatar (or person icon placeholder), username, date inline, like/dislike counts.
 * Supports sorting by Popular (likes) or Newest (date).
 * Reply hierarchy shown with vertical indentation lines.
 * Comments with replies are collapsible by tapping.
 * Uses accent color for theming when provided.
 */
@Composable
fun NovelCommentsDialog(
    comments: List<NovelComment>,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    accentColor: Color? = null,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary

    var sortMode by remember { mutableStateOf(CommentSortMode.POPULAR) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Track collapsed state per comment ID — comments with replies can be toggled
    val collapsedState = rememberSaveable { mutableStateMapOf<String, Boolean>() }

    val sortedComments = remember(comments, sortMode) {
        sortComments(comments, sortMode)
    }

    // Accent-tinted background: blend surface with a subtle accent wash
    val dialogBackgroundColor = lerp(
        MaterialTheme.colorScheme.surface,
        accent,
        0.06f,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            color = dialogBackgroundColor,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                // Header with comment icon, count, and sort button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Comment,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Comments (${comments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    // Sort icon with dropdown menu
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.Sort,
                                contentDescription = "Sort comments",
                                tint = accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Popular") },
                                onClick = {
                                    sortMode = CommentSortMode.POPULAR
                                    sortMenuExpanded = false
                                },
                                leadingIcon = {
                                    if (sortMode == CommentSortMode.POPULAR) {
                                        Icon(
                                            imageVector = Icons.Filled.ThumbUp,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Newest") },
                                onClick = {
                                    sortMode = CommentSortMode.NEWEST
                                    sortMenuExpanded = false
                                },
                                leadingIcon = {
                                    if (sortMode == CommentSortMode.NEWEST) {
                                        Icon(
                                            imageVector = Icons.Filled.Sort,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = accent)
                        }
                    }
                    error != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Tap to retry",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                            )
                        }
                    }
                    comments.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No comments yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 560.dp)
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(sortedComments, key = { it.id }) { comment ->
                                CommentItem(
                                    comment = comment,
                                    accent = accent,
                                    depth = 0,
                                    collapsedState = collapsedState,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Sorts comments (including nested replies) by the given mode.
 * Popular: sorted by like count descending.
 * Newest: sorted by date descending (newest first).
 */
private fun sortComments(comments: List<NovelComment>, mode: CommentSortMode): List<NovelComment> {
    return when (mode) {
        CommentSortMode.POPULAR -> comments.sortedByDescending { it.likes }
        CommentSortMode.NEWEST -> comments.sortedByDescending { it.date }
    }.map { comment ->
        // Also sort nested replies
        if (comment.replies.isNotEmpty()) {
            comment.copy(replies = sortComments(comment.replies, mode))
        } else {
            comment
        }
    }
}

private enum class CommentSortMode {
    POPULAR,
    NEWEST,
}

@Composable
private fun CommentItem(
    comment: NovelComment,
    accent: Color,
    depth: Int = 0,
    collapsedState: MutableMap<String, Boolean>,
) {
    val hasReplies = comment.replies.isNotEmpty()
    val isCollapsed = collapsedState[comment.id] == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (hasReplies) Modifier.clickable { 
                        collapsedState[comment.id] = !isCollapsed 
                    } else Modifier,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            // Vertical indentation lines for reply hierarchy
            if (depth > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 8.dp),
                ) {
                    repeat(depth) { i ->
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(
                                    if (i == depth - 1) accent.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                ),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Avatar — person icon placeholder for users without avatar
                    if (comment.avatarUrl != null) {
                        AsyncImage(
                            model = comment.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(16.dp)),
                        )
                    } else {
                        Surface(
                            color = accent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = accent.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))

                    // Username + date inline
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = comment.userName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (comment.date > 0) {
                            Text(
                                text = formatDate(comment.date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Collapse/expand indicator for comments with replies
                    if (hasReplies) {
                        Icon(
                            imageVector = if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                            contentDescription = if (isCollapsed) "Expand replies" else "Collapse replies",
                            tint = accent.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }

                    // Like / dislike counts
                    if (comment.likes > 0 || comment.dislikes > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (comment.likes > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.ThumbUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = formatCount(comment.likes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (comment.dislikes > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.ThumbDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = formatCount(comment.dislikes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Comment content — strip HTML tags to plain text for display
                val plainText = remember(comment.content) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        Html.fromHtml(comment.content, Html.FROM_HTML_MODE_COMPACT).toString()
                    } else {
                        @Suppress("DEPRECATION")
                        Html.fromHtml(comment.content).toString()
                    }
                }
                Text(
                    text = plainText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Reply count hint when collapsed
                if (hasReplies && isCollapsed) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${comment.replies.size} repl${if (comment.replies.size == 1) "y" else "ies"} hidden",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // Nested replies with vertical hierarchy lines — collapsible
        if (hasReplies) {
            AnimatedVisibility(visible = !isCollapsed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    comment.replies.forEach { reply ->
                        CommentItem(reply, accent, depth = depth + 1, collapsedState = collapsedState)
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1000 -> String.format("%.1fk", count / 1000.0)
        count >= 100 -> count.toString()
        else -> count.toString()
    }
}
