package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.LearningProgress
import io.github.markushornbach.voctrainer.learning.QuizRound
import io.github.markushornbach.voctrainer.learning.QuizSelection
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.learning.VerbDistractorGenerator
import io.github.markushornbach.voctrainer.learning.VerbQuestion
import io.github.markushornbach.voctrainer.learning.irregularFixtureVerbs
import io.github.markushornbach.voctrainer.settings.VerbFormMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** "3 Formen": both steps of a verb question, the scoring and which verbs take part. */
class ThreeVerbFormsTest {
    private val verbs = irregularFixtureVerbs()
    private val askable = verbsFor(VerbFormMode.THREE, verbs)

    private fun newRound(size: Int = 1) =
        QuizRound(askable.take(size), askable, VerbDistractorGenerator(Random(1), VerbQuestion.THREE_FORMS))

    private fun asking(round: QuizRound) = QuizRoundState.Asking(round.nextQuestion()!!, round.size)

    private val QuizRoundState.Asking.wrong get() = (correctIndex + 1) % options.size

    private fun answer(state: QuizRoundState.Asking, index: Int, round: QuizRound) =
        QuizInteraction.tap(state, index, round) as TapOutcome.Answered

    /** Answers the first step (right or wrong, then found again) and returns the second step. */
    private fun secondStep(round: QuizRound, firstRight: Boolean): QuizRoundState.Asking {
        val state = asking(round)
        val first = answer(state, if (firstRight) state.correctIndex else state.wrong, round)
        assertFalse("the first step is never recorded alone", first.countsForLevel)
        val found = if (firstRight) {
            first.state
        } else {
            assertNull(QuizInteraction.nextStep(first.state))
            val retry = QuizInteraction.retry(first.state, Random(2))
            answer(retry, retry.correctIndex, round).state
        }
        return QuizInteraction.nextStep(found)!!
    }

    @Test
    fun bothStepsRightRaisesTheVerb() {
        val round = newRound()
        val second = secondStep(round, firstRight = true)
        assertTrue(second.inSecondStep)
        assertEquals(AnswerPhase.ANSWERING, second.phase)
        assertEquals(second.question.question.secondStep!!.options[second.stepCorrectIndex], second.options[second.correctIndex])
        val done = answer(second, second.correctIndex, round)
        assertTrue(done.countsForLevel)
        assertTrue(done.countsAsCorrect)
        assertFalse(done.state.firstStepMissed)
        assertNull(QuizInteraction.nextStep(done.state))
        assertNull(round.nextQuestion())
        assertEquals(1, round.correctOnFirstAttempt)
    }

    @Test
    fun wrongFirstStepCountsAsWrongEvenIfTheSecondIsRight() {
        val round = newRound()
        val second = secondStep(round, firstRight = false)
        val done = answer(second, second.correctIndex, round)
        assertTrue(done.correct)
        assertTrue(done.advancesAutomatically)
        assertTrue(done.countsForLevel)
        assertFalse(done.countsAsCorrect)
        // "Das Simple Past war noch nicht richtig."
        assertTrue(done.state.firstStepMissed)
        // Repeated at the end of the round with both steps.
        val repeat = round.nextQuestion()!!
        assertTrue(repeat.isRepeat)
        assertTrue(repeat.question.secondStep != null)
        assertEquals(0, round.correctOnFirstAttempt)
    }

    @Test
    fun wrongSecondStepCountsAsWrong() {
        val round = newRound()
        val second = secondStep(round, firstRight = true)
        val wrong = answer(second, second.wrong, round)
        assertTrue(wrong.countsForLevel)
        assertFalse(wrong.countsAsCorrect)
        assertEquals(AnswerPhase.SOLUTION, wrong.state.phase)
        assertEquals(second.question.question.secondStep!!.solution, wrong.state.solution)
        assertFalse(wrong.state.firstStepMissed)
        // The retry only finds the answer; it is never recorded.
        val retry = QuizInteraction.retry(wrong.state, Random(3))
        val found = answer(retry, retry.correctIndex, round)
        assertFalse(found.countsForLevel)
        assertFalse(found.state.firstStepMissed)
        assertTrue(round.nextQuestion()!!.isRepeat)
    }

    @Test
    fun speechComesOnlyAfterTheSecondStep() {
        val round = newRound()
        val state = asking(round)
        val forms = state.question.question.word.verbForms!!
        assertNull(QuizInteraction.autoSpeechAfterTap(state, autoSpeak = true))
        val first = answer(state, state.correctIndex, round).state
        // The speaker button then speaks the infinitive again.
        assertEquals(state.question.question.speakOnShow, QuizInteraction.speakerText(first))
        val second = QuizInteraction.nextStep(first)!!
        val all = "${state.question.question.word.english} – ${forms.simplePast} – ${forms.pastParticiple}"
        assertEquals(all, QuizInteraction.autoSpeechAfterTap(second, autoSpeak = true))
        assertNull(QuizInteraction.autoSpeechAfterTap(second, autoSpeak = false))
        assertEquals(all, QuizInteraction.speakerText(answer(second, second.correctIndex, round).state))
    }

    @Test
    fun retryInTheSecondStepKeepsTheCorrectOptionOfThatStep() {
        val round = newRound()
        val second = secondStep(round, firstRight = true)
        val retry = QuizInteraction.retry(answer(second, second.wrong, round).state, Random(4))
        assertEquals(second.question.question.secondStep!!.options[second.stepCorrectIndex], retry.options[retry.correctIndex])
    }

    @Test
    fun verbsWithoutParticipleAreLeftOutOfSelectionAndCounts() {
        val withoutParticiple = verbs.filter { it.verbForms!!.pastParticiple == null }
        assertEquals(listOf("(to) ride a bike"), withoutParticiple.map { it.english })
        assertEquals(verbs, verbsFor(VerbFormMode.TWO, verbs))
        assertEquals(verbs.size - 1, askable.size)
        assertFalse(askable.any { it.english == "(to) ride a bike" })
        // "Dein Lernstand" and the selection count only what is asked.
        val unit3 = QuizSelection.filter(askable, QuizFilter(unit = UnitChoice.Number(3)))
        assertEquals(verbs.count { it.unit == 3 } - 1, unit3.size)
        assertEquals(askable.size, LearningProgress.of(askable).total)
        // Levels stay as stored: the same verbs in both modes keep their level.
        val leveled = verbs.map { it.copy(leitnerLevel = 3) }
        assertTrue(verbsFor(VerbFormMode.THREE, leveled).all { it.leitnerLevel == 3 })
    }

    @Test
    fun aSelectionWithOnlyVerbsWithoutParticipleHasNothingToAsk() {
        val ride = verbs.filter { it.verbForms!!.pastParticiple == null }
        assertTrue(verbsFor(VerbFormMode.THREE, ride).isEmpty())
    }
}
