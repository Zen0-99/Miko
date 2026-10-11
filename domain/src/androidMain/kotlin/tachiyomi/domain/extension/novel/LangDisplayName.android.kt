package tachiyomi.domain.extension.novel

import java.util.Locale

internal actual fun platformLangDisplayName(tag: String): String? {
    val locale = Locale.forLanguageTag(tag)
    if (locale.language.isEmpty()) return null
    return locale.getDisplayName(Locale.getDefault()).takeIf { it.isNotBlank() }
}
