package eu.kanade.tachiyomi.ui.history.shows

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.shows.interactor.UpdateAnime
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.presentation.history.shows.AnimeHistoryUiModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.shows.interactor.GetAnimeCollections
import tachiyomi.domain.collection.shows.interactor.SetAnimeCollections
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.entries.shows.interactor.GetAnime
import tachiyomi.domain.entries.shows.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.history.shows.interactor.GetAnimeHistory
import tachiyomi.domain.history.shows.interactor.GetNextEpisodes
import tachiyomi.domain.history.shows.interactor.RemoveAnimeHistory
import tachiyomi.domain.history.shows.model.AnimeHistoryWithRelations
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

class ShowsHistoryScreenModel(
    private val addTracks: AddAnimeTracks = Injekt.get(),
    private val getCollections: GetAnimeCollections = Injekt.get(),
    private val getDuplicateLibraryAnime: GetDuplicateLibraryAnime = Injekt.get(),
    private val getHistory: GetAnimeHistory = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val removeHistory: RemoveAnimeHistory = Injekt.get(),
    private val setAnimeCollections: SetAnimeCollections = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val cinemetaEntryUnifier: eu.kanade.tachiyomi.metadata.stream.CinemetaEntryUnifier = Injekt.get(),
) : StateScreenModel<ShowsHistoryScreenModel.State>(State()) {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val _query: MutableStateFlow<String?> = MutableStateFlow(null)
    val query: StateFlow<String?> = _query.asStateFlow()

    init {
        screenModelScope.launch {
            _query.collectLatest { query ->
                getHistory.subscribe(query ?: "")
                    // Unify extension-source rows onto their canonical
                    // Cinemeta entry (season-aware cover + season label).
                    .map { list -> list.map { cinemetaEntryUnifier.unify(it) } }
                    .distinctUntilChanged()
                    .catch { error ->
                        logcat(LogPriority.ERROR, error)
                        _events.send(Event.InternalError)
                    }
                    .map { it.toAnimeHistoryUiModels() }
                    .flowOn(Dispatchers.IO)
                    .collect { newList -> mutableState.update { it.copy(list = newList) } }
            }
        }
    }

    fun search(query: String?) {
        screenModelScope.launchIO {
            _query.emit(query)
        }
    }

    private fun List<AnimeHistoryWithRelations>.toAnimeHistoryUiModels(): List<AnimeHistoryUiModel> {
        // Group consecutive entries from the same anime whose seenAt timestamps
        // fall within batchWindowMs. If a group has >= batchMinSize entries,
        // collapse it into a single AnimeHistoryUiModel.Batch; otherwise emit
        // individual items. Date headers are inserted between groups/items that
        // cross a day boundary.
        val batchWindowMs = 60_000L // 1 minute
        val batchMinSize = 5

        if (isEmpty()) return emptyList()

        // First pass: build groups of consecutive same-anime entries within the window.
        data class Group(
            val animeId: Long,
            val entries: List<AnimeHistoryWithRelations>,
        )

        val groups = mutableListOf<Group>()
        var current: MutableList<AnimeHistoryWithRelations>? = null
        var currentAnimeId: Long? = null
        var windowStart: Long = 0L
        for (entry in this) {
            val t = entry.seenAt?.time ?: 0L
            if (current != null && entry.animeId == currentAnimeId && t - windowStart <= batchWindowMs) {
                current!!.add(entry)
            } else {
                if (current != null) {
                    groups.add(Group(currentAnimeId!!, current!!))
                }
                current = mutableListOf(entry)
                currentAnimeId = entry.animeId
                windowStart = t
            }
        }
        if (current != null) groups.add(Group(currentAnimeId!!, current!!))

        // Second pass: build the final UI model list with headers.
        val result = mutableListOf<AnimeHistoryUiModel>()
        var lastDate: LocalDate? = null
        groups.forEach { group ->
            val entries = group.entries
            val first = entries.first()
            val last = entries.last()
            val firstDate = first.seenAt?.time?.toLocalDate()
            if (firstDate != null && firstDate != lastDate) {
                result.add(AnimeHistoryUiModel.Header(firstDate))
                lastDate = firstDate
            }
            if (entries.size >= batchMinSize) {
                result.add(
                    AnimeHistoryUiModel.Batch(
                        animeId = group.animeId,
                        title = first.title,
                        firstEpisode = first.episodeNumber,
                        lastEpisode = last.episodeNumber,
                        firstSeenAt = first.seenAt ?: java.util.Date(0),
                        lastSeenAt = last.seenAt ?: java.util.Date(0),
                        coverData = first.coverData,
                        historyIds = entries.map { it.id },
                    ),
                )
            } else {
                entries.forEach { result.add(AnimeHistoryUiModel.Item(it)) }
            }
        }
        return result
    }

    suspend fun getNextEpisode(): Episode? {
        return withIOContext { getNextEpisodes.await(onlyUnseen = false).firstOrNull() }
    }

    fun getNextEpisodeForAnime(animeId: Long, episodeId: Long) {
        screenModelScope.launchIO {
            sendNextEpisodeEvent(getNextEpisodes.await(animeId, episodeId, onlyUnseen = false))
        }
    }

    private suspend fun sendNextEpisodeEvent(episodes: List<Episode>) {
        val episode = episodes.firstOrNull()
        _events.send(Event.OpenEpisode(episode))
    }

    fun removeFromHistory(history: AnimeHistoryWithRelations) {
        screenModelScope.launchIO {
            removeHistory.await(history)
        }
    }

    fun removeAllFromHistory(animeId: Long) {
        screenModelScope.launchIO {
            removeHistory.await(animeId)
        }
    }

    fun removeAllHistory() {
        screenModelScope.launchIO {
            val result = removeHistory.awaitAll()
            if (!result) return@launchIO
            _events.send(Event.HistoryCleared)
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    /**
     * Get user collections.
     *
     * @return List of collections, not including the default collection
     */
    suspend fun getCollections(): List<Collection> {
        return getCollections.await().filterNot { it.isSystemCollection }
    }

    private fun moveAnimeToCollection(animeId: Long, collections: Collection?) {
        val collectionIds = listOfNotNull(collections).map { it.id }
        moveAnimeToCollection(animeId, collectionIds)
    }

    private fun moveAnimeToCollection(animeId: Long, collectionIds: List<Long>) {
        screenModelScope.launchIO {
            setAnimeCollections.await(animeId, collectionIds)
        }
    }

    fun moveAnimeToCollectionsAndAddToLibrary(anime: Anime, collections: List<Long>) {
        moveAnimeToCollection(anime.id, collections)
        if (anime.favorite) return

        screenModelScope.launchIO {
            updateAnime.awaitUpdateFavorite(anime.id, true)
        }
    }

    private suspend fun getAnimeCollectionIds(anime: Anime): List<Long> {
        return getCollections.await(anime.id)
            .map { it.id }
    }

    fun addFavorite(animeId: Long) {
        screenModelScope.launchIO {
            val anime = getAnime.await(animeId) ?: return@launchIO

            val duplicate = getDuplicateLibraryAnime.await(anime).getOrNull(0)
            if (duplicate != null) {
                mutableState.update { it.copy(dialog = Dialog.DuplicateAnime(anime, duplicate)) }
                return@launchIO
            }

            addFavorite(anime)
        }
    }

    fun addFavorite(anime: Anime) {
        screenModelScope.launchIO {
            // Move to default collection if applicable
            val collections = getCollections()
            val defaultCollectionId = libraryPreferences.defaultShowsCollection().get().toLong()
            val defaultCollection = collections.find { it.id == defaultCollectionId }

            when {
                // Default collection set
                defaultCollection != null -> {
                    val result = updateAnime.awaitUpdateFavorite(anime.id, true)
                    if (!result) return@launchIO
                    moveAnimeToCollection(anime.id, defaultCollection)
                }

                // Automatic 'Default' or no collections
                defaultCollectionId == 0L || collections.isEmpty() -> {
                    val result = updateAnime.awaitUpdateFavorite(anime.id, true)
                    if (!result) return@launchIO
                    moveAnimeToCollection(anime.id, null)
                }

                // Choose a collection
                else -> showChangeCollectionDialog(anime)
            }

            // Sync with tracking services if applicable
            addTracks.bindEnhancedTrackers(anime, sourceManager.getOrStub(anime.source))
        }
    }

    fun showMigrateDialog(currentAnime: Anime, duplicate: Anime) {
        mutableState.update { currentState ->
            currentState.copy(dialog = Dialog.Migrate(newAnime = currentAnime, oldAnime = duplicate))
        }
    }

    fun showChangeCollectionDialog(anime: Anime) {
        screenModelScope.launch {
            val collections = getCollections()
            val selection = getAnimeCollectionIds(anime)
            mutableState.update { currentState ->
                currentState.copy(
                    dialog = Dialog.ChangeCollection(
                        anime = anime,
                        initialSelection = collections.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                    ),
                )
            }
        }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: List<AnimeHistoryUiModel>? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class Delete(val history: AnimeHistoryWithRelations) : Dialog
        data class DuplicateAnime(val anime: Anime, val duplicate: Anime) : Dialog
        data class ChangeCollection(
            val anime: Anime,
            val initialSelection: ImmutableList<CheckboxState<Collection>>,
        ) : Dialog
        data class Migrate(val newAnime: Anime, val oldAnime: Anime) : Dialog
    }

    sealed interface Event {
        data class OpenEpisode(val episode: Episode?) : Event
        data object InternalError : Event
        data object HistoryCleared : Event
    }
}
