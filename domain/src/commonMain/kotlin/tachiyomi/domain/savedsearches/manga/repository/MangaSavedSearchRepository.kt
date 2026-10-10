package tachiyomi.domain.savedsearches.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.savedsearches.model.SavedSearch

interface MangaSavedSearchRepository {

    fun getSavedSearchesBySourceId(sourceId: Long): Flow<List<SavedSearch>>

    suspend fun getSavedSearchById(id: Long): SavedSearch?

    suspend fun insertSavedSearch(savedSearch: SavedSearch)

    suspend fun deleteSavedSearch(id: Long)

    suspend fun deleteAllSavedSearchesBySourceId(sourceId: Long)
}
