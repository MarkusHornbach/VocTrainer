package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Test

class PointsTest {
    /** A word as the repository keeps it: level and highest level, updated like there. */
    private class Word(var level: Int, var highest: Int = level) {
        var points = 0

        fun answer(
            correct: Boolean = true, early: Boolean = false, earlierDay: Boolean = true,
            form: AnswerForm = AnswerForm.MULTIPLE_CHOICE, maxLevel: Int = Leitner.MAX_LEVEL
        ): Int {
            val gained = Points.forFirstAttempt(level, highest, correct, early, maxLevel, earlierDay, form)
            level = Leitner.afterFirstAttempt(level, correct, early, maxLevel, earlierDay, form)
            highest = maxOf(highest, level)
            points += gained
            return gained
        }
    }

    @Test
    fun firstRisesInOneDayGiveEachNewLevel() {
        val word = Word(0)
        // Not due after the first answer: rises to 3 in the same day still pay.
        assertEquals(listOf(1, 2, 3), listOf(word.answer(), word.answer(early = true), word.answer(early = true)))
        assertEquals(1 + 2 + 3, word.points)
        assertEquals(3, word.highest)
    }

    @Test
    fun reachingALevelAgainGivesNothingEvenWhenDue() {
        val word = Word(level = 3, highest = 3)
        word.answer(correct = false)
        assertEquals(2, word.level)
        assertEquals(0, word.answer(early = true))
        word.answer(correct = false)
        assertEquals(0, word.answer(early = false))
        assertEquals(3, word.level)
        // Also after a reset to 0: every level up to 3 was reached before.
        val reset = Word(level = 0, highest = 3)
        assertEquals(listOf(0, 0, 0), List(3) { reset.answer() })
        assertEquals(3, reset.level)
    }

    @Test
    fun newLevelsFourAndFiveGiveFourAndFive() {
        val word = Word(level = 3)
        assertEquals(4, word.answer(form = AnswerForm.TYPED))
        assertEquals(5, word.answer(form = AnswerForm.TYPED))
        // 3 → 4 only after a review on an earlier day; 4 → 5 only when due.
        assertEquals(0, Points.forFirstAttempt(3, 3, correct = true, early = false, reviewedEarlierDay = false))
        assertEquals(0, Points.forFirstAttempt(4, 4, correct = true, early = true))
    }

    @Test
    fun holdingLevelFiveGivesFivePointsOnlyWhenDue() {
        assertEquals(5, Points.forFirstAttempt(5, 5, correct = true, early = false, form = AnswerForm.TYPED))
        assertEquals(0, Points.forFirstAttempt(5, 5, correct = true, early = true, form = AnswerForm.TYPED))
        // Answering wrong on purpose never pays: 5 → 4 gives 0, then 4 → 5 gives 0 (already reached).
        val word = Word(level = 5)
        assertEquals(0, word.answer(correct = false))
        assertEquals(0, word.answer(form = AnswerForm.TYPED))
        assertEquals(5, word.level)
    }

    @Test
    fun choiceDropFromFiveAndRisingBackGivesNothing() {
        val cap = Leitner.CHOICE_MAX_TYPEABLE
        val word = Word(level = 5)
        assertEquals(0, word.answer(form = AnswerForm.CHOICE_INSTEAD_OF_TYPING, maxLevel = cap))
        assertEquals(3, word.level)
        assertEquals(0, word.answer(form = AnswerForm.TYPED, maxLevel = cap))
        assertEquals(0, word.answer(form = AnswerForm.TYPED, maxLevel = cap))
        assertEquals(5, word.level)
        assertEquals(0, word.points)
    }

    @Test
    fun aMultiLevelJumpSumsAllNewLevels() {
        assertEquals(4 + 5, Points.forNewLevels(highestLevel = 3, newLevel = 5))
        assertEquals(5, Points.forNewLevels(highestLevel = 4, newLevel = 5))
        assertEquals(0, Points.forNewLevels(highestLevel = 5, newLevel = 5))
        assertEquals(0, Points.forNewLevels(highestLevel = 5, newLevel = 4))
        assertEquals(1 + 2 + 3 + 4 + 5, Points.forNewLevels(highestLevel = 0, newLevel = 5))
    }

    @Test
    fun typeableWordsEarnPointsOnlyWhileTheyRiseToThreeByChoosing() {
        val cap = Leitner.CHOICE_MAX_TYPEABLE
        assertEquals(listOf(1, 2, 3), (0..2).map { Points.forFirstAttempt(it, it, correct = true, early = false, maxLevel = cap) })
        // Held on 3, or dropped from 4/5 to 3 (e.g. from a backup): nothing.
        (3..5).forEach { assertEquals(0, Points.forFirstAttempt(it, it, correct = true, early = false, maxLevel = cap)) }
    }

