package io.github.markushornbach.voctrainer.data

import io.github.markushornbach.voctrainer.learning.Points

/**
 * What "Buch neu beginnen" resets, as named on the info page; each item is one part of
 * [BookRestart] / `VocabularyRepository.restartBook`.
 */
enum class RestartItem {
    /** [BookRestart.vocabulary] and [BookRestart.verbs]. */
    LEVELS,

    /** The points rows of the book are cleared (`PointsDayReset`). */
    POINTS,

    /** All badges of the book are deleted. */
    BADGES,

    /** [BookRestart.practiceDays]: streak and today's daily goal start at 0. */
    STREAK,

    /** [BookRestart.figureSlotStart]: the growing figure is left behind, a new one is chosen. */
    GROWING_AVATAR
}

/**
 * "Buch neu beginnen": the rows of one book that have to be written so that it starts from scratch.
 * Only rows that change get a fresh updatedAt, like any other edit. Ignored vocabulary (stored as [Vocabulary.isDeleted],
 * there is no other deleted state for vocabulary) is reset too, so it is new when it is used again;
 * deleted verbs stay untouched. The vocabulary itself, the selections and the finished figures stay.
 */
object BookRestart {
    /** Level, highest level, review date and "first typed correctly" back to new, so points can be earned again. */
    fun vocabulary(entries: List<Vocabulary>, now: Long): List<Vocabulary> =
        entries.filter { it.hasProgress }
            .map { it.copy(leitnerLevel = 0, highestLevel = 0, lastReviewedAt = null, firstTypedCorrectAt = null, updatedAt = now) }

    fun verbs(verbs: List<IrregularVerb>, now: Long): List<IrregularVerb> =
        verbs.filter { !it.isDeleted && (it.leitnerLevel != 0 || it.lastReviewedAt != null) }
            .map { it.copy(leitnerLevel = 0, lastReviewedAt = null, updatedAt = now) }

    /** The rows stay; streak and today's daily goal start at 0. */
    fun practiceDays(days: List<PracticeDay>, now: Long): List<PracticeDay> =
        days.filter { it.roundsCompleted != 0 }.map { it.copy(roundsCompleted = 0, updatedAt = now) }

    /** The figures so far belong to an earlier start: the next figure starts after the highest slot. */
    fun figureSlotStart(figures: List<Figure>): Int = figures.nextSlot()

    /**
     * True if the book has anything "Buch neu beginnen" would reset: a word (also an ignored one)
     * or verb above level 0, a highest level above 0, points, a badge, a practice day with rounds or a current figure.
     */
    fun hasSomethingToReset(
        vocabulary: List<Vocabulary>,
        verbs: List<IrregularVerb>,
        pointsDays: List<PointsDay>,
        practiceDays: List<PracticeDay>,
        badges: List<Badge>,
        figures: List<Figure>,
        slotStart: Int
    ): Boolean =
        vocabulary.any { it.leitnerLevel > 0 || it.highestLevel > 0 } ||
            verbs.any { !it.isDeleted && it.leitnerLevel > 0 } ||
            Points.total(pointsDays.map { it.toRecord() }) != 0 ||
            badges.isNotEmpty() ||
            practiceDays.any { it.roundsCompleted > 0 } ||
            figures.current(slotStart) != null

    private val Vocabulary.hasProgress: Boolean
        get() = leitnerLevel != 0 || highestLevel != 0 || lastReviewedAt != null || firstTypedCorrectAt != null
}
