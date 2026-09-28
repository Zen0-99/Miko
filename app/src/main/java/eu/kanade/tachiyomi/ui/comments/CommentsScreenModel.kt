package eu.kanade.tachiyomi.ui.comments

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.comments.CommentsApi
import eu.kanade.tachiyomi.data.comments.DeviceIdentityProvider
import eu.kanade.tachiyomi.data.comments.RuleEvaluator
import eu.kanade.tachiyomi.data.comments.RulesetProvider
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Comment thread for one canonical work (`threadId` = `shows|manga|books:<workId>`).
 * Maps [CommentsApi.Result] onto UI state: suspension countdown, name onboarding,
 * degraded banner, per-comment `my_vote`.
 */
class CommentsScreenModel(
    val threadId: String,
    private val api: CommentsApi = Injekt.get(),
    private val identity: DeviceIdentityProvider = Injekt.get(),
    private val rulesets: RulesetProvider = Injekt.get(),
) : StateScreenModel<CommentsScreenModel.State>(State()) {

    init {
        // Stale-while-revalidate: show the last fetched page instantly,
        // then refresh in the background — avoids a skeleton flash on
        // every sheet reopen.
        mutableState.update {
            it.copy(
                myName = identity.displayName(),
                comments = pageCache[threadId]?.comments ?: emptyList(),
                nextBefore = pageCache[threadId]?.nextBefore,
            )
        }
        load()
    }

    fun load() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(loading = true, error = null, degraded = false) }
            rulesets.refresh()
            val token = identity.token()
            when (val r = api.getComments(threadId, token = token)) {
                is CommentsApi.Result.Ok -> {
                    pageCache[threadId] = CachedPage(r.value.comments, r.value.next_before)
                    mutableState.update {
                        it.copy(
                            comments = r.value.comments,
                            nextBefore = r.value.next_before,
                            loading = false,
                        )
                    }
                }
                is CommentsApi.Result.Degraded -> mutableState.update {
                    it.copy(loading = false, degraded = true)
                }
                is CommentsApi.Result.Failure -> mutableState.update {
                    it.copy(loading = false, error = r.message ?: "http_${r.code}")
                }
                else -> mutableState.update { it.copy(loading = false, error = "unexpected") }
            }
        }
    }

    fun loadMore() {
        val before = state.value.nextBefore ?: return
        screenModelScope.launchIO {
            mutableState.update { it.copy(loadingMore = true) }
            when (val r = api.getComments(threadId, before = before, token = identity.token())) {
                is CommentsApi.Result.Ok -> {
                    val merged = state.value.comments + r.value.comments
                    pageCache[threadId] = CachedPage(merged, r.value.next_before)
                    mutableState.update {
                        it.copy(
                            comments = merged,
                            nextBefore = r.value.next_before,
                            loadingMore = false,
                        )
                    }
                }
                else -> mutableState.update { it.copy(loadingMore = false) }
            }
        }
    }

    /** Pre-checks the body locally, then posts. `parentId` set = reply. */
    fun post(body: String, parentId: Long? = null) {
        val rule = RuleEvaluator.evaluateComment(body, rulesets.current())
        if (rule != null) {
            mutableState.update { it.copy(composerViolation = rule) }
            return
        }
        screenModelScope.launchIO {
            mutableState.update { it.copy(posting = true, composerViolation = null) }
            val token = identity.ensureToken()
            if (token == null) {
                mutableState.update { it.copy(posting = false, degraded = true) }
                return@launchIO
            }
            when (val r = api.postComment(token, threadId, body, parentId)) {
                is CommentsApi.Result.Ok -> {
                    mutableState.update {
                        it.copy(posting = false, replyTarget = null)
                    }
                    load()
                }
                is CommentsApi.Result.NameRequired -> {
                    // Stash the pending body; the onboarding dialog retries after naming.
                    mutableState.update {
                        it.copy(posting = false, showNameDialog = true, pendingPost = PendingPost(body, parentId))
                    }
                }
                is CommentsApi.Result.Suspended -> mutableState.update {
                    it.copy(posting = false, suspendedUntil = r.until)
                }
                is CommentsApi.Result.RuleViolation -> mutableState.update {
                    it.copy(
                        posting = false,
                        composerViolation = r.rule,
                        suspendedUntil = r.suspendedUntil ?: it.suspendedUntil,
                    )
                }
                is CommentsApi.Result.Unauthorized -> retryWithFreshToken {
                    api.postComment(it, threadId, body, parentId)
                }?.let(::applyPostResult)
                is CommentsApi.Result.Degraded -> mutableState.update { it.copy(posting = false, degraded = true) }
                is CommentsApi.Result.Failure -> mutableState.update {
                    it.copy(posting = false, error = r.message ?: "http_${r.code}")
                }
                else -> mutableState.update { it.copy(posting = false) }
            }
        }
    }

    fun submitName(name: String) {
        val rule = RuleEvaluator.evaluateName(name, rulesets.current())
        if (rule != null) {
            mutableState.update { it.copy(nameViolation = rule) }
            return
        }
        screenModelScope.launchIO {
            mutableState.update { it.copy(namingInProgress = true, nameViolation = null) }
            val token = identity.ensureToken()
            if (token == null) {
                mutableState.update { it.copy(namingInProgress = false, degraded = true) }
                return@launchIO
            }
            when (val r = api.setName(token, name)) {
                is CommentsApi.Result.Ok -> {
                    val tagged = r.value.name_tag
                        ?.let { "${r.value.display_name}#$it" }
                        ?: r.value.display_name
                    identity.setDisplayName(tagged)
                    val pending = state.value.pendingPost
                    mutableState.update {
                        it.copy(
                            namingInProgress = false,
                            showNameDialog = false,
                            myName = tagged,
                            pendingPost = null,
                        )
                    }
                    pending?.let { post(it.body, it.parentId) }
                }
                is CommentsApi.Result.NameTaken -> mutableState.update {
                    it.copy(namingInProgress = false, nameViolation = "name_taken")
                }
                is CommentsApi.Result.RuleViolation -> mutableState.update {
                    it.copy(namingInProgress = false, nameViolation = r.rule)
                }
                else -> mutableState.update { it.copy(namingInProgress = false, nameViolation = "unknown") }
            }
        }
    }

    fun dismissNameDialog() {
        mutableState.update { it.copy(showNameDialog = false, pendingPost = null, nameViolation = null) }
    }

    fun setReplyTarget(comment: CommentsApi.CommentDto?) {
        mutableState.update { it.copy(replyTarget = comment) }
    }

    fun vote(commentId: Long, value: Int) = authedAction(commentId) { token ->
        when (val r = api.vote(token, commentId, value)) {
            is CommentsApi.Result.Ok -> updateComment(commentId) {
                it.copy(score = r.value.score, my_vote = value.toLong().takeIf { v -> v != 0L })
            }
            else -> applyGenericError(r)
        }
    }

    fun report(commentId: Long) = authedAction(commentId) { token ->
        when (val r = api.report(token, commentId)) {
            is CommentsApi.Result.Ok -> updateComment(commentId) {
                it.copy(status = "reported")
            }
            else -> applyGenericError(r)
        }
    }

    fun delete(commentId: Long) = authedAction(commentId) { token ->
        when (val r = api.delete(token, commentId)) {
            is CommentsApi.Result.Ok -> updateComment(commentId) {
                it.copy(status = "deleted", body = null)
            }
            else -> applyGenericError(r)
        }
    }

    /** Appeal a hidden comment the user authored (Phase 10 / Jev). */
    fun appeal(commentId: Long, reason: String?) = authedAction(commentId) { token ->
        when (val r = api.appeal(token, commentId, reason)) {
            is CommentsApi.Result.Ok -> updateComment(commentId) {
                it.copy(
                    appeal_status = r.value.status,
                    status = if (r.value.status == "restored") "visible" else it.status,
                )
            }
            else -> applyGenericError(r)
        }
    }

    private inline fun authedAction(commentId: Long, crossinline block: suspend (String) -> Unit) {
        screenModelScope.launchIO {
            val token = identity.ensureToken() ?: run {
                mutableState.update { it.copy(degraded = true) }
                return@launchIO
            }
            block(token)
        }
    }

    private suspend fun retryWithFreshToken(
        block: suspend (String) -> CommentsApi.Result<CommentsApi.PostResponse>,
    ): CommentsApi.Result<CommentsApi.PostResponse>? {
        val fresh = identity.refreshToken() ?: return null
        return block(fresh)
    }

    private fun applyPostResult(r: CommentsApi.Result<CommentsApi.PostResponse>) {
        when (r) {
            is CommentsApi.Result.Ok -> load()
            is CommentsApi.Result.Suspended -> mutableState.update { it.copy(suspendedUntil = r.until) }
            is CommentsApi.Result.RuleViolation -> mutableState.update { it.copy(composerViolation = r.rule) }
            else -> Unit
        }
        mutableState.update { it.copy(posting = false) }
    }

    private fun applyGenericError(r: CommentsApi.Result<*>) {
        mutableState.update {
            when (r) {
                is CommentsApi.Result.Suspended -> it.copy(suspendedUntil = r.until)
                is CommentsApi.Result.Degraded -> it.copy(degraded = true)
                is CommentsApi.Result.RuleViolation -> it.copy(
                    suspendedUntil = r.suspendedUntil ?: it.suspendedUntil,
                )
                else -> it
            }
        }
    }

    private fun updateComment(id: Long, transform: (CommentsApi.CommentDto) -> CommentsApi.CommentDto) {
        mutableState.update { s ->
            val updated = s.comments.map { if (it.id == id) transform(it) else it }
            pageCache[threadId]?.let { pageCache[threadId] = it.copy(comments = updated) }
            s.copy(comments = updated)
        }
    }

    private data class CachedPage(
        val comments: List<CommentsApi.CommentDto>,
        val nextBefore: Long?,
    )

    companion object {
        /** Process-level page cache — survives ScreenModel recreation on sheet reopen. */
        private val pageCache = object : LinkedHashMap<String, CachedPage>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedPage>?) =
                size > 32
        }
    }

    data class PendingPost(val body: String, val parentId: Long?)

    @Immutable
    data class State(
        val comments: List<CommentsApi.CommentDto> = emptyList(),
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val posting: Boolean = false,
        val nextBefore: Long? = null,
        val suspendedUntil: Long? = null,
        val degraded: Boolean = false,
        val error: String? = null,
        val myName: String? = null,
        val showNameDialog: Boolean = false,
        val namingInProgress: Boolean = false,
        val nameViolation: String? = null,
        val composerViolation: String? = null,
        val replyTarget: CommentsApi.CommentDto? = null,
        val pendingPost: PendingPost? = null,
    ) {
        val suspended: Boolean get() = suspendedUntil?.let { it > System.currentTimeMillis() } == true
    }
}
