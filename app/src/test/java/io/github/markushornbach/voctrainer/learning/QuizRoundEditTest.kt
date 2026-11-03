package io.github.markushornbach.voctrainer.learning

import io.github.markushornbach.voctrainer.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The edit button: the question is rebuilt from the edited word, deleted words are skipped. */
class QuizRoundEditTest {
    private val pool = (1..8).map { word("w$it", "word$it", "Wort$it", level = 1) }

    private fun newRound(count: Int = 3) = QuizRound(pool.take(count), pool, MultipleChoiceGenerator(Random(1)))

    private fun ReaskResult.question() = (this as ReaskResult.Question).question

    @Test
    fun editButtonIsOffByDefault() {
        assertFalse(AppSettings.EDIT_BUTTON_IN_QUIZ_DEFAULT)
    }

    @Test
    fun openQuestionIsRebuiltFromTheEditedWord() {
        val round = newRound()
        val before = round.nextQuestion()!!
        assertEquals("word1", before.question.prompt)

        val edited = pool[0].copy(english = "edited1", germanMeanings = listOf("Neu1"))
        val after = round.reaskAfterEdit(edited).question()

        assertEquals(edited, after.question.word)
        assertEquals("edited1", after.question.prompt)
        assertEquals(1, after.number)
        assertFalse(after.isRepeat)
        // The pool follows, so the edited word is no stale distractor for later questions.
        assertTrue(round.pool.any { it.english == "edited1" } && round.pool.none { it.english == "word1" })
        // It is still the first attempt of the word, and the round goes on with the second word.
        assertTrue(round.recordAnswer(correct = true))
        assertEquals("word2", round.nextQuestion()!!.question.prompt)
    }

    @Test
    fun theQuestionFormFollowsTheNewLevel() {
        val round = newRound()
        round.nextQuestion()!!
        val raised = pool[0].copy(leitnerLevel = 3)
        val question = round.reaskAfterEdit(raised).question()
        // From level 2 the question is asked the other way round: the German meaning is shown.
        assertEquals("Wort1", question.question.prompt)
    }

    @Test
    fun deletedWordIsSkippedWithItsRepetition() {
        val round = newRound()
        round.nextQuestion()!!
        round.recordAnswer(correct = false)   // word1 is queued for the repetition
        round.nextQuestion()!!
        round.recordAnswer(correct = true)
        round.nextQuestion()!!
        round.recordAnswer(correct = true)
        val repeat = round.nextQuestion()!!
        assertTrue(repeat.isRepeat)

        assertTrue(round.reaskAfterEdit(null) === ReaskResult.Next)
        assertNull(round.nextQuestion())
        assertTrue(round.pool.none { it.id == "w1" })
    }

    @Test
    fun deletedWordIsRemovedFromTheQueueBeforeItsTurn() {
        val round = newRound()
        round.nextQuestion()!!
        assertTrue(round.reaskAfterEdit(null) === ReaskResult.Next)
        assertEquals("word2", round.nextQuestion()!!.question.prompt)
        round.recordAnswer(correct = true)
        assertEquals("word3", round.nextQuestion()!!.question.prompt)
    }

    @Test
    fun wrongAnsweredWordKeepsItsRecordAndIsAskedOnceMore() {
        val round = newRound(2)
        round.nextQuestion()!!
        assertTrue(round.recordAnswer(correct = false))

        val edited = pool[0].copy(germanMeanings = listOf("Neu1"))
        val again = round.reaskAfterEdit(edited).question()
        assertTrue(again.isRepeat)
        assertEquals(1, again.number)
        // The first-attempt record stays wrong; answering now is not a first attempt.
        assertEquals(0, round.correctOnFirstAttempt)
        assertEquals(listOf("w1"), round.wrongOnFirstAttempt.map { it.id })
        assertFalse(round.recordAnswer(correct = true))

        // The repetition was taken out of the queue: it does not come again at the end.
        assertEquals("word2", round.nextQuestion()!!.question.prompt)
        round.recordAnswer(correct = true)
        assertNull(round.nextQuestion())
    }

    @Test
    fun correctlyAnsweredWordIsNotAskedAgain() {
        val round = newRound(2)
        round.nextQuestion()!!
        assertTrue(round.recordAnswer(correct = true))

        assertTrue(round.reaskAfterEdit(pool[0].copy(english = "edited1")) === ReaskResult.Next)
        assertEquals(1, round.correctOnFirstAttempt)
        assertEquals("word2", round.nextQuestion()!!.question.prompt)
    }

    @Test
    fun nothingToDoBeforeTheFirstQuestionOrAfterTheEnd() {
        val round = newRound(1)
        assertTrue(round.reaskAfterEdit(pool[0]) === ReaskResult.Next)
        round.nextQuestion()!!
        round.recordAnswer(correct = true)
        assertNull(round.nextQuestion())
        assertTrue(round.reaskAfterEdit(pool[0]) === ReaskResult.Next)
    }
}
