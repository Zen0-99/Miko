package tachiyomi.domain.extension.novel

import platform.Foundation.NSLocale

private val localeLookup by lazy {
    val english = NSLocale(localeIdentifier = "en")
    val map = mutableMapOf<String, String>()
    for (language in NSLocale.isoLanguageCodes) {
        val nativeName = normalizeTokenForLang(
            NSLocale(localeIdentifier = language).localizedStringForLanguageCode(language),
        )
        if (nativeName.isNotEmpty()) map.putIfAbsent(nativeName, language)

        val englishName = normalizeTokenForLang(
            english.localizedStringForLanguageCode(language),
        )
        if (englishName.isNotEmpty()) map.putIfAbsent(englishName, language)
    }
    map
}

internal actual fun platformLanguageForDisplayName(normalizedDisplayName: String): String? {
    return localeLookup[normalizedDisplayName]
}
