package tachiyomi.domain.savedsearches.shows.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.savedsearches.model.SavedSearch

interface AnimeSavedSearchRepository {

    fun getSavedSearchesBySourceId(sourceId: Long): Flow<List<SavedSearch>>

    suspend fun getSavedSearchById(id: Long): SavedSearch?

    suspend fun insertSavedSearch(savedSearch: SavedSearch)

    suspend fun deleteSavedSearch(id: Long)

    suspend fun deleteAllSavedSearchesBySourceId(sourceId: Long)
}
