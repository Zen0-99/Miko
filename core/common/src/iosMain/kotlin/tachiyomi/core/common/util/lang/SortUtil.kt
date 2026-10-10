package tachiyomi.core.common.util.lang

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCompatibilityMapping

// Unicode nonspacing-mark ranges stripped after compatibility
// decomposition — together with case-insensitive compare this
// approximates java.text.Collator.PRIMARY (base letters only:
// case-, diacritic-, and width-insensitive).
private val combiningMarkRanges = listOf(
    0x0300..0x036F,
    0x1AB0..0x1AFF,
    0x1DC0..0x1DFF,
    0x20D0..0x20FF,
    0xFE20..0xFE2F,
)

private fun String.folded(): String {
    @Suppress("CAST_NEVER_SUCCEEDS")
    val nsString = this as NSString
    return nsString.decomposedStringWithCompatibilityMapping
        .filterNot { char -> combiningMarkRanges.any { char.code in it } }
}

actual fun String.compareToWithCollator(other: String): Int =
    folded().compareTo(other.folded(), ignoreCase = true)
