package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.UnitChoice
import io.github.markushornbach.voctrainer.settings.QuizMode

internal fun unitLabel(choice: UnitChoice): String = when (choice) {
    UnitChoice.All -> "Alle Units"
    is UnitChoice.Number -> "Unit ${choice.unit}"
    UnitChoice.WithoutUnit -> "Ohne Unit"
}

internal fun partLabel(part: String?): String = part ?: "Einstieg"

/** "67 Wörter" / "1 Wort" in vocabulary mode, "12 Verben" / "1 Verb" in verb mode. */
internal fun countLabel(count: Int, mode: QuizMode, suffix: String = ""): String = when (mode) {
    QuizMode.VOCABULARY -> if (count == 1) "1 Wort" else "$count Wörter"
    QuizMode.VERBS -> if (count == 1) "1 Verb" else "$count Verben"
} + suffix

/**
 * One-line summary of the quiz selection, e.g. "Unit 1 · Einstieg, A · S. 201–203 · 67 Wörter".
 * [partOptions] gives the textbook order of the parts.
 */
fun selectionSummary(
    filter: QuizFilter,
    partOptions: List<String?>,
    count: Int,
    mode: QuizMode = QuizMode.VOCABULARY
): String = selectionDescription(filter, partOptions) + " · " + countLabel(count, mode)

/** The summary without the count, e.g. "Unit 1 · Einstieg, A · S. 201–203". */
fun selectionDescription(
    filter: QuizFilter,
    partOptions: List<String?>
): String = buildList {
    add(unitLabel(filter.unit))
    if (filter.unit != UnitChoice.All) {
        add(
            if (filter.parts.isEmpty()) "Alle Parts"
            else partOptions.filter { it in filter.parts }.joinToString(", ") { partLabel(it) }
        )
    }
    filter.pageFrom?.let { from ->
        val to = filter.pageTo
        add(if (to == null || to == from) "S. $from" else "S. $from–$to")
    }
}.joinToString(" · ")

/** Name of a learning level for the details of "Dein Lernstand". */
fun levelName(level: Int): String = when (level) {
    0 -> "neu"
    1, 2, 3 -> "am Üben"
    4 -> "gelernt"
    else -> "gemeistert"
}

/**
 * The line about the next round on the start screen: "Nächste Runde: 3 Wörter kannst du fertig lernen."
 * if [learnable] > 0, else "Nächste Runde: 4 neue Wörter." if [newCount] > 0, else null (no line).
 */
fun nextRoundText(learnable: Int, newCount: Int, mode: QuizMode): String? = when {
    learnable > 0 -> "Nächste Runde: ${countLabel(learnable, mode)} kannst du fertig lernen."
    newCount > 0 -> {
        val what = when (mode) {
            QuizMode.VOCABULARY -> if (newCount == 1) "neues Wort" else "neue Wörter"
            QuizMode.VERBS -> if (newCount == 1) "neues Verb" else "neue Verben"
        }
        "Nächste Runde: $newCount $what."
    }
    else -> null
}
