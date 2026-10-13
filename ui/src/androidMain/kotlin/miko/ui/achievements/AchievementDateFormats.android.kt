package miko.ui.achievements

import tachiyomi.domain.achievement.model.YearMonth
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

internal actual fun formatMonthShortLabel(month: YearMonth): String {
    return java.time.Month.of(month.month).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        .replace(".", "")
        .lowercase(Locale.getDefault())
        .take(3)
}

internal actual fun formatMonthYearLabel(month: YearMonth): String {
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
    return java.time.YearMonth.of(month.year, month.month).format(formatter)
        .replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase(Locale.getDefault())
            } else {
                char.toString()
            }
        }
}

internal actual fun formatAchievementDate(epochMs: Long): String {
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))
}
