package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.reminder.Reminder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PracticeTextsTest {
    @Test
    fun streakBarTexts() {
        assertEquals("1 Tag", PracticeTexts.days(1))
        assertEquals("5 Tage", PracticeTexts.days(5))
        assertEquals("Heute: 0 von 3 Runden", PracticeTexts.roundsToday(0, 3))
        assertEquals("Heute: 1 von 2 Runden", PracticeTexts.roundsToday(1, 2))
        assertEquals("Tagesziel geschafft! 🔥 6 Tage in Folge", PracticeTexts.celebration(6))
        assertEquals("Tagesziel geschafft! 🔥 3 Tage in Folge · +13 Bonuspunkte", PracticeTexts.celebration(3, 13))
    }

    @Test
    fun pointsTexts() {
        assertEquals("+12 Punkte", PracticeTexts.roundPoints(12))
        assertEquals("+1 Punkt", PracticeTexts.roundPoints(1))
        assertEquals("+0 Punkte", PracticeTexts.roundPoints(0))
        assertEquals("+1.234 Punkte", PracticeTexts.roundPoints(1234))
    }

    @Test
    fun reminderTexts() {
        assertEquals("Deine Serie: 5 Tage 🔥", PracticeTexts.reminderTitle(5))
        assertEquals("Heute kannst du 12 Wörter fertig lernen – los geht's!", PracticeTexts.reminderText(5, 12))
        assertEquals("Heute fehlt noch deine Runde.", PracticeTexts.reminderText(5, 0))
        assertEquals("Zeit für eine kurze Runde", PracticeTexts.reminderTitle(0))
        assertEquals("Heute kannst du 12 Wörter fertig lernen – los geht's!", PracticeTexts.reminderText(0, 12))
        assertEquals("Heute kannst du 1 Wort fertig lernen – los geht's!", PracticeTexts.reminderText(0, 1))
        assertEquals("Eine Runde dauert nur ein paar Minuten.", PracticeTexts.reminderText(0, 0))
    }

    @Test
    fun nextReminderIsTodayIfStillAheadOtherwiseTomorrow() {
        val zone = ZoneId.of("Europe/Berlin")
        val morning = ZonedDateTime.of(2026, 10, 8, 9, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 10, 8, 17, 0, 0, 0, zone), Reminder.nextTime(morning, 17 * 60))
        val evening = ZonedDateTime.of(2026, 10, 8, 17, 0, 5, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 10, 9, 17, 0, 0, 0, zone), Reminder.nextTime(evening, 17 * 60))
        // Daylight saving time ends on 25 Oct 2026: still 17:00 local time.
        val beforeSwitch = ZonedDateTime.of(2026, 10, 24, 18, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 10, 25, 17, 0, 0, 0, zone), Reminder.nextTime(beforeSwitch, 17 * 60))
    }
}
