package eu.kanade.presentation.more.storage

import androidx.compose.runtime.Immutable
import tachiyomi.domain.collection.model.Collection

sealed class StorageScreenState {
    @Immutable
    object Loading : StorageScreenState()

    @Immutable
    data class Success(
        val selectedCollection: Collection,
        val items: List<StorageItem>,
        val collections: List<Collection>,
    ) : StorageScreenState()
}
