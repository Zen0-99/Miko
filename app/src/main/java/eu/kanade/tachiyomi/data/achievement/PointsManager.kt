package eu.kanade.tachiyomi.data.achievement

import tachiyomi.domain.achievement.model.UserProfile

/**
 * Manages XP, levels, and points for the achievements system.
 *
 * Level progression uses an increasing XP curve:
 * - Level 1→2: 100 XP
 * - Level 2→3: 200 XP
 * - Level N→N+1: N * 100 XP
 */
class PointsManager {

    /**
     * Calculate the XP required to advance from [level] to [level + 1].
     */
    fun xpForLevel(level: Int): Int {
        return level * 100
    }

    /**
     * Add [xp] to the [profile] and return an updated profile with
     * potentially incremented level.
     */
    fun addXp(profile: UserProfile, xp: Int): UserProfile {
        var currentXp = profile.currentXp + xp
        var totalXp = profile.totalXp + xp
        var level = profile.level
        var xpToNext = profile.xpToNextLevel

        while (currentXp >= xpToNext) {
            currentXp -= xpToNext
            level++
            xpToNext = xpForLevel(level)
        }

        return profile.copy(
            level = level,
            currentXp = currentXp,
            xpToNextLevel = xpToNext,
            totalXp = totalXp,
            lastUpdated = System.currentTimeMillis(),
        )
    }

    /**
     * Get the level title for a given level.
     */
    fun getLevelTitle(level: Int): String {
        return when {
            level >= 50 -> "Legend"
            level >= 40 -> "Master"
            level >= 30 -> "Expert"
            level >= 20 -> "Veteran"
            level >= 10 -> "Adept"
            level >= 5 -> "Apprentice"
            else -> "Novice"
        }
    }
}
