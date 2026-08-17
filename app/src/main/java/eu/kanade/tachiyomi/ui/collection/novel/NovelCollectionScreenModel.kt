package eu.kanade.tachiyomi.ui.collection.novel

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
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.novel.interactor.CreateNovelCollectionWithName
import tachiyomi.domain.collection.novel.interactor.DeleteNovelCollection
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.collection.novel.interactor.GetVisibleNovelCollections
import tachiyomi.domain.collection.novel.interactor.HideNovelCollection
import tachiyomi.domain.collection.novel.interactor.RenameNovelCollection
import tachiyomi.domain.collection.novel.interactor.ReorderNovelCollection
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelCollectionScreenModel(
    private val getAllCollections: GetNovelCollections = Injekt.get(),
    private val getVisibleCollections: GetVisibleNovelCollections = Injekt.get(),
    private val createCollectionWithName: CreateNovelCollectionWithName = Injekt.get(),
    private val hideCollection: HideNovelCollection = Injekt.get(),
    private val deleteCollection: DeleteNovelCollection = Injekt.get(),
    private val reorderCollection: ReorderNovelCollection = Injekt.get(),
    private val renameCollection: RenameNovelCollection = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : StateScreenModel<NovelCollectionScreenState>(NovelCollectionScreenState.Loading) {

    private val _events: Channel<NovelCollectionEvent> = Channel()
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
                    NovelCollectionScreenState.Success(
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
                is CreateNovelCollectionWithName.Result.InternalError -> _events.send(
                    NovelCollectionEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun hideCollection(collection: Collection) {
        screenModelScope.launch {
            when (hideCollection.await(collection)) {
                is HideNovelCollection.Result.InternalError -> _events.send(
                    NovelCollectionEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun deleteCollection(collectionId: Long) {
        screenModelScope.launch {
            when (deleteCollection.await(collectionId = collectionId)) {
                is DeleteNovelCollection.Result.InternalError -> _events.send(
                    NovelCollectionEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun changeOrder(collection: Collection, newIndex: Int) {
        screenModelScope.launch {
            when (reorderCollection.await(collection, newIndex)) {
                is ReorderNovelCollection.Result.InternalError -> _events.send(
                    NovelCollectionEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun renameCollection(collection: Collection, name: String) {
        screenModelScope.launch {
            when (renameCollection.await(collection, name)) {
                is RenameNovelCollection.Result.InternalError -> _events.send(
                    NovelCollectionEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun showDialog(dialog: NovelCollectionDialog) {
        mutableState.update {
            when (it) {
                NovelCollectionScreenState.Loading -> it
                is NovelCollectionScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                NovelCollectionScreenState.Loading -> it
                is NovelCollectionScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface NovelCollectionDialog {
    data object Create : NovelCollectionDialog
    data class Rename(val collection: Collection) : NovelCollectionDialog
    data class Delete(val collection: Collection) : NovelCollectionDialog
}

sealed interface NovelCollectionEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : NovelCollectionEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface NovelCollectionScreenState {

    @Immutable
    data object Loading : NovelCollectionScreenState

    @Immutable
    data class Success(
        val collections: ImmutableList<Collection>,
        val dialog: NovelCollectionDialog? = null,
    ) : NovelCollectionScreenState {

        val isEmpty: Boolean
            get() = collections.isEmpty()
    }
}
