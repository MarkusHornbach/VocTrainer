package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.BadgeKey
import io.github.markushornbach.voctrainer.learning.BadgeProgress
import io.github.markushornbach.voctrainer.learning.BadgeRules
import io.github.markushornbach.voctrainer.learning.BadgeSeries

/** German texts of the badges ("Meine Abzeichen", result screen, test tools). */
object BadgeTexts {
    const val TITLE = "Meine Abzeichen"
    const val ALL_REACHED = "Alle Abzeichen erreicht!"

    /** Line 1: "Serie · Gold" or "Serie · noch kein Abzeichen". */
    fun title(row: BadgeProgress): String =
        "${row.series.displayName} · " + if (row.tier == 0) "noch kein Abzeichen" else BadgeRules.tierName(row.tier)

    /** Line 2: the value of the series. */
    fun value(series: BadgeSeries, value: Int): String = when (series) {
        BadgeSeries.STREAK -> "Längste Serie: $value " + if (value == 1) "Tag" else "Tage"
        BadgeSeries.SURE_WORDS -> "Jetzt: " + if (value == 1) "1 gelerntes Wort" else "$value gelernte Wörter"
    }

    /** Line 3: "Nächstes Abzeichen bei 30" or "Alle Abzeichen erreicht!". */
    fun next(row: BadgeProgress): String = row.nextThreshold?.let { "Nächstes Abzeichen bei $it" } ?: ALL_REACHED

    /** Read out for the whole row. */
    fun description(row: BadgeProgress): String = "${title(row)}. ${value(row.series, row.value)}. ${next(row)}"

    /** Result screen: "Neues Abzeichen: Silber · 7 Tage Serie". */
    fun earned(badge: BadgeKey): String =
        "Neues Abzeichen: ${BadgeRules.tierName(badge.tier)} · ${thresholdText(badge)}"

    private fun thresholdText(badge: BadgeKey): String {
        val value = BadgeRules.threshold(badge.series, badge.tier)
        return when (badge.series) {
            BadgeSeries.STREAK -> "$value " + (if (value == 1) "Tag" else "Tage") + " Serie"
            BadgeSeries.SURE_WORDS -> if (value == 1) "1 gelerntes Wort" else "$value gelernte Wörter"
        }
    }

    /** Test app: "Serie: Wert 12 · erreicht: Bronze, Silber". */
    fun testLine(row: BadgeProgress, earnedTiers: List<Int>): String =
        "${row.series.displayName}: Wert ${row.value} · erreicht: " +
            if (earnedTiers.isEmpty()) "keine" else earnedTiers.sorted().joinToString(", ") { BadgeRules.tierName(it) }

    /** Test app, "Abzeichen jetzt prüfen": what was newly awarded. */
    fun testAwarded(badges: List<BadgeKey>): String =
        if (badges.isEmpty()) "Keine neuen Abzeichen"
        else "Neu: " + badges.joinToString(", ") { "${it.series.displayName} ${BadgeRules.tierName(it.tier)}" }

    fun testDeleted(count: Int): String = when (count) {
        0 -> "Keine Abzeichen vorhanden · Erstprüfung wieder offen"
        1 -> "1 Abzeichen gelöscht · Erstprüfung wieder offen"
        else -> "$count Abzeichen gelöscht · Erstprüfung wieder offen"
    }
}
