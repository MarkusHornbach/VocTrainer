package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.settings.QuizMode
import org.junit.Assert.assertEquals
import org.junit.Test

class QuizSummaryTest {
    private val parts = listOf(null, "A", "B", "Pet diary")

    @Test
    fun summarizesTheSelection() {
        assertEquals(
            "Unit 1 · Einstieg, A · S. 150–152 · 67 Wörter",
            selectionSummary(QuizFilter(UnitChoice.Number(1), setOf("A", null), 150, 152), parts, 67)
        )
        assertEquals("Alle Units · 187 Wörter", selectionSummary(QuizFilter(), emptyList(), 187))
        assertEquals(
            "Unit 2 · Alle Parts · S. 163 · 1 Wort",
            selectionSummary(QuizFilter(UnitChoice.Number(2), pageFrom = 163), parts, 1)
        )
        assertEquals("Ohne Unit · Alle Parts · 2 Wörter", selectionSummary(QuizFilter(UnitChoice.WithoutUnit), parts, 2))
    }

    @Test
    fun verbModeCountsVerbsAndNeverMentionsPhrases() {
        assertEquals(
            "Unit 1 · A · 12 Verben",
            selectionSummary(QuizFilter(UnitChoice.Number(1), setOf("A")), parts, 12, QuizMode.VERBS)
        )
        assertEquals("Alle Units · 1 Verb", selectionSummary(QuizFilter(), emptyList(), 1, QuizMode.VERBS))
    }

    /** The level explanation itself is on the info page (see [InfoTextsTest]). */
    @Test
    fun levelNames() {
        assertEquals(listOf("neu", "am Üben", "am Üben", "am Üben", "gelernt", "gemeistert"), (0..5).map(::levelName))
    }

    @Test
    fun nextRoundTextNamesLearnableWordsFirstThenNewWords() {
        val words = QuizMode.VOCABULARY
        assertEquals("Nächste Runde: 3 Wörter kannst du fertig lernen.", nextRoundText(3, 0, words))
        assertEquals("Nächste Runde: 1 Wort kannst du fertig lernen.", nextRoundText(1, 5, words))
        assertEquals("Nächste Runde: 3 Verben kannst du fertig lernen.", nextRoundText(3, 0, QuizMode.VERBS))
        assertEquals("Nächste Runde: 1 Verb kannst du fertig lernen.", nextRoundText(1, 2, QuizMode.VERBS))
        assertEquals("Nächste Runde: 4 neue Wörter.", nextRoundText(0, 4, words))
        assertEquals("Nächste Runde: 1 neues Wort.", nextRoundText(0, 1, words))
        assertEquals("Nächste Runde: 4 neue Verben.", nextRoundText(0, 4, QuizMode.VERBS))
        assertEquals("Nächste Runde: 1 neues Verb.", nextRoundText(0, 1, QuizMode.VERBS))
        assertEquals(null, nextRoundText(0, 0, words))
    }
}
