package eu.kanade.tachiyomi.util.novel

import androidx.palette.graphics.Palette
import eu.kanade.tachiyomi.util.getBestColor as sharedGetBestColor

/**
 * Deprecated — use [eu.kanade.tachiyomi.util.getBestColor] instead.
 * Kept as a thin delegate for backward compatibility.
 */
fun Palette.getBestColor(): Int? = sharedGetBestColor()
