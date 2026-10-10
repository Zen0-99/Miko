package tachiyomi.domain.extension.novel

private val bidiMarks = Regex("[\\u200E\\u200F\\u202A-\\u202E]")
private val tagPattern = Regex("^[a-zA-Z]{2,3}([_-][a-zA-Z0-9]{2,8})*$")

fun normalizeNovelLang(raw: String?): String {
    if (raw == null) return ""
    val cleaned = raw.replace(bidiMarks, "").trim()
    if (cleaned.isEmpty()) return ""

    val lower = cleaned.lowercase()
    if (lower == "multi" || lower == "all") return "all"

    val candidates = cleaned.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    for (candidate in candidates) {
        if (tagPattern.matches(candidate)) {
            return candidate.replace('_', '-').lowercase()
        }

        val match = findLocaleLanguage(candidate)
        if (match != null) return match
    }

    return cleaned
}

private fun findLocaleLanguage(value: String): String? {
    val normalizedValue = normalizeTokenForLang(value)
    if (normalizedValue.isEmpty()) return null

    return platformLanguageForDisplayName(normalizedValue)
}

internal fun normalizeTokenForLang(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return value
        .replace(bidiMarks, "")
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")
}

/**
 * Maps a normalized language display name (native or English) to its ISO
 * language code, or null when unrecognized. Platform lookup tables differ:
 * JVM `Locale.getAvailableLocales`, iOS `NSLocale.isoLanguageCodes`.
 */
internal expect fun platformLanguageForDisplayName(normalizedDisplayName: String): String?
