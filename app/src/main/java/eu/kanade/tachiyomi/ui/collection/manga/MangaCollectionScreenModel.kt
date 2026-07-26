package eu.kanade.tachiyomi.ui.collection.manga

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
import tachiyomi.domain.collection.manga.interactor.CreateMangaCollectionWithName
import tachiyomi.domain.collection.manga.interactor.DeleteMangaCollection
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.collection.manga.interactor.GetVisibleMangaCollections
import tachiyomi.domain.collection.manga.interactor.HideMangaCollection
import tachiyomi.domain.collection.manga.interactor.RenameMangaCollection
import tachiyomi.domain.collection.manga.interactor.ReorderMangaCollection
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaCollectionScreenModel(
    private val getAllCollections: GetMangaCollections = Injekt.get(),
    private val getVisibleCollections: GetVisibleMangaCollections = Injekt.get(),
    private val createCollectionWithName: CreateMangaCollectionWithName = Injekt.get(),
    private val hideCollection: HideMangaCollection = Injekt.get(),
    private val deleteCollection: DeleteMangaCollection = Injekt.get(),
    private val reorderCollection: ReorderMangaCollection = Injekt.get(),
    private val renameCollection: RenameMangaCollection = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : StateScreenModel<MangaCollectionScreenState>(MangaCollectionScreenState.Loading) {

    private val _events: Channel<MangaCollectionEvent> = Channel()
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
                    MangaCollectionScreenState.Success(
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
                is CreateMangaCollectionWithName.Result.InternalError -> _events.send(
                    MangaCollectionEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun hideCollection(collection: Collection) {
        screenModelScope.launch {
            when (hideCollection.await(collection)) {
                is HideMangaCollection.Result.InternalError -> _events.send(
                    MangaCollectionEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun deleteCollection(collectionId: Long) {
        screenModelScope.launch {
            when (deleteCollection.await(collectionId = collectionId)) {
                is DeleteMangaCollection.Result.InternalError -> _events.send(
                    MangaCollectionEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun changeOrder(collection: Collection, newIndex: Int) {
        screenModelScope.launch {
            when (reorderCollection.await(collection, newIndex)) {
                is ReorderMangaCollection.Result.InternalError -> _events.send(
                    MangaCollectionEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun renameCollection(collection: Collection, name: String) {
        screenModelScope.launch {
            when (renameCollection.await(collection, name)) {
                is RenameMangaCollection.Result.InternalError -> _events.send(
                    MangaCollectionEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun showDialog(dialog: MangaCollectionDialog) {
        mutableState.update {
            when (it) {
                MangaCollectionScreenState.Loading -> it
                is MangaCollectionScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                MangaCollectionScreenState.Loading -> it
                is MangaCollectionScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface MangaCollectionDialog {
    data object Create : MangaCollectionDialog
    data class Rename(val collection: Collection) : MangaCollectionDialog
    data class Delete(val collection: Collection) : MangaCollectionDialog
}

sealed interface MangaCollectionEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : MangaCollectionEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface MangaCollectionScreenState {

    @Immutable
    data object Loading : MangaCollectionScreenState

    @Immutable
    data class Success(
        val collections: ImmutableList<Collection>,
        val dialog: MangaCollectionDialog? = null,
    ) : MangaCollectionScreenState {

        val isEmpty: Boolean
            get() = collections.isEmpty()
    }
}
