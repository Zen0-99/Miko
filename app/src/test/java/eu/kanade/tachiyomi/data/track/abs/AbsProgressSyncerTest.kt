package eu.kanade.tachiyomi.data.track.abs

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.novel.abs.AbsClient
import eu.kanade.tachiyomi.source.novel.abs.AbsProgressBatchUpdate
import eu.kanade.tachiyomi.source.novel.abs.AbsProgressPatch
import eu.kanade.tachiyomi.source.novel.abs.AbsRequest
import eu.kanade.tachiyomi.source.novel.abs.AbsUrls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

/**
 * Merge-matrix vertical for [AbsProgressSyncer] — RESEARCH §7.2 exercised
 * end-to-end through the [AbsClient] transport seam: recorded requests
 * prove the PATCH path/body/headers, the in-memory pref fake proves the
 * row writes, and a fake clock drives the push-guard window.
 *
 * Fixture tracks: three 1 h files → item duration 10_800_000 ms; the
 * `abs_progress.json` remote row sits at 8_123_000 ms (8123 s), inside
 * track f3 (start 7_200_000).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AbsProgressSyncerTest {

    // ---- fakes ---------------------------------------------------------------

    /** SharedPreferences stub — only getString is read by AbsClient. */
    private class FakePrefs(private val values: Map<String, String>) : SharedPreferences {
        override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String?, defValue: Int) = defValue
        override fun getLong(key: String?, defValue: Long) = defValue
        override fun getFloat(key: String?, defValue: Float) = defValue
        override fun getBoolean(key: String?, defValue: Boolean) = defValue
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
    }

    /** In-memory `Preference<String>` backing the abs_progress pref. */
    private class FakePref(initial: String = "{}") : Preference<String> {
        private val state = MutableStateFlow(initial)
        override fun key() = "pref_novel_audiobook_abs_progress"
        override fun get(): String = state.value
        override fun set(value: String) {
            state.value = value
        }
        override fun isSet() = true
        override fun delete() {
            state.value = "{}"
        }
        override fun defaultValue() = "{}"
        override fun changes(): Flow<String> = state
        override fun stateIn(scope: CoroutineScope): StateFlow<String> = state
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)) { "missing fixture $name" }
            .readText()

    private val configuredPrefs = mapOf(
        "abs_base_url" to "http://abs.test:13378",
        "__PRIVATE_abs_api_key" to "tok-test",
        "abs_library_id" to "lib-1",
    )

    /**
     * ABS server fake: records every [AbsRequest], serves
     * `GET /api/me/progress/{id}` from [progressJson] (null → 404) and
     * `PATCH`es with [patchError] (when set) or [patchResponse].
     */
    private inner class FakeServer(
        private val progressJson: String? = fixture("abs_progress.json"),
        private val patchResponse: String = "{}",
        private val patchError: Exception? = null,
    ) {
        val recorded = mutableListOf<AbsRequest>()
        val client: AbsClient = AbsClient(FakePrefs(configuredPrefs))

        init {
            client.transport = { req ->
                recorded += req
                when {
                    req.method == "GET" && req.path.startsWith("/api/me/progress/") ->
                        progressJson ?: throw HttpException(404)
                    req.method == "PATCH" && req.path.startsWith("/api/me/progress/") ->
                        patchError?.let { throw it } ?: patchResponse
                    else -> error("unexpected ${req.method} ${req.path}")
                }
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val tracks = listOf(
        AbsProgressSyncer.TrackCtx("f1", 0L, 3_600_000L),
        AbsProgressSyncer.TrackCtx("f2", 3_600_000L, 3_600_000L),
        AbsProgressSyncer.TrackCtx("f3", 7_200_000L, 3_600_000L),
    )

    private var now = 1_800_000_000_000L

    private fun newSyncer(server: FakeServer?, pref: FakePref): AbsProgressSyncer =
        AbsProgressSyncer({ server?.client }, pref).apply { nowMs = { now } }

    private fun prefKey(itemId: String = "it-1") =
        "${AbsUrls.serverKeyFor("http://abs.test:13378")}|$itemId"

    private fun stored(pref: FakePref, itemId: String = "it-1"): AbsProgressSyncer.ItemProgress? =
        json.decodeFromString<Map<String, AbsProgressSyncer.ItemProgress>>(pref.get())[prefKey(itemId)]

    private fun rowJson(itemGlobalMs: Long, lastUpdate: Long, dirty: Boolean): String =
        """{"${prefKey()}":{"itemGlobalMs":$itemGlobalMs,"lastUpdateMs":$lastUpdate,"dirty":$dirty}}"""

    private fun patchBody(req: AbsRequest): AbsProgressPatch =
        json.decodeFromString<AbsProgressPatch>(req.body!!)

    private fun patches(server: FakeServer) = server.recorded.filter { it.method == "PATCH" }

    // ---- pull merge (§7.2) ------------------------------------------------------

    @Test
    fun `dirty local wins over a newer remote and pushes its position`() = runTest {
        val pref = FakePref(rowJson(1_000_000L, 1_758_999_900_000L, dirty = true))
        val server = FakeServer() // remote at 8_123_000 ms, newer than local
        val syncer = newSyncer(server, pref)

        val seed = syncer.pull("it-1", tracks)

        assertNull(seed, "dirty local never yields an adopt seed")
        val patch = patches(server).single()
        assertEquals("/api/me/progress/it-1", patch.path)
        val body = patchBody(patch)
        assertEquals(1_000.0, body.currentTime, 0.001)
        assertEquals(10_800.0, body.duration, 0.001)
        assertFalse(body.isFinished)
        // Push confirmed → dirty cleared; local position retained.
        val row = stored(pref)!!
        assertFalse(row.dirty)
        assertEquals(1_000_000L, row.itemGlobalMs)
    }

    @Test
    fun `clean local adopts a newer remote, seeds it, and the guard blocks the echo`() = runTest {
        // Local is clean and one minute stale; remote moved on.
        val pref = FakePref(rowJson(1_000_000L, FIXTURE_TS - 60_000L, dirty = false))
        val server = FakeServer()
        now = FIXTURE_TS
        val syncer = newSyncer(server, pref)

        val seed = syncer.pull("it-1", tracks)

        assertEquals(8_123_000L, seed)
        val adopted = stored(pref)!!
        assertEquals(8_123_000L, adopted.itemGlobalMs)
        assertEquals(FIXTURE_TS, adopted.lastUpdateMs)
        assertFalse(adopted.dirty)

        // Inside the 5 s guard window the restored position is never echoed.
        syncer.onPosition("it-1", 8_124_000L)
        syncer.flush("it-1", tracks)
        assertTrue(patches(server).isEmpty(), "guard must suppress the echo PATCH")
        assertTrue(stored(pref)!!.dirty, "guard keeps the row dirty for the next beat")

        // Past the window, accrued playback pushes normally.
        now += AbsProgressSyncer.PUSH_GUARD_MS + 1
        syncer.onPosition("it-1", 8_130_000L)
        syncer.flush("it-1", tracks)
        val body = patchBody(patches(server).single())
        assertEquals(8_130.0, body.currentTime, 0.001)
        assertFalse(stored(pref)!!.dirty)
    }

    @Test
    fun `clean local keeps an older-or-equal remote`() = runTest {
        // Local lastUpdate equals the remote's — remote is not newer.
        val pref = FakePref(rowJson(5_000_000L, FIXTURE_TS, dirty = false))
        val server = FakeServer()
        val syncer = newSyncer(server, pref)

        assertNull(syncer.pull("it-1", tracks))
        assertTrue(patches(server).isEmpty())
        assertEquals(5_000_000L, stored(pref)!!.itemGlobalMs)
    }

    @Test
    fun `position diffs under ten seconds are ignored`() = runTest {
        // Remote is newer but only 3 s ahead — same place, no churn.
        val pref = FakePref(rowJson(8_120_000L, FIXTURE_TS - 120_000L, dirty = false))
        val server = FakeServer()
        val syncer = newSyncer(server, pref)

        assertNull(syncer.pull("it-1", tracks))
        assertTrue(patches(server).isEmpty())
        assertEquals(8_120_000L, stored(pref)!!.itemGlobalMs)
    }

    @Test
    fun `timestamps equal-ish within the skew window keep local`() = runTest {
        // Remote 20 s "newer" — inside the ±30 s clock-skew guard; the big
        // position jump can't be trusted to the clock alone.
        val pref = FakePref(rowJson(1_000_000L, FIXTURE_TS - 20_000L, dirty = false))
        val server = FakeServer()
        val syncer = newSyncer(server, pref)

        assertNull(syncer.pull("it-1", tracks))
        assertTrue(patches(server).isEmpty())
        assertEquals(1_000_000L, stored(pref)!!.itemGlobalMs)
    }

    @Test
    fun `no local row adopts a fresh remote`() = runTest {
        val pref = FakePref()
        val server = FakeServer()
        val syncer = newSyncer(server, pref)

        assertEquals(8_123_000L, syncer.pull("it-1", tracks))
        val row = stored(pref)!!
        assertEquals(8_123_000L, row.itemGlobalMs)
        assertFalse(row.dirty)
    }

    @Test
    fun `remote isFinished adopts the row but returns no resume seed`() = runTest {
        val pref = FakePref()
        val server = FakeServer(
            progressJson = """{"currentTime":10800.0,"duration":10800.0,"progress":1.0,
                "isFinished":true,"lastUpdate":$FIXTURE_TS}""",
        )
        now = FIXTURE_TS
        val syncer = newSyncer(server, pref)

        assertNull(syncer.pull("it-1", tracks))
        assertEquals(10_800_000L, stored(pref)!!.itemGlobalMs)
    }

    @Test
    fun `missing remote progress is a no-op for a clean local`() = runTest {
        val pref = FakePref(rowJson(2_000_000L, FIXTURE_TS, dirty = false))
        val server = FakeServer(progressJson = null) // GET → 404
        val syncer = newSyncer(server, pref)

        assertNull(syncer.pull("it-1", tracks))
        assertTrue(patches(server).isEmpty())
    }

    @Test
    fun `dirty local still pushes when the pull 404s`() = runTest {
        val pref = FakePref(rowJson(4_000_000L, 1_000L, dirty = true))
        val server = FakeServer(progressJson = null)
        val syncer = newSyncer(server, pref)

        assertNull(syncer.pull("it-1", tracks))
        val body = patchBody(patches(server).single())
        assertEquals(4_000.0, body.currentTime, 0.001)
    }

    // ---- heartbeat / push -------------------------------------------------------

    @Test
    fun `onPosition marks dirty and flush patches once then clears`() = runTest {
        val pref = FakePref()
        val server = FakeServer()
        val syncer = newSyncer(server, pref)

        // First play on an empty map — the row is created dirty…
        syncer.onPosition("it-1", 600_000L)
        assertTrue(stored(pref)!!.dirty)

        // …and the heartbeat flush PATCHes it.
        syncer.flush("it-1", tracks)
        val patch = patches(server).single()
        assertEquals("/api/me/progress/it-1", patch.path)
        val body = patchBody(patch)
        assertEquals(600.0, body.currentTime, 0.001)
        assertEquals(10_800.0, body.duration, 0.001)
        assertFalse(body.isFinished)
        assertFalse(body.hideFromContinueListening)
        assertFalse(stored(pref)!!.dirty)

        // A clean row doesn't re-push.
        syncer.flush("it-1", tracks)
        assertEquals(1, patches(server).size)
    }

    @Test
    fun `a failed flush keeps the dirty flag for the next beat`() = runTest {
        val pref = FakePref(rowJson(900_000L, now, dirty = true))
        val server = FakeServer(patchError = HttpException(500))
        val syncer = newSyncer(server, pref)

        syncer.flush("it-1", tracks) // must not throw

        assertTrue(stored(pref)!!.dirty)
        assertEquals(1, patches(server).size)
    }

    @Test
    fun `the echoed server lastUpdate becomes the row's merge clock`() = runTest {
        val pref = FakePref()
        val server = FakeServer(
            patchResponse = """{"currentTime":600.0,"duration":10800.0,"progress":0.05,
                "isFinished":false,"lastUpdate":$FIXTURE_TS}""",
        )
        val syncer = newSyncer(server, pref)

        syncer.onPosition("it-1", 600_000L)
        syncer.flush("it-1", tracks)

        assertEquals(FIXTURE_TS, stored(pref)!!.lastUpdateMs)
    }

    @Test
    fun `a single-item 404 falls back to the batch update shape`() = runTest {
        val pref = FakePref()
        val server = FakeServer(patchError = HttpException(404))
        // Route the batch path around the single-item failure.
        server.client.transport = { req ->
            server.recorded += req
            when {
                req.path == "/api/me/progress/it-1" -> throw HttpException(404)
                req.path == "/api/me/progress/batch/update" -> "{}"
                else -> error("unexpected ${req.method} ${req.path}")
            }
        }
        val syncer = newSyncer(server, pref)

        syncer.onPosition("it-1", 300_000L)
        syncer.flush("it-1", tracks)

        val batch = patches(server).single { it.path == "/api/me/progress/batch/update" }
        val payload = json.decodeFromString<AbsProgressBatchUpdate>(batch.body!!)
        assertEquals("it-1", payload.progresses.single().libraryItemId)
        assertEquals(300.0, payload.progresses.single().currentTime, 0.001)
        assertFalse(stored(pref)!!.dirty)
    }

    @Test
    fun `finished rule pushes isFinished and progress one at the item's tail`() = runTest {
        val pref = FakePref()
        val server = FakeServer()
        val syncer = newSyncer(server, pref)

        // 1 s before the final track's end (10_800_000) — inside the 2 s rule.
        syncer.onPosition("it-1", 10_799_000L)
        syncer.flush("it-1", tracks)

        val body = patchBody(patches(server).single())
        assertTrue(body.isFinished)
        assertEquals(1.0, body.progress, 0.0001)
        assertEquals(10_800.0, body.currentTime, 0.001)
    }

    @Test
    fun `a position NOT near the end pushes isFinished false`() = runTest {
        val pref = FakePref()
        val server = FakeServer()
        val syncer = newSyncer(server, pref)

        syncer.onPosition("it-1", 10_700_000L) // 100 s shy of the end
        syncer.flush("it-1", tracks)

        val body = patchBody(patches(server).single())
        assertFalse(body.isFinished)
        assertEquals(10_700.0 / 10_800.0, body.progress, 0.001)
    }

    // ---- guards -----------------------------------------------------------------

    @Test
    fun `unconfigured ABS client turns every call into a silent no-op`() = runTest {
        val pref = FakePref(rowJson(1_000_000L, now, dirty = true))
        val syncer = newSyncer(null, pref)

        assertNull(syncer.pull("it-1", tracks))
        syncer.onPosition("it-1", 5_000L)
        syncer.flush("it-1", tracks)

        // The pref is untouched — the row stays exactly as seeded.
        assertEquals(1_000_000L, stored(pref)!!.itemGlobalMs)
    }

    @Test
    fun `flushAll launches the push on the caller's scope`() = runTest {
        val pref = FakePref()
        val server = FakeServer()
        val syncer = newSyncer(server, pref)

        syncer.onPosition("it-1", 450_000L)
        syncer.flushAll(this, "it-1", tracks)
        advanceUntilIdle()

        assertEquals(1, patches(server).size)
        assertFalse(stored(pref)!!.dirty)
    }

    @Test
    fun `the token rides transport headers only — never the pref or the patch body`() = runTest {
        val pref = FakePref()
        val server = FakeServer()
        val syncer = newSyncer(server, pref)

        syncer.onPosition("it-1", 123_000L)
        syncer.flush("it-1", tracks)

        server.recorded.forEach { req ->
            assertEquals("Bearer tok-test", req.headers["Authorization"], "missing bearer on ${req.path}")
        }
        val patch = patchBody(patches(server).single())
        // Serializing the patch back can't contain the token either.
        assertFalse(patch.toString().contains("tok-test"))
        assertFalse(pref.get().contains("tok-test"), "token must never land in the progress pref")
        assertFalse(pref.get().contains("abs.test"), "server URL must never land in the progress pref")
    }

    // ---- helpers ----------------------------------------------------------------

    @Test
    fun `trackIndexFor maps an item-global position back onto the track list`() {
        val (f3, offset) = AbsProgressSyncer.trackIndexFor(8_123_000L, tracks)!!
        assertEquals("f3", f3.ino)
        assertEquals(923_000L, offset)

        val (f1, zero) = AbsProgressSyncer.trackIndexFor(0L, tracks)!!
        assertEquals("f1", f1.ino)
        assertEquals(0L, zero)

        // Past the end → clamps onto the last track's tail.
        val (last, tail) = AbsProgressSyncer.trackIndexFor(99_000_000L, tracks)!!
        assertEquals("f3", last.ino)
        assertEquals(3_600_000L, tail)
    }

    private companion object {
        const val FIXTURE_TS = 1_759_000_000_000L
    }
}
