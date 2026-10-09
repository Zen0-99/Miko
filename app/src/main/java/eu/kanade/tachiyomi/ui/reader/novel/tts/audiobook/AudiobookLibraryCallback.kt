package eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.GetNovelFavorites
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.interactor.GetNovelChapter
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Android Auto / media-browser library for the audiobook player.
 *
 * Browse tree (Automotive's media tab):
 * - root → library novels that have rendered audio, as albums (cover + title)
 * - `novel/<id>` → every chapter of the novel in reading order, coverless;
 *   rendered chapters are playable, unrendered stay listed but non-playable
 *   (greyed rows — Auto has no per-row custom icons, so the playable flag +
 *   a "Not rendered" subtitle carry the state)
 *
 * Playing a `chapter/` id expands to the novel's full rendered queue so the
 * head unit's next/previous walks chapters, starting at the tapped chapter's
 * persisted resume position.
 */
@UnstableApi
class AudiobookLibraryCallback(
    private val context: Context,
    private val scope: CoroutineScope,
) : MediaLibraryService.MediaLibrarySession.Callback {

    private val json = Json { ignoreUnknownKeys = true }

    // -- suspend → ListenableFuture bridge -------------------------------------

    private fun <T> async(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            try {
                future.set(block())
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }

    // -- browsing ---------------------------------------------------------------

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return async { LibraryResult.ofItem(rootItem(), params) }
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return async {
            val items = when {
                parentId == ROOT_ID -> novelItems()
                parentId.startsWith(NOVEL_PREFIX) -> {
                    val novelId = parentId.removePrefix(NOVEL_PREFIX).toLongOrNull()
                    val novel = novelId?.let { Injekt.get<GetNovel>().await(it) }
                    val source = novel?.let { Injekt.get<NovelSourceManager>().get(it.source) }
                    if (novel != null && source != null) chapterItems(novel, source) else emptyList()
                }
                else -> emptyList()
            }
            val window = if (pageSize > 0) {
                val from = page * pageSize
                if (from >= items.size) emptyList() else items.subList(from, minOf(from + pageSize, items.size))
            } else {
                items
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(window), params)
        }
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return async {
            when {
                mediaId == ROOT_ID -> LibraryResult.ofItem(rootItem(), null)
                mediaId.startsWith(NOVEL_PREFIX) -> {
                    val novelId = mediaId.removePrefix(NOVEL_PREFIX).toLongOrNull()
                    val novel = novelId?.let { Injekt.get<GetNovel>().await(it) }
                    if (novel == null) {
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    } else {
                        LibraryResult.ofItem(novelItem(novel), null)
                    }
                }
                mediaId.startsWith(CHAPTER_PREFIX) -> {
                    val ids = parseChapterId(mediaId)
                    val item = ids?.let { (novelId, chapterId) -> chapterItem(novelId, chapterId) }
                    if (item == null) {
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    } else {
                        LibraryResult.ofItem(item, null)
                    }
                }
                else -> LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            }
        }
    }

    // -- play resolution ----------------------------------------------------------

    /** `addMediaItem(chapter/…)` — resolve the leaf to its playable file. */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        return async {
            mediaItems.map { item ->
                if (item.mediaId.startsWith(CHAPTER_PREFIX)) {
                    parseChapterId(item.mediaId)
                        ?.let { (novelId, chapterId) -> chapterItem(novelId, chapterId) }
                        ?: item
                } else {
                    item
                }
            }
        }
    }

    /**
     * `playFromMediaId` — a `chapter/` tap expands to the novel's whole
     * rendered queue starting at that chapter (+ its saved resume position);
     * a `novel/` tap plays the first rendered chapter. Everything else falls
     * through unchanged.
     */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return async {
            val target = mediaItems.firstOrNull {
                it.mediaId.startsWith(CHAPTER_PREFIX) || it.mediaId.startsWith(NOVEL_PREFIX)
            }
            if (target != null) {
                val novelId = if (target.mediaId.startsWith(CHAPTER_PREFIX)) {
                    parseChapterId(target.mediaId)?.first
                } else {
                    target.mediaId.removePrefix(NOVEL_PREFIX).toLongOrNull()
                }
                val chapterId = if (target.mediaId.startsWith(CHAPTER_PREFIX)) {
                    parseChapterId(target.mediaId)?.second
                } else {
                    null
                }
                val novel = novelId?.let { Injekt.get<GetNovel>().await(it) }
                val source = novel?.let { Injekt.get<NovelSourceManager>().get(it.source) }
                if (novel != null && source != null) {
                    val queue = chapterItems(novel, source)
                        .filter { it.mediaMetadata.isPlayable == true }
                    if (queue.isNotEmpty()) {
                        val start = chapterId?.let { id ->
                            queue.indexOfFirst { it.mediaId == "$CHAPTER_PREFIX$novelId/$id" }
                        }?.takeIf { it >= 0 } ?: 0
                        val resumePos = chapterId?.let { resumeMap()[it] } ?: startPositionMs
                        return@async MediaSession.MediaItemsWithStartPosition(
                            queue,
                            start,
                            resumePos ?: 0L,
                        )
                    }
                }
            }
            MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
        }
    }

    // -- item builders ---------------------------------------------------------

    private fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(ROOT_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(context.stringResource(AYMR.strings.audiobook_auto_root))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
                .setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
                .build(),
        )
        .build()

    private fun novelItem(novel: Novel): MediaItem = MediaItem.Builder()
        .setMediaId("$NOVEL_PREFIX${novel.id}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(novel.title)
                .setArtist(novel.author?.takeIf { it.isNotBlank() })
                .setArtworkUri(novel.thumbnailUrl?.let(Uri::parse))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                .setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
                .build(),
        )
        .build()

    /**
     * The ABS source behind an audio-scheme chapter url — the novel's own
     * source when it IS the built-in, else the built-in directly (a
     * canonical audiobook-flagged entry rides a stub source but still
     * streams from the configured server).
     */
    private fun absSourceFor(source: NovelSource): eu.kanade.tachiyomi.source.novel.abs.AbsNovelSource? =
        source as? eu.kanade.tachiyomi.source.novel.abs.AbsNovelSource
            ?: Injekt.get<NovelSourceManager>()
                .get(eu.kanade.tachiyomi.source.novel.abs.AbsNovelSource.ABS_SOURCE_ID)
                as? eu.kanade.tachiyomi.source.novel.abs.AbsNovelSource

    /** Library novels that actually have rendered audio — Auto is audio-only.
     *  Audiobook-flagged entries (abs:// items) qualify by streaming alone. */
    private suspend fun novelItems(): List<MediaItem> {
        val provider = Injekt.get<NovelDownloadProvider>()
        val sourceManager = Injekt.get<NovelSourceManager>()
        return Injekt.get<GetNovelFavorites>().await().mapNotNull { novel ->
            val source = sourceManager.get(novel.source) ?: return@mapNotNull null
            if (!novel.isAudiobook &&
                provider.renderedAudioFileNames(novel.title, source).isEmpty()
            ) {
                return@mapNotNull null
            }
            novelItem(novel)
        }
    }

    private suspend fun chapterItems(novel: Novel, source: NovelSource): List<MediaItem> {
        val provider = Injekt.get<NovelDownloadProvider>()
        val rendered = provider.renderedAudioFileNames(novel.title, source)
        return Injekt.get<GetNovelChaptersByNovelId>().await(novel.id)
            .sortedBy { it.chapterNumber }
            .map { chapter ->
                // 22-02: audio-scheme chapters stream — the playable URI is
                // the header-clean server file URL (Bearer rides the
                // service's host-scoped resolver, never the URI itself).
                // A same-chapter local .m4a remains playable as fallback.
                val isRendered =
                    provider.getAudioFileName(chapter.name, chapter.scanlator) in rendered
                val streamUri = if (novel.isAudiobook) {
                    eu.kanade.tachiyomi.source.novel.AudioChapterSchemes
                        .streamUriFor(chapter.url, absSourceFor(source))
                } else {
                    null
                }
                chapterItemBuilder(novel, chapter.id, chapter.name, isRendered, source, streamUri)
            }
    }

    private suspend fun chapterItem(novelId: Long, chapterId: Long): MediaItem? {
        val novel = Injekt.get<GetNovel>().await(novelId) ?: return null
        val source = Injekt.get<NovelSourceManager>().get(novel.source) ?: return null
        val chapter = Injekt.get<GetNovelChapter>().await(chapterId) ?: return null
        val provider = Injekt.get<NovelDownloadProvider>()
        val isRendered = provider.findAudioFile(chapter.name, chapter.scanlator, novel.title, source) != null
        val streamUri = if (novel.isAudiobook) {
            eu.kanade.tachiyomi.source.novel.AudioChapterSchemes
                .streamUriFor(chapter.url, absSourceFor(source))
        } else {
            null
        }
        return chapterItemBuilder(novel, chapterId, chapter.name, isRendered, source, streamUri)
    }

    private suspend fun chapterItemBuilder(
        novel: Novel,
        chapterId: Long,
        chapterName: String,
        isRendered: Boolean,
        source: NovelSource,
        remoteUri: String? = null,
    ): MediaItem {
        val provider = Injekt.get<NovelDownloadProvider>()
        val builder = MediaItem.Builder()
            .setMediaId("$CHAPTER_PREFIX${novel.id}/$chapterId")
        // Remote stream wins (the server copy is canonical); a rendered
        // local file stays as fallback when the stream can't resolve.
        if (remoteUri != null) {
            builder.setUri(remoteUri)
        } else if (isRendered) {
            provider.findAudioFile(chapterName, null, novel.title, source)
                ?.let { builder.setUri(it.uri.toString()) }
        }
        val playable = remoteUri != null || isRendered
        return builder.setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(chapterName)
                .setArtist(novel.title)
                .setAlbumTitle(novel.title)
                .setSubtitle(
                    if (playable) null else context.stringResource(AYMR.strings.audiobook_auto_not_rendered),
                )
                .setIsBrowsable(false)
                .setIsPlayable(playable)
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                .build(),
        ).build()
    }

    private fun resumeMap(): Map<Long, Long> = runCatching {
        json.decodeFromString<Map<Long, Long>>(
            Injekt.get<NovelTtsPreferences>().audiobookPositions().get(),
        )
    }.getOrDefault(emptyMap())

    private fun parseChapterId(mediaId: String): Pair<Long, Long>? =
        mediaId.removePrefix(CHAPTER_PREFIX).split('/')
            .takeIf { it.size == 2 }
            ?.let { runCatching { it[0].toLong() to it[1].toLong() }.getOrNull() }

    private companion object {
        const val ROOT_ID = "miko_audiobooks"
        const val NOVEL_PREFIX = "novel/"
        const val CHAPTER_PREFIX = "chapter/"
    }
}
