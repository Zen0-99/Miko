package eu.kanade.presentation.comments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.entries.components.accentTintedSurface
import eu.kanade.tachiyomi.data.comments.CommentsApi
import eu.kanade.tachiyomi.ui.comments.CommentsScreenModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.stringResource
import java.text.DateFormat
import java.util.Date

private enum class CommentSort(val labelRes: StringResource) {
    NEWEST(MR.strings.comment_sort_new),
    OLDEST(MR.strings.comment_sort_oldest),
    TOP(MR.strings.comment_sort_top),
    LOWEST(MR.strings.comment_sort_lowest),
}

private data class CommentThread(
    val parent: CommentsApi.CommentDto,
    val replies: List<CommentsApi.CommentDto>,
) {
    /** Server-reported reply count wins over the loaded list (pagination can lag). */
    val replyCount: Int get() = maxOf(replies.size, parent.replies.toInt())
}

/**
 * Work-level comment thread for an entry screen. Stateless — all
 * interaction flows through [CommentsScreenModel] callbacks.
 */
@Composable
fun CommentSection(
    state: CommentsScreenModel.State,
    onPost: (String, Long?) -> Unit,
    onSubmitName: (String) -> Unit,
    onDismissNameDialog: () -> Unit,
    onVote: (Long, Int) -> Unit,
    onReply: (CommentsApi.CommentDto?) -> Unit,
    onReport: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onAppeal: (Long, String?) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Entry cover accent the host sheet resolved. The sheet also overrides
     * `colorScheme.primary` with it — this explicit param exists so tinting
     * decisions here don't depend on that override staying in place.
     */
    accentColor: Color? = null,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    var sort by rememberSaveable { mutableStateOf(CommentSort.NEWEST.name) }
    // Local-only UI state: which threads have their replies collapsed.
    var collapsedThreads by remember { mutableStateOf(setOf<Long>()) }

    Column(modifier = modifier.fillMaxWidth()) {
        CommentsHeader(
            count = state.comments.size,
            sort = CommentSort.valueOf(sort),
            onSortChange = { sort = it.name },
        )

        state.suspendedUntil?.takeIf { it > System.currentTimeMillis() }?.let { until ->
            StatusBanner(
                icon = Icons.Outlined.Warning,
                text = stringResource(
                    MR.strings.comment_suspended,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(until)),
                ),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (state.degraded) {
            StatusBanner(
                icon = Icons.Outlined.Info,
                text = stringResource(MR.strings.comments_degraded),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }

        val threads = remember(state.comments, sort) {
            buildThreads(state.comments, CommentSort.valueOf(sort))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
        ) {
            when {
                state.loading && state.comments.isEmpty() -> {
                    items(4, key = { "skeleton_$it" }) {
                        CommentSkeleton(accent = accent)
                    }
                }
                threads.isEmpty() -> {
                    item(key = "empty") { CommentsEmptyState() }
                }
                else -> {
                    items(
                        items = threads,
                        key = { it.parent.id },
                        contentType = { "thread" },
                    ) { thread ->
                        CommentThreadItem(
                            thread = thread,
                            collapsed = thread.parent.id in collapsedThreads,
                            onToggleCollapsed = {
                                collapsedThreads = if (thread.parent.id in collapsedThreads) {
                                    collapsedThreads - thread.parent.id
                                } else {
                                    collapsedThreads + thread.parent.id
                                }
                            },
                            onVote = onVote,
                            onReply = onReply,
                            onReport = onReport,
                            onDelete = onDelete,
                            onAppeal = onAppeal,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            if (state.nextBefore != null) {
                item(key = "load_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(
                            onClick = onLoadMore,
                            enabled = !state.loadingMore,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            if (state.loadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(MR.strings.comments_load_more))
                        }
                    }
                }
            }
        }

        CommentComposer(
            posting = state.posting,
            replyTarget = state.replyTarget,
            violation = state.composerViolation,
            accent = accent,
            onClearReply = { onReply(null) },
            onPost = onPost,
        )

        if (state.showNameDialog) {
            NameOnboardingDialog(
                inProgress = state.namingInProgress,
                violation = state.nameViolation,
                onSubmit = onSubmitName,
                onDismiss = onDismissNameDialog,
            )
        }
    }
}

/**
 * Groups flat comments into parent+replies threads. Replies are flattened
 * under their root parent (YouTube-style): a reply to a reply still hangs
 * off the top-level comment.
 */
private fun buildThreads(
    comments: List<CommentsApi.CommentDto>,
    sort: CommentSort,
): List<CommentThread> {
    val byId = comments.associateBy { it.id }
    fun rootOf(comment: CommentsApi.CommentDto): CommentsApi.CommentDto {
        var current = comment
        var hops = 0
        while (current.parent_id != null && hops++ < 50) {
            current = byId[current.parent_id] ?: break
        }
        return current
    }

    val repliesByRoot = comments
        .filter { it.parent_id != null }
        .groupBy { rootOf(it).id }
    val parents = comments.filter { it.parent_id == null }

    val sortedParents = when (sort) {
        CommentSort.NEWEST -> parents.sortedByDescending { it.created_at }
        CommentSort.OLDEST -> parents.sortedBy { it.created_at }
        CommentSort.TOP -> parents.sortedByDescending { it.score }
        CommentSort.LOWEST -> parents.sortedBy { it.score }
    }

    return sortedParents.map { parent ->
        CommentThread(
            parent = parent,
            replies = repliesByRoot[parent.id].orEmpty().sortedBy { it.created_at },
        )
    }
}

@Composable
private fun CommentsHeader(
    count: Int,
    sort: CommentSort,
    onSortChange: (CommentSort) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(MR.strings.comments_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (count > 0) {
            Text(
                text = " · $count",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
            )
        }
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { sortMenuOpen = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = stringResource(MR.strings.action_sort),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
            ) {
                CommentSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        onClick = {
                            onSortChange(option)
                            sortMenuOpen = false
                        },
                        trailingIcon = if (option == sort) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun CommentThreadItem(
    thread: CommentThread,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onVote: (Long, Int) -> Unit,
    onReply: (CommentsApi.CommentDto?) -> Unit,
    onReport: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onAppeal: (Long, String?) -> Unit,
) {
    val parentIsMine = thread.parent.mine
    Column(modifier = Modifier.fillMaxWidth()) {
        CommentRow(
            comment = thread.parent,
            isMine = parentIsMine,
            onVote = onVote,
            onReply = { onReply(thread.parent) },
            onReport = onReport,
            onDelete = onDelete,
            onAppeal = onAppeal,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                .then(
                    // Tapping a parent comment with replies collapses /
                    // reopens the branch — no separate "N replies" row.
                    if (thread.replyCount > 0) {
                        Modifier.clickable(
                            onClickLabel = stringResource(
                                if (collapsed) {
                                    MR.strings.comment_collapse
                                } else {
                                    MR.strings.comment_hide_replies
                                },
                            ),
                            onClick = onToggleCollapsed,
                        )
                    } else {
                        Modifier
                    },
                ),
        )

        if (thread.replyCount > 0) {
            AnimatedVisibility(visible = !collapsed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = REPLY_INDENT.dp)
                        .height(IntrinsicSize.Min),
                ) {
                    // Thread bar — Summit-style vertical guide for the reply branch.
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 8.dp)
                            .width(2.dp)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        thread.replies.forEach { reply ->
                            CommentRow(
                                comment = reply,
                                isMine = reply.mine,
                                // Flattened replies target the root parent so new
                                // replies always join the same visible branch.
                                onVote = onVote,
                                onReply = { onReply(thread.parent) },
                                onReport = onReport,
                                onDelete = onDelete,
                                onAppeal = onAppeal,
                                modifier = Modifier.padding(
                                    start = 12.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                ),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CommentRow(
    comment: CommentsApi.CommentDto,
    isMine: Boolean,
    onVote: (Long, Int) -> Unit,
    onReply: () -> Unit,
    onReport: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onAppeal: (Long, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val deleted = comment.status == "deleted"
    val hidden = comment.status == "hidden"
    var showAppealDialog by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header line: name (+ accent #tag) (+ you badge) · relative time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                comment.author.tag?.let { tag ->
                    Text(
                        text = "#$tag",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                if (isMine) {
                    Surface(
                        modifier = Modifier.padding(start = 6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = stringResource(MR.strings.comment_you),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    text = " · ${relativeDateTimeText(comment.created_at)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (comment.edited_at != null && !deleted) {
                Text(
                    text = stringResource(MR.strings.comment_edited),
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
                )
            }

            Text(
                text = when {
                    deleted -> stringResource(MR.strings.comment_deleted)
                    hidden -> stringResource(MR.strings.comment_hidden)
                    else -> comment.body.orEmpty()
                },
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = if (deleted || hidden) FontStyle.Italic else FontStyle.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (deleted || hidden) SECONDARY_ALPHA else 1f,
                ),
                modifier = Modifier.padding(top = 2.dp),
            )

            if (hidden && isMine) {
                HiddenActions(comment = comment, onAppealClick = { showAppealDialog = true })
            } else if (!deleted && !hidden) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    VotePill(comment = comment, onVote = onVote)
                    ReplyButton(onReply = onReply)
                    CommentOverflowMenu(
                        isMine = isMine,
                        status = comment.status,
                        onReport = { onReport(comment.id) },
                        onDelete = { onDelete(comment.id) },
                    )
                }
            }
        }
    }
    if (showAppealDialog) {
        AppealDialog(
            onSubmit = { reason ->
                showAppealDialog = false
                onAppeal(comment.id, reason)
            },
            onDismiss = { showAppealDialog = false },
        )
    }
}

/** Compact vote cluster: up-arrow | score | down-arrow on a tonal pill. */
@Composable
private fun VotePill(comment: CommentsApi.CommentDto, onVote: (Long, Int) -> Unit) {
    val upActive = comment.my_vote == 1L
    val downActive = comment.my_vote == -1L
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onVote(comment.id, if (upActive) 0 else 1) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = stringResource(MR.strings.comment_upvote),
                    tint = if (upActive) activeColor else inactiveColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = comment.score.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (upActive || downActive) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    upActive || downActive -> activeColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            IconButton(
                onClick = { onVote(comment.id, if (downActive) 0 else -1) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = stringResource(MR.strings.comment_downvote),
                    tint = if (downActive) activeColor else inactiveColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ReplyButton(onReply: () -> Unit) {
    Surface(
        onClick = onReply,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(MR.strings.comment_reply),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
            )
        }
    }
}

@Composable
private fun HiddenActions(comment: CommentsApi.CommentDto, onAppealClick: () -> Unit) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (comment.appeal_status) {
            "pending" -> Text(
                text = stringResource(MR.strings.comment_appeal_pending),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
            )
            "upheld" -> Text(
                text = stringResource(MR.strings.comment_appeal_upheld),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
            )
            else -> TextButton(onClick = onAppealClick) {
                Text(
                    stringResource(MR.strings.comment_appeal),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun AppealDialog(
    onSubmit: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.comment_appeal_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(MR.strings.comment_appeal_body))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text(stringResource(MR.strings.comment_appeal_hint)) },
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(reason.trim().ifBlank { null }) }) {
                Text(stringResource(MR.strings.comment_appeal))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun CommentOverflowMenu(
    isMine: Boolean,
    status: String,
    onReport: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(MR.strings.comment_options),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (isMine) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.comment_delete)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (status == "reported") {
                                    MR.strings.comment_reported
                                } else {
                                    MR.strings.comment_report
                                },
                            ),
                        )
                    },
                    enabled = status != "reported",
                    onClick = {
                        expanded = false
                        onReport()
                    },
                )
            }
        }
    }
}

/** Shimmer-ish placeholder shown while the thread loads. */
@Composable
private fun CommentSkeleton(accent: Color) {
    // Ghost shapes carry the entry accent — same damped-hue recipe as the
    // tinted sheet surface behind them, anchored off the neutral container
    // tone so they stay readable as placeholders.
    val ghost = accentTintedSurface(
        accent = accent,
        background = MaterialTheme.colorScheme.surfaceContainerHighest,
        dimmed = false,
    )
    Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ghost),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ghost),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ghost),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ghost),
            )
        }
    }
}

