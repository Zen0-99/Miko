package miko.ui.library

import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD

/**
 * Android's LibraryUpdateJob item gate — the five skip arms applied to each
 * library entry before a refresh fetch: update strategy, then the four
 * `autoUpdateItemRestrictions` switches. Shared by all three carriers so a
 * manual update behaves like Android's scheduled job.
 */
fun passesUpdateRestrictions(
    restrictions: Set<String>,
    alwaysUpdate: Boolean,
    completed: Boolean,
    unviewedCount: Long,
    totalItems: Long,
    hasStarted: Boolean,
    nextUpdate: Long,
    fetchWindowUpperBound: Long,
): Boolean = when {
    !alwaysUpdate -> false
    ENTRY_NON_COMPLETED in restrictions && completed -> false
    ENTRY_HAS_UNVIEWED in restrictions && unviewedCount != 0L -> false
    ENTRY_NON_VIEWED in restrictions && totalItems > 0L && !hasStarted -> false
    ENTRY_OUTSIDE_RELEASE_PERIOD in restrictions &&
        nextUpdate > fetchWindowUpperBound -> false
    else -> true
}
