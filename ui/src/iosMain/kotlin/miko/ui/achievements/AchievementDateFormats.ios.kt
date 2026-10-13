package miko.ui.achievements

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import tachiyomi.domain.achievement.model.YearMonth

private fun nsDateFor(month: YearMonth): NSDate {
    val components = NSDateComponents()
    components.year = month.year.toLong()
    components.month = month.month.toLong()
    components.day = 1L
    return checkNotNull(NSCalendar.currentCalendar.dateFromComponents(components))
}

private fun localizedFormat(template: String): String =
    checkNotNull(NSDateFormatter.dateFormatFromTemplate(template, 0uL, NSLocale.currentLocale))

internal actual fun formatMonthShortLabel(month: YearMonth): String {
    val formatter = NSDateFormatter()
    formatter.locale = NSLocale.currentLocale
    formatter.dateFormat = localizedFormat("MMM")
    return formatter.stringFromDate(nsDateFor(month))
        .replace(".", "")
        .lowercase()
        .take(3)
}

internal actual fun formatMonthYearLabel(month: YearMonth): String {
    val formatter = NSDateFormatter()
    formatter.locale = NSLocale.currentLocale
    formatter.dateFormat = localizedFormat("MMMMyyyy")
    return formatter.stringFromDate(nsDateFor(month))
        .replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
}

internal actual fun formatAchievementDate(epochMs: Long): String {
    val formatter = NSDateFormatter()
    formatter.locale = NSLocale.currentLocale
    formatter.dateFormat = localizedFormat("MMMdyyyy")
    // Local timezone — same as SimpleDateFormat(Date) on Android.
    formatter.timeZone = NSTimeZone.localTimeZone
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))
}
