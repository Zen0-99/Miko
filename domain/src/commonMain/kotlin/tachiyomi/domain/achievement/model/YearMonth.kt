package tachiyomi.domain.achievement.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Calendar month without a day component — portable stand-in for
 * `java.time.YearMonth`.
 */
data class YearMonth(
    val year: Int,
    val month: Int,
) : Comparable<YearMonth> {

    val monthValue: Int get() = month

    fun minusMonths(months: Long): YearMonth {
        val total = year * 12L + (month - 1L) - months
        return YearMonth((total.floorDiv(12L)).toInt(), (total.mod(12L) + 1L).toInt())
    }

    fun atDay(day: Int): LocalDate = LocalDate(year, month, day)

    fun atEndOfMonth(): LocalDate = atDay(1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

    override fun compareTo(other: YearMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })

    companion object {
        fun now(timeZone: TimeZone = TimeZone.currentSystemDefault()): YearMonth {
            val date = Clock.System.now().toLocalDateTime(timeZone).date
            return YearMonth(date.year, date.monthNumber)
        }
    }
}
