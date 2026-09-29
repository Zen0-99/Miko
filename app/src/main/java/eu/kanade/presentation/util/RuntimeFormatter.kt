package eu.kanade.presentation.util

/**
 * Format a runtime string (Cinemeta's "127 min", "1h 30min", TMDB-style
 * "2h", or a bare minute count) into a compact "2h 07min" / "45min" label.
 * Returns null when the input can't be parsed into a duration.
 */
fun formatRuntime(runtime: String?): String? {
    if (runtime.isNullOrBlank()) return null

    var totalMinutes = 0

    // "1h 30min" / "2h" style
    Regex("""(\d+)\s*h""", RegexOption.IGNORE_CASE).find(runtime)
        ?.let { totalMinutes += it.groupValues[1].toInt() * 60 }
    Regex("""(\d+)\s*m(?:in)?\b""", RegexOption.IGNORE_CASE).find(runtime)
        ?.let { totalMinutes += it.groupValues[1].toInt() }

    // Bare number = minutes
    if (totalMinutes == 0) {
        totalMinutes = runtime.trim().toIntOrNull() ?: return null
    }
    if (totalMinutes <= 0) return null

    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "${minutes}min"
        minutes == 0 -> "${hours}h"
        else -> "%dh %02dmin".format(hours, minutes)
    }
}
