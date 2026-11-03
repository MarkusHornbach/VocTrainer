package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every vocabulary entry climbs 3 → 4 → 5 by typing (the short ladder is gone); verbs are unchanged. */
class VocabularyLadderTest {
    /** As the repository stores a first answer of a vocabulary entry (always typeable). */
    private fun vocabulary(level: Int, correct: Boolean, earlierDay: Boolean = true, early: Boolean = false,
                           form: AnswerForm = AnswerForm.TYPED) =
        Leitner.outcomeOfFirstAttempt(
            level, correct, early, Leitner.choiceMaxLevel(typeable = true), earlierDay, form
        )

    /** As the repository stores a first answer of an irregular verb. */
    private fun verb(level: Int, correct: Boolean, earlierDay: Boolean = true, early: Boolean = false) =
        Leitner.outcomeOfFirstAttempt(level, correct, early, reviewedEarlierDay = earlierDay)

    @Test
    fun vocabularyRisesThreeFourFiveWithoutJump() {
        assertEquals(4, vocabulary(3, correct = true).newLevel)
        assertEquals(4, vocabulary(3, correct = true, early = true).newLevel)
        assertEquals(5, vocabulary(4, correct = true).newLevel)
        assertEquals(LevelReason.STAYS_NEXT_DAY, vocabulary(3, correct = true, earlierDay = false).reason)
        assertEquals(LevelReason.STAYS_NOT_DUE, vocabulary(4, correct = true, early = true).reason)
    }

    @Test
    fun choosingInsteadOfTypingNeverPassesThree() {
        assertEquals(3, vocabulary(3, correct = true, form = AnswerForm.CHOICE_INSTEAD_OF_TYPING).newLevel)
        assertEquals(3, vocabulary(5, correct = true, form = AnswerForm.CHOICE_INSTEAD_OF_TYPING).newLevel)
        // Plain multiple choice on the levels below typing stops at 3 as well.
        assertEquals(3, vocabulary(3, correct = true, form = AnswerForm.MULTIPLE_CHOICE).newLevel)
    }

    @Test
    fun wrongAnswersDropOneLevel() {
        assertEquals(4, vocabulary(5, correct = false).newLevel)
        assertEquals(3, vocabulary(4, correct = false).newLevel)
        assertEquals(2, vocabulary(3, correct = false).newLevel)
    }

    @Test
    fun noJumpPointsAnyMore() {
        fun pts(highest: Int) = Points.forFirstAttempt(
            3, highest, correct = true, early = false, maxLevel = Leitner.choiceMaxLevel(true), form = AnswerForm.TYPED
        )
        assertEquals(4, pts(3))
        assertEquals(0, pts(4))
        assertEquals(RoundHint(HintKind.SURE, 4), RoundReport.hint(vocabulary(3, correct = true), alreadyReached = false))
    }

    @Test
    fun verbsKeepTheirLadder() {
        assertEquals(listOf(1, 2, 3, 4, 5), (0..4).map { verb(it, correct = true).newLevel })
        assertEquals(LevelReason.STAYS_NEXT_DAY, verb(3, correct = true, earlierDay = false).reason)
        assertEquals(LevelReason.STAYS_NOT_DUE, verb(4, correct = true, early = true).reason)
        assertEquals(4, verb(5, correct = false).newLevel)
        assertEquals(Leitner.MAX_LEVEL, Leitner.choiceMaxLevel(typeable = false))
    }
}
