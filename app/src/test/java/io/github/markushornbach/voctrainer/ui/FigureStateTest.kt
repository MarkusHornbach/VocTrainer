package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.data.Figure
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.NextStage
import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.QuizSelection
import io.github.markushornbach.voctrainer.learning.Reach
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.learning.word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FigureStateTest {
    @Test
    fun nextStageIsLockedWhenItsSureWordsCannotBeReached() {
        // Stage 3 needs 10 sure words; no askable word can become sure today.
        val state = FigureState(next = NextStage(3, missingPoints = 0, missingSureWords = 10), reach = Reach(100, 0))
        assertTrue(state.nextLocked)
    }

    @Test
    fun nextStageIsNotLockedWhenEnoughWordsCanBecomeSure() {
        val state = FigureState(next = NextStage(3, missingPoints = 0, missingSureWords = 10), reach = Reach(100, 40))
        assertFalse(state.nextLocked)
        // Stages without sure words are never locked.
        assertFalse(FigureState(next = NextStage(2, 50, 0), reach = Reach(100, 0)).nextLocked)
    }

    @Test
    fun theLastStageIsNeverLocked() {
        assertFalse(FigureState(next = null, reach = Reach(100, 0)).nextLocked)
    }

    /**
     * The goal of the next stage counts the sure words of the whole active book: 12 sure words in
     * Unit 1 count although the quiz selection is Unit 2 (whose words are all new).
     */
    @Test
    fun theNextStageCountsTheWholeBookNotTheSelection() {
        val unit1 = (1..12).map { word("a$it", "word$it", "Wort$it", unit = 1, level = 4) }
        val unit2 = (1..8).map { word("b$it", "new$it", "neu$it", unit = 2, level = 0) }
        val book = unit1 + unit2
        val figure = Figure(0, FigureKind.DRAGON.key, pointsAtStart = 0, stage = 2, startedAt = 1L, updatedAt = 1L)

        val state = figureStateOf(listOf(figure), total = 300, words = book)

        assertEquals(NextStage(3, missingPoints = 0, missingSureWords = 0), state.next)
        // The selection alone would still miss sure words.
        val selection = QuizSelection.filter(book, QuizFilter(UnitChoice.Number(2)))
        val onlySelection = figureStateOf(listOf(figure), total = 300, words = selection)
        assertTrue(onlySelection.next!!.missingSureWords > 0)
    }

    /** Learned words = active words on level 4 or higher of the whole book, phrases included, whatever the selection. */
    @Test
    fun learnedWordsCountTheWholeBookFromLevelFour() {
        val book = listOf(
            word("a", "one", "eins", unit = 1, level = 3),
            word("b", "two", "zwei", unit = 1, level = 4),
            word("c", "three", "drei", unit = 2, level = 5),
            word("d", "four", "vier", unit = 2, level = 0),
            word("e", "How are you?", "Wie geht es dir?", unit = 2, level = 4, type = io.github.markushornbach.voctrainer.learning.WordType.PHRASE)
        )
        val figure = Figure(0, FigureKind.DRAGON.key, pointsAtStart = 0, stage = 1, startedAt = 1L, updatedAt = 1L)
        assertEquals(3, figureStateOf(listOf(figure), total = 0, words = book).learnedWords)
        assertEquals(0, figureStateOf(emptyList(), total = 0, words = emptyList()).learnedWords)
    }

    /** A figure belongs to a book: without one, the choice would be shown again and again. */
    @Test
    fun theChoiceWaitsForABook() {
        assertFalse(figureStateOf(emptyList(), total = 0, words = emptyList(), hasBook = false).needsChoice)
        assertTrue(figureStateOf(emptyList(), total = 0, words = emptyList(), hasBook = true).needsChoice)
    }

    /** After a finished avatar the next one needs 150 new words; until then no choice, the last avatar stays on the start screen. */
    @Test
    fun theChoiceIsPausedUntilEnoughNewWordsExist() {
        val finished = Figure(0, FigureKind.MONSTER.key, pointsAtStart = 0, stage = 10, startedAt = 1L, finishedAt = 2L, updatedAt = 2L)
        fun words(count: Int) = (1..count).map { word("w$it", "word$it", "Wort$it", unit = 1, level = 4) }

        val paused = figureStateOf(listOf(finished), total = 5_000, words = words(500))
        assertTrue(paused.paused)
        assertFalse(paused.needsChoice)
        assertEquals(finished, paused.shown)
        assertEquals(FigureKind.MONSTER, paused.shownKind)
        assertEquals(1f, paused.pointsFraction, 0f)
        assertFalse(paused.nextLocked)

        val enough = figureStateOf(listOf(finished), total = 5_000, words = words(563))
        assertFalse(enough.paused)
        assertTrue(enough.needsChoice)
        assertEquals(null, enough.shown)

        // The first avatar is never paused.
        assertFalse(figureStateOf(emptyList(), total = 0, words = words(3)).paused)
    }

    /** After "Buch neu beginnen": the learner chooses avatar 1 again; the old finished avatar stays in the collection. */
    @Test
    fun afterARestartTheChoiceIsForTheFirstAvatarAgain() {
        val finished = Figure(0, FigureKind.DRAGON.key, pointsAtStart = 0, stage = 10, startedAt = 1L, finishedAt = 2L, updatedAt = 2L)
        val growing = Figure(1, FigureKind.MONSTER.key, pointsAtStart = 4_300, stage = 4, startedAt = 3L, updatedAt = 4L)
        val words = (1..500).map { word("w$it", "word$it", "Wort$it", level = 0) }

        val before = figureStateOf(listOf(finished, growing), total = 5_000, words = words)
        assertEquals(growing, before.current)
        assertTrue(before.avatarNumber == 2)

        val after = figureStateOf(listOf(finished, growing), total = 0, words = words, slotStart = 2)
        assertTrue(after.needsChoice)
        assertFalse(after.choosingNext)
        assertFalse(after.paused)
        assertEquals(1, after.avatarNumber)
        assertEquals(listOf(finished), after.collection)
    }
}
