package io.github.markushornbach.voctrainer.learning

import java.time.DayOfWeek
import java.time.LocalDate

/** Dates in the round hints ("Level 5 ab Do., 17.10."), always German, independent of the phone language. */
object HintDates {
    private val WEEKDAYS = mapOf(
        DayOfWeek.MONDAY to "Mo.", DayOfWeek.TUESDAY to "Di.", DayOfWeek.WEDNESDAY to "Mi.",
        DayOfWeek.THURSDAY to "Do.", DayOfWeek.FRIDAY to "Fr.", DayOfWeek.SATURDAY to "Sa.",
        DayOfWeek.SUNDAY to "So."
    )

    /** German weekday abbreviation, day and month: "Do., 17.10.". */
    fun format(date: LocalDate): String = "${WEEKDAYS.getValue(date.dayOfWeek)}, ${date.dayOfMonth}.${date.monthValue}."

    fun isTomorrow(date: LocalDate, today: LocalDate): Boolean = date == today.plusDays(1)
}
