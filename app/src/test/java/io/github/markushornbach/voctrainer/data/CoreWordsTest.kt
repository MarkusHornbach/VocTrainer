package io.github.markushornbach.voctrainer.data

import io.github.markushornbach.voctrainer.learning.EXAMPLE_BOOK
import io.github.markushornbach.voctrainer.learning.WordType
import io.github.markushornbach.voctrainer.learning.exampleFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every active vocabulary entry has a core word ("Wort zum Tippen"). */
class CoreWordsTest {
    @Test
    fun deriveTakesTheOnlyWord() {
        assertEquals("walk", CoreWords.derive("walk"))
        assertEquals("walk", CoreWords.derive("(to) walk"))
        assertNull(CoreWords.derive("care for sb."))
        assertNull(CoreWords.derive("sth. to eat"))
        assertEquals("e.g.", CoreWords.derive("e.g."))
        assertEquals("okay", CoreWords.derive("…, okay?"))
        assertNull(CoreWords.derive("We laughed a lot."))
        // As written in the entry.
        assertEquals("Hello", CoreWords.derive("Hello!"))
        assertEquals("somebody", CoreWords.derive("somebody?"))
        assertEquals("yet", CoreWords.derive("... yet"))
        assertNull(CoreWords.derive("…"))
        assertNull(CoreWords.derive("(to) ..."))
    }

    @Test
    fun everyDerivedWordPassesTheImportCheck() {
        val texts = listOf(
            "walk", "(to) walk", "e.g.", "…, okay?", "Hello!", "somebody?", "(not) bad", "Mr.", "don't", "Dad's",
            "(to) sb. help", "well-known", "„quote“", "yes – no", "a.m.", "o'clock", "... yet", "nice/…", "etc."
        ) + exampleFile(EXAMPLE_BOOK).entries.map { it.english }
        for (text in texts) {
            val derived = CoreWords.derive(text) ?: continue
            assertTrue("$text -> $derived", VocTrainerJson.isValidCoreWord(derived, text))
        }
        for (text in texts) {
            for (candidate in CoreWords.candidates(text)) {
                assertTrue("$text -> $candidate", VocTrainerJson.isValidCoreWord(candidate, text))
            }
        }
    }

    @Test
    fun candidatesInOrderWithoutDuplicatesBracketsAndPlaceholders() {
        assertEquals(listOf("care", "for"), CoreWords.candidates("(to) care for sb./sth. …"))
        assertEquals(listOf("take", "for", "a", "walk"), CoreWords.candidates("(to) take sb. for a walk"))
        assertEquals(listOf("as", "fast"), CoreWords.candidates("(not) as fast as"))
        assertEquals(listOf("Have", "you", "ever"), CoreWords.candidates("Have you ever …?"))
        assertEquals(listOf("every", "Monday", "Tuesday"), CoreWords.candidates("every Monday/Tuesday/…"))
        assertEquals(listOf("make", "up", "mind"), CoreWords.candidates("(to) make up one's mind"))
        assertEquals(listOf("The", "cat"), CoreWords.candidates("The cat, the CAT!"))
        assertEquals(emptyList<String>(), CoreWords.candidates("… (sth.)"))
    }

    @Test
    fun candidateForMatchesIgnoringCaseAndReturnsTheSpellingOfTheText() {
        assertEquals("Hello", CoreWords.candidateFor("hello", "Hello!"))
        assertNull(CoreWords.candidateFor("bye", "Hello!"))
        assertNull(CoreWords.candidateFor(null, "Hello!"))
    }

    // --- normalization ---

    private fun entry(id: String, english: String, coreWord: String? = null, choiceOnly: Boolean = false, deleted: Boolean = false) =
        Vocabulary(
            id = id, english = english, germanMeanings = listOf("x"), unit = 1, leitnerLevel = 4, highestLevel = 5,
            updatedAt = 777L, lastReviewedAt = 666L, isDeleted = deleted, coreWord = coreWord, choiceOnly = choiceOnly,
            firstTypedCorrectAt = 555L, wordType = WordType.PHRASE.takeIf { ' ' in english }
        )

