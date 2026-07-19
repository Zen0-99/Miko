package tachiyomi.data.achievement

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.achievement.model.Achievement

/**
 * Manages unlockable content that is unlocked via achievements.
 * Handles themes, badges, display preferences, and other unlockables.
 */
class UnlockableManager(
    private val preferences: SharedPreferences,
    private val userProfileManager: UserProfileManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val PREFIX = "unlocked_"
        private val EXCLUSIVE_THEME_IDS = setOf("ONYX_GOLD", "SAKURA_NOIR", "NEBULA_TIDE", "EVENT_HORIZON")
    }

    /**
     * Check if an unlockable is unlocked
     */
    fun isUnlockableUnlocked(unlockableId: String): Boolean {
        return preferences.getBoolean("$PREFIX$unlockableId", false)
    }

    /**
     * Mark an unlockable as unlocked
     */
    fun setUnlockableUnlocked(unlockableId: String) {
        preferences.edit(commit = true) {
            putBoolean("$PREFIX$unlockableId", true)
        }
        logcat(LogPriority.INFO) { "Unlockable unlocked: $unlockableId" }
    }

    /**
     * Get all unlocked unlockables
     */
    fun getUnlockedUnlockables(): Set<String> {
        val allKeys = preferences.all.keys
        return allKeys
            .filter { it.startsWith(PREFIX) }
            .filter { preferences.getBoolean(it, false) }
            .map { it.removePrefix(PREFIX) }
            .toSet()
    }

    /**
     * Observe all unlocked unlockables as a reactive Flow.
     * Emits a new set whenever any unlockable pref changes.
     */
    fun observeUnlockedUnlockables(): Flow<Set<String>> = callbackFlow {
        fun snapshot(): Set<String> {
            val allKeys = preferences.all.keys
            return allKeys
                .filter { it.startsWith(PREFIX) }
                .filter { preferences.getBoolean(it, false) }
                .map { it.removePrefix(PREFIX) }
                .toSet()
        }
        // Emit current state immediately.
        trySend(snapshot())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith(PREFIX)) {
                trySend(snapshot())
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /**
     * Unlock rewards for an achievement
     * Called when an achievement is unlocked
     */
    suspend fun unlockAchievementRewards(achievement: Achievement) {
        // Unlock main unlockable if exists
        achievement.unlockableId?.let { unlockableId ->
            setUnlockableUnlocked(unlockableId)
            // Apply the unlockable effect
            applyUnlockable(unlockableId)
        }

        // Unlock all rewards in the rewards list
        achievement.rewards?.forEach { reward ->
            setUnlockableUnlocked(reward.id)
            applyUnlockable(reward.id)
        }
    }

    /**
     * Apply an unlockable effect
     * This handles themes, badges, display preferences, etc.
     */
    private suspend fun applyUnlockable(unlockableId: String) = withContext(Dispatchers.Default) {
        when {
            // Theme unlockables are also mirrored into UserProfile because the
            // theme picker reads profile themes in addition to Treasury prefs.
            unlockableId.startsWith("theme_") -> {
                val themeId = unlockableId.removePrefix("theme_")
                try {
                    userProfileManager.unlockTheme(themeId)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Failed to unlock theme $themeId: ${e.message}" }
                }
            }

            // Badges are also mirrored into UserProfile for profile screens.
            unlockableId.startsWith("badge_") -> {
                val badgeName = unlockableId.removePrefix("badge_")
                try {
                    userProfileManager.addBadge(badgeName)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Failed to add badge $badgeName: ${e.message}" }
                }
            }

            unlockableId.startsWith("aura_") -> {
                logcat(LogPriority.INFO) { "Aura unlocked: $unlockableId" }
            }

            unlockableId.startsWith("title_") -> {
                logcat(LogPriority.INFO) { "Profile title unlocked: $unlockableId" }
            }

            unlockableId.startsWith("display_") -> {
                logcat(LogPriority.INFO) { "Display preference unlocked: $unlockableId" }
            }

            unlockableId.startsWith("profile_") -> {
                logcat(LogPriority.INFO) { "Profile reward unlocked: $unlockableId" }
            }

            unlockableId.startsWith("avatar_") -> {
                logcat(LogPriority.INFO) { "Avatar reward unlocked: $unlockableId" }
            }

            unlockableId.startsWith("reader_") -> {
                logcat(LogPriority.INFO) { "Reader preset unlocked: $unlockableId" }
            }

            unlockableId.startsWith("home_") -> {
                logcat(LogPriority.INFO) { "Home preset unlocked: $unlockableId" }
            }

            unlockableId.startsWith("special_") -> {
                logcat(LogPriority.INFO) { "Special visual reward unlocked: $unlockableId" }
            }

            else -> {
                logcat(LogPriority.WARN) { "Unknown unlockable type: $unlockableId" }
            }
        }
    }

    /**
     * Check if a theme is available (unlocked)
     */
    fun isThemeAvailable(themeId: String): Boolean {
        val normalizedThemeId = themeId.removePrefix("theme_")
        val canonicalThemeId = normalizedThemeId.uppercase()
        val unlockableId = "theme_$normalizedThemeId"

        if (isDefaultUnlockable(unlockableId)) return true
        if (preferences.getBoolean("debug_bypass_treasury_locks", false)) return true

        return isUnlockableUnlocked(unlockableId) ||
            isUnlockableUnlocked("theme_$canonicalThemeId") ||
            isUnlockableUnlocked("theme_${canonicalThemeId.lowercase()}")
    }

    /**
     * Check if a badge is available (unlocked)
     */
    fun isBadgeAvailable(badgeId: String): Boolean {
        val unlockableId = if (badgeId.startsWith("badge_")) badgeId else "badge_$badgeId"
        if (isDefaultUnlockable(unlockableId)) return true
        if (preferences.getBoolean("debug_bypass_treasury_locks", false)) return true
        return isUnlockableUnlocked(unlockableId)
    }

    /**
     * Check if a display preference is available (unlocked)
     */
    fun isDisplayPreferenceAvailable(prefId: String): Boolean {
        val unlockableId = if (prefId.startsWith("display_")) prefId else "display_$prefId"
        if (isDefaultUnlockable(unlockableId)) return true
        if (preferences.getBoolean("debug_bypass_treasury_locks", false)) return true
        return isUnlockableUnlocked(unlockableId)
    }

    /**
     * Generic availability check for Treasury cosmetics. UI should prefer this
     * method for aura/profile/avatar/home/special unlockables so all reward
     * types use the same source of truth.
     */
    fun isUnlockableAvailable(unlockableId: String): Boolean {
        if (isDefaultUnlockable(unlockableId)) return true
        if (preferences.getBoolean("debug_bypass_treasury_locks", false)) return true
        return isUnlockableUnlocked(unlockableId)
    }

    private fun isDefaultUnlockable(unlockableId: String): Boolean {
        return unlockableId.startsWith("default_") ||
            unlockableId.startsWith("theme_default_") ||
            unlockableId.startsWith("badge_default_") ||
            unlockableId.startsWith("display_default_")
    }

    /**
     * Get unlockable display name (localized) as a String.
     * Note: Tadami used Moko Resources (MR.strings.*) for localized names.
     * The data module cannot depend on Moko Resources, so hardcoded English
     * strings are returned here. UI layers may override with their own
     * localization if needed.
     */
    fun getUnlockableNameRes(unlockableId: String): String? {
        return when (unlockableId) {
            // Themes
            "theme_achievement_gold" -> "Achievement Gold"
            "theme_achievement_sapphire" -> "Achievement Sapphire"
            "theme_master" -> "Master Theme"
            "theme_ONYX_GOLD" -> "Onyx Gold"
            "theme_SAKURA_NOIR" -> "Sakura Noir"
            "theme_NEBULA_TIDE" -> "Nebula Tide"
            "theme_EVENT_HORIZON" -> "Event Horizon"
            "theme_void_red" -> "Void Red"

            // Badges
            "badge_achievement_master" -> "Achievement Master Badge"
            "badge_week_warrior" -> "Week Warrior Badge"

            // Display preferences
            "display_grid_large" -> "Large Grid"
            "display_list_compact" -> "Compact List"
            "display_grid_extra_large" -> "Extra Large Grid"

            // Auras
            "aura_harem" -> "Harem Aura"
            "aura_level_up" -> "Level Up Aura"
            "aura_matrix" -> "Matrix Aura"
            "aura_trinity_orbit" -> "Trinity Orbit Aura"
            "aura_deep_focus" -> "Deep Focus Aura"
            "aura_shadow_monarch" -> "Shadow Monarch Aura"
            "aura_ascendant_gold" -> "Ascendant Gold Aura"
            "aura_void_broadcast_red" -> "Void Broadcast Red Aura"

            // Profile presets
            "profile_nickname_effect_aurora_crown" -> "Aurora Crown Nickname Effect"
            "profile_nickname_effect_glitch_rune" -> "Glitch Rune Nickname Effect"
            "profile_nickname_effect_cipher" -> "Cipher Nickname Effect"
            "profile_nickname_effect_glitch_rune_red" -> "Glitch Rune Red Nickname Effect"

            // Avatar presets
            "avatar_frame_neon" -> "Neon Avatar Frame"
            "avatar_frame_hologram" -> "Hologram Avatar Frame"
            "avatar_frame_prismatic" -> "Prismatic Avatar Frame"
            "avatar_frame_glitch_red" -> "Glitch Red Avatar Frame"

            // Home presets
            "home_badge_orbit" -> "Orbit Home Badge"
            "home_badge_crown" -> "Crown Home Badge"
            "home_badge_shuriken" -> "Shuriken Home Badge"

            // Special visual rewards
            "special_background_petal_storm" -> "Petal Storm Background"
            "special_background_neon_orbit" -> "Neon Orbit Background"
            "special_background_event_horizon_library" -> "Event Horizon Library Background"
            "special_background_void_weeping_red" -> "Void Weeping Red Background"
            "special_tab_glow" -> "Tab Glow"
            else -> null
        }
    }

    /**
     * Get unlockable display name fallback string.
     */
    fun getUnlockableName(unlockableId: String): String {
        return unlockableId
            .removePrefix("theme_")
            .removePrefix("aura_")
            .removePrefix("title_")
            .removePrefix("special_background_")
            .removePrefix("special_")
            .removePrefix("profile_nickname_effect_")
            .removePrefix("avatar_frame_")
            .removePrefix("home_badge_")
            .replace("_", " ")
            .capitalize()
    }

    /**
     * Get unlockable type (theme, badge, display, etc.)
     */
    fun getUnlockableType(unlockableId: String): UnlockableType {
        return when {
            unlockableId.startsWith("theme_") -> UnlockableType.THEME
            unlockableId.startsWith("aura_") -> UnlockableType.AURA
            unlockableId.startsWith("title_") -> UnlockableType.TITLE
            unlockableId.startsWith("badge_") -> UnlockableType.BADGE
            unlockableId.startsWith("display_") -> UnlockableType.DISPLAY
            unlockableId.startsWith("profile_") -> UnlockableType.PROFILE
            unlockableId.startsWith("avatar_") -> UnlockableType.AVATAR
            unlockableId.startsWith("reader_") -> UnlockableType.READER
            unlockableId.startsWith("home_") -> UnlockableType.HOME
            unlockableId.startsWith("special_") -> UnlockableType.SPECIAL
            else -> UnlockableType.UNKNOWN
        }
    }

    /**
     * Reset all unlockables (for testing/debugging)
     */
    fun resetAllUnlockables() {
        val allKeys = preferences.all.keys.filter { it.startsWith(PREFIX) }
        preferences.edit {
            allKeys.forEach { remove(it) }
        }
        logcat(LogPriority.INFO) { "All unlockables reset" }
    }

    /**
     * Rebuild unlockable prefs from the current set of unlocked achievements.
     * Clears all existing prefs first, then re-derives them from DB state.
     * Use after any operation that might cause DB ↔ prefs desync.
     */
    suspend fun recomputeUnlockablesFromUnlockedAchievements(
        unlockedAchievements: List<Achievement>,
    ) {
        resetAllUnlockables()
        unlockedAchievements.forEach { achievement ->
            unlockAchievementRewards(achievement)
        }
        logcat(LogPriority.INFO) {
            "Recomputed unlockables from ${unlockedAchievements.size} unlocked achievement(s)"
        }
    }

    /**
     * Lock the unlockables derived from a single achievement.
     * Used when sanitization invalidates an unlock that had no real history.
     */
    fun lockUnlockablesForAchievement(achievement: Achievement) {
        achievement.unlockableId?.let { unlockableId ->
            preferences.edit { remove("$PREFIX$unlockableId") }
            removeUnlockableFromProfile(unlockableId)
        }
        achievement.rewards?.forEach { reward ->
            preferences.edit { remove("$PREFIX${reward.id}") }
            removeUnlockableFromProfile(reward.id)
        }
        logcat(LogPriority.INFO) {
            "Locked unlockables for achievement: ${achievement.id}"
        }
    }

    private fun removeUnlockableFromProfile(unlockableId: String) {
        scope.launch {
            try {
                when {
                    unlockableId.startsWith("theme_") -> {
                        userProfileManager.removeTheme(unlockableId.removePrefix("theme_"))
                    }
                    unlockableId.startsWith("badge_") -> {
                        userProfileManager.removeBadge(unlockableId.removePrefix("badge_"))
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Failed to remove unlockable $unlockableId from profile: ${e.message}"
                }
            }
        }
    }
}

/**
 * Types of unlockables
 */
enum class UnlockableType {
    THEME,
    AURA,
    TITLE,
    BADGE,
    DISPLAY,
    PROFILE,
    AVATAR,
    READER,
    HOME,
    SPECIAL,
    UNKNOWN,
}

private inline fun SharedPreferences.edit(
    commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit,
) {
    val editor = edit()
    action(editor)
    if (commit) {
        editor.commit()
    } else {
        editor.apply()
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
