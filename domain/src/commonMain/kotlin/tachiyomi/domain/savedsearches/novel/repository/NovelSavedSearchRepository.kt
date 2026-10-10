package tachiyomi.domain.savedsearches.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.savedsearches.model.SavedSearch

interface NovelSavedSearchRepository {

    fun getSavedSearchesBySourceId(sourceId: Long): Flow<List<SavedSearch>>

    suspend fun getSavedSearchById(id: Long): SavedSearch?

    suspend fun insertSavedSearch(savedSearch: SavedSearch)

    suspend fun deleteSavedSearch(id: Long)

    suspend fun deleteAllSavedSearchesBySourceId(sourceId: Long)
}
