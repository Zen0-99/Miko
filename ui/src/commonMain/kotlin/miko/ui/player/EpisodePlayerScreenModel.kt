package miko.ui.player

import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.ChapterSortMode
import miko.ui.carrier.RemoteItem
import miko.ui.carrier.VideoEntry
import miko.ui.carrier.ordered
import miko.ui.reader.ChapterRow
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Episode player (shows carrier): resolves the episode's hosters to a flat
 * stream list via [CarrierBinding.hosters]/[CarrierBinding.videos], marks
 * the episode seen on open, and tracks prev/next episode navigation.
 * Playback runs through the shared [VideoPlayer] expect/actual — Media3 on
 * Android, AVPlayerViewController on iOS; the stream rows pick the url.
 */
class EpisodePlayerScreenModel(
    private val binding: CarrierBinding,
    private val sourceId: Long,
    private val entryUrl: String,
    initialEpisodeUrl: String,
    private val preferenceStore: PreferenceStore,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * Android's skip-intro button state — the segment type under the
     * playhead, its display name, and a live "Don't skip" countdown when
     * Netflix-style is on. Null = no skip control.
     */
    data class SkipButton(
        val type: ChapterType,
        val name: String,
        val countdownSec: Int?,
    )

    data class State(
        val episodeUrl: String = "",
        val episodeName: String = "",
        val videos: List<VideoEntry> = emptyList(),
        /** Stream currently on the player surface — first quality by default. */
        val playingUrl: String? = null,
        /**
         * Seek target for the player — Android's `lastSecondSeen` resume on
         * open, and the live position when a stream swap re-prepares the
         * surface.
         */
        val resumePositionMs: Long = 0L,
        val loading: Boolean = true,
        val error: String? = null,
        val hasPrev: Boolean = false,
        val hasNext: Boolean = false,
        /** Episode-sheet rows — remote item names with local seen state. */
        val episodes: List<ChapterRow> = emptyList(),
        val showEpisodes: Boolean = false,
        /** Android's `skipIntroText` — the skip overlay's visible state. */
        val skipButton: SkipButton? = null,
        /**
         * One-shot seek request (auto-skip / button seek) — the player
         * consumes it then calls [onSeekHandled] so identical targets
         * still fire.
         */
        val seekToMs: Long? = null,
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State(episodeUrl = initialEpisodeUrl))
    val state: StateFlow<State> = _state.asStateFlow()

    private var items: List<RemoteItem> = emptyList()

    private val source
        get() = binding.source(sourceId)

    // Android's PlayerPreferences skip block — same keys/defaults.
    private val enableSkipIntroPref = preferenceStore.getBoolean(
        "pref_enable_skip_intro",
        true,
    )
    private val autoSkipIntroPref = preferenceStore.getBoolean(
        "pref_enable_auto_skip_ani_skip",
        false,
    )
    private val netflixStylePref = preferenceStore.getBoolean(
        "pref_enable_netflixStyle_aniskip",
        false,
    )
    private val waitingTimePref = preferenceStore.getInt(
        "pref_waiting_time_aniskip",
        5,
    )

    /** Merged segment list — Android's `chapters` StateFlow. */
    private var segments: List<SkipSegment> = emptyList()

    /**
     * Stamps awaiting a known duration — Android merges on `fileLoaded`
     * once `player.duration` resolves; ours arrives via position ticks.
     */
    private var pendingStamps: List<TimeStamp> = emptyList()
    private var stampsPending = false
    private var durationSec = 0.0

    /** Per-entry disable bit — `Anime.skipIntroDisable` via viewerFlags. */
    private var entrySkipDisable = false

    /** Netflix countdown — Android's `waitingSkipIntro`. */
    private var waitingSkipIntro = -1
    private var countdownJob: Job? = null

    init {
        scope.launch {
            binding.isInitialized.collect { initialized ->
                if (initialized && items.isEmpty()) {
                    runCatching { loadItemList() }
                    openEpisode(_state.value.episodeUrl)
                }
            }
        }
        scope.launch {
            binding.localEntry(entryUrl, sourceId).collect { local ->
                entrySkipDisable = local?.skipIntroDisable == true
            }
        }
    }

    private suspend fun loadItemList() {
        val src = source ?: return
        val local = binding.localEntry(entryUrl, sourceId).first()
        val remoteItems = src.items(entryUrl)
        // Android sorts the reader's episode list by the entry's episodeFlags.
        items = (local?.sortMode ?: ChapterSortMode.NUMBER)
            .ordered(remoteItems, local?.sortDescending == true)
        val seenUrls = local?.let { entry ->
            binding.getLocalItems(entry.id)
                .filter { it.done }
                .map { it.url }
                .toSet()
        }.orEmpty()
        _state.value = _state.value.copy(
            episodes = items.map {
                ChapterRow(
                    url = it.url,
                    name = it.name,
                    number = it.number,
                    done = it.url in seenUrls,
                )
            },
        )
    }

    fun openEpisodeSheet() {
        _state.value = _state.value.copy(showEpisodes = true)
    }

    fun closeEpisodeSheet() {
        _state.value = _state.value.copy(showEpisodes = false)
    }

    fun openEpisode(episodeUrl: String) {
        scope.launch {
            flushPlayback()
            val src = source ?: run {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Source unavailable",
                )
                return@launch
            }
            val index = items.indexOfFirst { it.url == episodeUrl }
            _state.value = _state.value.copy(
                episodeUrl = episodeUrl,
                episodeName = items.getOrNull(index)?.name ?: "",
                loading = true,
                error = null,
                hasPrev = index > 0,
                hasNext = index >= 0 && index < items.lastIndex,
                skipButton = null,
            )
            // New file — Android rebuilds its chapter list on fileLoaded.
            segments = emptyList()
            pendingStamps = emptyList()
            stampsPending = false
            waitingSkipIntro = waitingTimePref.get()
            countdownJob?.cancel()
            countdownJob = null
            val result = runCatching { resolveVideos(src.id, episodeUrl) }
            result.fold(
                onSuccess = { videos ->
                    _state.value = _state.value.copy(
                        videos = videos,
                        playingUrl = videos.firstOrNull()?.url,
                        loading = false,
                    )
                    // The playing stream's timestamps merge once duration
                    // resolves — Android's setupChapters on fileLoaded.
                    pendingStamps = videos.firstOrNull()?.timestamps.orEmpty()
                    stampsPending = pendingStamps.isNotEmpty()
                    markSeen(episodeUrl)
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Failed to resolve streams",
                    )
                },
            )
        }
    }

    /** Hosters → flat video list, resolving lazy hosters in parallel. */
    private suspend fun resolveVideos(srcId: Long, episodeUrl: String): List<VideoEntry> =
        coroutineScope {
            binding.hosters(srcId, episodeUrl)
                .map { hoster ->
                    async {
                        when {
                            hoster.videos != null -> hoster.videos
                            hoster.lazy -> {
                                val target = hoster.internalData.ifBlank { hoster.url }
                                runCatching { binding.videos(srcId, target) }
                                    .getOrDefault(emptyList())
                            }
                            else -> emptyList()
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }

    /** Stream-row tap — Android's quality-source selection. */
    fun playVideo(url: String) {
        _state.value = _state.value.copy(playingUrl = url)
        // Quality swap — merge the new stream's stamps into the current
        // segment list like Android's second setupChapters pass.
        val stamps = _state.value.videos.firstOrNull { it.url == url }
            ?.timestamps.orEmpty()
        if (stamps.isNotEmpty()) {
            pendingStamps = stamps
            stampsPending = true
            mergePendingStamps()
        }
    }

    fun prevEpisode() {
        val index = items.indexOfFirst { it.url == _state.value.episodeUrl }
        items.getOrNull(index - 1)?.let { openEpisode(it.url) }
    }

    fun nextEpisode() {
        val index = items.indexOfFirst { it.url == _state.value.episodeUrl }
        items.getOrNull(index + 1)?.let { openEpisode(it.url) }
    }

    /** Episode id with an open watch session — closed on switch/dispose. */
    private var openItemId: Long? = null

    private suspend fun markSeen(episodeUrl: String) {
        val local = binding.localEntry(entryUrl, sourceId).first() ?: return
        val episode = binding.getLocalItems(local.id)
            .firstOrNull { it.url == episodeUrl }
            ?: return
        openItemId?.takeIf { it != episode.id }?.let { binding.recordItemClose(it) }
        if (!episode.done) binding.setItemDone(episode.id, true)
        openItemId = episode.id
        binding.recordItemOpen(episode.id)
        // Android's resume — seed the seek target from lastSecondSeen.
        _state.value = _state.value.copy(resumePositionMs = episode.lastSecondSeen * 1000)
        pendingPositionSec = -1
        lastWrittenPositionSec = episode.lastSecondSeen
    }

    /** Latest reported position — flushed to `lastSecondSeen` periodically. */
    private var pendingPositionSec: Long = -1
    private var pendingTotalSec: Long = 0
    private var lastWrittenPositionSec: Long = 0

    /** Player progress callback — updates the live position and persists it. */
    fun onPlaybackPosition(positionMs: Long, durationMs: Long) {
        _state.value = _state.value.copy(resumePositionMs = positionMs)
        pendingPositionSec = positionMs / 1000
        pendingTotalSec = durationMs / 1000
        // Throttled write — the observer ticks twice a second; persist
        // every ~10s of drift or on flush.
        if (pendingPositionSec - lastWrittenPositionSec >= 10) {
            scope.launch { flushPlayback() }
        }
        if (durationMs > 0) {
            durationSec = durationMs / 1000.0
            mergePendingStamps()
        }
        updateSkip(positionMs / 1000f)
    }

    /**
     * Android's setupChapters/aniSkip merge — folds pending stamps into
     * the segment list once the duration is known (merge needs it for
     * end-of-file adjacency).
     */
    private fun mergePendingStamps() {
        if (!stampsPending || durationSec <= 0.0) return
        stampsPending = false
        segments = mergeSkipSegments(
            current = segments,
            stamps = pendingStamps,
            durationSec = durationSec,
        )
    }

    /**
     * Android's `setChapter` — the position-driven skip state machine.
     * `ChapterType.Other` segments (and the per-entry disable bit) hide
     * the control; inside a marked segment the pref picks manual button,
     * auto-skip, or Netflix countdown.
     */
    private fun updateSkip(positionSec: Float) {
        if (!enableSkipIntroPref.get() || entrySkipDisable) {
            if (_state.value.skipButton != null) {
                _state.value = _state.value.copy(skipButton = null)
            }
            countdownJob?.cancel()
            countdownJob = null
            return
        }
        val current = segments.withIndex()
            .filter { it.value.startSec <= positionSec }
            .maxByOrNull { it.value.startSec }
            ?: return
        val segment = current.value
        if (segment.type == ChapterType.Other) {
            _state.value = _state.value.copy(skipButton = null)
            waitingSkipIntro = waitingTimePref.get()
            countdownJob?.cancel()
            countdownJob = null
            return
        }
        val nextSegmentStart = segments.getOrNull(current.index + 1)
            ?.startSec ?: positionSec
        when {
            netflixStylePref.get() -> {
                if (countdownJob == null) {
                    waitingSkipIntro = waitingTimePref.get()
                    countdownJob = scope.launch {
                        while (isActive && waitingSkipIntro > 0) {
                            delay(1_000)
                            waitingSkipIntro--
                            _state.value.skipButton?.let { button ->
                                _state.value = _state.value.copy(
                                    skipButton = button.copy(
                                        countdownSec = waitingSkipIntro.coerceAtLeast(0),
                                    ),
                                )
                            }
                        }
                        if (waitingSkipIntro == 0) {
                            _state.value = _state.value.copy(
                                seekToMs = (nextSegmentStart * 1000).toLong(),
                                skipButton = null,
                            )
                        }
                        countdownJob = null
                    }
                }
                _state.value = _state.value.copy(
                    skipButton = SkipButton(
                        type = segment.type,
                        name = segment.name,
                        countdownSec = waitingSkipIntro.coerceAtLeast(0),
                    ),
                )
            }
            autoSkipIntroPref.get() -> {
                // Seek past the segment — position lands in the next one
                // and Other clears the button (Android's seekToWithText).
                _state.value = _state.value.copy(
                    seekToMs = (nextSegmentStart * 1000).toLong(),
                    skipButton = null,
                )
            }
            else -> {
                _state.value = _state.value.copy(
                    skipButton = SkipButton(
                        type = segment.type,
                        name = segment.name,
                        countdownSec = null,
                    ),
                )
            }
        }
    }

    /**
     * Android's `onSkipIntro` — a countdown tap cancels the auto-skip
     * (button becomes "Skip {type}"); otherwise seek to the segment end.
     */
    fun onSkipIntro() {
        val current = segments.withIndex()
            .filter { it.value.startSec <= pendingPositionSec.toFloat() }
            .maxByOrNull { it.value.startSec } ?: return
        val button = _state.value.skipButton ?: return
        if (button.countdownSec != null) {
            countdownJob?.cancel()
            countdownJob = null
            waitingSkipIntro = -1
            _state.value = _state.value.copy(
                skipButton = button.copy(countdownSec = null),
            )
            return
        }
        val nextStart = segments.getOrNull(current.index + 1)?.startSec
            ?: pendingPositionSec.toFloat()
        _state.value = _state.value.copy(
            seekToMs = (nextStart * 1000).toLong(),
            skipButton = null,
        )
    }

    /** Player consumed the seek — clear so an identical target refires. */
    fun onSeekHandled() {
        if (_state.value.seekToMs != null) {
            _state.value = _state.value.copy(seekToMs = null)
        }
    }

    private suspend fun flushPlayback() {
        val itemId = openItemId ?: return
        val pos = pendingPositionSec
        if (pos < 0 || pos == lastWrittenPositionSec) return
        lastWrittenPositionSec = pos
        binding.setItemPlayback(itemId, pos, pendingTotalSec)
    }

    fun dispose() {
        countdownJob?.cancel()
        val open = openItemId
        openItemId = null
        runBlocking {
            flushPlayback()
            if (open != null) binding.recordItemClose(open)
        }
        scope.cancel()
    }
}
