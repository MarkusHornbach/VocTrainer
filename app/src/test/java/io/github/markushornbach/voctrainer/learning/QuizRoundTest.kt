package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class QuizRoundTest {
    private val today = LocalDate.of(2026, 10, 6)
    private val zone = ZoneOffset.UTC
    private val pool = (1..12).map { word("w$it", "word$it", "Wort$it", level = it % 3) }

    @Test
    fun leitnerFirstAttemptChangesLevelWithinBounds() {
        assertEquals(1, Leitner.afterFirstAttempt(0, correct = true))
        assertEquals(5, Leitner.afterFirstAttempt(4, correct = true))
        assertEquals(5, Leitner.afterFirstAttempt(5, correct = true))
        assertEquals(2, Leitner.afterFirstAttempt(3, correct = false))
        assertEquals(0, Leitner.afterFirstAttempt(0, correct = false))
    }

    @Test
    fun withoutReviewDatesAllWordsAreDueReviewsFirstThenNewWords() {
        // 4 words each on level 0, 1, 2, none reviewed yet: levels 1 and 2 are due reviews.
        val picked = QuizRound.pickWords(pool, Random(7), today, zone)

        assertEquals(10, picked.words.size)
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2, 2, 0, 0), picked.words.map { it.leitnerLevel })
        assertTrue(picked.earlyIds.isEmpty())
        assertEquals(3, QuizRound.pickWords(pool.take(3), Random(7), today, zone).words.size)
    }

    @Test
    fun wrongWordsAreAskedAgainAndOnlyFirstAttemptsCount() {
        val words = pool.take(3)
        val round = QuizRound(words, pool, MultipleChoiceGenerator(Random(1)))

        val first = round.nextQuestion()!!
        assertEquals(1, first.number)
        assertFalse(first.isRepeat)
        assertTrue(round.recordAnswer(correct = false))

        val second = round.nextQuestion()!!
        assertTrue(round.recordAnswer(correct = true))
        val third = round.nextQuestion()!!
        assertEquals(3, third.number)
        assertTrue(round.recordAnswer(correct = true))

        val repeat = round.nextQuestion()!!
        assertTrue(repeat.isRepeat)
        assertEquals(first.question.word, repeat.question.word)
        assertFalse("Repeats do not count as first attempt", round.recordAnswer(correct = false))

        val repeatAgain = round.nextQuestion()!!
        assertEquals(first.question.word, repeatAgain.question.word)
        assertFalse(round.recordAnswer(correct = true))

        assertNull(round.nextQuestion())
        assertEquals(2, round.correctOnFirstAttempt)
        assertEquals(listOf(first.question.word), round.wrongOnFirstAttempt)
        assertEquals(setOf("w1", "w2", "w3"), setOf(first, second, third).map { it.question.word.id }.toSet())
    }

    @Test
    fun verbRoundsUseTheVerbGeneratorAndRepeatWrongVerbs() {
        val verbs = listOf("take" to "took", "swim" to "swam", "win" to "won", "bite" to "bit").mapIndexed { i, (base, past) ->
            QuizWord("v$i", "(to) $base", listOf("x$i"), 1, "A", 0, storedType = WordType.VERB, verbForms = VerbForms(past, null))
        }
        val round = QuizRound(verbs.take(2), verbs, VerbDistractorGenerator(Random(2)))

        val first = round.nextQuestion()!!
        assertEquals("Wie lautet das simple past?", first.question.instruction)
        assertEquals(first.question.word.verbForms!!.simplePast, first.question.options[first.question.correctIndex])
        round.recordAnswer(correct = false)
        round.nextQuestion()!!
        round.recordAnswer(correct = true)
        val repeat = round.nextQuestion()!!
        assertTrue(repeat.isRepeat)
        assertEquals(first.question.word, repeat.question.word)
    }

    @Test
    fun repeatedQuestionsGetFreshlyDrawnOptions() {
        val words = exampleWords()
        val target = words.first { it.english == "pet" }
        val round = QuizRound(listOf(target), words, MultipleChoiceGenerator(Random(5)))

        val optionSets = mutableSetOf<List<String>>()
        repeat(5) {
            optionSets += round.nextQuestion()!!.question.options
            round.recordAnswer(correct = false)
        }

        assertTrue(optionSets.size > 1)
    }

    @Test
    fun creditsSumUpFirstAttemptsOnly() {
        val round = QuizRound(pool.take(3), pool, MultipleChoiceGenerator(Random(1)))
        round.nextQuestion(); round.recordAnswer(correct = true)                         // plain: 3
        round.nextQuestion(); round.recordAnswer(correct = true, credit = RoundStars.REDUCED_CREDIT)
        round.nextQuestion(); round.recordAnswer(correct = false)                        // 0, repeated
        assertEquals(4, round.starCredit)
        assertEquals(1, round.reducedCredits)
        // The repetition never changes the credit.
        round.nextQuestion()
        assertFalse(round.recordAnswer(correct = true))
        assertEquals(4, round.starCredit)
        assertEquals(1, round.reducedCredits)
        assertEquals(2, round.correctOnFirstAttempt)
    }
}
