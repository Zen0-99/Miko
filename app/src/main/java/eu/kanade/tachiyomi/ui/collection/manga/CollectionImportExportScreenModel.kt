package eu.kanade.tachiyomi.ui.collection.manga

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.collection.manga.interactor.ExportMangaCollection
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.collection.manga.interactor.ImportMangaCollection
import tachiyomi.domain.collection.model.Collection
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CollectionImportExportScreenModel(
    private val getMangaCollections: GetMangaCollections = Injekt.get(),
    private val exportMangaCollection: ExportMangaCollection = Injekt.get(),
    private val importMangaCollection: ImportMangaCollection = Injekt.get(),
) : StateScreenModel<CollectionImportExportScreenModel.State>(State()) {

    @Immutable
    data class State(
        val collections: List<Collection> = emptyList(),
        val pendingExportCollectionId: Long? = null,
        val resultMessage: String? = null,
        val unmatchedTitles: List<String>? = null,
    )

    init {
        screenModelScope.launchIO {
            val collections = getMangaCollections.await()
                .filterNot { it.isSystemCollection }
            mutableState.update { it.copy(collections = collections) }
        }
    }

    fun setPendingExport(collectionId: Long) {
        mutableState.update { it.copy(pendingExportCollectionId = collectionId) }
    }

    fun exportCollection(context: Context, uri: Uri, collectionId: Long) {
        screenModelScope.launchIO {
            try {
                val count = context.contentResolver.openOutputStream(uri)?.use { stream ->
                    exportMangaCollection.await(collectionId, stream)
                } ?: error("Could not open output stream")

                mutableState.update {
                    it.copy(
                        pendingExportCollectionId = null,
                        resultMessage = "Exported $count manga",
                    )
                }
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(
                        pendingExportCollectionId = null,
                        resultMessage = "Export failed: ${e.message}",
                    )
                }
            }
        }
    }

    fun importCollection(context: Context, uri: Uri) {
        screenModelScope.launchIO {
            try {
                val result = context.contentResolver.openInputStream(uri)?.use { stream ->
                    importMangaCollection.await(stream)
                } ?: error("Could not open input stream")

                val message = "Imported \"${result.collectionName}\" with ${result.matchedManga} manga matched, ${result.unmatchedManga} unmatched"
                mutableState.update {
                    it.copy(
                        resultMessage = message,
                        unmatchedTitles = result.unmatchedTitles.ifEmpty { null },
                    )
                }
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(resultMessage = "Import failed: ${e.message}")
                }
            }
        }
    }

    fun clearResult() {
        mutableState.update {
            it.copy(resultMessage = null, unmatchedTitles = null)
        }
    }
}
