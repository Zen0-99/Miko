package tachiyomi.core.common.util.lang

import platform.Foundation.NSCaseInsensitiveSearch
import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSString
import platform.Foundation.NSWidthInsensitiveSearch

/**
 * Collator.PRIMARY equivalent: compare on base letters only — case-,
 * diacritic-, and width-insensitive via NSString's localized compare.
 */
actual fun String.compareToWithCollator(other: String): Int {
    val result = (this as NSString).compare(
        other,
        NSCaseInsensitiveSearch or NSDiacriticInsensitiveSearch or NSWidthInsensitiveSearch,
    )
    return result.toInt()
}
