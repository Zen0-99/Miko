package eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.NovelChapter

/** JVM tests for [AudiobookPlaylist] — ordering, filtering, dropped URIs. */
class AudiobookPlaylistTest {

    private fun chapter(id: Long, name: String, number: Double, scanlator: String? = null) =
        NovelChapter.create().copy(id = id, name = name, chapterNumber = number, scanlator = scanlator)

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
}
