package miko.ui.achievements

import tachiyomi.domain.achievement.model.YearMonth

/**
 * Locale-aware date labels for the achievement surface. Android formats
 * through `java.time`/`SimpleDateFormat`; iOS through `NSDateFormatter` —
 * both read the device locale, so output is identical.
 */
internal expect fun formatMonthShortLabel(month: YearMonth): String

internal expect fun formatMonthYearLabel(month: YearMonth): String

/** `SimpleDateFormat("MMM d, yyyy")` equivalent — e.g. "Oct 13, 2026". */
internal expect fun formatAchievementDate(epochMs: Long): String
