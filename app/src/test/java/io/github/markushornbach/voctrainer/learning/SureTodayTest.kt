package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SureTodayTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 10, 16)

    /** Epoch millis of [daysAgo] days before [today], at noon. */
    private fun reviewed(daysAgo: Long) = today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun typeable(id: String, level: Int, reviewedAt: Long?, unit: Int = 1) = QuizWord(
        id, "house", listOf("Haus"), unit, null, level, lastReviewedAt = reviewedAt, coreWord = "house"
    )

    /** An entry with the unused flag choiceOnly: it has a core word and is typed like every word. */
    private fun choiceOnlyFlagged(id: String, level: Int, reviewedAt: Long?) = QuizWord(
        id, "yes", listOf("ja"), 1, null, level, lastReviewedAt = reviewedAt, coreWord = "yes"
    )

    private fun canBecomeSure(word: QuizWord) = SureToday.canBecomeSure(word, today, zone)

    @Test
    fun levelThreeAskedOnAnEarlierDayCounts() {
        assertTrue(canBecomeSure(typeable("a", 3, reviewed(1))))
        // Not due yet (level 3 rests longer), but level 4 only needs an earlier day.
        assertTrue(canBecomeSure(typeable("b", 3, reviewed(2))))
        // No review date (e.g. set in the test app) counts as an earlier day.
        assertTrue(canBecomeSure(typeable("c", 3, null)))
    }

    @Test
    fun levelThreeAskedTodayDoesNotCount() {
        assertFalse(canBecomeSure(typeable("a", 3, reviewed(0))))
    }

    @Test
    fun otherLevelsDoNotCount() {
        listOf(0, 1, 2, 4, 5).forEach { level ->
            assertFalse("level $level", canBecomeSure(typeable("a", level, reviewed(20))))
        }
    }

    @Test
    fun choiceOnlyFlaggedWordsFollowTheTypedLadder() {
        assertTrue(canBecomeSure(choiceOnlyFlagged("a", 3, reviewed(1))))
        assertFalse(canBecomeSure(choiceOnlyFlagged("b", 3, reviewed(0))))
        assertFalse(canBecomeSure(choiceOnlyFlagged("c", 2, reviewed(5))))
    }

    @Test
    fun irregularVerbsUseTheirOwnRules() {
        val verb = { level: Int, at: Long? ->
            QuizWord("v", "catch", listOf("fangen"), 1, null, level, verbForms = VerbForms("caught", "caught"), lastReviewedAt = at)
        }
        assertTrue(canBecomeSure(verb(3, reviewed(1))))
        assertFalse(canBecomeSure(verb(3, reviewed(0))))
    }

    @Test
    fun countFollowsTheSelectionIncludingPhrases() {
        // Phrases are typed like words.
        val phrase = QuizWord(
            "p", "on the left", listOf("links"), 1, null, 3, storedType = WordType.PHRASE, lastReviewedAt = reviewed(1),
            coreWord = "left"
        )
        val typedPhrase = QuizWord(
            "q", "Have you ever …?", listOf("Hast du jemals …?"), 1, null, 3,
            storedType = WordType.PHRASE, lastReviewedAt = reviewed(1), coreWord = "ever"
        )
        val words = listOf(
            typeable("a", 3, reviewed(1)),
            typeable("b", 3, reviewed(0)),
            typeable("other unit", 3, reviewed(1), unit = 2),
            choiceOnlyFlagged("c", 3, reviewed(3)),
            phrase,
            typedPhrase
        )
        val unitOne = QuizFilter(unit = UnitChoice.Number(1))
        assertTrue(typedPhrase.isTypeable)
        assertEquals(4, SureToday.count(QuizSelection.filter(words, unitOne), today, zone))
    }
}
