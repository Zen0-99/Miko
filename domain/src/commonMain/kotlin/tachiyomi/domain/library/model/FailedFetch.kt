package tachiyomi.domain.library.model

/**
 * A failed library fetch entry, persisted until the user clears it, migrates it,
 * or swipes it away in the Fetching tab.
 */
data class FailedFetch(
    val id: Long,
    val entryId: Long,
    val entryKind: EntryKind,
    val title: String,
    val coverUrl: String?,
    val sourceId: Long,
    val sourceName: String,
    val reason: String,
    val timestamp: Long,
)
