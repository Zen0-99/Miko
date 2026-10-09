package eu.kanade.tachiyomi.source.novel

import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.novel.abs.AbsClient
import eu.kanade.tachiyomi.source.novel.abs.AbsNovelSource
import eu.kanade.tachiyomi.source.novel.abs.AbsUrls
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `streamUriFor` dispatch matrix (22-02): `abs://track/…` chapter urls
 * resolve to header-clean `{base}/api/items/{itemId}/file/{ino}` stream
 * URIs — and ONLY through a configured ABS source. Every other scheme
 * (canon/carrier/miko/plain http) and every unconfigured/malformed shape
 * returns null so callers fail closed instead of emitting a request to
 * an arbitrary host (T-22-02-04).
 */
class AudioChapterSchemesTest {

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

    private fun absSource(prefs: Map<String, String>): AbsNovelSource =
        AbsNovelSource(AbsClient(FakePrefs(prefs)))

    private val configured = absSource(
        mapOf(
            "abs_base_url" to "http://abs.test:13378",
            "__PRIVATE_abs_api_key" to "tok-test",
        ),
    )

    private val unconfigured = absSource(emptyMap())

    // ---- abs arm ------------------------------------------------------

    @Test
    fun `abs track url resolves to the stream endpoint`() {
        assertEquals(
            "http://abs.test:13378/api/items/it-1/file/f2",
            AudioChapterSchemes.streamUriFor("abs://track/it-1/f2", configured),
        )
    }

    @Test
    fun `resolved uri carries no credentials`() {
        val uri = AudioChapterSchemes.streamUriFor("abs://track/it-1/f2", configured)
        checkNotNull(uri)
        // Bearer rides the host-scoped header path instead — the URI stays
        // clean: no query, no `?token=` (T-22-02-01/SD-6).
        assertEquals(false, uri.contains("?"))
        assertEquals(false, uri.contains("token="))
    }

    @Test
    fun `abs track url needs a configured source`() {
        assertNull(AudioChapterSchemes.streamUriFor("abs://track/it-1/f2", unconfigured))
        assertNull(AudioChapterSchemes.streamUriFor("abs://track/it-1/f2", null))
        // Base url without a token is still unconfigured.
        assertNull(
            AudioChapterSchemes.streamUriFor(
                "abs://track/it-1/f2",
                absSource(mapOf("abs_base_url" to "http://abs.test:13378")),
            ),
        )
    }

    @Test
    fun `malformed abs urls resolve to null rather than a bad request`() {
        // Novel url, not a track.
        assertNull(AudioChapterSchemes.streamUriFor("abs://item/it-1", configured))
        // Missing ino.
        assertNull(AudioChapterSchemes.streamUriFor("abs://track/it-1", configured))
        assertNull(AudioChapterSchemes.streamUriFor("abs://track/it-1/", configured))
        // Empty segments.
        assertNull(AudioChapterSchemes.streamUriFor("abs://track//f1", configured))
    }

    // ---- non-matches ----------------------------------------------------

    @Test
    fun `non-audio schemes never resolve`() {
        for (url in listOf(
            "canon:mk:mb:123:4",
            "carrier:6400:https://annas-archive.org/md5/x",
            "miko:novel:mk:ol:OL42W",
            "https://example.org/chapter-1.txt",
            "http://abs.test:13378/api/items/it-1/file/f2",
            "chapter-1.txt",
            "",
        )) {
            assertNull(AudioChapterSchemes.streamUriFor(url, configured), url)
            assertNull(AudioChapterSchemes.streamUriFor(url, null), url)
        }
    }

    @Test
    fun `AbsClient helpers expose the same stream mapping and host key`() {
        val client = configured.client
        assertEquals(
            "http://abs.test:13378/api/items/it-9/file/f7",
            client.streamUriForChapter(AbsUrls.trackUrl("it-9", "f7")),
        )
        assertNull(client.streamUriForChapter("abs://item/it-9"))
        assertEquals(mapOf("Authorization" to "Bearer tok-test"), client.authHeaders())
        assertEquals("abs.test:13378", client.hostKey())
    }

    // ---- lv:// + ia:// arms (22-03) --------------------------------------------

