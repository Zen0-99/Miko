package eu.kanade.tachiyomi.data.comments

import eu.kanade.domain.ui.model.Carrier
import eu.kanade.tachiyomi.data.mikodb.WorkIdentityApi
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.injectLazy

/**
 * Client for the deployed comments Worker
 * (`miko-comments.keypop3750.workers.dev`, Phase 8). Identity-domain
 * endpoints (`/v1/works/resolve`) live in [WorkIdentityApi]; this class
 * covers the comment feature surface only.
 */
class CommentsApi {

    // ---------- DTOs (mirror worker/src/comments.ts shapes) ----------

    @Serializable
    data class RegisterResponse(val token: String, val registered_at: Long)

    @Serializable
    data class NameResponse(val display_name: String, val name_tag: String? = null)

    @Serializable
    data class CommentDto(
        val id: Long,
        val thread_id: String,
        val parent_id: Long? = null,
        val author: AuthorDto,
        val body: String? = null,
        val status: String = "visible",
        val score: Long = 0,
        val replies: Long = 0,
        val my_vote: Long? = null,
        val mine: Boolean = false,
        val appeal_status: String? = null,
        val created_at: Long,
        val edited_at: Long? = null,
    ) {
        @Serializable
        data class AuthorDto(val name: String, val tag: String? = null) {
            /** "Name#1234" when a discriminator exists, bare name otherwise. */
            val displayName: String get() = tag?.let { "$name#$it" } ?: name
        }
    }

    @Serializable
    data class CommentsPage(
        val comments: List<CommentDto>,
        val next_before: Long? = null,
    )

    @Serializable
    data class PostResponse(val comment: CommentDto)

    @Serializable
    data class VoteResponse(val score: Long)

    @Serializable
    data class RulesetResponse(
        val ruleset_version: Int,
        val ruleset: RuleEvaluator.Ruleset,
    )

    @Serializable
    data class ErrorBody(
        val error: String,
        val rule: String? = null,
        val suspended_until: Long? = null,
    )

