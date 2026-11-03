package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerCheckerTest {
    @Test
    fun optionalToIsAcceptedWithOrWithoutTo() {
        val expected = "(to) climb trees"
        assertTrue(AnswerChecker.isCorrectEnglish("climb trees", expected))
        assertTrue(AnswerChecker.isCorrectEnglish("to climb trees", expected))
        assertTrue(AnswerChecker.isCorrectEnglish("(to) climb trees", expected))
        assertFalse(AnswerChecker.isCorrectEnglish("climb tree", expected))
        assertFalse(AnswerChecker.isCorrectEnglish("to", expected))
    }

    @Test
    fun toIsNotStrippedFromEntriesWithoutOptionalTo() {
        assertTrue(AnswerChecker.isCorrectEnglish("to", "to"))
        assertFalse(AnswerChecker.isCorrectEnglish("", "to"))
        assertTrue(AnswerChecker.isCorrectEnglish("to the left", "to the left"))
        assertFalse(AnswerChecker.isCorrectEnglish("the left", "to the left"))
    }

    @Test
    fun ignoresCaseAndSurroundingWhitespace() {
        assertTrue(AnswerChecker.isCorrectEnglish("  Climb Trees ", "(to) climb trees"))
        assertTrue(AnswerChecker.isCorrectEnglish(" WEATHER ", "weather"))
        assertTrue(AnswerChecker.isCorrectGerman("  tüte ", listOf("Tasche", "Tüte")))
    }

    @Test
    fun everyGermanMeaningIsCorrect() {
        val meanings = listOf("Tasche", "Beutel", "Tüte")
        meanings.forEach { assertTrue(AnswerChecker.isCorrectGerman(it, meanings)) }
        assertFalse(AnswerChecker.isCorrectGerman("Koffer", meanings))
        assertFalse(AnswerChecker.isCorrectGerman("  ", meanings))
    }

    @Test
    fun germanPromptShowsAllMeanings() {
        assertEquals("Tasche; Beutel; Tüte", AnswerChecker.germanPrompt(listOf("Tasche", "Beutel", "Tüte")))
        assertEquals("Tänzer; Künstler", AnswerChecker.germanPrompt(listOf("Tänzer/in", "Künstler/in")))
    }
}
