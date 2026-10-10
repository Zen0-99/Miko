package tachiyomi.domain.achievement.model

enum class AchievementCategory {
    SHOWS,
    MANGA,
    BOTH,
    SECRET,
    NOVEL,
    ;

    companion object {
        /**
         * Parse boundary for stored/bundled category names. Legacy `"ANIME"`
         * values (achievements table rows, bundled JSON definitions) map to
         * [SHOWS]; everything else delegates to [valueOf].
         */
        fun fromString(value: String): AchievementCategory =
            if (value.equals("ANIME", ignoreCase = true)) SHOWS else valueOf(value)
    }
}
