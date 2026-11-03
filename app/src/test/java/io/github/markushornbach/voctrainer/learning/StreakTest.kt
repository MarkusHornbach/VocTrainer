package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakTest {
    // Thursday, 8 Oct 2026; the week started on Monday, 5 Oct.
    private val today = LocalDate.of(2026, 10, 8)
    private val me = "phone"

    private fun day(daysAgo: Long, rounds: Int = 1, goal: Int = 1, device: String = me) =
        PracticeRecord(today.minusDays(daysAgo), device, rounds, goal)

    private fun streak(vararg records: PracticeRecord, goal: Int = 1) = Streak.compute(records.toList(), today, me, goal)

    @Test
    fun noPracticeNoStreak() {
        val info = streak()
        assertEquals(0, info.current)
        assertEquals(0, info.longest)
        assertEquals(0, info.roundsToday)
        assertEquals(1, info.goalToday)
        assertFalse(info.todayReached)
        assertFalse(info.jokerUsedThisWeek)
    }

    @Test
    fun consecutiveDaysEndingToday() {
        val info = streak(day(0), day(1), day(2))
        assertEquals(3, info.current)
        assertTrue(info.todayReached)
        assertFalse(info.jokerUsedThisWeek)
    }

    @Test
    fun todayNotReachedYetKeepsTheStreakAlive() {
        val info = streak(day(1), day(2))
        assertEquals(2, info.current)
        assertFalse(info.todayReached)
        // Not enough rounds today is the same.
        assertEquals(2, streak(day(0, rounds = 1), day(1), day(2), goal = 2).current)
    }

    @Test
    fun oneMissedDayIsBridgedByTheJoker() {
        // Tue 6 Oct missed.
        val info = streak(day(0), day(1), day(3), day(4))
        assertEquals(4, info.current)
        assertTrue(info.jokerUsedThisWeek)
    }

    @Test
    fun twoMissesInOneWeekEndTheStreak() {
        // Missed Tue 6 and Mon 5 Oct (same week).
        val info = streak(day(0), day(1), day(4), day(5))
        assertEquals(2, info.current)
        // The joker did not save anything, so it does not count as used.
        assertFalse(info.jokerUsedThisWeek)
    }

    @Test
    fun missesInDifferentWeeksAreBothBridged() {
        // Missed Tue 6 Oct (this week) and Thu 1 Oct (last week).
        val records = listOf(0L, 1, 3, 4, 5, 6, 8, 9).map { day(it) }.toTypedArray()
        val info = streak(*records)
        assertEquals(8, info.current)
        assertTrue(info.jokerUsedThisWeek)
    }

    @Test
    fun jokerOfLastWeekDoesNotCountForThisWeek() {
        // Missed Thu 1 Oct only.
        val info = streak(day(0), day(1), day(2), day(3), day(4), day(5), day(6), day(8))
        assertEquals(8, info.current)
        assertFalse(info.jokerUsedThisWeek)
    }

    @Test
    fun aJokerWithoutPracticeBeforeItIsNotUsed() {
        // Yesterday missed and nothing before it: the joker would bridge nothing.
        val info = streak(day(0))
        assertEquals(1, info.current)
        assertFalse(info.jokerUsedThisWeek)
    }

    @Test
    fun todayNeverUsesAJoker() {
        // Yesterday missed, today not reached yet: the joker bridges yesterday.
        val info = streak(day(2), day(3))
        assertEquals(2, info.current)
        assertTrue(info.jokerUsedThisWeek)
        assertFalse(info.todayReached)
    }

    @Test
    fun roundsOfAllDevicesAddUpAndTheHighestGoalCounts() {
        val yesterday = arrayOf(day(1, rounds = 1, goal = 2), day(1, rounds = 1, goal = 1, device = "tablet"))
        assertEquals(1, streak(*yesterday).current)
        // The tablet set goal 3 yesterday: 2 rounds are not enough.
        val higherGoal = arrayOf(day(1, rounds = 1, goal = 2), day(1, rounds = 1, goal = 3, device = "tablet"))
        assertEquals(0, streak(*higherGoal).current)
    }

    @Test
    fun todayUsesTheCurrentGoalOfThisDevice() {
        val info = streak(day(0, rounds = 1, goal = 1), goal = 2)
        assertEquals(1, info.roundsToday)
        assertEquals(2, info.goalToday)
        assertFalse(info.todayReached)
        val other = streak(day(0, rounds = 2, goal = 3, device = "tablet"), goal = 2)
        assertEquals(3, other.goalToday)
        assertEquals(2, other.roundsToday)
    }

    @Test
    fun longestStreakEver() {
        // 5 days in a row 20–24 days ago, then a gap, then the current 2.
        val old = (20L..24L).map { day(it) }
        val info = Streak.compute(old + listOf(day(0), day(1)), today, me, 1)
        assertEquals(2, info.current)
        assertEquals(5, info.longest)
        assertEquals(2, streak(day(0), day(1)).longest)
    }

    @Test
    fun longestStreakAlsoUsesJokers() {
        // 17–22 days ago with 20 days ago (Fri 18 Sep) missed: 5 counted days.
        val old = listOf(17L, 18, 19, 21, 22).map { day(it) }
        assertEquals(5, Streak.compute(old, today, me, 1).longest)
    }

    @Test
    fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 10, 5), Streak.weekOf(LocalDate.of(2026, 10, 11)))
        assertEquals(LocalDate.of(2026, 10, 5), Streak.weekOf(LocalDate.of(2026, 10, 5)))
    }
}
