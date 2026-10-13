package miko.ui.achievements

import tachiyomi.domain.achievement.model.Achievement

/**
 * Global manager for showing achievement unlock banners — verbatim port of
 * Android's `AchievementBannerManager`. Supports deferred notifications when
 * the user is in the reader/player.
 */
object AchievementBannerManager {
    private var onShowCallback: ((Achievement) -> Unit)? = null
    private var onShowGroupCallback: ((List<Achievement>) -> Unit)? = null
    private val pendingAchievements = mutableListOf<Achievement>()
    private var isInReaderOrPlayer = false

    fun setOnShowCallback(callback: (Achievement) -> Unit) {
        onShowCallback = callback
    }

    fun setOnShowGroupCallback(callback: (List<Achievement>) -> Unit) {
        onShowGroupCallback = callback
    }

    fun registerOnShowCallback(callback: (Achievement) -> Unit): () -> Unit {
        onShowCallback = callback
        return {
            if (onShowCallback === callback) {
                onShowCallback = null
            }
        }
    }

    fun registerOnShowGroupCallback(callback: (List<Achievement>) -> Unit): () -> Unit {
        onShowGroupCallback = callback
        return {
            if (onShowGroupCallback === callback) {
                onShowGroupCallback = null
            }
        }
    }

    /**
     * Call when entering reader or player to defer notifications
     */
    fun setInReaderOrPlayer(value: Boolean) {
        isInReaderOrPlayer = value
        if (!value && pendingAchievements.isNotEmpty()) {
            // User exited reader/player, show grouped notification
            showPendingAchievements()
        }
    }

    fun showAchievement(achievement: Achievement) {
        if (isInReaderOrPlayer) {
            // Defer notification until user exits reader/player
            pendingAchievements.add(achievement)
        } else {
            // Show immediately
            onShowCallback?.invoke(achievement)
        }
    }

    /**
     * Show all pending achievements as a group
     */
    private fun showPendingAchievements() {
        if (pendingAchievements.isEmpty()) return

        if (pendingAchievements.size == 1) {
            // Single achievement - show normal banner
            onShowCallback?.invoke(pendingAchievements.first())
        } else {
            // Multiple achievements - show group notification
            onShowGroupCallback?.invoke(pendingAchievements.toList())
        }
        pendingAchievements.clear()
    }

    /**
     * Get current pending count for UI display
     */
    fun getPendingCount(): Int = pendingAchievements.size

    /**
     * Get list of pending achievements and clear the queue
     */
    fun getPendingAchievements(): List<Achievement> {
        val list = pendingAchievements.toList()
        pendingAchievements.clear()
        return list
    }

    fun clear() {
        onShowCallback = null
        onShowGroupCallback = null
        pendingAchievements.clear()
    }
}
