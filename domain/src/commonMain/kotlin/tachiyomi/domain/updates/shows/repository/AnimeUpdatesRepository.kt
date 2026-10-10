package tachiyomi.domain.updates.shows.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.shows.model.AnimeUpdatesWithRelations

interface AnimeUpdatesRepository {

    suspend fun awaitWithSeen(seen: Boolean, after: Long, limit: Long): List<AnimeUpdatesWithRelations>

    fun subscribeAllAnimeUpdates(after: Long, limit: Long): Flow<List<AnimeUpdatesWithRelations>>

    fun subscribeWithSeen(seen: Boolean, after: Long, limit: Long): Flow<List<AnimeUpdatesWithRelations>>
}
