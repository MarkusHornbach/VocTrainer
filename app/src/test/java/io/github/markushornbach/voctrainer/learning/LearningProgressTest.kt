package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LearningProgressTest {
    private val today = LocalDate.of(2026, 10, 6)
    private val zone = ZoneOffset.UTC

    @Test
    fun emptySelectionHasNoProgress() {
        val progress = LearningProgress.of(emptyList())
        assertEquals(SelectionProgress.EMPTY, progress)
        assertEquals(0, progress.percent)
        assertEquals(0, progress.sure)
        assertFalse(progress.hasProgress)
    }

    @Test
    fun countsItemsPerLevelAndFromLevelFourAsSure() {
        val words = listOf(0, 0, 1, 2, 3, 4, 5, 5).mapIndexed { index, level -> word("$index", "w$index", "x", level = level) }
        val progress = LearningProgress.of(words)
        assertEquals(listOf(2, 1, 1, 1, 1, 2), progress.countsPerLevel)
        assertEquals(8, progress.total)
        assertEquals(3, progress.sure)
        assertEquals(38, progress.percent)
        assertTrue(progress.hasProgress)
    }

    @Test
    fun onlyLevelZeroHasNothingToReset() {
        val progress = LearningProgress.of(listOf(word("1", "a", "A"), word("2", "b", "B")))
        assertFalse(progress.hasProgress)
        assertEquals(listOf(2, 0, 0, 0, 0, 0), progress.countsPerLevel)
    }

    @Test
    fun roundsToWholePercent() {
        fun percent(sure: Int, total: Int) =
            SelectionProgress(total, listOf(total - sure, 0, 0, 0, sure, 0)).percent
        assertEquals(67, percent(2, 3))
        assertEquals(33, percent(1, 3))
        assertEquals(100, percent(7, 7))
        assertEquals(1, percent(1, 198))
    }

    @Test
    fun followsTheSelectionIncludingPhrases() {
        val words = listOf(
            word("1", "bus", "Bus", unit = 1, part = "A", level = 3),
            word("2", "car", "Auto", unit = 1, part = "B", level = 1),
            word("3", "Nice to meet you.", "Freut mich.", unit = 1, part = "A", level = 5),
            word("4", "train", "Zug", unit = 2, part = "A", level = 4)
        )
        fun progress(filter: QuizFilter) = LearningProgress.of(QuizSelection.filter(words, filter))

        val unit1 = QuizFilter(unit = UnitChoice.Number(1))
        val progress = progress(unit1)
        assertEquals(3, progress.total)
        // Level 3 is not "gelernt"; the phrase on level 5 is.
        assertEquals(1, progress.sure)
        assertEquals(listOf(0, 1, 0, 1, 0, 1), progress.countsPerLevel)
        assertEquals(2, progress(unit1.copy(parts = setOf("A"))).total)
    }

    @Test
    fun exampleFileSelectionMatchesTheWordCount() {
        val words = exampleWords(EXAMPLE_BOOK)
        val filter = QuizFilter(unit = UnitChoice.Number(1), parts = setOf("A"))
        val selected = QuizSelection.filter(words, filter)
        assertEquals(selected.size, LearningProgress.of(selected).total)
        assertEquals(listOf(selected.size, 0, 0, 0, 0, 0), LearningProgress.of(selected).countsPerLevel)
    }
}
