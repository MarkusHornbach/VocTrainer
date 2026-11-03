package io.github.markushornbach.voctrainer.learning

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** The test app's "Datum verschieben": the learning rules follow the simulated date. */
class AppClockTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @After
    fun backToTheRealDate() = AppClock.setOffsetDays(0)

    @Test
    fun shiftMovesTodayAndNowAndResetReturnsToTheRealDate() {
        assertFalse(AppClock.isShifted)
        AppClock.setOffsetDays(7)
        assertTrue(AppClock.isShifted)
        assertEquals(LocalDate.now(zone).plusDays(7), AppClock.today(zone))
        val shiftedDay = java.time.Instant.ofEpochMilli(AppClock.nowMillis(zone)).atZone(zone).toLocalDate()
        assertEquals(LocalDate.now(zone).plusDays(7), shiftedDay)
        AppClock.setOffsetDays(0)
        assertFalse(AppClock.isShifted)
        assertEquals(LocalDate.now(zone), AppClock.today(zone))
    }

    @Test
    fun calendarDayRuleRisesToLevel4OnTheSimulatedNextDay() {
        val reviewedAt = AppClock.nowMillis(zone)
        val sameDay = Due.reviewedEarlierDay(reviewedAt, AppClock.today(zone), zone)
        assertEquals(3, Leitner.afterFirstAttempt(3, correct = true, early = true, reviewedEarlierDay = sameDay, form = AnswerForm.TYPED))
        AppClock.setOffsetDays(1)
        val nextDay = Due.reviewedEarlierDay(reviewedAt, AppClock.today(zone), zone)
        assertEquals(4, Leitner.afterFirstAttempt(3, correct = true, early = true, reviewedEarlierDay = nextDay, form = AnswerForm.TYPED))
    }

    @Test
    fun dueDateFollowsTheSimulatedDate() {
        val reviewedAt = AppClock.nowMillis(zone)
        assertFalse(Due.isDue(4, reviewedAt, AppClock.today(zone), zone))
        AppClock.setOffsetDays(Leitner.intervalDays(4) - 1)
        assertFalse(Due.isDue(4, reviewedAt, AppClock.today(zone), zone))
        AppClock.setOffsetDays(Leitner.intervalDays(4))
        assertTrue(Due.isDue(4, reviewedAt, AppClock.today(zone), zone))
        AppClock.setOffsetDays(0)
        assertFalse(Due.isDue(4, reviewedAt, AppClock.today(zone), zone))
    }

    @Test
    fun typedWordRisesFrom3To4OnTheSimulatedNextDay() {
        val reviewedAt = AppClock.nowMillis(zone)
        fun answer() = Leitner.afterFirstAttempt(
            3, correct = true, early = true, reviewedEarlierDay = Due.reviewedEarlierDay(reviewedAt, AppClock.today(zone), zone),
            form = AnswerForm.TYPED
        )
        assertEquals(3, answer())
        AppClock.setOffsetDays(1)
        assertEquals(4, answer())
    }
}
