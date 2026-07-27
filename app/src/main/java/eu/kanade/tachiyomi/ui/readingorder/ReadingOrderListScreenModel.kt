package eu.kanade.tachiyomi.ui.readingorder

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.readingorder.interactor.CreateReadingOrder
import tachiyomi.domain.readingorder.interactor.DeleteReadingOrder
import tachiyomi.domain.readingorder.interactor.ExportReadingOrder
import tachiyomi.domain.readingorder.interactor.GetReadingOrders
import tachiyomi.domain.readingorder.interactor.ImportReadingOrder
import tachiyomi.domain.readingorder.model.ReadingOrder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReadingOrderListScreenModel(
    private val getReadingOrders: GetReadingOrders = Injekt.get(),
    private val createReadingOrder: CreateReadingOrder = Injekt.get(),
    private val deleteReadingOrder: DeleteReadingOrder = Injekt.get(),
    private val exportReadingOrder: ExportReadingOrder = Injekt.get(),
    private val importReadingOrder: ImportReadingOrder = Injekt.get(),
) : StateScreenModel<ReadingOrderListScreenModel.State>(State()) {

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val readingOrders: List<ReadingOrder> = emptyList(),
        val dialog: Dialog? = null,
        val pendingExportOrderId: Long? = null,
        val resultMessage: String? = null,
        val unmatchedTitles: List<String>? = null,
    )

    sealed interface Dialog {
        data object Create : Dialog
        data class DeleteConfirm(val order: ReadingOrder) : Dialog
    }

    init {
        screenModelScope.launchIO {
            val orders = getReadingOrders.await()
            mutableState.update { it.copy(isLoading = false, readingOrders = orders) }
        }
    }

    fun showCreateDialog() {
        mutableState.update { it.copy(dialog = Dialog.Create) }
    }

    fun showDeleteDialog(order: ReadingOrder) {
        mutableState.update { it.copy(dialog = Dialog.DeleteConfirm(order)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun createOrder(name: String, description: String?) {
        screenModelScope.launchIO {
            createReadingOrder.await(name, description)
            refresh()
            closeDialog()
        }
    }

    fun deleteOrder(id: Long) {
        screenModelScope.launchIO {
            deleteReadingOrder.await(id)
            refresh()
            closeDialog()
        }
    }

    fun setPendingExport(orderId: Long) {
        mutableState.update { it.copy(pendingExportOrderId = orderId) }
    }

    fun exportReadingOrder(context: Context, uri: Uri, orderId: Long) {
        screenModelScope.launchIO {
            try {
                val count = context.contentResolver.openOutputStream(uri)?.use { stream ->
                    exportReadingOrder.await(orderId, stream)
                } ?: error("Could not open output stream")

                mutableState.update {
                    it.copy(
                        pendingExportOrderId = null,
                        resultMessage = "Exported $count entries",
                    )
                }
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(
                        pendingExportOrderId = null,
                        resultMessage = "Export failed: ${e.message}",
                    )
                }
            }
        }
    }

    fun importReadingOrder(context: Context, uri: Uri) {
        screenModelScope.launchIO {
            try {
                val result = context.contentResolver.openInputStream(uri)?.use { stream ->
                    importReadingOrder.await(stream)
                } ?: error("Could not open input stream")

                val message = "Imported \"${result.orderName}\" with ${result.matchedNodes} entries matched, " +
                    "${result.unmatchedNodes} unmatched, ${result.edgesImported} edges, ${result.progressImported} progress entries"
                mutableState.update {
                    it.copy(
                        resultMessage = message,
                        unmatchedTitles = result.unmatchedTitles.ifEmpty { null },
                    )
                }
                refresh()
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

    private suspend fun refresh() {
        val orders = getReadingOrders.await()
        mutableState.update { it.copy(readingOrders = orders) }
    }
}
