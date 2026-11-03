package io.github.markushornbach.voctrainer.learning

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Rounds finished on one device on one day, with the daily goal set there. */
data class PracticeRecord(val date: LocalDate, val deviceId: String, val rounds: Int, val goal: Int)

data class StreakInfo(
    /** Consecutive reached days ending today (if reached) or yesterday. */
    val current: Int,
    val longest: Int,
    /** Rounds finished today on all devices. */
    val roundsToday: Int,
    val goalToday: Int,
    /** A missed day of the current week was bridged by the joker. */
    val jokerUsedThisWeek: Boolean
) {
    val todayReached: Boolean get() = roundsToday >= goalToday
}

/**
 * Daily goal and streak, derived from the practice records only (no stored streak or joker
 * state). A day is reached when the rounds of all devices reach the highest goal of that day.
 * At most one missed day per calendar week (Monday–Sunday) is bridged by a joker; bridged
 * days do not count. Today never uses a joker – the streak stays alive until the day is over.
 */
object Streak {
    fun compute(records: List<PracticeRecord>, today: LocalDate, deviceId: String, currentGoal: Int): StreakInfo {
        val byDate = records.groupBy { it.date }
        val todayRecords = byDate[today].orEmpty()
        val roundsToday = todayRecords.sumOf { it.rounds }
        // This device counts with its current setting; other devices with the goal they set today.
        val goalToday = (todayRecords.filter { it.deviceId != deviceId }.map { it.goal } + currentGoal).max()
        val todayReached = roundsToday >= goalToday

        fun reached(date: LocalDate): Boolean {
            val day = byDate[date] ?: return false
            return day.sumOf { it.rounds } >= day.maxOf { it.goal }
        }

        // Current streak: walk back from yesterday.
        var current = if (todayReached) 1 else 0
        val usedWeeks = HashSet<LocalDate>()
        val pendingJokers = ArrayList<LocalDate>()
        val confirmedJokers = HashSet<LocalDate>()
        var date = today.minusDays(1)
        while (true) {
            if (reached(date)) {
                current++
                // A joker only counts once a reached day lies behind it.
                confirmedJokers += pendingJokers
                pendingJokers.clear()
            } else {
                val week = weekOf(date)
                if (!usedWeeks.add(week)) break
                pendingJokers += week
            }
            date = date.minusDays(1)
        }

        // Longest streak: walk forward through the whole history.
        var longest = current
        val first = records.minOfOrNull { it.date }
        if (first != null) {
            var run = 0
            val runWeeks = HashSet<LocalDate>()
            var day: LocalDate = first
            while (day.isBefore(today)) {
                when {
                    reached(day) -> {
                        run++
                        longest = maxOf(longest, run)
                    }
                    run > 0 && runWeeks.add(weekOf(day)) -> Unit
                    else -> {
                        run = 0
                        runWeeks.clear()
                    }
                }
                day = day.plusDays(1)
            }
            if (todayReached) longest = maxOf(longest, run + 1)
        }

        return StreakInfo(current, longest, roundsToday, goalToday, weekOf(today) in confirmedJokers)
    }

    /** Monday of the calendar week of [date]. */
    fun weekOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
