package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeitnerCapTest {
    private val typeable = Leitner.choiceMaxLevel(typeable = true)
    private val other = Leitner.choiceMaxLevel(typeable = false)

    @Test
    fun capsAreThreeForTypeableAndFiveOtherwise() {
        assertEquals(3, typeable)
        assertEquals(5, other)
    }

    @Test
    fun typeableWordsRiseOnlyToThreeWithMultipleChoice() {
        // Correct and due: 0–2 → +1, 3 stays, 4/5 (e.g. from a backup) → 3.
        assertEquals(listOf(1, 2, 3, 3, 3, 3), (0..5).map { Leitner.afterFirstAttempt(it, correct = true, early = false, maxLevel = typeable) })
        // Correct but early: the same, as practice rises up to level 3 and the cap applies first.
        assertEquals(listOf(1, 2, 3, 3, 3, 3), (0..5).map { Leitner.afterFirstAttempt(it, correct = true, early = true, maxLevel = typeable) })
        // Wrong: −1 as before.
        assertEquals(listOf(0, 0, 1, 2, 3, 4), (0..5).map { Leitner.afterFirstAttempt(it, correct = false, early = false, maxLevel = typeable) })
    }

    @Test
    fun typedCorrectRisesToFiveWithTheRulesOfStep4d() {
        val typed = AnswerForm.TYPED
        // Due, reviewed on an earlier day: 3 → 4 → 5, 5 stays; the multiple-choice cap does not apply.
        assertEquals(listOf(4, 5, 5), (3..5).map { Leitner.afterFirstAttempt(it, correct = true, early = false, maxLevel = typeable, form = typed) })
        // Reviewed today: 3 stays 3 (also on the day it reached level 3).
        assertEquals(3, Leitner.afterFirstAttempt(3, correct = true, early = false, maxLevel = typeable, reviewedEarlierDay = false, form = typed))
        // Not due: 3 → 4 after an earlier day, 4 → 5 never.
        assertEquals(listOf(4, 4, 5), (3..5).map { Leitner.afterFirstAttempt(it, correct = true, early = true, maxLevel = typeable, form = typed) })
        // Wrong: −1.
        assertEquals(listOf(2, 3, 4), (3..5).map { Leitner.afterFirstAttempt(it, correct = false, maxLevel = typeable, form = typed) })
    }

    @Test
    fun choiceInsteadOfTypingFallsBackToThree() {
        val choice = AnswerForm.CHOICE_INSTEAD_OF_TYPING
        // Correct: 3 stays, 4/5 → 3, due or not.
        for (early in listOf(false, true)) {
            assertEquals(listOf(3, 3, 3), (3..5).map { Leitner.afterFirstAttempt(it, correct = true, early = early, maxLevel = typeable, form = choice) })
        }
        // Wrong: −1 like any wrong answer.
        assertEquals(listOf(2, 3, 4), (3..5).map { Leitner.afterFirstAttempt(it, correct = false, maxLevel = typeable, form = choice) })
    }

    @Test
    fun alsoCorrectKeepsTheLevel() {
        (3..5).forEach {
            assertEquals(it, Leitner.afterFirstAttempt(it, correct = false, maxLevel = typeable, form = AnswerForm.TYPED, kept = KeptAnswer.ALSO_CORRECT))
        }
    }

    @Test
    fun nonTypeableWordsAndVerbsRiseToFive() {
        assertEquals(listOf(1, 2, 3, 4, 5, 5), (0..5).map { Leitner.afterFirstAttempt(it, correct = true, early = false, maxLevel = other) })
        assertEquals(listOf(1, 2, 3, 4, 5, 5), (0..5).map { Leitner.afterFirstAttempt(it, correct = true) })
        assertEquals(listOf(0, 0, 1, 2, 3, 4), (0..5).map { Leitner.afterFirstAttempt(it, correct = false, maxLevel = other) })
    }

    @Test
    fun irregularVerbsFollowTheSameRuleUpToFive() {
        // Early, previous review today: 0 → 3 rise, 3 → 4 blocked, 4 and 5 stay.
        assertEquals(listOf(1, 2, 3, 3, 4, 5), (0..5).map { Leitner.afterFirstAttempt(it, correct = true, early = true, maxLevel = other, reviewedEarlierDay = false) })
        // Early, reviewed on an earlier day: 3 → 4 allowed, 4 → 5 still needs a due word.
        assertEquals(listOf(1, 2, 3, 4, 4, 5), (0..5).map { Leitner.afterFirstAttempt(it, correct = true, early = true, maxLevel = other, reviewedEarlierDay = true) })
    }

    @Test
    fun typeableNeedsACoreWord() {
        val late = word("1", "(to) be late", "zu spät sein").copy(coreWord = "late")
        assertTrue(late.isTypeable)
        assertFalse(late.copy(coreWord = null).isTypeable)
        // Phrases with a core word are typed like words.
        val phrase = word("2", "Nice to meet you.", "Freut mich.", type = WordType.PHRASE)
        assertTrue(phrase.copy(coreWord = "meet").isTypeable)
        assertFalse(phrase.isTypeable)
        // Irregular verbs in the verb quiz are never capped.
        assertFalse(late.copy(verbForms = VerbForms("was", "been")).isTypeable)
    }
}
