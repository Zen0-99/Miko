package tachiyomi.domain.extension.novel

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val localeLookup by lazy {
    val map = ConcurrentHashMap<String, String>()
    for (locale in Locale.getAvailableLocales()) {
        val language = locale.language
        if (language.isNullOrBlank()) continue

        val nativeName = normalizeTokenForLang(locale.getDisplayLanguage(locale))
        if (nativeName.isNotEmpty()) map.putIfAbsent(nativeName, language)

        val englishName = normalizeTokenForLang(locale.getDisplayLanguage(Locale.ENGLISH))
        if (englishName.isNotEmpty()) map.putIfAbsent(englishName, language)
    }
    map
}

internal actual fun platformLanguageForDisplayName(normalizedDisplayName: String): String? {
    return localeLookup[normalizedDisplayName]
}
