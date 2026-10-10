package eu.kanade.tachiyomi.ui.reader.novel.tts.audiobook

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import eu.kanade.domain.items.chapter.interactor.SetNovelReadStatus
import eu.kanade.domain.items.chapter.interactor.SetNovelReadingPosition
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider
import eu.kanade.tachiyomi.data.track.abs.AbsProgressSyncer
import eu.kanade.tachiyomi.source.novel.abs.AbsClient
import eu.kanade.tachiyomi.source.novel.abs.AbsItem
import eu.kanade.tachiyomi.source.novel.abs.AbsNovelSource
import eu.kanade.tachiyomi.source.novel.abs.AbsUrls
import eu.kanade.tachiyomi.ui.reader.novel.NovelScrollPositionCodec
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.render.Mp4AtomReader
import eu.kanade.tachiyomi.ui.reader.novel.tts.render.decodeManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileInputStream
import kotlin.coroutines.resume

/** UI-facing state of the audiobook player. */
data class AudiobookPlayerState(
    val track: AudiobookTrack? = null,
    val novelId: Long? = null,
    val novelTitle: String = "",
    val cover: NovelCover? = null,
    val chapterIndex: Int = -1,
    val chapterCount: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val sleepTimer: SleepTimer = SleepTimer.Off,
    /** Whether the full player overlay is expanded (D-01; driven by 06-02 UI). */
    val overlayVisible: Boolean = false,
    /**
     * Play is mid-load: the overlay is already up but the playlist isn't
     * bound yet — UI shows the loading branch instead of transport.
     */
    val loading: Boolean = false,
    /** Chapter name to display while [loading] (no [AudiobookTrack] yet). */
    val pendingChapterName: String? = null,
    /**
     * Paragraph texts + start offsets (ms) from the loaded track's embedded
     * `MikoTTS` manifest — the sync source for the overlay's lyrics-style
     * text modes. Empty when the file has no manifest or hasn't loaded yet.
     */
    val paragraphs: List<String> = emptyList(),
    val paragraphOffsetsMs: List<Long> = emptyList(),
)

/**
 * Singleton front-end for the audiobook player (PB-2).
 *
 * Owns NO player — it is a `MediaController` client of
 * [AudiobookPlaybackService]'s Media3 session, so playback survives overlay
 * dismissal, screen-off, and process backgrounding (foreground service +
 * media notification are handled inside `MediaSessionService`). All
 * transport (play/pause/seek/skip/speed) goes through the session, which is
 * also what lock-screen and Bluetooth controls drive.
 *
 * Responsibilities beyond transport:
 * - Builds the rendered-chapter playlist via [AudiobookPlaylist] (PB-3).
 * - Persists per-chapter resume positions in [NovelTtsPreferences.audiobookPositions]
 *   (PB-4): write-through on pause/stop/track-change plus a ~5s tick while
 *   playing; a finished chapter's entry is dropped so replay starts at 0.
 * - Applies the separate [NovelTtsPreferences.audiobookSpeed] pref (D-11).
 * - Runs the [SleepTimer] (D-14): minute presets tick on the position poller;
 *   EndOfChapter intercepts the auto-advance transition.
 */
