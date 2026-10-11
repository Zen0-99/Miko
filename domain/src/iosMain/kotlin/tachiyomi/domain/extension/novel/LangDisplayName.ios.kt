package tachiyomi.domain.extension.novel

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForLanguageCode

// NSLocale members land in platform.Foundation as extension functions —
// each needs its own import (K2 companion-extension rule).
internal actual fun platformLangDisplayName(tag: String): String? {
    return (NSLocale.currentLocale as NSLocale)
        .localizedStringForLanguageCode(tag)
        ?.takeIf { it.isNotBlank() }
}
