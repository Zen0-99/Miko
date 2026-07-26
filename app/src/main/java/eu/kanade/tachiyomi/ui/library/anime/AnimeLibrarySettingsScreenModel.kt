package eu.kanade.tachiyomi.ui.library.anime

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.collection.anime.interactor.SetAnimeDisplayMode
import tachiyomi.domain.collection.anime.interactor.SetSortModeForAnimeCollection
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class AnimeLibrarySettingsScreenModel(
    val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setAnimeDisplayMode: SetAnimeDisplayMode = Injekt.get(),
    private val setSortModeForCollection: SetSortModeForAnimeCollection = Injekt.get(),
    trackerManager: TrackerManager = Injekt.get(),
) : ScreenModel {

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = trackerManager.loggedInTrackers(),
        )

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTrackedAnime(id) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setAnimeDisplayMode.await(mode)
    }

    fun setSort(
        collection: Collection?,
        mode: AnimeLibrarySort.Type,
        direction: AnimeLibrarySort.Direction,
    ) {
        screenModelScope.launchIO {
            setSortModeForCollection.await(collection, mode, direction)
        }
    }
}
