package miko.ui.player

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.ChapterSortMode
import miko.ui.carrier.RemoteItem
import miko.ui.carrier.VideoEntry
import miko.ui.carrier.ordered
import miko.ui.reader.ChapterRow

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
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

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
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State(episodeUrl = initialEpisodeUrl))
    val state: StateFlow<State> = _state.asStateFlow()

    private var items: List<RemoteItem> = emptyList()

    private val source
        get() = binding.source(sourceId)

    init {
        scope.launch {
            binding.isInitialized.collect { initialized ->
                if (initialized && items.isEmpty()) {
                    runCatching { loadItemList() }
                    openEpisode(_state.value.episodeUrl)
                }
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
            )
            val result = runCatching { resolveVideos(src.id, episodeUrl) }
            result.fold(
                onSuccess = { videos ->
                    _state.value = _state.value.copy(
                        videos = videos,
                        playingUrl = videos.firstOrNull()?.url,
                        loading = false,
                    )
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
    }

    private suspend fun flushPlayback() {
        val itemId = openItemId ?: return
        val pos = pendingPositionSec
        if (pos < 0 || pos == lastWrittenPositionSec) return
        lastWrittenPositionSec = pos
        binding.setItemPlayback(itemId, pos, pendingTotalSec)
    }

    fun dispose() {
        val open = openItemId
        openItemId = null
        runBlocking {
            flushPlayback()
            if (open != null) binding.recordItemClose(open)
        }
        scope.cancel()
    }
}
