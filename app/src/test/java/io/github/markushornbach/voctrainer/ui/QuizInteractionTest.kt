package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.MultipleChoiceGenerator
import io.github.markushornbach.voctrainer.learning.QuizRound
import io.github.markushornbach.voctrainer.learning.word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizInteractionTest {
    private val pool = (1..8).map { word("w$it", "word$it", "Wort$it") }

    private fun newRound() = QuizRound(pool.take(2), pool, MultipleChoiceGenerator(Random(1)))

    private fun asking(round: QuizRound) = QuizRoundState.Asking(round.nextQuestion()!!, round.size)

    private val QuizRoundState.Asking.wrong get() = (correctIndex + 1) % options.size

    private fun answer(state: QuizRoundState.Asking, index: Int, round: QuizRound) =
        QuizInteraction.tap(state, index, round) as TapOutcome.Answered

    @Test
    fun resultBannerEmptyBeforeTheAnswerAndInTheRetry() {
        val round = newRound()
        val state = asking(round)
        assertNull(answerResult(state))
        val solution = answer(state, state.wrong, round).state
        assertEquals(AnswerResult.WRONG, answerResult(solution))
        val retry = QuizInteraction.retry(solution, Random(2))
        assertNull(answerResult(retry))
        assertEquals(AnswerResult.CORRECT, answerResult(answer(retry, retry.correctIndex, round).state))
        assertEquals(listOf("Richtig!", "Fast richtig", "Leider falsch"), AnswerResult.entries.map { it.text })
    }

    @Test
    fun vocabularySpeaksTheEnglishWordAndNothingAfterTheAnswer() {
        val round = newRound()
        val state = asking(round)
        assertEquals(state.question.question.word.english, state.question.question.speakOnShow)
        assertEquals(state.question.question.word.english, QuizInteraction.speakerText(state))
        assertNull(QuizInteraction.autoSpeechAfterTap(state, autoSpeak = true))
        val answered = answer(state, state.correctIndex, round).state
        assertEquals(state.question.question.word.english, QuizInteraction.speakerText(answered))
    }

    @Test
    fun speechAfterEveryAnswerIncludingRetriesAndOnlyWithAutoSpeak() {
        val round = newRound()
        val plain = asking(round)
        val state = plain.copy(
            question = plain.question.copy(question = plain.question.question.copy(speakAfterAnswer = "went"))
        )
        val shown = state.question.question.speakOnShow
        assertEquals("went", QuizInteraction.autoSpeechAfterTap(state, autoSpeak = true))
        assertNull(QuizInteraction.autoSpeechAfterTap(state, autoSpeak = false))
        assertNull(QuizInteraction.autoSpeechOnRetry(state, autoSpeak = true))
        assertEquals(shown, QuizInteraction.speakerText(state))

        val solution = answer(state, state.wrong, round).state
        assertEquals("went", QuizInteraction.speakerText(solution))
        assertNull(QuizInteraction.autoSpeechAfterTap(solution, autoSpeak = true))

        // "Nochmal versuchen": the question is spoken again, as if it had just appeared.
        val retry = QuizInteraction.retry(solution, Random(2))
        assertEquals(shown, QuizInteraction.autoSpeechOnRetry(retry, autoSpeak = true))
        assertNull(QuizInteraction.autoSpeechOnRetry(retry, autoSpeak = false))
        assertEquals(shown, QuizInteraction.speakerText(retry))

        // An answer in the retry phase speaks the solution like a first answer.
        assertEquals("went", QuizInteraction.autoSpeechAfterTap(retry, autoSpeak = true))
        assertNull(QuizInteraction.autoSpeechAfterTap(retry, autoSpeak = false))
        val correctRetry = answer(retry, retry.correctIndex, round)
        assertFalse(correctRetry.countsForLevel)
        assertTrue(correctRetry.advancesAutomatically)
        assertEquals("went", QuizInteraction.speakerText(correctRetry.state))
    }

    @Test
    fun germanToEnglishSpeaksNothingBeforeTheAnswer() {
        val pool = (1..8).map { word("w$it", "word$it", "Wort$it", level = 2) }
        val round = QuizRound(pool.take(1), pool, MultipleChoiceGenerator(Random(1)))
        val state = asking(round)
        assertEquals("Wort1", state.question.question.prompt)
        assertNull(QuizInteraction.speakerText(state))
        val solution = answer(state, state.wrong, round).state
        assertEquals("word1", QuizInteraction.speakerText(solution))
        val retry = QuizInteraction.retry(solution, Random(2))
        assertNull(QuizInteraction.autoSpeechOnRetry(retry, autoSpeak = true))
        assertNull(QuizInteraction.speakerText(retry))
        assertEquals("word1", QuizInteraction.autoSpeechAfterTap(retry, autoSpeak = true))
    }

    @Test
    fun correctFirstAnswerAdvancesAutomatically() {
        val round = newRound()
        val state = asking(round)

        val outcome = answer(state, state.correctIndex, round)

        assertTrue(outcome.correct)
        assertTrue(outcome.countsForLevel)
        assertTrue(outcome.advancesAutomatically)
        assertEquals(AnswerPhase.CORRECT, outcome.state.phase)
        assertEquals(1, round.correctOnFirstAttempt)
    }

    @Test
    fun wrongAnswerShowsTheSolutionWithoutAdvancingAndAllOptionsDisabled() {
        val round = newRound()
        val state = asking(round)

        val outcome = answer(state, state.wrong, round)

        assertFalse(outcome.correct)
        assertTrue(outcome.countsForLevel)
        assertFalse(outcome.advancesAutomatically)
        assertEquals(AnswerPhase.SOLUTION, outcome.state.phase)
        assertEquals(state.wrong, outcome.state.selectedIndex)
        // Every option, including the correct one, is disabled in the solution phase.
        state.options.indices.forEach { index ->
            assertEquals(TapOutcome.Ignored, QuizInteraction.tap(outcome.state, index, round))
        }
    }

    @Test
    fun retryShowsTheSameOptionsInANewOrderWithTheCorrectOneMoved() {
        repeat(30) { seed ->
            val round = newRound()
            val state = asking(round)
            val solution = answer(state, state.wrong, round).state

            val retry = QuizInteraction.retry(solution, Random(seed))

            assertEquals(AnswerPhase.RETRY, retry.phase)
            assertNull(retry.selectedIndex)
            assertEquals(state.options.toSet(), retry.options.toSet())
            assertEquals(state.options.size, retry.options.size)
            assertNotEquals(solution.correctIndex, retry.correctIndex)
            assertEquals(state.question.question.options[state.question.question.correctIndex], retry.options[retry.correctIndex])
        }
    }

    @Test
    fun retryOnlyWorksInTheSolutionPhase() {
        val round = newRound()
        val state = asking(round)
        assertSame(state, QuizInteraction.retry(state, Random(1)))
        val correct = answer(state, state.correctIndex, round).state
        assertSame(correct, QuizInteraction.retry(correct, Random(1)))
    }

    @Test
    fun correctRetryAdvancesWrongRetryShowsTheSolutionAgain() {
        val round = newRound()
        val state = asking(round)
        val retry = QuizInteraction.retry(answer(state, state.wrong, round).state, Random(2))

        val wrongRetry = answer(retry, retry.wrong, round)
        assertFalse(wrongRetry.advancesAutomatically)
        assertEquals(AnswerPhase.SOLUTION, wrongRetry.state.phase)
        assertEquals(retry.order, wrongRetry.state.order)

        val again = QuizInteraction.retry(wrongRetry.state, Random(3))
        val correctRetry = answer(again, again.correctIndex, round)
        assertTrue(correctRetry.correct)
        assertTrue(correctRetry.advancesAutomatically)
        assertEquals(AnswerPhase.CORRECT, correctRetry.state.phase)
    }

    @Test
    fun retriesNeverCountAndTheWordIsStillRepeated() {
        val round = newRound()
        val first = asking(round)
        answer(first, first.wrong, round).state.let { solution ->
            val retry = QuizInteraction.retry(solution, Random(4))
            val wrongRetry = answer(retry, retry.wrong, round)
            assertFalse(wrongRetry.countsForLevel)
            val again = QuizInteraction.retry(wrongRetry.state, Random(5))
            assertFalse(answer(again, again.correctIndex, round).countsForLevel)
        }
        assertEquals(0, round.correctOnFirstAttempt)
        assertEquals(listOf(first.question.question.word), round.wrongOnFirstAttempt)

        val second = asking(round)
        answer(second, second.correctIndex, round)

        val repeat = asking(round)
        assertTrue(repeat.question.isRepeat)
        assertEquals(first.question.question.word, repeat.question.question.word)
        assertFalse(answer(repeat, repeat.correctIndex, round).countsForLevel)
        assertNull(round.nextQuestion())
        assertEquals(1, round.correctOnFirstAttempt)
    }
}
