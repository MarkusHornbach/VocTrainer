package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GermanToEnglishTest {
    private fun atLevel(words: List<QuizWord>, level: Int) = words.map { it.copy(leitnerLevel = level) }

    @Test
    fun directionFollowsTheLevel() {
        assertEquals(
            listOf(
                QuestionDirection.ENGLISH_TO_GERMAN, QuestionDirection.ENGLISH_TO_GERMAN,
                QuestionDirection.GERMAN_TO_ENGLISH, QuestionDirection.GERMAN_TO_ENGLISH,
                QuestionDirection.GERMAN_TO_ENGLISH, QuestionDirection.GERMAN_TO_ENGLISH
            ),
            (0..5).map(QuestionDirection::forLevel)
        )
    }

    @Test
    fun levelOneIsStillEnglishToGerman() {
        val words = atLevel(exampleWords(), 1)
        val question = MultipleChoiceGenerator(Random(1)).generate(words.first { it.english == "pet" }, words)!!
        assertEquals("pet", question.prompt)
        assertNull(question.instruction)
        assertEquals("Haustier", question.options[question.correctIndex])
        assertEquals("pet", question.speakOnShow)
        assertNull(question.speakAfterAnswer)
    }

    @Test
    fun promptShowsAllMeaningsWithoutGenderEndings() {
        val target = word("t", "bag", "Tasche", "Beutel", "Tüte", level = 2)
        val dancer = word("d", "dancer", "Tänzer/in", level = 2)
        val others = (1..4).map { word("o$it", "other$it", "Anderes$it") }
        val generator = MultipleChoiceGenerator(Random(1))

        val question = generator.generate(target, listOf(target, dancer) + others)!!
        assertEquals("Tasche; Beutel; Tüte", question.prompt)
        assertNull(question.instruction)
        assertEquals("bag", question.options[question.correctIndex])
        assertEquals("bag", question.solution)
        assertEquals("Tänzer", generator.generate(dancer, listOf(target, dancer) + others)!!.prompt)
    }

    @Test
    fun speaksTheEnglishWordOnlyAfterTheAnswer() {
        val words = atLevel(exampleWords(), 3)
        val target = words.first { it.english == "pet" }
        val question = MultipleChoiceGenerator(Random(1)).generate(target, words)!!
        assertNull(question.speakOnShow)
        assertEquals("pet", question.speakAfterAnswer)
    }

    @Test
    fun everyExampleWordGetsFourDistinctEnglishOptionsThatAreNeverAlsoCorrect() {
        val words = atLevel(exampleWords(EXAMPLE_BOOK), 2)
        val byEnglish = words.associateBy { it.english }
        val generator = MultipleChoiceGenerator(Random(7))
        for (target in words) {
            if (!generator.canGenerate(target, words)) continue
            val question = generator.generate(target, words)!!
            assertEquals(target.english, 4, question.options.map { normalizeText(it) }.toSet().size)
            assertEquals(target.english, question.options[question.correctIndex])
            question.options.filterIndexed { index, _ -> index != question.correctIndex }.forEach { option ->
                val distractor = byEnglish.getValue(option)
                assertTrue(
                    "${target.english}: $option shares a meaning",
                    distractor.meaningKeys.none { it in target.meaningKeys }
                )
            }
        }
    }

    @Test
    fun canGenerateChecksDistinctEnglishTextsForGermanToEnglish() {
        // Three candidates, but two of them share the English text: enough German meanings,
        // not enough English texts.
        val target = word("t", "bag", "Tasche")
        val candidates = listOf(
            word("a", "bus", "Bus", unit = 1),
            word("b", "bus", "Omnibus", unit = 2),
            word("c", "car", "Auto")
        )
        val generator = MultipleChoiceGenerator(Random(1))

        assertTrue(generator.canGenerate(target, listOf(target) + candidates))
        val upper = target.copy(leitnerLevel = 2)
        assertFalse(generator.canGenerate(upper, listOf(upper) + candidates))
        assertNull(generator.generate(upper, listOf(upper) + candidates))
        assertTrue(generator.canGenerate(upper, listOf(upper) + candidates + word("d", "bike", "Rad")))
    }

    @Test
    fun repeatKeepsTheDirectionOfTheFirstQuestion() {
        val words = atLevel(exampleWords(), 1)
        val target = words.first { it.english == "pet" }
        val round = QuizRound(listOf(target), words, MultipleChoiceGenerator(Random(1)))

        val first = round.nextQuestion()!!
        assertEquals("pet", first.question.prompt)
        // A correct first answer would raise the stored level to 2; the round keeps its snapshot.
        round.recordAnswer(correct = false)
        val repeat = round.nextQuestion()!!
        assertTrue(repeat.isRepeat)
        assertEquals("pet", repeat.question.prompt)

        val upper = target.copy(leitnerLevel = 2)
        val germanRound = QuizRound(listOf(upper), words, MultipleChoiceGenerator(Random(1)))
        assertEquals("Haustier", germanRound.nextQuestion()!!.question.prompt)
        germanRound.recordAnswer(correct = false)
        assertEquals("Haustier", germanRound.nextQuestion()!!.question.prompt)
    }
}
