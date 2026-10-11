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
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.RemoteItem
import miko.ui.carrier.VideoEntry

/**
 * Episode player (shows carrier): resolves the episode's hosters to a flat
 * stream list via [CarrierBinding.hosters]/[CarrierBinding.videos], marks
 * the episode seen on open, and tracks prev/next episode navigation.
 * Playback itself is delegated to the platform — the screen opens streams
 * through [androidx.compose.ui.platform.LocalUriHandler] until an in-app
 * AVPlayer lands.
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
        val loading: Boolean = true,
        val error: String? = null,
        val hasPrev: Boolean = false,
        val hasNext: Boolean = false,
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
        items = src.items(entryUrl)
    }

    fun openEpisode(episodeUrl: String) {
        scope.launch {
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

    fun prevEpisode() {
        val index = items.indexOfFirst { it.url == _state.value.episodeUrl }
        items.getOrNull(index - 1)?.let { openEpisode(it.url) }
    }

    fun nextEpisode() {
        val index = items.indexOfFirst { it.url == _state.value.episodeUrl }
        items.getOrNull(index + 1)?.let { openEpisode(it.url) }
    }

    private suspend fun markSeen(episodeUrl: String) {
        val local = binding.localEntry(entryUrl, sourceId).first() ?: return
        val episode = binding.getLocalItems(local.id)
            .firstOrNull { it.url == episodeUrl }
            ?: return
        if (episode.done) return
        binding.setItemDone(episode.id, true)
    }

    fun dispose() = scope.cancel()
}
