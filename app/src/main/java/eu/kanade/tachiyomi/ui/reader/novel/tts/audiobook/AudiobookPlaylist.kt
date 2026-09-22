package eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook

import tachiyomi.domain.items.chapter.model.NovelChapter

/**
 * One rendered chapter in the audiobook playlist.
 *
 * [uri] is the string form of the audio file's `UniFile.uri` — kept as a
 * String (not `android.net.Uri`) so playlist math stays testable on the bare
 * JVM; `MediaItem.fromUri(String)` accepts it directly.
 */
data class AudiobookTrack(
    val chapterId: Long,
    val chapterName: String,
    val uri: String,
    val chapterNumber: Double,
)

/**
 * Pure playlist builder for the audiobook player (PB-3).
 *
 * Membership is the rendered-`.m4a` filename set from
 * [eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider.renderedAudioFileNames];
 * ordering is `chapterNumber` ascending — the same ordering
 * `NovelScreenModel.getUnreadChapters()` uses for "next chapter", so
 * prev/next in the player matches the reading order.
 */
object AudiobookPlaylist {

    /**
     * @param chapters all chapters of the novel (any order)
     * @param renderedFileNames `.m4a` filenames present under the novel's `audio/` dir
     * @param audioFileName chapter → expected `.m4a` filename (provider's `getAudioFileName`)
     * @param uriFor chapter → playable URI string, or null when the file can't be resolved
     * @return rendered chapters in reading order; chapters whose URI failed to resolve are dropped
     */
    fun build(
        chapters: List<NovelChapter>,
        renderedFileNames: Set<String>,
        audioFileName: (NovelChapter) -> String,
        uriFor: (NovelChapter) -> String?,
    ): List<AudiobookTrack> {
        return chapters
            .filter { audioFileName(it) in renderedFileNames }
            .sortedBy { it.chapterNumber }
            .mapNotNull { chapter ->
                uriFor(chapter)?.let { uri ->
                    AudiobookTrack(
                        chapterId = chapter.id,
                        chapterName = chapter.name,
                        uri = uri,
                        chapterNumber = chapter.chapterNumber,
                    )
                }
            }
    }
}
