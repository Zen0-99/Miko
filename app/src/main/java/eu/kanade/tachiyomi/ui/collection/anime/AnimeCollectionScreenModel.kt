package eu.kanade.tachiyomi.ui.collection.anime

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.collection.anime.interactor.CreateAnimeCollectionWithName
import tachiyomi.domain.collection.anime.interactor.DeleteAnimeCollection
import tachiyomi.domain.collection.anime.interactor.GetAnimeCollections
import tachiyomi.domain.collection.anime.interactor.GetVisibleAnimeCollections
import tachiyomi.domain.collection.anime.interactor.HideAnimeCollection
import tachiyomi.domain.collection.anime.interactor.RenameAnimeCollection
import tachiyomi.domain.collection.anime.interactor.ReorderAnimeCollection
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeCollectionScreenModel(
    private val getAllCollections: GetAnimeCollections = Injekt.get(),
    private val getVisibleCollections: GetVisibleAnimeCollections = Injekt.get(),
    private val createCollectionWithName: CreateAnimeCollectionWithName = Injekt.get(),
    private val hideCollection: HideAnimeCollection = Injekt.get(),
    private val deleteCollection: DeleteAnimeCollection = Injekt.get(),
    private val reorderCollection: ReorderAnimeCollection = Injekt.get(),
    private val renameCollection: RenameAnimeCollection = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : StateScreenModel<AnimeCollectionScreenState>(AnimeCollectionScreenState.Loading) {

    private val _events: Channel<AnimeCollectionEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            val allCollections = if (libraryPreferences.hideHiddenCollectionsSettings().get()) {
                getVisibleCollections.subscribe()
            } else {
                getAllCollections.subscribe()
            }

            allCollections.collectLatest { collections ->
                mutableState.update {
                    AnimeCollectionScreenState.Success(
                        collections = collections
                            .filterNot(Collection::isSystemCollection)
                            .toImmutableList(),
                    )
                }
            }
        }
    }

    fun createCollection(name: String) {
        screenModelScope.launch {
            when (createCollectionWithName.await(name)) {
                is CreateAnimeCollectionWithName.Result.InternalError -> _events.send(
                    AnimeCollectionEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun hideCollection(collection: Collection) {
        screenModelScope.launch {
            when (hideCollection.await(collection)) {
                is HideAnimeCollection.Result.InternalError -> _events.send(
                    AnimeCollectionEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun deleteCollection(collectionId: Long) {
        screenModelScope.launch {
            when (deleteCollection.await(collectionId = collectionId)) {
                is DeleteAnimeCollection.Result.InternalError -> _events.send(
                    AnimeCollectionEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun changeOrder(collection: Collection, newIndex: Int) {
        screenModelScope.launch {
            when (reorderCollection.await(collection, newIndex)) {
                is ReorderAnimeCollection.Result.InternalError -> _events.send(
                    AnimeCollectionEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun renameCollection(collection: Collection, name: String) {
        screenModelScope.launch {
            when (renameCollection.await(collection, name)) {
                is RenameAnimeCollection.Result.InternalError -> _events.send(
                    AnimeCollectionEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun showDialog(dialog: AnimeCollectionDialog) {
        mutableState.update {
            when (it) {
                AnimeCollectionScreenState.Loading -> it
                is AnimeCollectionScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                AnimeCollectionScreenState.Loading -> it
                is AnimeCollectionScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface AnimeCollectionDialog {
    data object Create : AnimeCollectionDialog
    data class Rename(val collection: Collection) : AnimeCollectionDialog
    data class Delete(val collection: Collection) : AnimeCollectionDialog
}

sealed interface AnimeCollectionEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : AnimeCollectionEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface AnimeCollectionScreenState {

    @Immutable
    data object Loading : AnimeCollectionScreenState

    @Immutable
    data class Success(
        val collections: ImmutableList<Collection>,
        val dialog: AnimeCollectionDialog? = null,
    ) : AnimeCollectionScreenState {

        val isEmpty: Boolean
            get() = collections.isEmpty()
    }
}
