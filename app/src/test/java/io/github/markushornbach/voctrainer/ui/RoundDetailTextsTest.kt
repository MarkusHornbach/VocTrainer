package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.AlmostKind
import io.github.markushornbach.voctrainer.learning.AnswerForm
import io.github.markushornbach.voctrainer.learning.AttemptRecord
import io.github.markushornbach.voctrainer.learning.KeptAnswer
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.learning.RoundDetail
import io.github.markushornbach.voctrainer.learning.RoundReport
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundDetailTextsTest {
    private val today = LocalDate.of(2026, 10, 15)
    private val typed = AnswerForm.TYPED
    private val choice = AnswerForm.CHOICE_INSTEAD_OF_TYPING

    private fun detail(
        level: Int, correct: Boolean = true, early: Boolean = false, earlier: Boolean = true,
        form: AnswerForm = AnswerForm.MULTIPLE_CHOICE, kept: KeptAnswer? = null, points: Int? = null,
        pointsApply: Boolean = true, from: LocalDate? = null, almost: AlmostKind? = null, word: String = "house"
    ): RoundDetail {
        val outcome = Leitner.outcomeOfFirstAttempt(level, correct, early, Leitner.MAX_LEVEL, earlier, form, kept)
        val earned = points ?: if (outcome.rose) outcome.newLevel else 0
        return RoundReport.details(
            listOf(AttemptRecord(word, outcome, earned, pointsApply, availableFrom = from, almost = almost))
        ).single()
    }

    @Test
    fun roseWithPointsHasNoSecondLine() {
        val d = detail(3, form = typed, earlier = true)
        assertEquals("↑ 4", RoundDetailTexts.status(d))
        assertEquals("+4 P", RoundDetailTexts.points(d))
        assertNull(RoundDetailTexts.explanation(d, today))
    }

    @Test
    fun roseWithoutPointsSaysAlreadyReached() {
        val d = detail(1, early = true, points = 0)
        assertEquals("0 P", RoundDetailTexts.points(d))
        assertEquals("Level bereits vorher erreicht", RoundDetailTexts.explanation(d, today))
    }

    @Test
    fun staysBecauseOfTheNextDayRule() {
        val d = detail(3, form = typed, earlier = false)
        assertEquals("= 3", RoundDetailTexts.status(d))
        assertEquals("Aufstieg ab morgen", RoundDetailTexts.explanation(d, today))
    }

    @Test
    fun notDueTomorrowOrNoRiseTodayWithoutADate() {
        val tomorrow = detail(4, form = typed, early = true, from = today.plusDays(1))
        assertEquals("Aufstieg ab morgen", RoundDetailTexts.explanation(tomorrow, today))
        val later = detail(4, form = typed, early = true, from = LocalDate.of(2026, 10, 17))
        assertEquals("heute kein Aufstieg", RoundDetailTexts.explanation(later, today))
        assertEquals("heute kein Aufstieg", RoundDetailTexts.explanation(detail(4, form = typed, early = true), today))
    }

    @Test
    fun withSuggestionsKeepsOrDrops() {
        val keeps = detail(3, form = choice)
        assertEquals("= 3", RoundDetailTexts.status(keeps))
        assertEquals("mit Vorschlägen nur bis Level 3", RoundDetailTexts.explanation(keeps, today))
        val drops = detail(5, form = choice)
        assertEquals("↓ 3", RoundDetailTexts.status(drops))
        assertEquals("mit Vorschlägen nur bis Level 3", RoundDetailTexts.explanation(drops, today))
    }

    @Test
    fun wrongDropsOrStaysAtTheBottom() {
        val drop = detail(3, correct = false)
        assertEquals("↓ 2", RoundDetailTexts.status(drop))
        assertEquals("0 P", RoundDetailTexts.points(drop))
        assertEquals("falsch", RoundDetailTexts.explanation(drop, today))
        val bottom = detail(0, correct = false)
        assertEquals("= 0", RoundDetailTexts.status(bottom))
        assertEquals("falsch", RoundDetailTexts.explanation(bottom, today))
    }

    @Test
    fun alsoCorrectAndBothAlmostCorrectKinds() {
        assertEquals("anderes Wort gesucht", RoundDetailTexts.explanation(detail(2, form = typed, kept = KeptAnswer.ALSO_CORRECT), today))
        val almost = { kind: AlmostKind ->
            RoundDetailTexts.explanation(
                detail(3, correct = false, form = typed, kept = KeptAnswer.ALMOST_CORRECT, almost = kind), today
            )
        }
        assertEquals("fast richtig (Tippfehler)", almost(AlmostKind.TYPO))
        assertEquals("fast richtig (Tippfehler)", almost(AlmostKind.TYPO_OF_OTHER))
        assertEquals("fast richtig (amerikanisch)", almost(AlmostKind.AMERICAN))
    }

    @Test
    fun levelFiveStaysDueOrNot() {
        val due = detail(5, form = typed, points = 5)
        assertEquals("+5 P", RoundDetailTexts.points(due))
        assertEquals("Höchstlevel", RoundDetailTexts.explanation(due, today))
        val notDue = detail(5, form = typed, early = true, points = 0)
        assertEquals("0 P", RoundDetailTexts.points(notDue))
        assertEquals("Höchstlevel · später wieder Punkte", RoundDetailTexts.explanation(notDue, today))
    }

    @Test
    fun verbTrainingShowsNoPoints() {
        val d = detail(1, pointsApply = false, word = "catch")
        assertNull(RoundDetailTexts.points(d))
        assertNull(RoundDetailTexts.explanation(d, today))
        assertEquals("catch, ↑ 2", RoundDetailTexts.description(d, today))
    }

    @Test
    fun theSecondLineStaysShortEnoughForOneLineOnANarrowPhone() {
        val all = listOf(
            detail(1, early = true, points = 0), detail(3, form = typed, earlier = false),
            detail(4, form = typed, early = true, from = LocalDate.of(2026, 10, 17)),
            detail(4, form = typed, early = true), detail(5, form = choice), detail(3, correct = false),
            detail(2, form = typed, kept = KeptAnswer.ALSO_CORRECT),
            detail(3, correct = false, form = typed, kept = KeptAnswer.ALMOST_CORRECT, almost = AlmostKind.AMERICAN),
            detail(5, form = typed, early = true, points = 0)
        )
        all.forEach { d ->
            val text = RoundDetailTexts.explanation(d, today)
            assertTrue("$text", text != null && text.length <= 34)
            // Line 1 (status and points) is short as well; the word itself takes the remaining width.
            assertTrue((RoundDetailTexts.status(d) + RoundDetailTexts.points(d)).length <= 10)
        }
    }

    @Test
    fun headerHasAPointsColumnOnlyForVocabulary() {
        assertEquals(listOf("Vokabel", "Level", "Punkte"), RoundDetailTexts.header(verbs = false))
        assertEquals(listOf("Verb", "Level"), RoundDetailTexts.header(verbs = true))
    }
}
