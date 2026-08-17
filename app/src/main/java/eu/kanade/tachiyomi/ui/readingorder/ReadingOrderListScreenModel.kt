package eu.kanade.tachiyomi.ui.readingorder

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.readingorder.interactor.CreateReadingOrder
import tachiyomi.domain.readingorder.interactor.DeleteReadingOrder
import tachiyomi.domain.readingorder.interactor.GetReadingOrders
import tachiyomi.domain.readingorder.model.ReadingOrder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReadingOrderListScreenModel(
    private val entryKind: String = "manga",
    private val getReadingOrders: GetReadingOrders = Injekt.get(),
    private val createReadingOrder: CreateReadingOrder = Injekt.get(),
    private val deleteReadingOrder: DeleteReadingOrder = Injekt.get(),
) : StateScreenModel<ReadingOrderListScreenModel.State>(State()) {

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val readingOrders: List<ReadingOrder> = emptyList(),
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object Create : Dialog
        data class DeleteConfirm(val order: ReadingOrder) : Dialog
    }

    init {
        screenModelScope.launchIO {
            val orders = getReadingOrders.await(entryKind)
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
            createReadingOrder.await(name, description, entryKind)
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

    private suspend fun refresh() {
        val orders = getReadingOrders.await(entryKind)
        mutableState.update { it.copy(readingOrders = orders) }
    }
}
