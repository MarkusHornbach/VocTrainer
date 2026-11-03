package io.github.markushornbach.voctrainer.learning

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundReportTest {
    private val typed = AnswerForm.TYPED
    private val choice = AnswerForm.CHOICE_INSTEAD_OF_TYPING

    private fun outcome(
        level: Int, correct: Boolean = true, early: Boolean = false, earlier: Boolean = true,
        form: AnswerForm = AnswerForm.MULTIPLE_CHOICE, kept: KeptAnswer? = null, maxLevel: Int = Leitner.MAX_LEVEL
    ) = Leitner.outcomeOfFirstAttempt(level, correct, early, maxLevel, earlier, form, kept)

    private fun hint(o: LevelOutcome, alreadyReached: Boolean = false) = RoundReport.hint(o, alreadyReached)

    @Test
    fun outcomeMatchesTheLevelRules() {
        for (level in 0..5) for (correct in listOf(true, false)) for (early in listOf(true, false))
            for (earlier in listOf(true, false)) for (form in AnswerForm.entries) for (also in KeptAnswer.entries + null) {
                assertEquals(
                    Leitner.afterFirstAttempt(level, correct, early, 5, earlier, form, also),
                    outcome(level, correct, early, earlier, form, also).newLevel
                )
            }
    }

    @Test
    fun normalPromotion() {
        assertEquals(RoundHint(HintKind.RISE, 2), hint(outcome(1)))
        assertEquals(RoundHint(HintKind.RISE, 3), hint(outcome(2, form = typed)))
    }

    @Test
    fun promotedWithoutPointsWhenAlreadyReached() {
        val o = outcome(1, early = true)
        assertEquals(LevelReason.RISE, o.reason)
        assertEquals(RoundHint(HintKind.RISE_ALREADY_REACHED, 2), hint(o, alreadyReached = true))
        assertEquals(RoundHint(HintKind.RISE, 2), hint(o))
        assertTrue(hint(o, alreadyReached = true)!!.isRise)
    }

    @Test
    fun levelFourOnlyOnALaterDay() {
        val o = outcome(3, form = typed, earlier = false)
        assertEquals(LevelReason.STAYS_NEXT_DAY, o.reason)
        assertEquals(RoundHint(HintKind.STAYS_NEXT_DAY, 3), hint(o))
    }

    @Test
    fun levelFiveOnlyWhenDue() {
        val o = outcome(4, form = typed, early = true)
        assertEquals(LevelReason.STAYS_NOT_DUE, o.reason)
        assertEquals(RoundHint(HintKind.STAYS_NOT_DUE, 4), hint(o))
    }

    @Test
    fun correctOnLevelFiveHasNoHint() {
        assertNull(hint(outcome(5, form = typed)))
        assertNull(hint(outcome(5, form = typed, early = true)))
    }

    @Test
    fun auswahlKeepsLevelThreeAndDropsHigherLevels() {
        assertEquals(RoundHint(HintKind.CHOICE_KEEPS, 3), hint(outcome(3, form = choice)))
        assertEquals(RoundHint(HintKind.CHOICE_DROPS, 3), hint(outcome(4, form = choice)))
        assertEquals(RoundHint(HintKind.CHOICE_DROPS, 3), hint(outcome(5, form = choice)))
        assertTrue(outcome(5, form = choice).dropped)
        assertFalse(outcome(3, form = choice).dropped)
    }

    @Test
    fun alsoCorrectKeepsTheLevel() {
        for (level in 0..5) {
            assertEquals(RoundHint(HintKind.ALSO_CORRECT, level), hint(outcome(level, form = typed, kept = KeptAnswer.ALSO_CORRECT)))
        }
    }

    @Test
    fun almostCorrectKeepsTheLevelAndIsListedByKind() {
        val kept = KeptAnswer.ALMOST_CORRECT
        for (level in 0..5) {
            val o = outcome(level, correct = false, form = typed, kept = kept)
            assertEquals(RoundHint(HintKind.ALMOST_CORRECT, level), hint(o))
            assertFalse(o.rose || o.dropped)
        }
        val records = listOf(
            AttemptRecord("colour", outcome(3, correct = false, form = typed, kept = kept), 0, almost = AlmostKind.AMERICAN),
            AttemptRecord("house", outcome(4, correct = false, form = typed, kept = kept), 0, almost = AlmostKind.TYPO),
            AttemptRecord("big", outcome(3, correct = false, form = typed, kept = kept), 0, almost = AlmostKind.TYPO_OF_OTHER)
        )
        // Neither risen nor dropped.
        assertEquals(0, RoundReport.risen(RoundReport.details(records)))
        assertEquals(0, RoundReport.dropped(RoundReport.details(records)))
        assertEquals(
            listOf(DetailReason.ALMOST_AMERICAN, DetailReason.ALMOST_TYPO, DetailReason.ALMOST_TYPO),
            RoundReport.details(records).map { it.reason }
        )
        assertTrue(RoundReport.details(records).all { it.status == DetailStatus.SAME })
    }

    @Test
    fun sureAndMastered() {
        val sure = hint(outcome(3, form = typed))!!
        assertEquals(RoundHint(HintKind.SURE, 4), sure)
        assertEquals(RoundHint(HintKind.SURE_ALREADY_REACHED, 4), hint(outcome(3, form = typed), alreadyReached = true))
        assertTrue(hint(outcome(3, form = typed), alreadyReached = true)!!.alreadyReached)
        assertFalse(sure.alreadyReached)
        val mastered = hint(outcome(4, form = typed))!!
        assertEquals(RoundHint(HintKind.MASTERED, 5), mastered)
        assertTrue(mastered.isRise)
        // Re-rising to 5 after a drop: still a rise, but no points.
        val again = hint(outcome(4, form = typed), alreadyReached = true)!!
        assertEquals(RoundHint(HintKind.MASTERED_ALREADY_REACHED, 5), again)
        assertTrue(again.alreadyReached && again.isRise)
        assertFalse(hint(outcome(2))!!.alreadyReached)
    }

    @Test
    fun wrongAnswers() {
        assertEquals(RoundHint(HintKind.WRONG_DROP, 2), hint(outcome(3, correct = false)))
        assertEquals(RoundHint(HintKind.WRONG_DROP, 4), hint(outcome(5, correct = false, form = typed)))
        assertEquals(RoundHint(HintKind.WRONG_BOTTOM, 0), hint(outcome(0, correct = false)))
    }

    private fun record(word: String, o: LevelOutcome, points: Int = o.newLevel.takeIf { o.rose } ?: 0) =
        AttemptRecord(word, o, points)

    @Test
    fun countsAndDetailsOfEveryWord() {
        val records = listOf(
            record("rose", outcome(1)),                                             // up, points
            record("dog", outcome(1, early = true), points = 0),                    // up to a level it already had
            record("phone", outcome(3, form = typed, earlier = false)),             // next day
            record("house", outcome(4, form = typed, early = true)),                // not due
            record("give", outcome(4, form = choice)),                              // "Mit Vorschlägen" drop
            record("keep", outcome(3, form = choice)),                              // "Mit Vorschlägen" keeps
            record("cat", outcome(2, correct = false)),                             // wrong
            record("new", outcome(0, correct = false)),                             // wrong at the bottom
            record("same", outcome(2, form = typed, kept = KeptAnswer.ALSO_CORRECT)),           // also correct
            record("top", outcome(5, form = typed))                                 // stays on 5, due: 0 points here
        )
        val details = RoundReport.details(records)
        assertEquals(2, RoundReport.risen(details))
        assertEquals(2, RoundReport.dropped(details)) // give, cat; "new" cannot drop
        // One entry per first attempt, in round order.
        assertEquals(records.map { it.english }, details.map { it.english })
        assertEquals(
            listOf(
                DetailStatus.UP, DetailStatus.UP, DetailStatus.SAME, DetailStatus.SAME, DetailStatus.DOWN,
                DetailStatus.SAME, DetailStatus.DOWN, DetailStatus.SAME, DetailStatus.SAME, DetailStatus.SAME
            ),
            details.map { it.status }
        )
        assertEquals(listOf(2, 2, 3, 4, 3, 3, 1, 0, 2, 5), details.map { it.level })
        assertEquals(
            listOf(
                null, null, DetailReason.NEXT_DAY, DetailReason.NOT_DUE, DetailReason.CHOICE, DetailReason.CHOICE,
                DetailReason.WRONG, DetailReason.WRONG, DetailReason.ALSO_CORRECT, DetailReason.TOP
            ),
            details.map { it.reason }
        )
        // Rose with points: no explanation; rose without points: "Level bereits vorher erreicht".
        assertEquals(2, details[0].points)
        assertFalse(details[0].reachedBefore)
        assertEquals(0, details[1].points)
        assertTrue(details[1].reachedBefore)
        // Correct on level 5 that stays: "später wieder Punkte" only when it gave no points.
        assertTrue(details[9].notDue)
        assertFalse(RoundReport.details(listOf(record("top", outcome(5, form = typed), points = 5))).single().notDue)
    }

    @Test
    fun detailsKeepTheDateOfAWordThatIsNotDue() {
        val day = LocalDate.of(2026, 10, 17)
        val notDue = AttemptRecord("stone", outcome(4, form = typed, early = true), 0, availableFrom = day)
        assertEquals(day, RoundReport.details(listOf(notDue)).single().availableFrom)
        val plain = AttemptRecord("stone", outcome(4, form = typed, early = true), 0)
        assertNull(RoundReport.details(listOf(plain)).single().availableFrom)
    }

    @Test
    fun verbTrainingDetailsHaveNoPoints() {
        val verb = AttemptRecord("catch", outcome(1, early = true), points = 0, pointsApply = false)
        assertEquals(RoundHint(HintKind.RISE, 2), RoundReport.hint(verb))
        val detail = RoundReport.details(listOf(verb)).single()
        assertNull(detail.points)
        assertFalse(detail.reachedBefore)
        assertEquals(DetailStatus.UP, detail.status)
    }

    @Test
    fun everyWordIsListedEvenIfNothingNeedsExplaining() {
        assertEquals(2, RoundReport.details(listOf(record("a", outcome(1)), record("b", outcome(5, form = typed)))).size)
        assertTrue(RoundReport.details(emptyList()).isEmpty())
        assertEquals(0, RoundReport.dropped(emptyList()))
        assertEquals(0, RoundReport.risen(emptyList()))
    }

    @Test
    fun verbTrainingCountsRisesFromTheDetailsAndListsThemFirst() {
        // Verb training: no points at all, so a rise is never tied to points.
        fun verb(word: String, o: LevelOutcome) = AttemptRecord(word, o, points = 0, pointsApply = false)
        val records = listOf(
            verb("fall", outcome(2, correct = false)),          // drop
            verb("go", outcome(2, earlier = true)),              // rise
            verb("see", outcome(3, earlier = false)),            // stays (next day)
            verb("take", outcome(0)),                            // rise
            verb("bring", outcome(1, correct = false))           // drop
        )
        val details = RoundReport.risesFirst(RoundReport.details(records))
        assertEquals(listOf("go", "take", "see", "fall", "bring"), details.map { it.english })
        // The number on the result screen comes from the same list as "Details".
        assertEquals(details.count { it.status == DetailStatus.UP }, RoundReport.risen(details))
        assertEquals(2, RoundReport.risen(details))
        assertEquals(2, RoundReport.dropped(details))
        assertTrue(details.all { it.points == null })
    }
}
