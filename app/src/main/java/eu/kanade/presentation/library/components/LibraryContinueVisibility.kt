package eu.kanade.presentation.library.components

/**
 * Determines whether the "continue viewing" action button should be shown
 * for a library item.
 *
 * @param hasContinueAction whether a continue-reading callback is available.
 * @param remainingCount the number of unread items remaining.
 * @return true if the button should be visible, false otherwise.
 */
internal fun shouldShowContinueViewingAction(
    hasContinueAction: Boolean,
    remainingCount: Long,
): Boolean {
    return hasContinueAction && remainingCount > 0L
}
