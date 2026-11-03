package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.QuizWord
import io.github.markushornbach.voctrainer.learning.RoundPick
import io.github.markushornbach.voctrainer.learning.SureToday
import io.github.markushornbach.voctrainer.settings.QuizMode
import java.time.LocalDate
import java.time.ZoneId

/** Why a word is in the next round (test tool "Nächste Runde anzeigen"). */
enum class PickReason(val label: String) {
    LEARNABLE("kann gelernt werden"),
    REVIEW("Wiederholung"),
    NEW("neu"),
    EARLY("aufgefüllt (vorzeitig)")
}

/**
 * The round the next "Los geht's" starts: [pick] was made from [source] (all words of the mode)
 * with [filter] on [day]. A round only uses it while [isFor] still holds.
 */
class NextRoundPreview(
    val mode: QuizMode,
    private val source: List<QuizWord>,
    private val filter: QuizFilter,
    private val day: LocalDate,
    val pick: RoundPick,
    zone: ZoneId
) {
    /** The picked words that one correct answer today makes "gelernt". */
    val learnable: Int = pick.words.count { SureToday.canBecomeSure(it, day, zone) }

    /** The picked words that were never asked (words that dropped back to level 0 are not new). */
    val newCount: Int = pick.words.count { it.leitnerLevel == 0 && it.lastReviewedAt == null }

    /** Picked words in pick order with the reason, for the test tool. */
    val reasons: List<Pair<QuizWord, PickReason>> = pick.words.map { word ->
        word to when {
            SureToday.canBecomeSure(word, day, zone) -> PickReason.LEARNABLE
            word.id in pick.earlyIds -> PickReason.EARLY
            word.leitnerLevel >= 1 -> PickReason.REVIEW
            else -> PickReason.NEW
        }
    }

    fun isFor(mode: QuizMode, source: List<QuizWord>, filter: QuizFilter, day: LocalDate): Boolean =
        this.mode == mode && this.source === source && this.filter == filter && this.day == day
}
