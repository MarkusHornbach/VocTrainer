package io.github.markushornbach.voctrainer.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextTest {
    @Test
    fun keepsWordsInBracketsWithoutTheBrackets() {
        assertEquals("to be allowed to", SpeechText.english("(to) be allowed to"))
    }

    @Test
    fun spellsOutSomebodyAndSomething() {
        assertEquals("to take somebody for a walk", SpeechText.english("(to) take sb. for a walk"))
        assertEquals("to tell somebody something", SpeechText.english("(to) tell sb. sth."))
        assertEquals("Somebody is here.", SpeechText.english("Sb. is here."))
        assertEquals("Something's wrong", SpeechText.english("Sth.'s wrong"))
    }

    @Test
    fun readsSlashesAsOrAndDropsEllipses() {
        assertEquals("every Monday or Tuesday", SpeechText.english("every Monday/Tuesday/…"))
        assertEquals("every Monday or Tuesday", SpeechText.english("every Monday/Tuesday/..."))
        assertEquals("was or were", SpeechText.english("was / were"))
        assertEquals("somebody or something", SpeechText.english("sb./sth."))
        assertEquals("is allowed", SpeechText.english("… is allowed"))
    }

    @Test
    fun collapsesWhitespaceAndKeepsOtherText() {
        assertEquals("Keep your dog on a lead.", SpeechText.english("  Keep   your dog on a lead. "))
        assertEquals("weather", SpeechText.english("weather"))
        assertEquals("", SpeechText.english(" … "))
    }
}
