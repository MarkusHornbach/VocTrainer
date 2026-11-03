package io.github.markushornbach.voctrainer.learning

object RoundStars {
    const val MAX = 3

    /** Credit of a first attempt in thirds: full for a right answer in the best possible form. */
    const val FULL_CREDIT = 3

    /** Credit of "Mit Vorschlägen" instead of typing and of "Fast richtig". */
    const val REDUCED_CREDIT = 1

    const val REDUCED_HINT = "Für mehr Sterne: selbst tippen. „Mit Vorschlägen“ und „Fast richtig“ zählen nur ein Drittel."

    fun creditFor(correct: Boolean): Int = if (correct) FULL_CREDIT else 0

    /**
     * ≥ 90 % → 3, ≥ 70 % → 2, ≥ 40 % → 1, otherwise 0: the share of the possible credit
     * (3 per word) earned by the first attempts of the round.
     */
    fun forCredit(credit: Int, totalWords: Int): Int {
        if (totalWords <= 0) return 0
        val tenths = credit * 10
        val max = totalWords * FULL_CREDIT
        return when {
            tenths >= max * 9 -> 3
            tenths >= max * 7 -> 2
            tenths >= max * 4 -> 1
            else -> 0
        }
    }

    /** The explanation under the result is shown if stars were lost (below 3) and some answer was reduced. */
    fun showsReducedHint(stars: Int, reducedCredits: Int): Boolean = reducedCredits > 0 && stars < MAX
}
