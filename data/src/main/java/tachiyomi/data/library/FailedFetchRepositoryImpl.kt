package tachiyomi.data.library

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.library.model.EntryKind
import tachiyomi.domain.library.model.FailedFetch
import tachiyomi.domain.library.repository.FailedFetchRepository

class FailedFetchRepositoryImpl(
    private val handler: MangaDatabaseHandler,
) : FailedFetchRepository {

    override suspend fun getAll(): List<FailedFetch> {
        return handler.awaitList { failed_fetchesQueries.selectAll(::mapFailedFetch) }
    }

    override fun subscribeAll(): Flow<List<FailedFetch>> {
        return handler.subscribeToList { failed_fetchesQueries.selectAllAsFlow(::mapFailedFetch) }
    }

    override suspend fun insert(failedFetch: FailedFetch): Long {
        return handler.await(inTransaction = true) {
            failed_fetchesQueries.insert(
                entryId = failedFetch.entryId,
                entryKind = failedFetch.entryKind.name,
                title = failedFetch.title,
                coverUrl = failedFetch.coverUrl,
                sourceId = failedFetch.sourceId,
                sourceName = failedFetch.sourceName,
                reason = failedFetch.reason,
                timestamp = failedFetch.timestamp,
            )
            failed_fetchesQueries.selectLastInsertedRowId().executeAsOne()
        }
    }

    override suspend fun insertAll(failedFetches: List<FailedFetch>): List<Long> {
        return handler.await(inTransaction = true) {
            failedFetches.map { ff ->
                failed_fetchesQueries.insert(
                    entryId = ff.entryId,
                    entryKind = ff.entryKind.name,
                    title = ff.title,
                    coverUrl = ff.coverUrl,
                    sourceId = ff.sourceId,
                    sourceName = ff.sourceName,
                    reason = ff.reason,
                    timestamp = ff.timestamp,
                )
                failed_fetchesQueries.selectLastInsertedRowId().executeAsOne()
            }
        }
    }

    override suspend fun deleteById(id: Long) {
        handler.await { failed_fetchesQueries.deleteById(id) }
    }

    override suspend fun deleteByEntryId(entryId: Long, entryKind: String) {
        handler.await { failed_fetchesQueries.deleteByEntryId(entryId, entryKind) }
    }

    override suspend fun deleteByReason(reason: String) {
        handler.await { failed_fetchesQueries.deleteByReason(reason) }
    }

    override suspend fun clearAll() {
        handler.await { failed_fetchesQueries.clearAll() }
    }

    override suspend fun count(): Long {
        return handler.awaitOne { failed_fetchesQueries.count() }
    }

    private fun mapFailedFetch(
        id: Long,
        entryId: Long,
        entryKind: String,
        title: String,
        coverUrl: String?,
        sourceId: Long,
        sourceName: String,
        reason: String,
        timestamp: Long,
    ): FailedFetch = FailedFetch(
        id = id,
        entryId = entryId,
        entryKind = EntryKind.valueOf(entryKind),
        title = title,
        coverUrl = coverUrl,
        sourceId = sourceId,
        sourceName = sourceName,
        reason = reason,
        timestamp = timestamp,
    )
}

