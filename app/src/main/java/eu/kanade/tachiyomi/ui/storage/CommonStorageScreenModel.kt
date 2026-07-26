package eu.kanade.tachiyomi.ui.storage

import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.more.storage.StorageItem
import eu.kanade.presentation.more.storage.StorageScreenState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random

abstract class CommonStorageScreenModel<T>(
    private val downloadCacheChanges: SharedFlow<Unit>,
    private val downloadCacheIsInitializing: StateFlow<Boolean>,
    private val libraries: Flow<List<T>>,
    private val collections: (Boolean) -> Flow<List<Collection>>,
    private val getDownloadSize: T.() -> Long,
    private val getDownloadCount: T.() -> Int,
    private val getId: T.() -> Long,
    private val getCollectionId: T.() -> Long,
    private val getTitle: T.() -> String,
    private val getThumbnail: T.() -> String?,
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : StateScreenModel<StorageScreenState>(StorageScreenState.Loading) {

    private val selectedCollection = MutableStateFlow(AllCollection)

    init {
        screenModelScope.launchIO {
            val hideHiddenCollections = libraryPreferences.hideHiddenCollectionsSettings().get()

            combine(
                flow = downloadCacheChanges,
                flow2 = downloadCacheIsInitializing,
                flow3 = libraries,
                flow4 = collections(hideHiddenCollections),
                flow5 = selectedCollection,
                transform = { _, _, libraries, collections, selectedCollection ->
                    // initialize the screen with an empty state
                    mutableState.update {
                        StorageScreenState.Success(
                            selectedCollection = selectedCollection,
                            collections = listOf(AllCollection, *collections.toTypedArray()),
                            items = emptyList(),
                        )
                    }

                    val distinctLibraries = libraries.distinctBy {
                        it.getId()
                    }.filter { item ->
                        val collectionId = item.getCollectionId()
                        when {
                            // if all is selected, we want to make sure to include all entries
                            // from only visible collections
                            selectedCollection == AllCollection -> collections.find {
                                it.id == collectionId
                            } != null

                            // else include only entries from the selected collection
                            else -> collectionId == selectedCollection.id
                        }
                    }

                    distinctLibraries.forEach { library ->
                        val random = Random(library.getId())
                        val item = StorageItem(
                            id = library.getId(),
                            title = library.getTitle(),
                            size = library.getDownloadSize(),
                            thumbnail = library.getThumbnail(),
                            entriesCount = library.getDownloadCount(),
                            color = Color(
                                random.nextInt(255),
                                random.nextInt(255),
                                random.nextInt(255),
                            ),
                        )

                        mutableState.update { state ->
                            when (state) {
                                is StorageScreenState.Success -> state.copy(
                                    items = (state.items + item).sortedByDescending { it.size },
                                )

                                else -> state
                            }
                        }
                    }
                },
            ).collectLatest {}
        }
    }

    fun setSelectedCollection(collection: Collection) {
        selectedCollection.update { collection }
    }

    abstract fun deleteEntry(id: Long)

    companion object {
        /**
         * A dummy collection used to display all entries irrespective of the collection.
         */
        private val AllCollection = Collection(
            id = -1L,
            name = "All",
            order = 0L,
            flags = 0L,
            hidden = false,
        )
    }
}
