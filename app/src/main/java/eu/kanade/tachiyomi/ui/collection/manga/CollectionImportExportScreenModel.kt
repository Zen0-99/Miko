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
        val selectedCollectionIds: Set<Long> = emptySet(),
        val includeReadingOrders: Boolean = false,
        val crossCollectionWarnings: List<ExportMangaCollection.CrossCollectionWarning>? = null,
        val warningShown: Boolean = false,
        val pendingExport: Boolean = false,
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

    fun toggleCollection(collectionId: Long) {
        mutableState.update {
            val newSelection = if (collectionId in it.selectedCollectionIds) {
                it.selectedCollectionIds - collectionId
            } else {
                it.selectedCollectionIds + collectionId
            }
            it.copy(
                selectedCollectionIds = newSelection,
                crossCollectionWarnings = null,
                warningShown = false,
            )
        }
    }

    fun setIncludeReadingOrders(include: Boolean) {
        mutableState.update {
            it.copy(
                includeReadingOrders = include,
                crossCollectionWarnings = null,
                warningShown = false,
            )
        }
    }

    /**
     * Called when the user taps export. Checks for cross-collection warnings
     * first; if there are warnings, the UI shows a dialog. Otherwise proceeds
     * directly to export.
     */
    fun startExport() {
        val selectedIds = state.value.selectedCollectionIds
        if (selectedIds.isEmpty()) return

        screenModelScope.launchIO {
            if (state.value.includeReadingOrders) {
                val warnings = exportMangaCollection.checkCrossCollection(selectedIds)
                if (warnings.isNotEmpty()) {
                    mutableState.update {
                        it.copy(crossCollectionWarnings = warnings, warningShown = false)
                    }
                    return@launchIO
                }
            }
            // No warnings — proceed to export
            mutableState.update { it.copy(pendingExport = true) }
        }
    }

    fun dismissWarning() {
        mutableState.update { it.copy(warningShown = true) }
    }

    fun clearPendingExport() {
        mutableState.update { it.copy(pendingExport = false) }
    }

    fun exportCollection(context: Context, uri: Uri) {
        val selectedIds = state.value.selectedCollectionIds
        val includeRO = state.value.includeReadingOrders
        if (selectedIds.isEmpty()) return

        screenModelScope.launchIO {
            try {
                val count = context.contentResolver.openOutputStream(uri)?.use { stream ->
                    exportMangaCollection.await(selectedIds, includeRO, stream)
                } ?: error("Could not open output stream")

                mutableState.update {
                    it.copy(
                        pendingExport = false,
                        crossCollectionWarnings = null,
                        warningShown = false,
                        resultMessage = "Exported $count manga",
                    )
                }
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(
                        pendingExport = false,
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

                val message = buildString {
                    append("Imported ${result.collectionsCreated} collection(s): ${result.collectionNames.joinToString()}")
                    if (result.readingOrdersCreated > 0) {
                        append(", ${result.readingOrdersCreated} reading order(s)")
                    }
                    append(", ${result.mangaMatched} manga matched, ${result.mangaInserted} inserted")
                    if (result.unmatchedTitles.isNotEmpty()) {
                        append(", ${result.unmatchedTitles.size} unmatched")
                    }
                }
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
