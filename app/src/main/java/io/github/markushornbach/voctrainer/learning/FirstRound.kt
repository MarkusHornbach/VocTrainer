package io.github.markushornbach.voctrainer.learning

/**
 * Whether the learner has finished a vocabulary round in the active book: a stored round
 * ([roundsPerDay], the finished rounds of the practice days of the book) or a word above level 0.
 * Both are stored, so the answer survives a restart of the app; "Buch neu beginnen" resets both.
 */
object FirstRound {
    fun done(words: List<QuizWord>, roundsPerDay: List<Int>): Boolean =
        roundsPerDay.any { it > 0 } || words.any { it.leitnerLevel > 0 }
}
