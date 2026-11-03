package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.random.Random

class DueTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 10, 6)

    /** Epoch millis of [daysAgo] days before [today], at noon. */
    private fun reviewed(daysAgo: Long, zone: ZoneId = this.zone) =
        today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun intervalsByLevel() {
        assertEquals(listOf(0, 1, 2, 4, 7, 14), (0..5).map { Leitner.intervalDays(it) })
    }

    @Test
    fun eachLevelIsDueAfterItsInterval() {
        listOf(1 to 1L, 2 to 2L, 3 to 4L, 4 to 7L, 5 to 14L).forEach { (level, days) ->
            assertFalse("level $level one day early", Due.isDue(level, reviewed(days - 1), today, zone))
            assertTrue("level $level on the day", Due.isDue(level, reviewed(days), today, zone))
            assertTrue("level $level later", Due.isDue(level, reviewed(days + 3), today, zone))
            assertEquals(today, Due.dueDate(level, reviewed(days), zone))
        }
    }

    @Test
    fun levelZeroAndMissingReviewDateAreAlwaysDue() {
        assertTrue(Due.isDue(0, reviewed(0), today, zone))
        assertNull(Due.dueDate(0, reviewed(0), zone))
        (1..5).forEach { level ->
            assertTrue(Due.isDue(level, null, today, zone))
            assertNull(Due.dueDate(level, null, zone))
        }
    }

    @Test
    fun calendarDaysCountInTheDeviceTimeZone() {
        val berlin = ZoneId.of("Europe/Berlin")
        // Reviewed on 5 Oct at 23:30 in Berlin (21:30 UTC): level 1 is due on 6 Oct in Berlin.
        val lateEvening = ZonedDateTime.of(2026, 10, 5, 23, 30, 0, 0, berlin).toInstant().toEpochMilli()
        assertTrue(Due.isDue(1, lateEvening, today, berlin))
        // Reviewed on 6 Oct at 00:30 in Berlin (5 Oct 22:30 UTC): in Berlin not due before 7 Oct,
        // although in UTC it was still the 5th.
        val afterMidnight = ZonedDateTime.of(2026, 10, 6, 0, 30, 0, 0, berlin).toInstant().toEpochMilli()
        assertFalse(Due.isDue(1, afterMidnight, today, berlin))
        assertTrue(Due.isDue(1, afterMidnight, today, ZoneOffset.UTC))
        assertEquals(LocalDate.of(2026, 10, 7), Due.dueDate(1, afterMidnight, berlin))
    }

    @Test
    fun countsDueReviewsAndNewWords() {
        val words = listOf(
            word("new", "a", "A", level = 0),
            word("due", "b", "B", level = 2).copy(lastReviewedAt = reviewed(2)),
            word("rest", "c", "C", level = 3).copy(lastReviewedAt = reviewed(1)),
            word("old", "d", "D", level = 4)
        )
        assertEquals(3, Due.countDue(words, today, zone))
    }

    @Test
    fun picksDueReviewsThenNewWordsThenWeakestFillUp() {
        val words = listOf(
            word("n1", "n1", "N1", level = 0),
            word("n2", "n2", "N2", level = 0),
            word("r3", "r3", "R3", level = 3).copy(lastReviewedAt = reviewed(5)), // due
            word("r1", "r1", "R1", level = 1).copy(lastReviewedAt = reviewed(1)), // due
            word("e5", "e5", "E5", level = 5).copy(lastReviewedAt = reviewed(0)), // due in 14 days
            word("e2", "e2", "E2", level = 2).copy(lastReviewedAt = reviewed(0)), // due in 2 days
            word("e4", "e4", "E4", level = 4).copy(lastReviewedAt = reviewed(1))  // due in 6 days
        )

        val pick = QuizRound.pickWords(words, Random(3), today, zone, size = 6)

        val ids = pick.words.map { it.id }
        assertEquals(listOf("r3", "r1"), ids.take(2)) // r3 can become "gelernt" today: it takes a slot
        assertEquals(setOf("n1", "n2"), ids.subList(2, 4).toSet())
        assertEquals(listOf("e2", "e4"), ids.drop(4))
        assertEquals(setOf("e2", "e4"), pick.earlyIds)
    }

    @Test
    fun alwaysTenWordsIfTheSelectionHasEnough() {
        val words = (1..15).map { word("w$it", "w$it", "W$it", level = 5).copy(lastReviewedAt = reviewed(0)) }
        val pick = QuizRound.pickWords(words, Random(1), today, zone)
        assertEquals(10, pick.words.size)
        assertEquals(pick.words.map { it.id }.toSet(), pick.earlyIds)
    }

    @Test
    fun earlyFlagReachesTheQuestion() {
        val pool = (1..6).map { word("w$it", "w$it", "W$it") }
        val round = QuizRound(pool.take(2), pool, MultipleChoiceGenerator(Random(1)), earlyIds = setOf("w2"))
        assertFalse(round.nextQuestion()!!.pickedEarly)
        round.recordAnswer(correct = true)
        assertTrue(round.nextQuestion()!!.pickedEarly)
    }

    @Test
    fun fillUpTakesLowerLevelsFirstThenTheOlderReviewDay() {
        val words = listOf(
            word("l5", "l5", "L5", level = 5).copy(lastReviewedAt = reviewed(1)),
            word("l4", "l4", "L4", level = 4).copy(lastReviewedAt = reviewed(1)),
            word("l3new", "l3new", "L3N", level = 3).copy(lastReviewedAt = reviewed(0)),
            word("l3old", "l3old", "L3O", level = 3).copy(lastReviewedAt = reviewed(2)),
            word("l1", "l1", "L1", level = 1).copy(lastReviewedAt = reviewed(0)),
            word("due", "due", "D", level = 2).copy(lastReviewedAt = reviewed(3)),
            word("new", "new", "N", level = 0)
        )
        val pick = QuizRound.pickWords(words, Random(5), today, zone, size = 7)
        assertEquals(listOf("l3old", "due", "new", "l1", "l3new", "l4", "l5"), pick.words.map { it.id }) // l3old takes a slot
        assertEquals(setOf("l1", "l3old", "l3new", "l4", "l5"), pick.earlyIds)
    }

    @Test
    fun earlierDayCheckUsesCalendarDaysAndTreatsMissingAsEarlier() {
        assertTrue(Due.reviewedEarlierDay(null, today, zone))
        assertTrue(Due.reviewedEarlierDay(reviewed(1), today, zone))
        assertFalse(Due.reviewedEarlierDay(reviewed(0), today, zone))
        val late = today.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        assertFalse(Due.reviewedEarlierDay(late, today, zone))
    }

    @Test
    fun earlyPracticeRisesUpToThreeOnTheSameDay() {
        var level = 0
        repeat(3) { level = Leitner.afterFirstAttempt(level, correct = true, early = true, reviewedEarlierDay = false) }
        assertEquals(Leitner.PRACTICE_MAX_LEVEL, level)
    }

    @Test
    fun levelFourNeedsAnEarlierReviewDayDueOrEarly() {
        for (early in listOf(true, false)) {
            assertEquals(3, Leitner.afterFirstAttempt(3, correct = true, early = early, reviewedEarlierDay = false))
            assertEquals(4, Leitner.afterFirstAttempt(3, correct = true, early = early, reviewedEarlierDay = true))
        }
    }

    @Test
    fun levelFiveNeedsADueWord() {
        assertEquals(4, Leitner.afterFirstAttempt(4, correct = true, early = true))
        assertEquals(5, Leitner.afterFirstAttempt(4, correct = true, early = false))
        assertEquals(5, Leitner.afterFirstAttempt(5, correct = true, early = false))
        assertEquals(5, Leitner.afterFirstAttempt(5, correct = true, early = true))
    }

    @Test
    fun wrongAnswersDropOneLevelDueOrEarly() {
        for (early in listOf(true, false)) {
            assertEquals(2, Leitner.afterFirstAttempt(3, correct = false, early = early))
            assertEquals(0, Leitner.afterFirstAttempt(0, correct = false, early = early))
        }
    }
}
