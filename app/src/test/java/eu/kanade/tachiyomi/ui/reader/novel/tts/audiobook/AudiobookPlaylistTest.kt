package eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.NovelChapter

/** JVM tests for [AudiobookPlaylist] — ordering, filtering, dropped URIs. */
class AudiobookPlaylistTest {

    private fun chapter(
        id: Long,
        name: String,
        number: Double,
        scanlator: String? = null,
        url: String = "",
    ) = NovelChapter.create().copy(
        id = id,
        name = name,
        chapterNumber = number,
        scanlator = scanlator,
        url = url,
    )

    private fun audioName(chapter: NovelChapter) =
        "${chapter.name}.m4a"

    @Test
    fun `build keeps only rendered chapters in chapterNumber order`() {
        val chapters = listOf(
            chapter(3, "ch3", 3.0),
            chapter(1, "ch1", 1.0),
            chapter(2, "ch2", 2.0),
        )
        val rendered = setOf("ch1.m4a", "ch3.m4a")

        val playlist = AudiobookPlaylist.build(chapters, rendered, ::audioName) { "uri:${it.id}" }

        playlist.map { it.chapterId } shouldBe listOf(1L, 3L)
        playlist.map { it.uri } shouldBe listOf("uri:1", "uri:3")
    }

    @Test
    fun `build drops chapters whose uri fails to resolve`() {
        val chapters = listOf(
            chapter(1, "ch1", 1.0),
            chapter(2, "ch2", 2.0),
        )
        val rendered = setOf("ch1.m4a", "ch2.m4a")

        val playlist = AudiobookPlaylist.build(chapters, rendered, ::audioName) { ch ->
            if (ch.id == 2L) null else "uri:${ch.id}"
        }

        playlist.map { it.chapterId } shouldBe listOf(1L)
    }

    @Test
    fun `empty render set yields empty playlist`() {
        val chapters = listOf(chapter(1, "ch1", 1.0))
        AudiobookPlaylist.build(chapters, emptySet(), ::audioName) { "uri:${it.id}" } shouldBe emptyList()
    }

    @Test
    fun `membership uses the provider filename not the raw chapter name`() {
        // Scanlator-prefixed filename — the rendered set must match the
        // provider's getAudioFileName output, not chapter.name.
        val chapters = listOf(chapter(1, "ch1", 1.0, scanlator = "tl"))
        val rendered = setOf("tl_ch1.m4a")

        val playlist = AudiobookPlaylist.build(
            chapters,
            rendered,
            audioFileName = { ch -> "${ch.scanlator}_${ch.name}.m4a" },
        ) { "uri:${it.id}" }

        playlist.map { it.chapterId } shouldBe listOf(1L)
    }

    // ---- buildRemote (22-02) ----------------------------------------------

    @Test
    fun `buildRemote keeps only audio-scheme chapters in chapterNumber order`() {
        val chapters = listOf(
            chapter(3, "trk3", 3.0, url = "abs://track/it-1/f3"),
            chapter(1, "canon row", 1.0, url = "canon:mk:mb:9:1"),
            chapter(4, "ch4", 4.0, url = "chapter-4.txt"),
            chapter(2, "trk2", 2.0, url = "abs://track/it-1/f2"),
        )

        val playlist = AudiobookPlaylist.buildRemote(chapters) { url ->
            "https://abs.test/api/${url.substringAfterLast('/')}"
        }

        playlist.map { it.chapterId } shouldBe listOf(2L, 3L)
        playlist.map { it.uri } shouldBe listOf(
            "https://abs.test/api/f2",
            "https://abs.test/api/f3",
        )
        playlist.map { it.chapterName } shouldBe listOf("trk2", "trk3")
    }

    @Test
    fun `buildRemote drops chapters whose stream uri fails to resolve`() {
        val chapters = listOf(
            chapter(1, "trk1", 1.0, url = "abs://track/it-1/f1"),
            chapter(2, "trk2", 2.0, url = "abs://track/it-1/f2"),
        )

        // e.g. ABS unconfigured — the resolver returns null for every url,
        // or selectively for malformed ones; both drop silently.
        AudiobookPlaylist.buildRemote(chapters) { null } shouldBe emptyList()
        AudiobookPlaylist.buildRemote(chapters) { url ->
            if (url.endsWith("f2")) "https://abs.test/f2" else null
        }.map { it.chapterId } shouldBe listOf(2L)
    }

    @Test
    fun `mergeRemote dedupes by chapterId with the remote uri winning`() {
        // A user-mixed entry: ch1 rendered locally, ch2 present remotely AND
        // locally, ch3 remote-only.
        val local = listOf(
            AudiobookTrack(1L, "ch1", "file:///a/ch1.m4a", 1.0),
            AudiobookTrack(2L, "ch2", "file:///a/ch2.m4a", 2.0),
        )
        val remote = listOf(
            AudiobookTrack(2L, "ch2", "https://abs.test/f2", 2.0),
            AudiobookTrack(3L, "ch3", "https://abs.test/f3", 3.0),
        )

        val merged = AudiobookPlaylist.mergeRemote(remote, local)

        merged.map { it.chapterId } shouldBe listOf(1L, 2L, 3L)
        // Remote wins the collision; the merged list stays in chapter order.
        merged.first { it.chapterId == 2L }.uri shouldBe "https://abs.test/f2"
    }
}