@Composable
private fun CommentsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(MR.strings.comments_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
        )
    }
}

/** Docked composer: reply context chip, violation text, field + send. */
@Composable
private fun CommentComposer(
    posting: Boolean,
    replyTarget: CommentsApi.CommentDto?,
    violation: String?,
    accent: Color,
    onClearReply: () -> Unit,
    onPost: (String, Long?) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    // The composer band carries the entry accent the same way the sheet
    // surface and ghost shapes do — divider, reply chip, and the field's
    // resting outline all pick the accent hue up.
    val chipColor = accentTintedSurface(
        accent = accent,
        background = MaterialTheme.colorScheme.surface,
        dimmed = false,
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = accent.copy(alpha = 0.35f))
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            replyTarget?.let { target ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = chipColor,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(MR.strings.comment_replying_to, target.author.displayName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(onClick = onClearReply, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(MR.strings.action_cancel),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            violation?.let { rule ->
                Text(
                    text = ruleMessage(rule),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(MR.strings.comment_hint)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = accent.copy(alpha = 0.5f),
                    ),
                    maxLines = 4,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                onPost(text, replyTarget?.id)
                                text = ""
                            },
                            enabled = !posting && text.isNotBlank(),
                        ) {
                            if (posting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Send,
                                    contentDescription = stringResource(MR.strings.comment_send),
                                    tint = if (text.isNotBlank()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA)
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NameOnboardingDialog(
    inProgress: Boolean,
    violation: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.comment_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(MR.strings.comment_name_body))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    isError = violation != null,
                    supportingText = violation?.let { { Text(nameRuleMessage(it)) } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(name.trim()) }, enabled = !inProgress && name.isNotBlank()) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun ruleMessage(rule: String): String = when (rule) {
    "min_len" -> stringResource(MR.strings.comment_rule_min_len)
    "max_len" -> stringResource(MR.strings.comment_rule_max_len)
    "max_links" -> stringResource(MR.strings.comment_rule_max_links)
    "rate_min", "rate_hour", "rate_thread" -> stringResource(MR.strings.comment_rate_limited)
    "duplicate" -> stringResource(MR.strings.comment_rule_duplicate)
    else -> stringResource(MR.strings.comment_rule_blocked)
}

@Composable
private fun nameRuleMessage(rule: String): String = when (rule) {
    "name_taken" -> stringResource(MR.strings.comment_name_taken)
    "min_len", "max_len" -> stringResource(MR.strings.comment_name_rule_len)
    "pattern" -> stringResource(MR.strings.comment_name_rule_pattern)
    "reserved" -> stringResource(MR.strings.comment_name_reserved)
    else -> stringResource(MR.strings.comment_name_failed)
}

/** Left gutter for reply branches — far enough in that replies read as
 * a nested branch, not a flat list. */
private const val REPLY_INDENT = 28
