package tachiyomi.domain.extension.novel

private val langTagPattern = Regex("^[a-zA-Z]{2,3}([_-][a-zA-Z0-9]{2,8})*$")

/**
 * Display name for a stored language value — mirrors Android's
 * `LocaleHelper.getSourceDisplayName`. Stored langs are ISO codes (`en`,
 * `pt-BR`, `all`); anything that doesn't look like a tag (already a display
 * name, a site-specific token) passes through unchanged.
 */
fun langDisplayName(lang: String): String {
    val cleaned = lang.trim()
    if (cleaned.isEmpty() || cleaned == "all") return cleaned
    if (!langTagPattern.matches(cleaned)) return cleaned
    return platformLangDisplayName(cleaned.replace('_', '-')) ?: cleaned
}

internal expect fun platformLangDisplayName(tag: String): String?
