package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.DetailReason
import io.github.markushornbach.voctrainer.learning.DetailStatus
import io.github.markushornbach.voctrainer.learning.HintDates
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.learning.RoundDetail
import java.time.LocalDate

/**
 * German texts of "Details" on the result screen. Line 1 is "english", the level status and the
 * points; line 2 (only if there is something to explain) is one short combined text. Every short
 * reason is listed by [allReasons], so the info page "Zeichen in „Details“" explains all of them.
 */
object RoundDetailTexts {
    const val TITLE = "Details"

    /** Correct, but the word cannot rise today (no dates: the pauses are internal). */
    const val NO_RISE_TODAY = "heute kein Aufstieg"

    /** A correct answer whose rise is possible again from tomorrow (the level is in the status column). */
    const val RISE_TOMORROW = "Aufstieg ab morgen"
    const val REACHED_BEFORE = "Level bereits vorher erreicht"
    const val LATER_POINTS = "später wieder Punkte"
    val CHOICE_CAP = "mit Vorschlägen nur bis Level ${Leitner.CHOICE_MAX_TYPEABLE}"
    const val WRONG = "falsch"
    const val ALSO_CORRECT = "anderes Wort gesucht"
    const val ALMOST_TYPO = "fast richtig (Tippfehler)"
    const val ALMOST_AMERICAN = "fast richtig (amerikanisch)"
    const val TOP = "Höchstlevel"

    /** Column titles of "Details": vocabulary rounds with points, verb training without. */
    fun header(verbs: Boolean): List<String> =
        if (verbs) listOf("Verb", "Level") else listOf("Vokabel", "Level", "Punkte")

    /** The symbols of the level status; the tip "Deine Runde" explains the same ones. */
    const val SYMBOL_UP = "↑"
    const val SYMBOL_SAME = "="
    const val SYMBOL_DOWN = "↓"

    /** "↑ 4" (rose), "↓ 2" (dropped) or "= 3" (unchanged): the level after the answer. */
    fun status(detail: RoundDetail): String = statusText(detail.status, detail.level)

    fun statusText(status: DetailStatus, level: Int): String = when (status) {
        DetailStatus.UP -> "$SYMBOL_UP $level"
        DetailStatus.DOWN -> "$SYMBOL_DOWN $level"
        DetailStatus.SAME -> "$SYMBOL_SAME $level"
    }

    /** "+4 P" or "0 P"; null in verb training (no points there). */
    fun points(detail: RoundDetail): String? = detail.points?.let(::pointsText)

    fun pointsText(points: Int): String = if (points > 0) "+$points P" else "0 P"

    /** The short second line, or null for a word that rose and earned points. */
    fun explanation(detail: RoundDetail, today: LocalDate): String? {
        val parts = listOfNotNull(
            detail.reason?.let { reasonText(detail, it, today) },
            REACHED_BEFORE.takeIf { detail.reachedBefore },
            LATER_POINTS.takeIf { detail.notDue }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun reasonText(detail: RoundDetail, reason: DetailReason, today: LocalDate): String = when (reason) {
        DetailReason.NOT_DUE -> when {
            detail.availableFrom != null && HintDates.isTomorrow(detail.availableFrom, today) -> RISE_TOMORROW
            else -> NO_RISE_TODAY
        }
        else -> reasonTexts(reason).single()
    }

    /** Every text [reason] can show (two for [DetailReason.NOT_DUE], depending on the date). */
    fun reasonTexts(reason: DetailReason): List<String> = when (reason) {
        DetailReason.NEXT_DAY -> listOf(RISE_TOMORROW)
        DetailReason.NOT_DUE -> listOf(NO_RISE_TODAY, RISE_TOMORROW)
        DetailReason.CHOICE -> listOf(CHOICE_CAP)
        DetailReason.WRONG -> listOf(WRONG)
        DetailReason.ALSO_CORRECT -> listOf(ALSO_CORRECT)
        DetailReason.ALMOST_TYPO -> listOf(ALMOST_TYPO)
        DetailReason.ALMOST_AMERICAN -> listOf(ALMOST_AMERICAN)
        DetailReason.TOP -> listOf(TOP)
    }

    /** All short reasons of the second line, each once. */
    fun allReasons(): List<String> =
        (DetailReason.entries.flatMap(::reasonTexts) + REACHED_BEFORE + LATER_POINTS).distinct()

    /** Read out for the whole entry. */
    fun description(detail: RoundDetail, today: LocalDate): String =
        listOfNotNull(detail.english, status(detail), points(detail), explanation(detail, today)).joinToString(", ")
}