    // ---------- results ----------

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class RuleViolation(val rule: String, val suspendedUntil: Long? = null) : Result<Nothing>
        data class Suspended(val until: Long) : Result<Nothing>
        data object NameRequired : Result<Nothing>
        data object NameTaken : Result<Nothing>
        data object Unauthorized : Result<Nothing>
        data object Degraded : Result<Nothing>
        data class Failure(val code: Int, val message: String? = null) : Result<Nothing>
    }

    private val networkHelper: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val json: Json by injectLazy()

    private val baseUrlPref by lazy {
        preferenceStore.getString("miko_comments_base_url", WorkIdentityApi.DEFAULT_BASE_URL)
    }
    private fun base() = baseUrlPref.get().trim().trimEnd('/')

    private val authHeaders = Headers.headersOf("User-Agent", WorkIdentityApi.USER_AGENT)

    private fun authed(url: String, token: String?, method: String, body: okhttp3.RequestBody?): Request {
        val headers = Headers.headersOf(
            "User-Agent",
            WorkIdentityApi.USER_AGENT,
            *(token?.let { arrayOf("Authorization", "Bearer $it") } ?: emptyArray()),
        )
        val builder = Request.Builder().url(url).headers(headers)
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            else -> builder.post(body ?: EMPTY_BODY)
        }
        return builder.build()
    }

    private suspend inline fun <reified T> call(
        request: Request,
    ): Result<T> = try {
        networkHelper.client.newCall(request).await().use { response ->
            if (response.isSuccessful) {
                with(json) { Result.Ok(response.parseAs<T>()) }
            } else {
                mapError(response.code, response.peekBody(4096).string())
            }
        }
    } catch (e: Exception) {
        Result.Failure(-1, e.message)
    }

    private fun <T> mapError(code: Int, body: String?): Result<T> {
        val parsed = body?.let { runCatching { json.decodeFromString<ErrorBody>(it) }.getOrNull() }
        return when (
            parsed?.error ?: when (code) {
                401 -> "unauthorized"
                503 -> "degraded"
                else -> "http_$code"
            }
        ) {
            "unauthorized" -> Result.Unauthorized
            "suspended" -> Result.Suspended(parsed?.suspended_until ?: 0L)
            "rule" -> Result.RuleViolation(parsed?.rule ?: "unknown", parsed?.suspended_until)
            "name_required" -> Result.NameRequired
            "name_taken" -> Result.NameTaken
            "degraded" -> Result.Degraded
            else -> Result.Failure(code, parsed?.error)
        }
    }

    // ---------- endpoints ----------

    suspend fun register(deviceHash: String): Result<RegisterResponse> =
        call(
            POST(
                "${base()}/v1/device/register",
                headers = authHeaders,
                body = json.encodeToString(mapOf("device_hash" to deviceHash)).toRequestBody(jsonMime),
            ),
        )

    suspend fun setName(token: String, name: String): Result<NameResponse> =
        call(
            authed(
                "${base()}/v1/names",
                token,
                "POST",
                json.encodeToString(mapOf("name" to name)).toRequestBody(jsonMime),
            ),
        )

    suspend fun postComment(
        token: String,
        threadId: String,
        body: String,
        parentId: Long? = null,
    ): Result<PostResponse> = call(
        authed(
            "${base()}/v1/comments",
            token,
            "POST",
            json.encodeToString(
                buildMap {
                    put("thread_id", kotlinx.serialization.json.JsonPrimitive(threadId))
                    put("body", kotlinx.serialization.json.JsonPrimitive(body))
                    parentId?.let { put("parent_id", kotlinx.serialization.json.JsonPrimitive(it)) }
                },
            ).toRequestBody(jsonMime),
        ),
    )

    suspend fun getComments(
        threadId: String,
        before: Long? = null,
        limit: Int = 50,
        token: String? = null,
    ): Result<CommentsPage> = call(
        authed(
            "${base()}/v1/comments?thread_id=$threadId&limit=$limit" +
                (before?.let { "&before=$it" } ?: ""),
            token,
            "GET",
            null,
        ),
    )

    suspend fun vote(token: String, commentId: Long, value: Int): Result<VoteResponse> =
        call(
            authed(
                "${base()}/v1/comments/$commentId/vote",
                token,
                "POST",
                json.encodeToString(mapOf("value" to value)).toRequestBody(jsonMime),
            ),
        )

    suspend fun report(token: String, commentId: Long): Result<JsonObject> =
        call(authed("${base()}/v1/comments/$commentId/report", token, "POST", EMPTY_BODY))

    suspend fun delete(token: String, commentId: Long): Result<JsonObject> =
        call(authed("${base()}/v1/comments/$commentId", token, "DELETE", null))

    suspend fun getRuleset(): Result<RulesetResponse> =
        call(authed("${base()}/v1/ruleset", null, "GET", null))

    // ---------- Phase 10: chapter alignment + appeals ----------

    @Serializable
    data class AlignResponse(
        val status: String,
        val score: Double? = null,
        val reference: String? = null,
    )

    @Serializable
    data class AppealResponse(
        val appeal_id: Long? = null,
        val status: String,
    )

    @Serializable
    data class AppealDto(
        val id: Long,
        val comment_id: Long,
        val status: String,
        val jev_answer: String? = null,
        val created_at: Long,
        val resolved_at: Long? = null,
    )

    @Serializable
    data class AppealsResponse(val appeals: List<AppealDto>)

    suspend fun alignChapters(
        workId: String,
        sourceKey: String,
        carrier: Carrier,
        title: String?,
        fingerprint: ChapterFingerprint,
    ): Result<AlignResponse> = call(
        authed(
            "${base()}/v1/chapters/align",
            null,
            "POST",
            json.encodeToString(
                buildMap {
                    put("work_id", kotlinx.serialization.json.JsonPrimitive(workId))
                    put("source_key", kotlinx.serialization.json.JsonPrimitive(sourceKey))
                    put("medium", kotlinx.serialization.json.JsonPrimitive(carrier.medium))
                    title?.let { put("title", kotlinx.serialization.json.JsonPrimitive(it)) }
                    put("count", kotlinx.serialization.json.JsonPrimitive(fingerprint.count))
                    put(
                        "numbers",
                        kotlinx.serialization.json.JsonArray(
                            fingerprint.numbers.map { kotlinx.serialization.json.JsonPrimitive(it) },
                        ),
                    )
                    put("names_hash", kotlinx.serialization.json.JsonPrimitive(fingerprint.namesHash))
                },
            ).toRequestBody(jsonMime),
        ),
    )

    suspend fun appeal(token: String, commentId: Long, reason: String?): Result<AppealResponse> =
        call(
            authed(
                "${base()}/v1/comments/$commentId/appeal",
                token,
                "POST",
                json.encodeToString(
                    buildMap {
                        reason?.let { put("reason", kotlinx.serialization.json.JsonPrimitive(it)) }
                    },
                ).toRequestBody(jsonMime),
            ),
        )

    suspend fun myAppeals(token: String): Result<AppealsResponse> =
        call(authed("${base()}/v1/appeals/mine", token, "GET", null))

    companion object {
        private val EMPTY_BODY: okhttp3.RequestBody = "".toRequestBody()
    }
}
