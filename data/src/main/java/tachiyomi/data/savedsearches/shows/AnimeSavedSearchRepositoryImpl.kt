package tachiyomi.data.savedsearches.shows

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.savedsearches.shows.repository.AnimeSavedSearchRepository
import tachiyomi.domain.savedsearches.model.SavedSearch

class AnimeSavedSearchRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : AnimeSavedSearchRepository {

    override fun getSavedSearchesBySourceId(sourceId: Long): Flow<List<SavedSearch>> {
        return handler.subscribeToList {
            saved_searchesQueries.getSavedSearches(sourceId, ::mapSavedSearch)
        }
    }

    override suspend fun getSavedSearchById(id: Long): SavedSearch? {
        return handler.awaitOneOrNull {
            saved_searchesQueries.getSavedSearchById(id, ::mapSavedSearch)
        }
    }

    override suspend fun insertSavedSearch(savedSearch: SavedSearch) {
        handler.await {
            saved_searchesQueries.insertSavedSearch(
                sourceId = savedSearch.sourceId,
                name = savedSearch.name,
                query = savedSearch.query,
                filtersJson = savedSearch.filtersJson,
            )
        }
    }

    override suspend fun deleteSavedSearch(id: Long) {
        handler.await {
            saved_searchesQueries.deleteSavedSearch(id)
        }
    }

    override suspend fun deleteAllSavedSearchesBySourceId(sourceId: Long) {
        handler.await {
            saved_searchesQueries.deleteAllSavedSearchesBySource(sourceId)
        }
    }

    private fun mapSavedSearch(
        id: Long,
        sourceId: Long,
        name: String,
        query: String,
        filtersJson: String,
    ): SavedSearch {
        return SavedSearch(
            id = id,
            sourceId = sourceId,
            name = name,
            query = query,
            filtersJson = filtersJson,
        )
    }
}
