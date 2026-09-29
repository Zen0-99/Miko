package eu.kanade.tachiyomi.ui.comments

import eu.kanade.tachiyomi.data.comments.CommentsApi
import eu.kanade.tachiyomi.data.comments.DeviceIdentityProvider
import eu.kanade.tachiyomi.data.comments.RuleEvaluator
import eu.kanade.tachiyomi.data.comments.RulesetProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class CommentsScreenModelTest {

    companion object {
        const val THREAD = "manga:mk:mb:1"

        @BeforeAll
        @JvmStatic
        fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

        @AfterAll
        @JvmStatic
        fun resetMain() = Dispatchers.resetMain()
    }

    private val api = mockk<CommentsApi>()
    private val identity = mockk<DeviceIdentityProvider>()
    private val rulesets = mockk<RulesetProvider>()

    private val comment = CommentsApi.CommentDto(
        id = 1,
        thread_id = THREAD,
        author = CommentsApi.CommentDto.AuthorDto("reader"),
        body = "great chapter",
        created_at = 1_700_000_000_000L,
    )

    @BeforeEach
    fun setup() {
        coEvery { rulesets.refresh() } returns RuleEvaluator.FALLBACK
        every { rulesets.current() } returns RuleEvaluator.FALLBACK
        every { identity.displayName() } returns null
        every { identity.token() } returns "tok"
        coEvery { identity.ensureToken() } returns "tok"
    }

    private fun model(): CommentsScreenModel = CommentsScreenModel(THREAD, api, identity, rulesets)

    private suspend fun awaitIdle(model: CommentsScreenModel) {
        // screenModelScope runs on Dispatchers.IO — poll real time.
        val deadline = System.nanoTime() + 10.seconds.inWholeNanoseconds
        while (model.state.value.loading && System.nanoTime() < deadline) {
            delay(10)
        }
    }

    @Test
    fun `load populates comments`() = runBlocking {
        coEvery { api.getComments(THREAD, any(), any(), any()) } returns
            CommentsApi.Result.Ok(CommentsApi.CommentsPage(listOf(comment), null))
        val model = model()
        awaitIdle(model)
        assertEquals(listOf(comment), model.state.value.comments)
        assertFalse(model.state.value.loading)
    }

    @Test
    fun `degraded backend surfaces banner state`() = runBlocking {
        coEvery { api.getComments(THREAD, any(), any(), any()) } returns CommentsApi.Result.Degraded
        val model = model()
        awaitIdle(model)
        assertTrue(model.state.value.degraded)
    }

    @Test
    fun `name_required opens onboarding and retains pending post`() = runBlocking {
        coEvery { api.getComments(THREAD, any(), any(), any()) } returns
            CommentsApi.Result.Ok(CommentsApi.CommentsPage(emptyList(), null))
        coEvery { api.postComment("tok", THREAD, "hello", null) } returns CommentsApi.Result.NameRequired
        val model = model()
        awaitIdle(model)
        model.post("hello")
        eventually { assertTrue(model.state.value.showNameDialog) }
        assertEquals("hello", model.state.value.pendingPost?.body)
    }

    @Test
    fun `suspended post surfaces countdown data`() = runBlocking {
        val until = System.currentTimeMillis() + 3_600_000
        coEvery { api.getComments(THREAD, any(), any(), any()) } returns
            CommentsApi.Result.Ok(CommentsApi.CommentsPage(emptyList(), null))
        coEvery { api.postComment("tok", THREAD, "hello", null) } returns
            CommentsApi.Result.Suspended(until)
        val model = model()
        awaitIdle(model)
        model.post("hello")
        eventually { assertEquals(until, model.state.value.suspendedUntil) }
        assertTrue(model.state.value.suspended)
    }

    @Test
    fun `local rule violation short-circuits before api call`() = runBlocking {
        coEvery { api.getComments(THREAD, any(), any(), any()) } returns
            CommentsApi.Result.Ok(CommentsApi.CommentsPage(emptyList(), null))
        val model = model()
        awaitIdle(model)
        model.post("   ") // min_len
        eventually { assertEquals("min_len", model.state.value.composerViolation) }
    }

    @Test
    fun `vote updates score and my_vote`() = runBlocking {
        coEvery { api.getComments(THREAD, any(), any(), any()) } returns
            CommentsApi.Result.Ok(CommentsApi.CommentsPage(listOf(comment), null))
        coEvery { api.vote("tok", 1L, 1) } returns CommentsApi.Result.Ok(CommentsApi.VoteResponse(5))
        val model = model()
        awaitIdle(model)
        model.vote(1L, 1)
        eventually {
            assertEquals(5L, model.state.value.comments.first().score)
            assertEquals(1L, model.state.value.comments.first().my_vote)
        }
    }

    @Test
    fun `successful name submits pending post`() = runBlocking {
        coEvery { api.getComments(THREAD, any(), any(), any()) } returns
            CommentsApi.Result.Ok(CommentsApi.CommentsPage(emptyList(), null))
        coEvery { api.postComment("tok", THREAD, "hello", null) } returnsMany
            listOf(
                CommentsApi.Result.NameRequired,
                CommentsApi.Result.Ok(CommentsApi.PostResponse(comment)),
            )
        coEvery { api.setName("tok", "reader1") } returns
            CommentsApi.Result.Ok(CommentsApi.NameResponse("reader1"))
        every { identity.setDisplayName("reader1") } returns Unit
        val model = model()
        awaitIdle(model)
        model.post("hello")
        eventually { assertTrue(model.state.value.showNameDialog) }
        model.submitName("reader1")
        eventually {
            assertFalse(model.state.value.showNameDialog)
            assertEquals("reader1", model.state.value.myName)
        }
    }

    private suspend fun eventually(block: suspend () -> Unit) {
        val deadline = System.nanoTime() + 10.seconds.inWholeNanoseconds
        var last: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                block()
                return
            } catch (t: Throwable) {
                last = t
                delay(20)
            }
        }
        throw last ?: AssertionError("condition not met")
    }
}
