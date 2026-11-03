package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.data.LevelReset
import io.github.markushornbach.voctrainer.data.Vocabulary
import io.github.markushornbach.voctrainer.learning.KeptAnswer
import io.github.markushornbach.voctrainer.learning.AlsoCorrectIndex
import io.github.markushornbach.voctrainer.learning.AnswerForm
import io.github.markushornbach.voctrainer.learning.MultipleChoiceGenerator
import io.github.markushornbach.voctrainer.learning.QuizRound
import io.github.markushornbach.voctrainer.learning.SelfTyped
import io.github.markushornbach.voctrainer.learning.VerbDistractorGenerator
import io.github.markushornbach.voctrainer.learning.irregularFixtureVerbs
import io.github.markushornbach.voctrainer.learning.word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** "Selbst getippt": which answers store the first correctly typed date. */
class SelfTypedTest {
    private val pool = (1..8).map { word("w$it", "word$it", "Wort$it").copy(coreWord = "word$it", leitnerLevel = 3) }
    private val big = word("big", "big", "Wort1").copy(coreWord = "big")
    private val index = AlsoCorrectIndex(pool + big)

    private fun newRound(size: Int = 2) = QuizRound(pool.take(size), pool, MultipleChoiceGenerator(Random(1)))

    private fun asking(round: QuizRound) = QuizRoundState.Asking(round.nextQuestion()!!, round.size)

    private fun submit(state: QuizRoundState.Asking, round: QuizRound, text: String) =
        QuizInteraction.submitTyped(QuizInteraction.typingInput(state, text), round, index) as TapOutcome.Answered

    /** What the ViewModel stores: only first attempts reach the repository, which checks [SelfTyped]. */
    private fun storesSelfTyped(outcome: TapOutcome.Answered): Boolean =
        outcome.countsForLevel && SelfTyped.counts(outcome.form, outcome.correct, outcome.kept)

    @Test
    fun onlyACorrectlyTypedTargetWordCounts() {
        assertTrue(SelfTyped.counts(AnswerForm.TYPED, correct = true, kept = null))
        assertFalse(SelfTyped.counts(AnswerForm.TYPED, correct = false, kept = KeptAnswer.ALSO_CORRECT))
        assertFalse(SelfTyped.counts(AnswerForm.TYPED, correct = false, kept = null))
        assertFalse(SelfTyped.counts(AnswerForm.CHOICE_INSTEAD_OF_TYPING, correct = true, kept = null))
        assertFalse(SelfTyped.counts(AnswerForm.MULTIPLE_CHOICE, correct = true, kept = null))
    }

    @Test
    fun firstAttemptTypedCorrectlyCounts() {
        val round = newRound()
        assertTrue(storesSelfTyped(submit(asking(round), round, "word1")))
    }

    @Test
    fun alsoCorrectDoesNotCount() {
        val round = newRound()
        val outcome = submit(asking(round), round, "big")
        assertTrue(outcome.kept == KeptAnswer.ALSO_CORRECT)
        assertFalse(storesSelfTyped(outcome))
    }

    @Test
    fun auswahlDoesNotCount() {
        val round = newRound()
        val choice = QuizInteraction.chooseInstead(QuizInteraction.choiceDelayPassed(asking(round)))
        val outcome = QuizInteraction.tap(choice, choice.correctIndex, round) as TapOutcome.Answered
        assertTrue(outcome.correct && outcome.countsForLevel)
        assertFalse(storesSelfTyped(outcome))
    }

    @Test
    fun copyAfterAMistakeAndTheRepetitionAtTheEndDoNotCount() {
        val round = newRound(size = 1)
        val wrong = submit(asking(round), round, "nonsense")
        assertFalse(storesSelfTyped(wrong))
        val copied = submit(wrong.state, round, "word1")
        assertTrue(copied.correct)
        assertFalse(storesSelfTyped(copied))
        // The word comes again at the end of the round; typed correctly there it still does not count.
        val repeat = asking(round)
        assertTrue(repeat.question.isRepeat)
        val repeated = submit(repeat, round, "word1")
        assertTrue(repeated.correct)
        assertFalse(storesSelfTyped(repeated))
    }

    @Test
    fun multipleChoiceAndVerbTrainingDoNotCount() {
        val level0 = pool.map { it.copy(leitnerLevel = 0) }
        val round = QuizRound(level0.take(1), level0, MultipleChoiceGenerator(Random(1)))
        val choice = asking(round)
        val outcome = QuizInteraction.tap(choice, choice.correctIndex, round) as TapOutcome.Answered
        assertEquals(AnswerForm.MULTIPLE_CHOICE, outcome.form)
        assertFalse(storesSelfTyped(outcome))

        val verbs = irregularFixtureVerbs().map { it.copy(leitnerLevel = 3) }
        val verbRound = QuizRound(verbs.take(1), verbs, VerbDistractorGenerator(Random(1)))
        val verb = asking(verbRound)
        val verbOutcome = QuizInteraction.tap(verb, verb.correctIndex, verbRound) as TapOutcome.Answered
        assertEquals(AnswerForm.MULTIPLE_CHOICE, verbOutcome.form)
        assertFalse(storesSelfTyped(verbOutcome))
    }

    @Test
    fun theDateIsSetOnceAndSurvivesSettingTheLevelInTheTestApp() {
        val entry = Vocabulary(id = "v1", english = "bag", germanMeanings = listOf("Tasche"), leitnerLevel = 3)
        assertNull(entry.firstTypedCorrectAt)
        val typed = entry.withFirstTypedCorrect(1_000)
        assertEquals(1_000L, typed.firstTypedCorrectAt)
        assertEquals(1_000L, typed.withFirstTypedCorrect(5_000).firstTypedCorrectAt)
        val reset = LevelReset.vocabularyTo(listOf(typed), setOf("v1"), level = 0, now = 9_000).single()
        assertEquals(0, reset.leitnerLevel)
        assertEquals(1_000L, reset.firstTypedCorrectAt)
    }
}
