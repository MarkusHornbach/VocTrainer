package io.github.markushornbach.voctrainer.learning

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class HintDatesTest {
    private val earlier = LevelReason.STAYS_NEXT_DAY
    private val locale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(locale)

    @Test
    fun formatsWeekdayDayAndMonthInGerman() {
        assertEquals("Sa., 17.10.", HintDates.format(LocalDate.of(2026, 10, 17)))
        assertEquals("Mo., 5.1.", HintDates.format(LocalDate.of(2026, 1, 5)))
        assertEquals("So., 31.12.", HintDates.format(LocalDate.of(2028, 12, 31)))
    }

    @Test
    fun weekdayDoesNotDependOnThePhoneLanguage() {
        Locale.setDefault(Locale.US)
        assertEquals("Do., 15.10.", HintDates.format(LocalDate.of(2026, 10, 15)))
        Locale.setDefault(Locale.FRANCE)
        assertEquals("Do., 15.10.", HintDates.format(LocalDate.of(2026, 10, 15)))
    }

    @Test
    fun recognisesTomorrowOnly() {
        val today = LocalDate.of(2026, 10, 12)
        assertTrue(HintDates.isTomorrow(today.plusDays(1), today))
        assertFalse(HintDates.isTomorrow(today.plusDays(2), today))
        assertFalse(HintDates.isTomorrow(today, today))
    }

    @Test
    fun dateUsesTheSimulatedDayOfTheAppClock() {
        val real = AppClock.today()
        try {
            AppClock.setOffsetDays(3)
            assertEquals(real.plusDays(3), AppClock.today())
            assertTrue(HintDates.isTomorrow(real.plusDays(4), AppClock.today()))
            assertFalse(HintDates.isTomorrow(real.plusDays(1), AppClock.today()))
        } finally {
            AppClock.setOffsetDays(0)
        }
    }

    @Test
    fun notDueHintCarriesTheDateOnlyForThatKind() {
        val day = LocalDate.of(2026, 10, 17)
        val notDue = Leitner.outcomeOfFirstAttempt(4, true, early = true, form = AnswerForm.TYPED)
        assertEquals(LevelReason.STAYS_NOT_DUE, notDue.reason)
        assertEquals(day, RoundReport.hint(notDue, false, day)!!.availableFrom)
        assertNull(RoundReport.hint(notDue, false)!!.availableFrom)

        val nextDay = Leitner.outcomeOfFirstAttempt(3, true, reviewedEarlierDay = false, form = AnswerForm.TYPED)
        assertEquals(earlier, nextDay.reason)
        val hint = RoundReport.hint(nextDay, false, day)!!
        assertNull(hint.availableFrom)
        assertEquals(4, hint.targetLevel)
    }

    @Test
    fun everyWordOnLevelThreeNamesLevelFourAsTarget() {
        val outcome = Leitner.outcomeOfFirstAttempt(3, true, reviewedEarlierDay = false, form = AnswerForm.TYPED)
        assertEquals(earlier, outcome.reason)
        assertEquals(4, RoundReport.hint(outcome, false)!!.targetLevel)
    }

    @Test
    fun recordsAndDetailsKeepTheDate() {
        val day = LocalDate.of(2026, 10, 17)
        val notDue = Leitner.outcomeOfFirstAttempt(4, true, early = true, form = AnswerForm.TYPED)
        val record = AttemptRecord("stone", notDue, 0, availableFrom = day)
        assertEquals(day, RoundReport.hint(record)!!.availableFrom)
        val detail = RoundReport.details(listOf(record)).single()
        assertEquals(DetailReason.NOT_DUE, detail.reason)
        assertEquals(day, detail.availableFrom)
    }
}
