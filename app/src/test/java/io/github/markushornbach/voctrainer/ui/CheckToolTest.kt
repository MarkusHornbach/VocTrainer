package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.AlsoCorrectIndex
import io.github.markushornbach.voctrainer.learning.LearningMaterial
import io.github.markushornbach.voctrainer.learning.QuizWord
import io.github.markushornbach.voctrainer.learning.word
import org.junit.Assert.assertEquals
import org.junit.Test

/** The result line of "Prüfung testen" on the page "Testwerkzeuge". */
class CheckToolTest {
    private val colour = word("colour", "colour", "Farbe").copy(coreWord = "colour")
    private val cat = word("cat", "cat", "Katze").copy(coreWord = "cat")
    private val cut = word("cut", "(to) cut", "schneiden").copy(coreWord = "cut")
    private val big = word("big", "big", "groß").copy(coreWord = "big")
    private val large = word("large", "large", "groß").copy(coreWord = "large")
    private val phrase = word("p", "How are you?", "Wie geht's?")
    private val words = listOf(colour, cat, cut, big, large, phrase)
    private val index = AlsoCorrectIndex(words)
    private val material = LearningMaterial(words)

    private fun result(answer: String, word: QuizWord) =
        TestToolsTexts.checkResult(answer, word, index, material)

    @Test
    fun everyResultHasItsLine() {
        assertEquals("Richtig", result("Colour", colour))
        assertEquals("Auch richtig (Eintrag „large“)", result("large", big))
        assertEquals("Fast richtig – Tippfehler (Stelle 4)", result("colur", colour))
        assertEquals("Fast richtig – Tippfehler (Stelle 4–5)", result("coluor", colour))
        assertEquals("Fast richtig – amerikanisch (Regel our → or)", result("color", colour))
        assertEquals("Fast richtig – Gesucht war „big“ (Tippfehler von „large“, Stelle 5)", result("larg", big))
        assertEquals("Falsch – mehr als ein Fehler", result("clr", colour))
        assertEquals("Falsch – ergibt Lernstoff-Wort „cut“", result("cut", cat))
        assertEquals("Falsch – Wort kürzer als 4 Buchstaben", result("cay", cat))
        assertEquals("Falsch – nicht tippbar", result("How are you?", phrase))
    }
}
