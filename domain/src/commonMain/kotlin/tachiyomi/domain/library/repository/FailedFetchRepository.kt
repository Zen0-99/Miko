package tachiyomi.domain.library.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.library.model.FailedFetch

interface FailedFetchRepository {

    suspend fun getAll(): List<FailedFetch>

    fun subscribeAll(): Flow<List<FailedFetch>>

    suspend fun insert(failedFetch: FailedFetch): Long

    suspend fun insertAll(failedFetches: List<FailedFetch>): List<Long>

    suspend fun deleteById(id: Long)

    suspend fun deleteByEntryId(entryId: Long, entryKind: String)

    suspend fun deleteByReason(reason: String)

    suspend fun clearAll()

    suspend fun count(): Long
}
