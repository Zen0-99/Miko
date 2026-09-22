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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadProvider
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
    private val ttsPreferences: NovelTtsPreferences = Injekt.get(),
    private val getNovelChaptersByNovelId: GetNovelChaptersByNovelId = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var playlist: List<AudiobookTrack> = emptyList()
    private var tickerJob: Job? = null
    private var lastResumeWriteMs = 0L

    private val resumeMap: MutableMap<Long, Long> by lazy {
        runCatching {
            json.decodeFromString<Map<Long, Long>>(ttsPreferences.audiobookPositions().get())
        }.getOrDefault(emptyMap()).toMutableMap()
    }

    private val _state = MutableStateFlow(
        AudiobookPlayerState(speed = ttsPreferences.audiobookSpeed().get()),
    )
    val state: StateFlow<AudiobookPlayerState> = _state.asStateFlow()

    // ------------------------------------------------------------------
    // Playback entry point
    // ------------------------------------------------------------------

    /**
     * Load [novel]'s rendered-chapter playlist and start playing [chapter]
     * through the session service. Resumes from the persisted position (PB-4).
     * Returns false when the chapter has no rendered audio or the session
     * can't be reached — callers fall back to the reader's live TTS path.
     */
    suspend fun play(novel: Novel, chapter: NovelChapter): Boolean {
        // Fast path: same track already loaded — just resume and surface the overlay.
        if (state.value.track?.chapterId == chapter.id && mediaController != null) {
            showOverlay()
            mediaController?.play()
            return true
        }

        val source = sourceManager.get(novel.source) ?: return false
        val tracks = withContext(Dispatchers.IO) {
            val rendered = downloadProvider.renderedAudioFileNames(novel.title, source)
            AudiobookPlaylist.build(
                chapters = getNovelChaptersByNovelId.await(novel.id),
                renderedFileNames = rendered,
                audioFileName = { downloadProvider.getAudioFileName(it.name, it.scanlator) },
                uriFor = {
                    downloadProvider.findAudioFile(it.name, it.scanlator, novel.title, source)
                        ?.uri?.toString()
                },
            )
        }
        val startIndex = tracks.indexOfFirst { it.chapterId == chapter.id }
        if (startIndex < 0) {
            logcat(LogPriority.WARN) { "Audiobook: no rendered audio for chapter '${chapter.name}'" }
            return false
        }
        val controller = awaitController() ?: return false

        playlist = tracks
        pruneResumeMap(tracks)
        val resumePosition = resumeMap[chapter.id] ?: 0L

        controller.setMediaItems(
            tracks.map { it.toMediaItem(novel) },
            startIndex,
            resumePosition,
        )
        controller.setPlaybackParameters(PlaybackParameters(state.value.speed, 1f))
        controller.prepare()
        controller.play()

        _state.update {
            it.copy(
                track = tracks[startIndex],
                novelId = novel.id,
                novelTitle = novel.title,
                cover = novel.asNovelCover(),
                chapterIndex = startIndex,
                chapterCount = tracks.size,
                positionMs = resumePosition,
                overlayVisible = true,
            )
        }
        return true
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    fun togglePause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    /** −30s — same increment the notification's rewind button uses. */
    fun rewind() {
        mediaController?.seekBack()
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
                startTicker()
            } else {
                stopTicker()
                saveCurrentPosition()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // The previous chapter played to completion — drop its resume
                // entry so a later replay starts from the beginning.
                playlist.getOrNull(state.value.chapterIndex)?.let { prev ->
                    resumeMap.remove(prev.chapterId)
                    persistResumeMap()
                }
                if (state.value.sleepTimer.pauseOnChapterEnd()) {
                    mediaController?.pause()
                    disarmSleepTimer()
                }
            }
            pushPlayerState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                playlist.getOrNull(state.value.chapterIndex)?.let { track ->
                    resumeMap.remove(track.chapterId)
                    persistResumeMap()
                }
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
        _state.update {
            it.copy(
                track = playlist.getOrNull(index),
                chapterIndex = index,
                chapterCount = playlist.size,
                isPlaying = controller.isPlaying,
            )
        }
        pushPosition()
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
            // Finished (or effectively finished) — replay starts at 0.
            resumeMap.remove(track.chapterId)
        } else {
            resumeMap[track.chapterId] = position
        }
        lastResumeWriteMs = System.currentTimeMillis()
        persistResumeMap()
    }

    /** Drop resume entries for chapters that are no longer rendered. */
    private fun pruneResumeMap(tracks: List<AudiobookTrack>) {
        val ids = tracks.map { it.chapterId }.toSet()
        if (resumeMap.keys.retainAll(ids)) persistResumeMap()
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
        mediaController?.stop()
        mediaController?.clearMediaItems()
        mediaController?.release()
        mediaController = null
        controllerFuture?.cancel(true)
        controllerFuture = null
        playlist = emptyList()
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

        /** Resume-map write-through interval while playing. */
        const val RESUME_WRITE_MS = 5_000L

        /** Positions within this of the end count as "finished". */
        const val END_OF_TRACK_MS = 2_000L
    }
}
