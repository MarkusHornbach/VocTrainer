package io.github.markushornbach.voctrainer.learning

import kotlin.math.roundToInt

/** Learning progress of the current quiz selection. */
data class SelectionProgress(
    val total: Int,
    /** Number of items per level 0–[Leitner.MAX_LEVEL]. */
    val countsPerLevel: List<Int>
) {
    val sure: Int get() = countsPerLevel.drop(LearningProgress.SURE_LEVEL).sum()

    val percent: Int get() = if (total == 0) 0 else (sure * 100.0 / total).roundToInt()

    /** True if any item is above level 0, i.e. there is something to reset. */
    val hasProgress: Boolean get() = countsPerLevel.drop(1).any { it > 0 }

    companion object {
        val EMPTY = SelectionProgress(0, List(Leitner.MAX_LEVEL + 1) { 0 })
    }
}

object LearningProgress {
    /** An item counts as "gelernt" from this Leitner level on. */
    const val SURE_LEVEL = 4

    /** [words] must already be filtered to the selection (see [QuizSelection.filter]). */
    fun of(words: List<QuizWord>): SelectionProgress {
        val counts = IntArray(Leitner.MAX_LEVEL + 1)
        words.forEach { counts[it.leitnerLevel.coerceIn(0, Leitner.MAX_LEVEL)]++ }
        return SelectionProgress(words.size, counts.toList())
    }
}