    @Test
    fun `lv and ia url grammar joins the audio predicates`() {
        // Novel urls.
        assertTrue(AudioChapterSchemes.isAudioNovelUrl("abs://item/it-1"))
        assertTrue(AudioChapterSchemes.isAudioNovelUrl("lv://book/15147"))
        assertTrue(AudioChapterSchemes.isAudioNovelUrl("ia://item/some_item"))
        assertFalse(AudioChapterSchemes.isAudioNovelUrl("lv://track/ia_id/f.mp3"))
        assertFalse(AudioChapterSchemes.isAudioNovelUrl("ia://track/ia_id/f.mp3"))

        // Chapter urls.
        assertTrue(AudioChapterSchemes.isAudioChapterUrl("abs://track/it-1/f1"))
        assertTrue(AudioChapterSchemes.isAudioChapterUrl("lv://track/ia_id/f.mp3"))
        assertTrue(AudioChapterSchemes.isAudioChapterUrl("lv://file/https%3A%2F%2Fx.org%2Ff.mp3"))
        assertTrue(AudioChapterSchemes.isAudioChapterUrl("ia://track/ia_id/f.mp3"))
        assertFalse(AudioChapterSchemes.isAudioChapterUrl("lv://book/15147"))
        assertFalse(AudioChapterSchemes.isAudioChapterUrl("ia://item/some_item"))
    }

    @Test
    fun `lv track urls resolve to archive org downloads without abs`() {
        val expected = "https://archive.org/download/ia_id/chapter_01.mp3"
        assertEquals(
            expected,
            AudioChapterSchemes.streamUriFor("lv://track/ia_id/chapter_01.mp3", null),
        )
        // The public arm ignores absSource — configured or not.
        assertEquals(
            expected,
            AudioChapterSchemes.streamUriFor("lv://track/ia_id/chapter_01.mp3", configured),
        )
    }

    @Test
    fun `ia track urls resolve to archive org downloads`() {
        assertEquals(
            "https://archive.org/download/some_item/track%201.mp3",
            AudioChapterSchemes.streamUriFor("ia://track/some_item/track%201.mp3", null),
        )
        // File names carrying subdirectories keep their slashes.
        assertEquals(
            "https://archive.org/download/some_item/dir/f.mp3",
            AudioChapterSchemes.streamUriFor("ia://track/some_item/dir/f.mp3", null),
        )
    }

    @Test
    fun `lv file urls resolve to the decoded url`() {
        val raw = "https://ia801402.us.archive.org/13/items/x/f 02.mp3"
        val encoded = java.net.URLEncoder.encode(raw, "UTF-8")
        assertEquals(raw, AudioChapterSchemes.streamUriFor("lv://file/$encoded", null))
    }

    @Test
    fun `public arms fail closed on malformed or hostile urls`() {
        for (url in listOf(
            // Path traversal must never reach archive.org (T-22-03-02).
            "ia://track/../../x",
            "ia://track/../evil/f.mp3",
            "lv://track/../f.mp3",
            // Missing identifier or file segment.
            "ia://track/onlyid",
            "ia://track/onlyid/",
            "ia://track//f.mp3",
            "lv://track/",
            // Decodes to a non-http(s) scheme — no file:// minting.
            "lv://file/${java.net.URLEncoder.encode("file:///etc/passwd", "UTF-8")}",
            // The novel-shaped urls are not chapter urls.
            "lv://book/15147",
            "ia://item/some_item",
        )) {
            assertNull(AudioChapterSchemes.streamUriFor(url, configured), url)
            assertNull(AudioChapterSchemes.streamUriFor(url, null), url)
        }
    }

    @Test
    fun `hostKey normalizes the default port`() {
        val noPort = absSource(
            mapOf(
                "abs_base_url" to "https://abs.example",
                "__PRIVATE_abs_api_key" to "tok",
            ),
        )
        assertEquals("abs.example:443", noPort.client.hostKey())
        val http = absSource(
            mapOf(
                "abs_base_url" to "http://abs.lan",
                "__PRIVATE_abs_api_key" to "tok",
            ),
        )
        assertEquals("abs.lan:80", http.client.hostKey())
        assertEquals("", unconfigured.client.hostKey())
    }
}