    @Test
    fun choiceInsteadOfTypingAndAlsoCorrectGiveNothing() {
        (3..5).forEach { level ->
            assertEquals(0, Points.forFirstAttempt(level, 0, correct = true, early = false, form = AnswerForm.CHOICE_INSTEAD_OF_TYPING))
            assertEquals(0, Points.forFirstAttempt(level, 0, correct = false, early = false, form = AnswerForm.TYPED, kept = KeptAnswer.ALSO_CORRECT))
        }
    }

    @Test
    fun wrongAnswersGiveNothing() {
        (0..5).forEach { level ->
            assertEquals(0, Points.forFirstAttempt(level, 0, correct = false, early = false))
            assertEquals(0, Points.forFirstAttempt(level, 0, correct = false, early = true))
        }
    }

    @Test
    fun theRuleMatchesLeitner() {
        (0..5).forEach { level ->
            for (early in listOf(true, false)) for (earlier in listOf(true, false)) {
                val newLevel = Leitner.afterFirstAttempt(level, correct = true, early = early, reviewedEarlierDay = earlier, form = AnswerForm.TYPED)
                val expected = when {
                    newLevel > level -> newLevel
                    level == Leitner.MAX_LEVEL && !early -> Leitner.MAX_LEVEL
                    else -> 0
                }
                assertEquals(expected, Points.forFirstAttempt(level, level, correct = true, early = early, reviewedEarlierDay = earlier, form = AnswerForm.TYPED))
            }
        }
    }

    @Test
    fun dailyBonusGrowsWithTheStreakUpToTwenty() {
        assertEquals(11, Points.dailyBonus(1))
        assertEquals(13, Points.dailyBonus(3))
        assertEquals(20, Points.dailyBonus(10))
        assertEquals(20, Points.dailyBonus(15))
        assertEquals(10, Points.dailyBonus(0))
    }

    @Test
    fun totalCountsBonusPerDayAndStartPointsOnlyOnce() {
        val rows = listOf(
            PointsRecord("2026-10-08", "a", risePoints = 10, dailyBonus = 11, startPoints = 120),
            PointsRecord("2026-10-09", "a", risePoints = 5, dailyBonus = 12, startPoints = 0),
            // The other device reached the goal on the same day and calculated start points too.
            PointsRecord("2026-10-09", "b", risePoints = 7, dailyBonus = 12, startPoints = 0),
            PointsRecord("2026-10-07", "b", risePoints = 0, dailyBonus = 0, startPoints = 80)
        )
        assertEquals(10 + 5 + 7 + 11 + 12 + 120, Points.total(rows))
        assertEquals(0, Points.total(emptyList()))
    }

    @Test
    fun risePointsForTotalChangesOnlyTodaysRowOfThisDevice() {
        val rows = listOf(
            PointsRecord("2026-10-09", "phone", risePoints = 40, dailyBonus = 12, startPoints = 100),
            PointsRecord("2026-10-09", "tablet", risePoints = 10, dailyBonus = 11, startPoints = 0),
            PointsRecord("2026-10-10", "phone", risePoints = 5, dailyBonus = 0, startPoints = 0)
        )
        assertEquals(167, Points.total(rows))

        // Higher: today's 5 rise points become 38.
        assertEquals(38, Points.risePointsForTotal(rows, "2026-10-10", "phone", 200))
        // Lower, below what today has: the rise points go negative.
        assertEquals(-62, Points.risePointsForTotal(rows, "2026-10-10", "phone", 100))
        // No row for today yet: the new row carries the whole difference.
        assertEquals(-167, Points.risePointsForTotal(rows, "2026-10-11", "phone", 0))
    }

    @Test
    fun risePointsForTotalReachesTheTarget() {
        val rows = listOf(
            PointsRecord("2026-10-09", "phone", 40, 12, 100),
            PointsRecord("2026-10-10", "phone", 5, 0, 0)
        )
        listOf(0, 57, 157, 1_000).forEach { target ->
            val rise = Points.risePointsForTotal(rows, "2026-10-10", "phone", target)
            val adjusted = rows.map { if (it.date == "2026-10-10") it.copy(risePoints = rise) else it }
            assertEquals(target, Points.total(adjusted))
        }
    }
}
