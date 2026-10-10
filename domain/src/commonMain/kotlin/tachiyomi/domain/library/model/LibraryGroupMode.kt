package tachiyomi.domain.library.model

/**
 * Group modes for library collections.
 * Matches the old Miko-Yokai-Old LibraryGroup constants.
 */
object LibraryGroupMode {
    const val BY_DEFAULT = 0
    const val BY_TAG = 1
    const val BY_SOURCE = 2
    const val BY_STATUS = 3
    const val BY_TRACK_STATUS = 4
    const val UNGROUPED = 5
    const val BY_AUTHOR = 6
    const val BY_LANGUAGE = 7
}