class AudiobookPlayerController(
    private val context: Context,
    private val downloadProvider: NovelDownloadProvider = Injekt.get(),
    private val downloadManager: eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager = Injekt.get(),
    private val ttsPreferences: NovelTtsPreferences = Injekt.get(),
    private val getNovelChaptersByNovelId: GetNovelChaptersByNovelId = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val workCarriers: eu.kanade.tachiyomi.data.mikodb.WorkCarriers = Injekt.get(),
    private val setNovelReadStatus: SetNovelReadStatus = Injekt.get(),
    private val setNovelReadingPosition: SetNovelReadingPosition = Injekt.get(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var playlist: List<AudiobookTrack> = emptyList()

    /**
     * chapterId → NovelChapter for the loaded playlist — feeds the
     * listened-progress writes (read flag on completion, `lastCharRead`
     * on mid-chapter pause/tick) so listening counts as reading progress
     * and survives process death.
     */
    private var playlistChapters: Map<Long, NovelChapter> = emptyMap()

    /** Session peak of the listened char offset per chapter — never write
     * a `lastCharRead` below this or the chapter's recorded position. */
    private val listenedCharPeak = mutableMapOf<Long, Long>()
    private var tickerJob: Job? = null
    private var lastResumeWriteMs = 0L

    // ------------------------------------------------------------------
    // ABS progress sync (22-04) — the §7.2 merge over the item-level row.
    // All calls run on [scope]/session coroutines; nothing here can block
    // or fail playback (unconfigured → every call no-ops).
    // ------------------------------------------------------------------

    private val absSyncer = AbsProgressSyncer(
        clientProvider = { absClient() },
        pref = ttsPreferences.absProgress(),
    )

    /** Item id of the ABS entry under play, null for local/lv/ia sessions. */
    private var absItemId: String? = null

    /** ino → track geometry from the session's one `client.item` fetch (SD-2). */
    @Volatile
    private var absTrackCtx: List<AbsProgressSyncer.TrackCtx> = emptyList()

    /** chapterId → ino for the loaded playlist's `abs://track/` rows. */
    private var absChapterToIno: Map<Long, String> = emptyMap()

    /** One-item session cache — the item is fetched once per play/entry-open. */
    @Volatile
    private var absItemCache: AbsItem? = null

    private var lastAbsFlushMs = 0L

    private fun absClient(): AbsClient? =
        (sourceManager.get(AbsNovelSource.ABS_SOURCE_ID) as? AbsNovelSource)
            ?.client?.takeIf { it.isConfigured() }

    private suspend fun cachedAbsItem(client: AbsClient, itemId: String): AbsItem? =
        absItemCache?.takeIf { it.id == itemId }
            ?: runCatching { client.item(itemId) }.getOrNull()?.also { absItemCache = it }

    private fun AbsItem.toTrackCtx(): List<AbsProgressSyncer.TrackCtx> {
        val tracks = media?.tracks.orEmpty()
        if (tracks.isNotEmpty()) {
            return tracks.map {
                AbsProgressSyncer.TrackCtx(
                    ino = it.ino,
                    startOffsetMs = (it.startOffset * 1000).toLong(),
                    durationMs = (it.duration * 1000).toLong(),
                )
            }.sortedBy { it.startOffsetMs }
        }
        // ABS ≥2.37 omits media.tracks on freshly scanned items — derive the
        // same geometry from audioFiles (startOffset = cumulative duration).
        var offset = 0L
        return media?.audioFiles.orEmpty()
            .filter { !it.exclude }
            .sortedBy { it.trackNumFromFilename ?: it.index }
            .map { file ->
                val durationMs = (file.duration * 1000).toLong()
                AbsProgressSyncer.TrackCtx(
                    ino = file.ino,
                    startOffsetMs = offset,
                    durationMs = durationMs,
                ).also { offset += durationMs }
            }
    }

    /**
     * Arm the sync context for a play session: `chapterId → ino` parses out
     * of the chapter urls (no fetch); the track-offset ctx rides the
     * concurrent pull in [playOnMain]. Called on a non-ABS or unconfigured
     * play too — resets so a stale item can't leak into another session.
     */
    private fun armAbsSync(
        novel: Novel,
        chapters: List<NovelChapter>,
        absSource: AbsNovelSource?,
    ) {
        absItemId = null
        absTrackCtx = emptyList()
        absChapterToIno = emptyMap()
        if (!AbsUrls.isAbsNovelUrl(novel.url) || absSource?.client?.isConfigured() != true) return
        val itemId = AbsUrls.itemIdFrom(novel.url) ?: return
        absItemId = itemId
        absChapterToIno = chapters.mapNotNull { ch ->
            if (AbsUrls.itemIdFrom(ch.url) != itemId) return@mapNotNull null
            AbsUrls.inoFrom(ch.url)?.let { ch.id to it }
        }.toMap()
    }

    /**
     * Heartbeat record — item-global position = `track.startOffsetMs +
     * positionInTrackMs` (SD-2). Only fires while the loaded track is an
     * ABS row of the armed item.
     */
    private fun recordAbsPosition() {
        val itemId = absItemId ?: return
        val ino = absChapterToIno[state.value.track?.chapterId] ?: return
        val ctx = absTrackCtx.firstOrNull { it.ino == ino } ?: return
        val position = mediaController?.currentPosition?.takeIf { it >= 0 }
            ?: state.value.durationMs.takeIf { it > 0 }
            ?: return
        absSyncer.onPosition(itemId, ctx.startOffsetMs + position)
    }

    /**
     * Fires a serialized dirty-flush on the session scope — heartbeat,
     * pause, track boundary, release, and service-destroy all funnel here.
     */
    private fun flushAbs() {
        val itemId = absItemId ?: return
        scope.launch { absSyncer.flush(itemId, absTrackCtx) }
    }

    /**
     * Called by [AudiobookPlaybackService.onDestroy] — the service dies
     * while the singleton controller's scope lives on, so the final dirty
     * push still lands. Idempotent: the syncer's mutex serializes it with
     * any in-flight heartbeat flush.
     */
    fun flushProgressOnDestroy() {
        flushAbs()
    }

    /**
     * Pull-on-open for ABS-sourced audiobook entries (SD-5b): fetch the
     * item's server progress, and when the merge adopts a newer remote
     * position seed the local resume chain (resumeMap + lastPlayed) so the
     * first tap continues from it. Bounded by [timeoutMs] — the entry
     * screen never stalls on sync; unconfigured/unreachable → false.
     */
    suspend fun pullAbsProgress(
        novel: Novel,
        chapters: List<NovelChapter>,
        timeoutMs: Long = ABS_PULL_TIMEOUT_MS,
    ): Boolean {
        if (!novel.isAudiobook) return false
        val itemId = AbsUrls.itemIdFrom(novel.url) ?: return false
        val client = absClient() ?: return false
        return runCatching {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(timeoutMs) {
                    val item = cachedAbsItem(client, itemId) ?: return@withTimeoutOrNull false
                    val ctx = item.toTrackCtx()
                    if (ctx.isEmpty()) return@withTimeoutOrNull false
                    absTrackCtx = ctx
                    val seed = absSyncer.pull(itemId, ctx) ?: return@withTimeoutOrNull false
                    val (track, offsetMs) = AbsProgressSyncer.trackIndexFor(seed, ctx)
                        ?: return@withTimeoutOrNull false
                    val chapter = chapters.firstOrNull {
                        AbsUrls.itemIdFrom(it.url) == itemId && AbsUrls.inoFrom(it.url) == track.ino
                    } ?: return@withTimeoutOrNull false
                    // resumeMap/lastPlayedMap are plain maps shared with the
                    // Main-confined play path — seed them there.
                    withContext(Dispatchers.Main) {
                        seedAbsResume(novel.id, chapter.id, offsetMs)
                    }
                    true
                } ?: false
            }
        }.getOrElse {
            logcat(LogPriority.WARN) { "ABS pull-on-open failed for ${novel.url}: ${it.message}" }
            false
        }
    }

    /**
     * Seed the resume chain from a pulled ABS item-global position — the
     * same entries [playOnMain] reads (resumeMap + lastPlayedMap).
     */
    private fun seedAbsResume(novelId: Long, chapterId: Long, positionMs: Long) {
        resumeMap[chapterId] = positionMs
        lastPlayedMap[novelId] = chapterId
        persistResumeMap()
        ttsPreferences.audiobookLastPlayed().set(json.encodeToString(lastPlayedMap))
    }

    private val resumeMap: MutableMap<Long, Long> by lazy {
        runCatching {
            json.decodeFromString<Map<Long, Long>>(ttsPreferences.audiobookPositions().get())
        }.getOrDefault(emptyMap()).toMutableMap()
    }

    /**
     * novelId → chapterId of the last chapter that played — the
     * "continue listening" anchor, independent of reading progress.
     */
    private val lastPlayedMap: MutableMap<Long, Long> by lazy {
        runCatching {
            json.decodeFromString<Map<Long, Long>>(ttsPreferences.audiobookLastPlayed().get())
        }.getOrDefault(emptyMap()).toMutableMap()
    }

    private val _state = MutableStateFlow(
        AudiobookPlayerState(speed = ttsPreferences.audiobookSpeed().get()),
    )
    val state: StateFlow<AudiobookPlayerState> = _state.asStateFlow()

    /**
     * Emitted whenever [play] actually starts playback — the read-along side
     * (reader model + TTS service) collects this to stop itself, enforcing
     * D-05's "never two audio sources" without a service-to-service reference.
     */
    val playbackStarted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Whether the mini-player card is currently on screen — the library
     * update overlay reads this to lift itself above the card. Written by
     * the mini-player composable, so swipe-dismissed counts as not visible.
     */
    private val _miniPlayerVisible = MutableStateFlow(false)
    val miniPlayerVisible: StateFlow<Boolean> = _miniPlayerVisible.asStateFlow()

    /**
     * User swiped the mini-player away. Kept here — not in the composable —
     * because `remember`ed state dies when navigation tears the mini-player
     * out of composition, which made the card resurrect on the next screen.
     * Cleared by any playback start ([playerListener.onIsPlayingChanged]) so
     * a fresh listen session re-shows the card.
     */
    private val _miniPlayerDismissed = MutableStateFlow(false)
    val miniPlayerDismissed: StateFlow<Boolean> = _miniPlayerDismissed.asStateFlow()

    fun setMiniPlayerVisible(visible: Boolean) {
        _miniPlayerVisible.value = visible
    }

    fun dismissMiniPlayer() {
        _miniPlayerDismissed.value = true
    }

    // ------------------------------------------------------------------
    // Playback entry point
    // ------------------------------------------------------------------

    /**
     * Load [novel]'s rendered-chapter playlist and start playing [chapter]
     * through the session service. Resumes from the persisted position (PB-4).
     * Returns false when the chapter has no rendered audio or the session
     * can't be reached — callers fall back to the reader's live TTS path.
     */
    // MediaController only accepts calls on the app's main thread — callers
    // may be on Dispatchers.IO, so play() marshals itself there up front.
    suspend fun play(novel: Novel, chapter: NovelChapter): Boolean =
        withContext(Dispatchers.Main) { playOnMain(novel, chapter) }

    private suspend fun playOnMain(novel: Novel, chapter: NovelChapter): Boolean {
        // Fast path: same track already loaded — just resume and surface the overlay.
        if (state.value.track?.chapterId == chapter.id && mediaController != null) {
            showOverlay()
            mediaController?.play()
            playbackStarted.tryEmit(Unit)
            return true
        }

        // Instant nav: the overlay goes up NOW — before the source await,
        // chapter DB read, rendered-dir scan and controller bind — so the
        // tap's feedback is the player UI itself; the pipeline runs behind
        // it (PB spec: navigation must not wait on load).
        _state.update {
            it.copy(
                novelId = novel.id,
                novelTitle = novel.title,
                cover = novel.asNovelCover(),
                overlayVisible = true,
                loading = true,
                pendingChapterName = chapter.name,
            )
        }
        fun fail(): Boolean {
            _state.update {
                it.copy(
                    overlayVisible = false,
                    loading = false,
                    pendingChapterName = null,
                )
            }
            return false
        }

        val source = awaitSource(novel.source) ?: return fail()
        val chapters = withContext(Dispatchers.IO) {
            getNovelChaptersByNovelId.await(novel.id)
        }
        playlistChapters = chapters.associateBy { it.id }
        val tracks = withContext(Dispatchers.IO) {
            // 22-04: reset per-item sync state up front — a non-ABS or
            // unconfigured play must not inherit the last session's row.
            absItemId = null
            absTrackCtx = emptyList()
            absChapterToIno = emptyMap()
            // Same union the chapter list's render-state seed uses:
            // the entry's source (often a canon stub with no dir) +
            // every bound carrier + each row's own url tag. A chapter's
            // .m4a counts when its name shows in ANY of those dirs.
            val audioDirs = run {
                val boundCarrierIds = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
                    .workIdFromNovelUrl(novel.url)
                    ?.let { workId ->
                        // peek() — the detail screen's render-state seed
                        // already warmed this cache; a cold read would
                        // hit the worker API + extension scan mid-tap.
                        workCarriers.peek(workId)?.carriers
                    }
                    ?.flatMap { it.sourceIds }
                    .orEmpty()
                (
                    listOf(source) +
                        chapters.mapNotNull { downloadManager.carrierDownloadSource(it.url) } +
                        boundCarrierIds.mapNotNull { sourceManager.get(it) }
                    )
                    .distinctBy { it.id }
            }
            val rendered = audioDirs
                .flatMapTo(mutableSetOf()) {
                    downloadProvider.renderedAudioFileNames(novel.title, it)
                }
            val local = AudiobookPlaylist.build(
                chapters = chapters,
                renderedFileNames = rendered,
                audioFileName = { downloadProvider.getAudioFileName(it.name, it.scanlator) },
                uriFor = { chapter ->
                    audioDirs.firstNotNullOfOrNull { dir ->
                        downloadProvider.findAudioFile(
                            chapter.name,
                            chapter.scanlator,
                            novel.title,
                            dir,
                        )?.uri?.toString()
                    }
                },
            )
            if (novel.isAudiobook) {
                // SD-3: audio-scheme chapters resolve to stream URIs
                // (header-clean; the service injects Bearer host-scoped)
                // and merge over the local union — a user-mixed entry
                // with rendered .m4a files keeps them, remote wins on
                // a chapterId collision.
                val absSource = source as? AbsNovelSource
                    ?: sourceManager.get(AbsNovelSource.ABS_SOURCE_ID) as? AbsNovelSource
                armAbsSync(novel, chapters, absSource)
                AudiobookPlaylist.mergeRemote(
                    AudiobookPlaylist.buildRemote(chapters) { url ->
                        eu.kanade.tachiyomi.source.novel.AudioChapterSchemes
                            .streamUriFor(url, absSource)
                    },
                    local,
                )
            } else {
                local
            }
        }
        val tappedIndex = tracks.indexOfFirst { it.chapterId == chapter.id }
        if (tappedIndex < 0) {
            logcat(LogPriority.WARN) { "Audiobook: no rendered audio for chapter '${chapter.name}'" }
            return fail()
        }

        // 22-04 pull (SD-5a): concurrent with the controller bind — if the
        // pull lands before setMediaItems its adopted seed becomes the
        // resume; a slower pull still lands the merge in the pref for the
        // next session. Never awaited — playback must not block on sync.
        val absPull: Deferred<Long?>? = absItemId?.let { itemId ->
            scope.async {
                runCatching {
                    val client = absClient() ?: return@runCatching null
                    val item = cachedAbsItem(client, itemId) ?: return@runCatching null
                    val ctx = item.toTrackCtx()
                    if (ctx.isEmpty()) return@runCatching null
                    absTrackCtx = ctx
                    absSyncer.pull(itemId, ctx)
                }.getOrElse {
                    logcat(LogPriority.WARN) { "ABS progress pull failed for $itemId: ${it.message}" }
                    null
                }
            }
        }

        val controller = awaitController() ?: return fail()

        playlist = tracks
        pruneResumeMap(tracks)
        var startIndex = tappedIndex
        var resumePosition = resumeMap[chapter.id] ?: 0L

        // Apply the remote seed only when the pull already landed — the
        // adopted item-global position maps back to (track, offset) via
        // the armed ctx (§7.2 "mark track via startOffset math").
        absPull?.takeIf { it.isCompleted }
            ?.let { runCatching { it.getCompleted() }.getOrNull() }
            ?.let { seedMs ->
                AbsProgressSyncer.trackIndexFor(seedMs, absTrackCtx)?.let { (ctx, offsetMs) ->
                    tracks.indexOfFirst { absChapterToIno[it.chapterId] == ctx.ino }
                        .takeIf { it >= 0 }
                        ?.let {
                            startIndex = it
                            resumePosition = offsetMs
                        }
                }
            }

        controller.setMediaItems(
            tracks.map { it.toMediaItem(novel) },
            startIndex,
            resumePosition,
        )
        controller.setPlaybackParameters(PlaybackParameters(state.value.speed, 1f))
        controller.prepare()
        controller.play()
        playbackStarted.tryEmit(Unit)

        _state.update {
            it.copy(
                track = tracks[startIndex],
                novelId = novel.id,
                novelTitle = novel.title,
                cover = novel.asNovelCover(),
                chapterIndex = startIndex,
                chapterCount = tracks.size,
                positionMs = resumePosition,
                loading = false,
                pendingChapterName = null,
                // overlayVisible intentionally untouched: it went true
                // optimistically at tap-time, and a user who swiped the
                // overlay away mid-load must not have it pop back up.
            )
        }
        loadManifestFor(tracks[startIndex])
        recordLastPlayed()
        return true
    }

    /**
     * "Continue listening" entry point (library 🎧 + detail combo): resumes
     * the *audio* position, not the reading one. Priority: the in-session
     * track for this novel → the last-played chapter with a saved position
     * → the next rendered chapter after it → the first rendered chapter.
     * Returns false when nothing is playable; callers fall back to live TTS.
     */
    /**
     * Whether this novel has a listening history — the session track or a
     * persisted last-played chapter — so a "continue" affordance can pick
     * audio vs. reader by the medium the user actually consumed last.
     */
    fun hasListenedBefore(novelId: Long): Boolean =
        state.value.novelId == novelId || lastPlayedMap.containsKey(novelId)

    suspend fun resumeListening(novel: Novel): Boolean {
        val chapters = getNovelChaptersByNovelId.await(novel.id)
        if (chapters.isEmpty()) return false
        val source = awaitSource(novel.source) ?: return false
        val rendered = withContext(Dispatchers.IO) {
            // Same carrier union as play() — bound carriers cover rows
            // whose url carries no `carrier:{id}` tag.
            val boundCarrierIds = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
                .workIdFromNovelUrl(novel.url)
                ?.let { workId -> workCarriers.peek(workId)?.carriers }
                ?.flatMap { it.sourceIds }
                .orEmpty()
            (
                listOf(source) +
                    chapters.mapNotNull { downloadManager.carrierDownloadSource(it.url) } +
                    boundCarrierIds.mapNotNull { sourceManager.get(it) }
                )
                .distinctBy { it.id }
                .flatMapTo(mutableSetOf()) {
                    downloadProvider.renderedAudioFileNames(novel.title, it)
                }
        }
        fun fileName(chapter: NovelChapter) =
            downloadProvider.getAudioFileName(chapter.name, chapter.scanlator)

        // Audio entries stream instead of rendering — a remote-scheme
        // chapter is as playable as an on-disk .m4a (22-02).
        fun isPlayable(chapter: NovelChapter) =
            fileName(chapter) in rendered ||
                (
                    novel.isAudiobook &&
                        eu.kanade.tachiyomi.source.novel.AudioChapterSchemes.isAudioChapterUrl(chapter.url)
                    )

        val target = when {
            // Same novel already loaded — resume that track (fast path inside play()).
            state.value.novelId == novel.id && state.value.track != null ->
                chapters.firstOrNull { it.id == state.value.track!!.chapterId }
            else -> {
                val lastId = lastPlayedMap[novel.id]
                when {
                    // Paused mid-chapter — resume that chapter (position comes
                    // from resumeMap inside play()).
                    lastId != null && resumeMap.containsKey(lastId) ->
                        chapters.firstOrNull { it.id == lastId }
                    lastId != null -> {
                        // Finished the last-listened chapter — the next rendered
                        // one in reading order; fall back to the chapter itself
                        // if nothing after it is rendered.
                        val idx = chapters.indexOfFirst { it.id == lastId }
                        chapters.asSequence()
                            .drop((idx + 1).coerceAtLeast(0))
                            .firstOrNull { isPlayable(it) }
                            ?: chapters.firstOrNull { it.id == lastId && isPlayable(it) }
                    }
                    // Never listened — the first rendered chapter.
                    else -> chapters.firstOrNull { isPlayable(it) }
                }
            }
        } ?: return false
        return play(novel, target)
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    fun togglePause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    /** Unconditional pause — swipe-away on the mini-player uses this (D-09). */
    fun pause() {
        mediaController?.pause()
    }

    /**
     * Rewind by the player's configured back-increment (30s, same as the
     * notification's rewind button). When the playhead sits at the head of
     * a chapter — 00:00–00:0x — the remainder carries into the tail of the
     * previous track instead of clamping at 0, so a rewind near a boundary
     * lands in the previous file by the full increment.
     */
    fun rewind() {
        val player = mediaController ?: return
        val backMs = player.seekBackIncrement.takeIf { it > 0 } ?: 15_000L
        val position = player.currentPosition
        val previousIndex = player.previousMediaItemIndex
        if (position == C.TIME_UNSET ||
            position >= backMs ||
            previousIndex == C.INDEX_UNSET
        ) {
            player.seekBack()
            return
        }
        saveCurrentPosition()
        val remainderMs = backMs - position
        val previousDuration = player.currentTimeline
            .getWindow(previousIndex, Timeline.Window())
            .durationMs
        if (previousDuration != C.TIME_UNSET) {
            player.seekTo(previousIndex, (previousDuration - remainderMs).coerceAtLeast(0L))
        } else {
            // Duration resolves when the previous item prepares — land on it
            // first, then step back from its tail once the duration arrives.
            seekIntoTailOnceLoaded(previousIndex, remainderMs)
            player.seekTo(previousIndex, C.TIME_UNSET)
        }
        pushPosition()
    }

    /** One-shot: when [index] becomes the current item and its duration is
     *  known, seek to `duration - fromEndMs`. Aborts if the user navigates
     *  to a different item before the duration resolves. */
    private fun seekIntoTailOnceLoaded(index: Int, fromEndMs: Long) {
        val player = mediaController ?: return
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (p.currentMediaItemIndex != index) {
                    p.removeListener(this)
                    return
                }
                val duration = p.duration
                if (duration != C.TIME_UNSET) {
                    p.removeListener(this)
                    p.seekTo(index, (duration - fromEndMs).coerceAtLeast(0L))
                }
            }
        }
        player.addListener(listener)
    }

    /** +10s — same increment the notification's fast-forward button uses. */
    fun forward() {
        mediaController?.seekForward()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        pushPosition()
    }

    fun nextChapter() {
        saveCurrentPosition()
        mediaController?.seekToNextMediaItem()
    }

    /**
     * Previous-chapter semantics: >3s in restarts the current chapter,
     * otherwise jumps to the previous track (ExoPlayer's `seekToPrevious`
     * window logic).
     */
    fun previousChapter() {
        saveCurrentPosition()
        mediaController?.seekToPrevious()
    }

    fun setSpeed(speed: Float) {
        ttsPreferences.audiobookSpeed().set(speed)
        mediaController?.setPlaybackParameters(PlaybackParameters(speed, 1f))
        _state.update { it.copy(speed = speed) }
    }

    // ------------------------------------------------------------------
    // Sleep timer (D-14)
    // ------------------------------------------------------------------

    fun armSleepMinutes(minutes: Int) {
        _state.update { it.copy(sleepTimer = SleepTimer.armMinutes(minutes, System.currentTimeMillis())) }
    }

    fun armEndOfChapter() {
        _state.update { it.copy(sleepTimer = SleepTimer.EndOfChapter) }
    }

    fun disarmSleepTimer() {
        _state.update { it.copy(sleepTimer = SleepTimer.Off) }
    }

    // ------------------------------------------------------------------
    // Overlay visibility (read by 06-02/03 UI)
    // ------------------------------------------------------------------

    fun showOverlay() = _state.update { it.copy(overlayVisible = true) }
    fun hideOverlay() = _state.update { it.copy(overlayVisible = false) }

    // ------------------------------------------------------------------
    // Session plumbing
    // ------------------------------------------------------------------

    private fun connect(): ListenableFuture<MediaController> {
        controllerFuture?.let { return it }
        val token = SessionToken(
            context,
            ComponentName(context, AudiobookPlaybackService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val controller = try {
                    future.get()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Audiobook: MediaController bind failed" }
                    controllerFuture = null
                    return@addListener
                }
                mediaController = controller
                controller.addListener(playerListener)
                controller.setPlaybackParameters(PlaybackParameters(state.value.speed, 1f))
                pushPlayerState()
            },
            ContextCompat.getMainExecutor(context),
        )
        return future
    }

    /**
     * Cold-start guard: extension sources load asynchronously, so a
     * "continue listening" tap in the first seconds after launch used to hit
     * `get() == null` and silently fall back to the reader. Waits for the
     * catalogue (bounded), then resolves.
     */
    private suspend fun awaitSource(sourceId: Long): eu.kanade.tachiyomi.novelsource.NovelSource? {
        // isInitialized flips when EITHER the APK-extension or JS-plugin flow
        // first emits — it does not mean every source is loaded. Poll get()
        // for this specific id instead (bounded).
        // Canon entries point at the stub id (0L) — it never enters the
        // catalogue, so the wait is a guaranteed stall; materialize the
        // stub immediately (playback lives under carrier dirs anyway).
        if (sourceId == eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog.CANONICAL_SOURCE_ID) {
            return sourceManager.getOrStub(sourceId)
        }
        var waited = 0L
        var source = sourceManager.get(sourceId)
        while (source == null && waited < SOURCE_INIT_TIMEOUT_MS) {
            // Once the catalogue has emitted, an id it still doesn't
            // hold is a stale/uninstalled source — either way getOrStub
            // is the answer, so stop waiting.
            if (sourceManager.isInitialized.value && waited > 0) break
            delay(500)
            waited += 500
            source = sourceManager.get(sourceId)
        }
        // Canon entries point at a stub id that never enters the
        // catalogue — getOrStub materializes the stub instead of
        // returning null (which used to bounce every play() to the
        // reader's live-TTS fallback).
        return source ?: sourceManager.getOrStub(sourceId)
    }

    private suspend fun awaitController(): MediaController? {
        mediaController?.let { return it }
        val future = connect()
        return suspendCancellableCoroutine { cont ->
            future.addListener(
                {
                    cont.resume(
                        try {
                            future.get()
                        } catch (e: Exception) {
                            null
                        },
                    )
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            pushPosition()
            if (isPlaying) {
                _miniPlayerDismissed.value = false
                startTicker()
            } else {
                stopTicker()
                saveCurrentPosition()
                flushAbs() // 22-04: pause boundary push (SD-5)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // The previous chapter played to completion — mark it read
                // and drop its resume entry so a later replay starts at 0.
                playlist.getOrNull(state.value.chapterIndex)?.let { prev ->
                    resumeMap.remove(prev.chapterId)
                    persistResumeMap()
                    markChapterListened(prev.chapterId)
                }
                if (state.value.sleepTimer.pauseOnChapterEnd()) {
                    mediaController?.pause()
                    disarmSleepTimer()
                }
                flushAbs() // 22-04: track-boundary push (SD-5)
            }
            pushPlayerState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                playlist.getOrNull(state.value.chapterIndex)?.let { track ->
                    resumeMap.remove(track.chapterId)
                    persistResumeMap()
                    markChapterListened(track.chapterId)
                }
                // 22-04: item tail reached — the recorded position lands
                // inside the 2 s finished rule, so this flush pushes
                // isFinished/progress=1.0 (SD-7).
                recordAbsPosition()
                flushAbs()
                disarmSleepTimer()
            }
            pushPlayerState()
        }

        override fun onPlayerError(error: PlaybackException) {
            logcat(LogPriority.ERROR) { "Audiobook playback error: ${error.errorCodeName} ${error.message}" }
        }
    }

    private fun pushPlayerState() {
        val controller = mediaController ?: return
        val index = controller.currentMediaItemIndex
        val track = playlist.getOrNull(index)
        _state.update {
            it.copy(
                track = track,
                chapterIndex = index,
                chapterCount = playlist.size,
                isPlaying = controller.isPlaying,
            )
        }
        loadManifestFor(track)
        recordLastPlayed()
        pushPosition()
    }

    /** novelId → chapterId anchor for "continue listening" (PB-4 adjacent). */
    private fun recordLastPlayed() {
        val track = state.value.track ?: return
        val novelId = state.value.novelId ?: return
        if (lastPlayedMap[novelId] != track.chapterId) {
            lastPlayedMap[novelId] = track.chapterId
            ttsPreferences.audiobookLastPlayed().set(json.encodeToString(lastPlayedMap))
        }
    }

    // ------------------------------------------------------------------
    // Synced paragraph text — manifest embedded in the rendered .m4a
    // ------------------------------------------------------------------

    /** Chapter the loaded manifest belongs to; null = nothing loaded. */
    private var manifestForChapterId: Long? = null

    /**
     * Reads the track's embedded `MikoTTS` manifest (paragraph texts + start
     * offsets) off the main thread. Deduped by chapter id — called from
     * [pushPlayerState] on every listener event but only reads the file once
     * per track. A missing/malformed manifest leaves the lists empty and the
     * synced-text UI falls back to the plain cover.
     */
    private fun loadManifestFor(track: AudiobookTrack?) {
        if (track?.chapterId == manifestForChapterId) return
        manifestForChapterId = track?.chapterId
        _state.update { it.copy(paragraphs = emptyList(), paragraphOffsetsMs = emptyList()) }
        if (track == null) return
        // SD-5: remote stream URIs have no openable fd — skip the SAF
        // read entirely (a foreign .m4a with an embedded Miko atom may
        // get a byte-range manifest fetch in a later plan).
        if (track.uri.startsWith("http")) return
        scope.launch {
            val manifest = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .openFileDescriptor(Uri.parse(track.uri), "r")
                        ?.use { pfd ->
                            Mp4AtomReader.read(FileInputStream(pfd.fileDescriptor).channel)
                                ?.let(::decodeManifest)
                        }
                }.getOrNull()
            }
            // Ignore the result if the track changed while reading.
            if (manifestForChapterId == track.chapterId && manifest != null) {
                _state.update {
                    it.copy(
                        paragraphs = manifest.paragraphTexts,
                        paragraphOffsetsMs = manifest.paragraphOffsetsMs,
                    )
                }
            }
        }
    }

    private fun pushPosition() {
        val controller = mediaController ?: return
        _state.update {
            it.copy(
                positionMs = controller.currentPosition.coerceAtLeast(0),
                durationMs = controller.duration.takeIf { d -> d != C.TIME_UNSET && d > 0 } ?: it.durationMs,
            )
        }
    }

    // ------------------------------------------------------------------
    // Position ticker — UI position, resume write-through, sleep deadline
    // ------------------------------------------------------------------

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun tick() {
        pushPosition()
        val now = System.currentTimeMillis()
        if (state.value.sleepTimer.shouldPause(now)) {
            mediaController?.pause()
            disarmSleepTimer()
            return
        }
        if (now - lastResumeWriteMs >= RESUME_WRITE_MS) {
            saveCurrentPosition()
            // 22-04: ABS heartbeat — the 30 s push cadence rides the same
            // 5 s persist beat (onPosition lands inside saveCurrentPosition);
            // the syncer's dirty flag + push guard decide whether to PATCH.
            if (now - lastAbsFlushMs >= ABS_FLUSH_MS) {
                lastAbsFlushMs = now
                flushAbs()
            }
        }
    }

    // ------------------------------------------------------------------
    // Resume map (PB-4)
    // ------------------------------------------------------------------

    private fun saveCurrentPosition() {
        val track = state.value.track ?: return
        val position = mediaController?.currentPosition?.coerceAtLeast(0) ?: return
        val duration = state.value.durationMs
        if (duration > 0 && position >= duration - END_OF_TRACK_MS) {
            // Finished (or effectively finished) — replay starts at 0 and
            // the chapter counts as read.
            resumeMap.remove(track.chapterId)
            markChapterListened(track.chapterId)
        } else {
            resumeMap[track.chapterId] = position
            persistListenedCharPos(track.chapterId, position)
        }
        lastResumeWriteMs = System.currentTimeMillis()
        persistResumeMap()
        recordAbsPosition() // 22-04: every persist beat also dirties the ABS item row
    }

    /**
     * A chapter whose audio played to the end counts as read — listening
     * is consuming the chapter, and the read flag is what the chapter
     * list, history, and "continue reading" anchor actually surface.
     */
    private fun markChapterListened(chapterId: Long) {
        val chapter = playlistChapters[chapterId] ?: return
        if (chapter.read) return
        scope.launch(Dispatchers.IO) {
            runCatching { setNovelReadStatus.await(true, chapter) }
                .onFailure {
                    logcat(LogPriority.WARN) { "Audiobook: mark-read failed for ch$chapterId: ${it.message}" }
                }
        }
    }

    /**
     * Whispersync-style mid-chapter progress: map the audio position to a
     * character offset via the track's synced-paragraph manifest (same
     * segmentation the reader uses — cumulative paragraph lengths give the
     * active paragraph's startCharIndex) and write `lastCharRead` so the
     * reader resumes where the listener left off. Only ever advances —
     * a re-listen to an earlier point must not regress recorded progress.
     */
    private fun persistListenedCharPos(chapterId: Long, positionMs: Long) {
        val offsets = state.value.paragraphOffsetsMs
        val paragraphs = state.value.paragraphs
        if (offsets.isEmpty() || paragraphs.isEmpty()) return
        val activeIndex = offsets.indexOfLast { it <= positionMs }.coerceAtLeast(0)
        val charPos = paragraphs.take(activeIndex).sumOf { it.length }.toLong()
        if (charPos <= 0) return
        val chapter = playlistChapters[chapterId] ?: return
        val floor = maxOf(
            NovelScrollPositionCodec.decodeCharacterPosition(chapter.lastCharRead),
            listenedCharPeak[chapterId] ?: 0L,
        )
        if (charPos <= floor) return
        listenedCharPeak[chapterId] = charPos
        scope.launch(Dispatchers.IO) {
            runCatching { setNovelReadingPosition.await(chapterId, charPos) }
                .onFailure {
                    logcat(LogPriority.WARN) { "Audiobook: char-pos write failed for ch$chapterId: ${it.message}" }
                }
        }
    }

    /** Drop resume entries for chapters that are no longer rendered. */
    private fun pruneResumeMap(tracks: List<AudiobookTrack>) {
        val ids = tracks.map { it.chapterId }.toSet()
        if (resumeMap.keys.retainAll(ids)) persistResumeMap()
    }

    /**
     * A chapter's rendered `.m4a` was deleted (PB-21): drop its resume entry
     * immediately, and if it was the loaded track, stop playback and reset
     * to idle — the file under the playhead no longer exists.
     */
    fun onAudioDeleted(chapterId: Long) {
        if (resumeMap.remove(chapterId) != null) persistResumeMap()
        if (state.value.track?.chapterId == chapterId) {
            stopTicker()
            mediaController?.stop()
            mediaController?.clearMediaItems()
            playlist = emptyList()
            manifestForChapterId = null
            _miniPlayerDismissed.value = false
            _state.value = AudiobookPlayerState(speed = state.value.speed)
        }
    }

    private fun persistResumeMap() {
        ttsPreferences.audiobookPositions().set(json.encodeToString(resumeMap))
    }

    // ------------------------------------------------------------------
    // Teardown
    // ------------------------------------------------------------------

    /** Stop playback and release the controller connection. */
    fun release() {
        stopTicker()
        saveCurrentPosition()
        flushAbs() // 22-04: close boundary push (SD-5) — idempotent via the syncer mutex
        absItemId = null
        absTrackCtx = emptyList()
        absChapterToIno = emptyMap()
        mediaController?.stop()
        mediaController?.clearMediaItems()
        mediaController?.release()
        mediaController = null
        controllerFuture?.cancel(true)
        controllerFuture = null
        playlist = emptyList()
        manifestForChapterId = null
        _state.value = AudiobookPlayerState(speed = state.value.speed)
    }

    private fun AudiobookTrack.toMediaItem(novel: Novel): MediaItem =
        MediaItem.Builder()
            .setMediaId(chapterId.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(chapterName)
                    .setArtist(novel.title)
                    .setAlbumTitle(novel.title)
                    .setArtworkUri(novel.thumbnailUrl?.let(Uri::parse))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                    .build(),
            )
            .build()

    private companion object {
        /** Position/UI tick cadence while playing. */
        const val TICK_MS = 500L
        private const val SOURCE_INIT_TIMEOUT_MS = 10_000L

        /** Resume-map write-through interval while playing. */
        const val RESUME_WRITE_MS = 5_000L

        /** Positions within this of the end count as "finished". */
        const val END_OF_TRACK_MS = 2_000L

        /** ABS progress push cadence while playing (22-04 SD-5). */
        const val ABS_FLUSH_MS = 30_000L

        /** Pull-on-open network bound for the entry screen (22-04 SD-5b). */
        const val ABS_PULL_TIMEOUT_MS = 3_000L
    }
}
