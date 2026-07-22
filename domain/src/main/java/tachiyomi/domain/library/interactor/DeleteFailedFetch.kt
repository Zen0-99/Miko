package tachiyomi.domain.library.interactor

import tachiyomi.domain.library.model.EntryKind
import tachiyomi.domain.library.repository.FailedFetchRepository

class DeleteFailedFetch(
    private val repository: FailedFetchRepository,
) {
    suspend fun awaitById(id: Long) = repository.deleteById(id)
    suspend fun awaitByEntry(entryId: Long, kind: EntryKind) = repository.deleteByEntryId(entryId, kind.name)
    suspend fun awaitByReason(reason: String) = repository.deleteByReason(reason)
}
