package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.AlmostKind
import io.github.markushornbach.voctrainer.learning.AlsoCorrectIndex
import io.github.markushornbach.voctrainer.learning.AnswerForm
import io.github.markushornbach.voctrainer.learning.KeptAnswer
import io.github.markushornbach.voctrainer.learning.LearningMaterial
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.learning.MultipleChoiceGenerator
import io.github.markushornbach.voctrainer.learning.Points
import io.github.markushornbach.voctrainer.learning.QuizRound
import io.github.markushornbach.voctrainer.learning.ReaskResult
import io.github.markushornbach.voctrainer.learning.RoundStars
import io.github.markushornbach.voctrainer.learning.SelfTyped
import io.github.markushornbach.voctrainer.learning.word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TypingInteractionTest {
    private val pool = (1..8).map { word("w$it", "word$it", "Wort$it").copy(coreWord = "word$it", leitnerLevel = 3) }
    private val big = word("big", "big", "Wort1").copy(coreWord = "big")
    private val index = AlsoCorrectIndex(pool + big)

    private fun newRound() = QuizRound(pool.take(2), pool, MultipleChoiceGenerator(Random(1)))

    private fun asking(round: QuizRound) = QuizRoundState.Asking(round.nextQuestion()!!, round.size)

    private fun submit(state: QuizRoundState.Asking, round: QuizRound, text: String) =
        QuizInteraction.submitTyped(QuizInteraction.typingInput(state, text), round, index)

    @Test
    fun choiceButtonAppearsAfterTheDelayOnlyWhileTheFieldIsEmpty() {
        val state = asking(newRound())
        assertFalse(state.typing!!.showsChoiceButton)
        // Typing before the delay: the button does not appear when the delay passes.
        val typed = QuizInteraction.choiceDelayPassed(QuizInteraction.typingInput(state, "w"))
        assertFalse(typed.typing!!.showsChoiceButton)
        // Field empty again: it shows (the delay ran once and stays passed).
        val cleared = QuizInteraction.typingInput(typed, "")
        assertTrue(cleared.typing!!.showsChoiceButton)
        assertFalse(QuizInteraction.typingInput(cleared, "wo").typing!!.showsChoiceButton)
        // Without the delay, an empty field shows nothing.
        assertFalse(QuizInteraction.typingInput(QuizInteraction.typingInput(state, "x"), "").typing!!.showsChoiceButton)
    }

    @Test
    fun choiceShowsTheOptionsWithNoWayBackAndCountsAsChoiceInsteadOfTyping() {
        val round = newRound()
        val state = asking(round)
        // Options cannot be tapped before "Mit Vorschlägen", and "Mit Vorschlägen" needs the delay.
        assertSame(TapOutcome.Ignored, QuizInteraction.tap(state, state.correctIndex, round))
        assertSame(state, QuizInteraction.chooseInstead(state))
        val choice = QuizInteraction.chooseInstead(QuizInteraction.choiceDelayPassed(state))
        assertEquals(TypingPhase.CHOICE, choice.typing!!.phase)
        assertFalse(choice.typing!!.showsChoiceButton)
        assertFalse(choice.typing!!.inputOpen)
        assertSame(choice, QuizInteraction.typingInput(choice, "word"))
        assertNull(answerResult(choice))
        val answered = QuizInteraction.tap(choice, choice.correctIndex, round) as TapOutcome.Answered
        assertEquals(AnswerResult.CORRECT, answerResult(answered.state))
        assertEquals(AnswerForm.CHOICE_INSTEAD_OF_TYPING, answered.form)
        assertTrue(answered.correct && answered.countsForLevel)
    }

    @Test
    fun emptyInputOnlyShowsTheHint() {
        val round = newRound()
        val state = asking(round)
        val outcome = submit(state, round, "   ") as TapOutcome.Updated
        assertTrue(outcome.state.typing!!.emptyHint)
        assertFalse(QuizInteraction.typingInput(outcome.state, "w").typing!!.emptyHint)
        // Nothing was recorded: the real answer still counts.
        assertTrue((submit(state, round, "word1") as TapOutcome.Answered).countsForLevel)
    }

    @Test
    fun correctTypedAnswerAdvancesAndCountsAsTyped() {
        val round = newRound()
        val outcome = submit(asking(round), round, " Word1 ") as TapOutcome.Answered
        assertTrue(outcome.correct && outcome.countsForLevel && outcome.advancesAutomatically)
        assertEquals(AnswerForm.TYPED, outcome.form)
        assertTrue(outcome.kept == null)
        assertEquals(TypingPhase.CORRECT, outcome.state.typing!!.phase)
        assertEquals(AnswerResult.CORRECT, answerResult(outcome.state))
        assertEquals(1, round.correctOnFirstAttempt)
    }

    @Test
    fun alsoCorrectAdvancesKeepsTheLevelAndIsNotRepeated() {
        val round = newRound()
        val outcome = submit(asking(round), round, "big") as TapOutcome.Answered
        assertTrue(outcome.kept == KeptAnswer.ALSO_CORRECT && outcome.advancesAutomatically && outcome.countsForLevel)
        assertFalse(outcome.correct)
        assertEquals(TypingPhase.ALSO_CORRECT, outcome.state.typing!!.phase)
        assertEquals(big, outcome.state.typing!!.alsoCorrect)
        assertEquals("big", outcome.state.typing!!.typedAnswer)
        assertEquals(AnswerResult.CORRECT, answerResult(outcome.state))
        // The round goes on with the second word and then ends: no repetition.
        assertEquals("w2", round.nextQuestion()!!.question.word.id)
        round.recordAnswer(true)
        assertNull(round.nextQuestion())
    }

    @Test
    fun wrongTypedAnswerNeedsTheCoreWordCopiedAndIsRepeatedInTheSameForm() {
        val round = newRound()
        // Two mistakes: wrong (one would be "Fast richtig").
        assertNull(answerResult(asking(newRound())))
        val wrong = submit(asking(round), round, "wrdo11") as TapOutcome.Answered
        assertFalse(wrong.correct || wrong.kept != null || wrong.advancesAutomatically)
        assertTrue(wrong.countsForLevel)
        val copying = wrong.state
        assertEquals(TypingPhase.COPYING, copying.typing!!.phase)
        assertEquals("", copying.typing!!.input)
        // The typed answer stays for the frame; the field is free for copying.
        assertEquals("wrdo11", copying.typing!!.typedAnswer)
        assertEquals(AnswerResult.WRONG, answerResult(copying))
        assertTrue(copying.typing!!.inputOpen)
        assertFalse(copying.typing!!.showsChoiceButton)
        // Copying wrongly only shows a hint; the solution stays.
        val mismatch = submit(copying, round, "word") as TapOutcome.Updated
        assertTrue(mismatch.state.typing!!.copyMismatch)
        assertEquals(TypingPhase.COPYING, mismatch.state.typing!!.phase)
        // Copying correctly (any case) moves on, without counting or speaking again.
        val copied = submit(mismatch.state, round, "WORD1") as TapOutcome.Answered
        assertTrue(copied.correct && copied.advancesAutomatically)
        assertFalse(copied.countsForLevel || copied.speaks)
        assertEquals(TypingPhase.COPIED, copied.state.typing!!.phase)
        assertEquals("wrdo11", copied.state.typing!!.typedAnswer)
        assertEquals(AnswerResult.WRONG, answerResult(copied.state))

        // Second word, then the repetition: again a typing question, not counting.
        assertEquals("w2", round.nextQuestion()!!.question.word.id)
        round.recordAnswer(true)
        val repeat = round.nextQuestion()!!
        assertTrue(repeat.isRepeat)
        assertNotNull(repeat.question.typing)
        assertNotNull(QuizRoundState.Asking(repeat, round.size).typing)
        assertFalse((submit(QuizRoundState.Asking(repeat, round.size), round, "word1") as TapOutcome.Answered).countsForLevel)
        assertNull(round.nextQuestion())
    }

    @Test
    fun almostCorrectNeedsTheCoreWordCopiedKeepsTheLevelAndIsNotRepeated() {
        val round = newRound()
        val material = LearningMaterial(pool + big)
        val state = QuizInteraction.typingInput(asking(round), "wrod1")
        val almost = QuizInteraction.submitTyped(state, round, index, material) as TapOutcome.Answered
        assertEquals(KeptAnswer.ALMOST_CORRECT, almost.kept)
        assertFalse(almost.correct || almost.advancesAutomatically)
        assertTrue(almost.countsForLevel && almost.speaks)
        assertEquals(AnswerForm.TYPED, almost.form)
        val copying = almost.state.typing!!
        assertEquals(TypingPhase.COPYING, copying.phase)
        assertEquals(AlmostKind.TYPO, copying.almost!!.kind)
        assertEquals("", copying.input)
        assertEquals("wrod1", copying.typedAnswer)
        assertEquals(AnswerResult.ALMOST, answerResult(almost.state))
        // Copying moves on without counting again.
        val copied = submit(almost.state, round, "word1") as TapOutcome.Answered
        assertTrue(copied.correct && copied.advancesAutomatically)
        assertFalse(copied.countsForLevel || copied.speaks)
        assertEquals(AnswerResult.ALMOST, answerResult(copied.state))
        // Counts as right for the round: no repetition, and editing now asks nothing again.
        assertEquals(1, round.correctOnFirstAttempt)
        assertEquals(ReaskResult.Next, round.reaskAfterEdit(pool[0]))
        assertEquals("w2", round.nextQuestion()!!.question.word.id)
        round.recordAnswer(true)
        assertNull(round.nextQuestion())
    }

    @Test
    fun creditPerAnswerForm() {
        // Typed correct and "Auch richtig": full credit.
        var round = newRound()
        submit(asking(round), round, "word1")
        assertEquals(3, round.starCredit)
        round = newRound()
        submit(asking(round), round, "big")
        assertEquals(3, round.starCredit)
        assertEquals(0, round.reducedCredits)
        // "Fast richtig": one third.
        round = newRound()
        QuizInteraction.submitTyped(QuizInteraction.typingInput(asking(round), "wrod1"), round, index, LearningMaterial(pool + big))
        assertEquals(1, round.starCredit)
        assertEquals(1, round.reducedCredits)
        // "Mit Vorschlägen", correct: one third; wrong: nothing.
        round = newRound()
        var choice = QuizInteraction.chooseInstead(QuizInteraction.choiceDelayPassed(asking(round)))
        QuizInteraction.tap(choice, choice.correctIndex, round)
        assertEquals(1, round.starCredit)
        assertEquals(1, round.reducedCredits)
        round = newRound()
        choice = QuizInteraction.chooseInstead(QuizInteraction.choiceDelayPassed(asking(round)))
        QuizInteraction.tap(choice, (choice.correctIndex + 1) % choice.options.size, round)
        assertEquals(0, round.starCredit)
        // Typed wrong: nothing.
        round = newRound()
        submit(asking(round), round, "xyz")
        assertEquals(0, round.starCredit)
    }

    @Test
    fun auswahlOnlyRoundGetsFewerStarsThanTheTypedRound() {
        fun play(useChoice: Boolean): Int {
            val round = newRound()
            repeat(round.size) {
                val state = asking(round)
                if (useChoice) {
                    val choice = QuizInteraction.chooseInstead(QuizInteraction.choiceDelayPassed(state))
                    QuizInteraction.tap(choice, choice.correctIndex, round)
                } else {
                    submit(state, round, state.question.question.word.english)
                }
            }
            return RoundStars.forCredit(round.starCredit, round.size)
        }
        assertEquals(3, play(useChoice = false))
        assertTrue(play(useChoice = true) < 3)
    }

    @Test
    fun almostCorrectDoesNotCountAsSelfTyped() {
        assertFalse(SelfTyped.counts(AnswerForm.TYPED, correct = false, kept = KeptAnswer.ALMOST_CORRECT))
        assertEquals(0, Points.forFirstAttempt(3, 0, correct = false, early = false, form = AnswerForm.TYPED, kept = KeptAnswer.ALMOST_CORRECT))
        assertEquals(4, Leitner.afterFirstAttempt(4, correct = false, form = AnswerForm.TYPED, kept = KeptAnswer.ALMOST_CORRECT))
    }

    @Test
    fun almostCorrectTexts() {
        assertEquals("Monday", shownLike("Monday", "monday"))
        assertEquals("Colr", shownLike(" to Colr ", "colr"))
        assertEquals("colour", shownLike("the colour", "colour"))
        assertEquals("x", shownLike(null, "x"))
    }

    @Test
    fun speechOnlyAfterTheAnswer() {
        val round = newRound()
        val state = asking(round)
        assertNull(QuizInteraction.speakerText(state))
        val answered = (submit(state, round, "x") as TapOutcome.Answered).state
        assertEquals("word1", QuizInteraction.speakerText(answered))
    }

    @Test
    fun choiceFallbackNoteOnlyForLevelsFourAndFiveOnTheFirstQuestion() {
        assertEquals(listOf(false, true, true), (3..5).map { showsChoiceFallbackNote(it, isRepeat = false) })
        assertFalse(showsChoiceFallbackNote(5, isRepeat = true))
        assertEquals("Auch richtig! Gesucht war: (to) take sb. for a walk", alsoCorrectText("(to) take sb. for a walk"))
    }
}

class AuswahlDelayTest {
    @Test
    fun levelThreeWaitsThreeSecondsLevelFourAndFiveFive() {
        assertEquals(3000L, auswahlDelayMs(3))
        assertEquals(5000L, auswahlDelayMs(4))
        assertEquals(5000L, auswahlDelayMs(5))
        assertEquals(AUSWAHL_DELAY_LEVEL3_MS, auswahlDelayMs(0))
        assertEquals(AUSWAHL_DELAY_HIGHER_MS, auswahlDelayMs(5))
    }

    @Test
    fun repetitionUsesTheLevelAfterTheDrop() {
        assertEquals(3000L, auswahlDelayMs(levelForAuswahlDelay(3, isRepeat = true)))
        assertEquals(3000L, auswahlDelayMs(levelForAuswahlDelay(4, isRepeat = true)))
        assertEquals(5000L, auswahlDelayMs(levelForAuswahlDelay(5, isRepeat = true)))
        assertEquals(5000L, auswahlDelayMs(levelForAuswahlDelay(4, isRepeat = false)))
        assertEquals(0, levelForAuswahlDelay(0, isRepeat = true))
    }
}
