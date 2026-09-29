package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.util.lang.toRelativeString
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun relativeDateText(
    dateEpochMillis: Long,
): String {
    return relativeDateText(
        localDate = LocalDate.ofInstant(
            Instant.ofEpochMilli(dateEpochMillis),
            ZoneId.systemDefault(),
        )
            .takeIf { dateEpochMillis > 0L },
    )
}

/**
 * Full, locale-aware date (e.g. "Jun 4, 2003" / "04.06.2003"), matching the
 * date style the user's phone uses. For episode air dates where a complete
 * day/month/year is wanted rather than relative text.
 */
@Composable
fun fullDateText(
    dateEpochMillis: Long,
): String {
    val text = remember(dateEpochMillis) {
        if (dateEpochMillis <= 0L) {
            null
        } else {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .format(
                    LocalDate.ofInstant(
                        Instant.ofEpochMilli(dateEpochMillis),
                        ZoneId.systemDefault(),
                    ),
                )
        }
    }
    return text ?: stringResource(MR.strings.not_applicable)
}

// For use in chapter/episode release time
@Composable
fun relativeDateTimeText(
    dateEpochMillis: Long,
): String {
    return relativeDateTimeText(
        localDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(dateEpochMillis),
            ZoneId.systemDefault(),
        )
            .takeIf { dateEpochMillis > 0L },
    )
}

@Composable
fun relativeDateText(
    localDate: LocalDate?,
): String {
    val context = LocalContext.current

    val preferences = remember { Injekt.get<UiPreferences>() }
    val relativeTime = remember { preferences.relativeTime().get() }
    val dateFormat = remember { UiPreferences.dateFormat(preferences.dateFormat().get()) }

    return localDate?.toRelativeString(
        context = context,
        relative = relativeTime,
        dateFormat = dateFormat,
    )
        ?: stringResource(MR.strings.not_applicable)
}

// For use in chapter/episode release time
@Composable
fun relativeDateTimeText(
    localDateTime: LocalDateTime?,
): String {
    val context = LocalContext.current

    val preferences = remember { Injekt.get<UiPreferences>() }
    val relativeTime = remember { preferences.relativeTime().get() }
    val dateFormat = remember { UiPreferences.dateFormat(preferences.dateFormat().get()) }

    return localDateTime?.toRelativeString(
        context = context,
        relative = relativeTime,
        dateFormat = dateFormat,
    )
        ?: stringResource(MR.strings.not_applicable)
}
