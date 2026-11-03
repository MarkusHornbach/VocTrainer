package io.github.markushornbach.voctrainer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualEntryTest {
    private fun valid(input: ManualEntryInput) = (parseManualEntry(input) as ManualEntryResult.Valid).vocabulary

    @Test
    fun splitsMeaningsAtSemicolonKeepingOrder() {
        val vocabulary = valid(ManualEntryInput(" bag ", "Tasche; Beutel ;;Tüte", "1", " ", ""))

        assertEquals("bag", vocabulary.english)
        assertEquals(listOf("Tasche", "Beutel", "Tüte"), vocabulary.germanMeanings)
        assertEquals(1, vocabulary.unit)
        assertNull(vocabulary.part)
        assertNull(vocabulary.extra)
        assertNull(vocabulary.page)
        assertNull(vocabulary.wordType)
    }

    @Test
    fun pageIsOptional() {
        assertEquals(150, valid(ManualEntryInput("bag", "Tasche", "1", page = " 150 ")).page)
        assertNull(valid(ManualEntryInput("bag", "Tasche", "1", page = "  ")).page)
        assertTrue(parseManualEntry(ManualEntryInput("bag", "Tasche", "1", page = "S. 150")) is ManualEntryResult.Invalid)
        assertTrue(parseManualEntry(ManualEntryInput("bag", "Tasche", "1", page = "0")) is ManualEntryResult.Invalid)
    }

    @Test
    fun aSingleWordIsTheCoreWord() {
        assertEquals("bag", valid(ManualEntryInput("bag", "Tasche", "1")).coreWord)
        assertEquals("walk", valid(ManualEntryInput("(to) walk", "gehen", "1")).coreWord)
    }

    @Test
    fun severalWordsNeedATappedWord() {
        val input = ManualEntryInput("care for sb.", "jn. pflegen", "1")
        assertEquals(
            ManualEntryResult.Invalid("Bitte tippe ein Wort an.", ManualEntryField.CORE_WORD),
            parseManualEntry(input)
        )
        assertEquals("for", valid(input.copy(coreWord = CoreWordChoice.tap("for"))).coreWord)
        // A tapped word that is not in the text (any more) does not count.
        assertTrue(parseManualEntry(input.copy(coreWord = CoreWordChoice.tap("about"))) is ManualEntryResult.Invalid)
    }

    @Test
    fun englishWithoutAnyWordIsAnErrorAtTheEnglishField() {
        assertEquals(
            ManualEntryResult.Invalid("Bitte gib mindestens ein englisches Wort ein.", ManualEntryField.ENGLISH),
            parseManualEntry(ManualEntryInput("…", "x", "1"))
        )
    }

    @Test
    fun rejectsMissingRequiredFields() {
        listOf(
            ManualEntryInput("", "Tasche", "1"),
            ManualEntryInput("bag", " ; ", "1"),
            ManualEntryInput("bag", "Tasche", ""),
            ManualEntryInput("bag", "Tasche", "eins"),
            ManualEntryInput("bag", "Tasche", "0")
        ).forEach { assertTrue(it.toString(), parseManualEntry(it) is ManualEntryResult.Invalid) }
    }
}
