package miko.ui.achievements

import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementProgress

/**
 * Verbatim port of Android's `AchievementRevealHelper` — hidden-achievement
 * name/description reveal rules. `String.format(prefix, text)` becomes a
 * single `%s` substitution (the moko prefix strings each take one arg).
 */
object AchievementRevealHelper {

    fun getDisplayName(achievement: Achievement, progress: AchievementProgress?): String {
        val isUnlocked = progress?.isUnlocked == true
        if (!achievement.isHidden || isUnlocked) return achievement.title

        val threshold = achievement.threshold ?: return "???"
        val currentProgress = progress?.progress ?: return "???"
        if (threshold <= 1) return "???"

        val percent = (currentProgress * 100) / threshold
        return when {
            percent >= 50 -> achievement.title
            percent >= 25 -> obfuscateTitle(achievement.title, revealLettersCount = 2)
            percent >= 10 -> obfuscateTitle(achievement.title, revealLettersCount = 1)
            else -> "???"
        }
    }

    private fun obfuscateTitle(title: String, revealLettersCount: Int): String {
        return title.split(" ").joinToString(" ") { word ->
            var lettersCount = 0
            val sb = StringBuilder()
            for (char in word) {
                if (char.isLetterOrDigit()) {
                    if (lettersCount < revealLettersCount) {
                        sb.append(char)
                        lettersCount++
                    } else {
                        sb.append('•')
                    }
                } else {
                    sb.append(char)
                }
            }
            sb.toString()
        }
    }

    fun getDisplayDescription(
        achievement: Achievement,
        progress: AchievementProgress?,
        vaguePrefix: String,
        directPrefix: String,
        obviousPrefix: String,
        cluePrefix: String,
    ): String? {
        val isUnlocked = progress?.isUnlocked == true
        if (!achievement.isHidden || isUnlocked) return achievement.description

        val threshold = achievement.threshold ?: return null
        val currentProgress = progress?.progress ?: return null

        val hintText = achievement.hint
        val vagueText = achievement.hintVague ?: hintText
        val directText = achievement.hintDirect ?: hintText
        val obviousText = achievement.hintObvious ?: hintText

        if (threshold <= 1) {
            return if (!vagueText.isNullOrBlank()) {
                formatOneArg(vaguePrefix, vagueText)
            } else {
                null
            }
        }

        val percent = (currentProgress * 100) / threshold

        return when {
            percent >= 75 -> {
                val partialDesc = getPartialDescription(achievement.description.orEmpty())
                val clue = formatOneArg(cluePrefix, partialDesc)
                if (!obviousText.isNullOrBlank()) {
                    val target = formatOneArg(obviousPrefix, obviousText)
                    "$target\n\n$clue"
                } else {
                    clue
                }
            }
            percent >= 25 -> {
                if (!directText.isNullOrBlank()) {
                    formatOneArg(directPrefix, directText)
                } else {
                    null
                }
            }
            percent >= 1 -> {
                if (!vagueText.isNullOrBlank()) {
                    formatOneArg(vaguePrefix, vagueText)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun getPartialDescription(description: String): String {
        val words = description.split(" ")
        return if (words.size <= 3) {
            description
        } else {
            words.take(3).joinToString(" ") + "..."
        }
    }

    /** `String.format("%s", arg)` — one-argument substitution. */
    private fun formatOneArg(template: String, arg: String): String =
        template.replace("%s", arg)
}
