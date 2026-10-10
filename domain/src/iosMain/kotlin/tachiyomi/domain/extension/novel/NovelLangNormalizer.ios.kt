package tachiyomi.domain.extension.novel

import platform.Foundation.ISOLanguageCodes
import platform.Foundation.NSLocale
import platform.Foundation.localizedStringForLanguageCode

// NSLocale's ObjC members land in platform.Foundation as extension functions —
// each needs its own import (K2 companion-extension rule).
private val localeLookup by lazy {
    val english = NSLocale(localeIdentifier = "en")
    val map = mutableMapOf<String, String>()
    for (language in NSLocale.ISOLanguageCodes.filterIsInstance<String>()) {
        val nativeName = normalizeTokenForLang(
            NSLocale(localeIdentifier = language).localizedStringForLanguageCode(language),
        )
        if (nativeName.isNotEmpty() && nativeName !in map) map[nativeName] = language

        val englishName = normalizeTokenForLang(
            english.localizedStringForLanguageCode(language),
        )
        if (englishName.isNotEmpty() && englishName !in map) map[englishName] = language
    }
    map
}

internal actual fun platformLanguageForDisplayName(normalizedDisplayName: String): String? {
    return localeLookup[normalizedDisplayName]
}