    @Test
    fun normalizationClearsDerivesAndIgnores() {
        val choiceOnly = entry("c", "Hello!", coreWord = "Hello", choiceOnly = true)
        val single = entry("s", "(to) walk")
        val multi = entry("m", "care for sb.")
        val choiceOnlyMulti = entry("x", "every Monday/Tuesday/…", choiceOnly = true)

        assertEquals(choiceOnly.copy(choiceOnly = false), CoreWords.normalize(choiceOnly))
        assertEquals(single.copy(coreWord = "walk"), CoreWords.normalize(single))
        assertEquals(multi.copy(isDeleted = true), CoreWords.normalize(multi))
        assertEquals(choiceOnlyMulti.copy(choiceOnly = false, isDeleted = true), CoreWords.normalize(choiceOnlyMulti))
    }

    @Test
    fun normalizationKeepsLevelsAndUpdatedAt() {
        val all = listOf(entry("s", "walk"), entry("m", "care for sb."), entry("c", "yes", choiceOnly = true))
        for (changed in CoreWords.normalizeAll(all)) {
            assertEquals(4, changed.leitnerLevel)
            assertEquals(5, changed.highestLevel)
            assertEquals(777L, changed.updatedAt)
            assertEquals(666L, changed.lastReviewedAt)
            assertEquals(555L, changed.firstTypedCorrectAt)
        }
    }

    @Test
    fun ignoredAndCompleteEntriesStayAsTheyAre() {
        val ignored = entry("i", "care for sb.", choiceOnly = true, deleted = true)
        val ignoredSingle = entry("j", "walk", deleted = true)
        val complete = entry("k", "care for sb.", coreWord = "care")
        assertSame(ignored, CoreWords.normalize(ignored))
        assertSame(ignoredSingle, CoreWords.normalize(ignoredSingle))
        assertSame(complete, CoreWords.normalize(complete))
        assertEquals(emptyList<Vocabulary>(), CoreWords.normalizeAll(listOf(ignored, ignoredSingle, complete)))
    }

    @Test
    fun normalizationIsIdempotent() {
        val all = listOf(
            entry("a", "walk"), entry("b", "care for sb."), entry("c", "yes", choiceOnly = true),
            entry("d", "dog", coreWord = "dog"), entry("e", "…", deleted = false)
        )
        val changed = CoreWords.normalizeAll(all).associateBy { it.id }
        val once = all.map { changed[it.id] ?: it }
        assertEquals(4, changed.size)
        assertEquals(emptyList<Vocabulary>(), CoreWords.normalizeAll(once))
    }

    /** Guard: after the normalization no active vocabulary entry is "not typeable". */
    @Test
    fun noActiveEntryIsUntypeableAfterNormalization() {
        val texts = exampleFile(EXAMPLE_BOOK).entries.map { it.english } + listOf(
            "…", "(to) ...", "sb.", "Hello!", "e.g.", "Have you ever …?", "(not) as fast as", "We laughed a lot."
        )
        val entries = texts.flatMapIndexed { index, text ->
            listOf(
                entry("a$index", text),
                entry("b$index", text, choiceOnly = true),
                entry("c$index", text, coreWord = CoreWords.candidates(text).firstOrNull()),
                entry("d$index", text, coreWord = " "),
                entry("e$index", text, deleted = true)
            )
        }
        val changed = CoreWords.normalizeAll(entries).associateBy { it.id }
        val normalized = entries.map { changed[it.id] ?: it }
        val active = normalized.filterNot { it.isDeleted }
        assertTrue(active.isNotEmpty())
        assertTrue(active.all { it.toQuizWord().isTypeable })
        assertFalse(normalized.any { !it.isDeleted && it.choiceOnly })
        // Levels never change.
        assertTrue(normalized.all { it.leitnerLevel == 4 && it.highestLevel == 5 && it.updatedAt == 777L })
    }
}
