package io.github.markushornbach.voctrainer.ui

/** German texts of the daily goal, the streak and the reminder. */
object PracticeTexts {
    fun days(count: Int): String = if (count == 1) "1 Tag" else "$count Tage"

    /** "Heute: 1 von 2 Runden"; the goal is at least 2 (see [io.github.markushornbach.voctrainer.settings.AppSettings.DAILY_GOAL_RANGE]). */
    fun roundsToday(rounds: Int, goal: Int): String = "Heute: $rounds von $goal Runden"

    const val START_STREAK = "Starte heute deine Serie!"

    /** "Tagesziel geschafft! 🔥 3 Tage in Folge · +13 Bonuspunkte"; the bonus part only if > 0. */
    fun celebration(streak: Int, bonus: Int = 0): String =
        "Tagesziel geschafft! 🔥 ${days(streak)} in Folge" + if (bonus > 0) " · +$bonus Bonuspunkte" else ""

    /** "+12 Punkte" / "+1 Punkt" for the result screen. */
    fun roundPoints(points: Int): String = "+${formatNumber(points)} " + if (points == 1) "Punkt" else "Punkte"

    /** German thousands separator: 1234 → "1.234". */
    fun formatNumber(value: Int): String = String.format(java.util.Locale.GERMANY, "%,d", value)

    const val STREAK_TITLE = "Deine Serie"

    fun reminderTitle(streak: Int): String =
        if (streak > 0) "Deine Serie: ${days(streak)} 🔥" else "Zeit für eine kurze Runde"

    /**
     * "Heute kannst du 3 Wörter fertig lernen – los geht's!" ([sureToday] from
     * [io.github.markushornbach.voctrainer.learning.SureToday]); the general text if none can.
     */
    fun reminderText(streak: Int, sureToday: Int): String = when {
        sureToday == 1 -> "Heute kannst du 1 Wort fertig lernen – los geht's!"
        sureToday > 1 -> "Heute kannst du $sureToday Wörter fertig lernen – los geht's!"
        streak > 0 -> "Heute fehlt noch deine Runde."
        else -> "Eine Runde dauert nur ein paar Minuten."
    }
}
