package io.github.markushornbach.voctrainer.learning

/** What the vocabulary can reach today; see [Reachability.of]. */
data class Reach(
    /** Askable vocabulary entries, as counted by [FigureRules.countWords]. */
    val askable: Int,
    /** Askable entries that can reach [LearningProgress.SURE_LEVEL] today. */
    val possibleSureWords: Int,
    /** Avatar number the stages are measured for (see [AvatarPlan]). */
    val avatarNumber: Int = 1
) {
    /** True if some askable entries cannot become sure today (typeable words). */
    val hasCappedWords: Boolean get() = possibleSureWords < askable

    /** True if [stage] needs more sure words than can be reached today; never for stages without sure words. */
    fun stageLocked(stage: Int): Boolean = AvatarPlan(avatarNumber, askable).requirement(stage).sureWords > possibleSureWords

    companion object {
        val EMPTY = Reach(0, 0)
    }
}

/**
 * "Was ist gerade erreichbar?" Since typing exists every word can reach [Leitner.MAX_LEVEL]
 * (typeable words by typing them), so nothing is locked any more; kept so the locks come back
 * by themselves if a level ever becomes unreachable again.
 */
object Reachability {
    /** Highest level a word can reach today. */
    @Suppress("UNUSED_PARAMETER")
    fun maxLevel(word: QuizWord): Int = Leitner.MAX_LEVEL

    /** True if a typeable word cannot reach [level] today (vocabulary mode, for the level lists). */
    fun levelLockedForTypeable(level: Int): Boolean = level > Leitner.MAX_LEVEL

    /** [words] are the active vocabulary entries, as for [FigureRules.countWords]. */
    fun of(words: List<QuizWord>, avatarNumber: Int = 1): Reach =
        Reach(words.size, words.count { maxLevel(it) >= LearningProgress.SURE_LEVEL }, avatarNumber)
}
