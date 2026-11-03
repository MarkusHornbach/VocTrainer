package io.github.markushornbach.voctrainer.learning

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The single place the app gets "today" and the time of an answer from. The test app can shift
 * it by whole days ("Datum verschieben" in "Testwerkzeuge"), so calendar-day rules, due dates,
 * daily goal and streak can be tried without waiting; it is 0 in release builds (only
 * [io.github.markushornbach.voctrainer.settings.AppSettings] sets it, and only in the debug build).
 * The timestamps updatedAt keep the real time.
 */
object AppClock {
    private val _offsetDays = MutableStateFlow(0)

    /** Days added to the real date; 0 = real date. */
    val offsetDays: StateFlow<Int> = _offsetDays.asStateFlow()

    fun setOffsetDays(days: Int) {
        _offsetDays.value = days
    }

    val isShifted: Boolean get() = _offsetDays.value != 0

    /** Tests only: the real time is this instant instead of the system clock; null = system clock. */
    @VisibleForTesting
    @Volatile
    var fixedTimeForTest: Instant? = null

    /** The real time in epoch millis (never shifted), for timestamps such as updatedAt. */
    fun currentTimeMillis(): Long = fixedTimeForTest?.toEpochMilli() ?: System.currentTimeMillis()

    private fun realNow(zone: ZoneId): ZonedDateTime = fixedTimeForTest?.atZone(zone) ?: ZonedDateTime.now(zone)

    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = realNow(zone).toLocalDate().plusDays(_offsetDays.value.toLong())

    /** Epoch millis of "now" on the (shifted) day, for review dates and similar learning dates. */
    fun nowMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        realNow(zone).plusDays(_offsetDays.value.toLong()).toInstant().toEpochMilli()
}
