package io.github.markushornbach.voctrainer.learning

import java.time.LocalDate
import java.time.ZoneId

/**
 * Items that one correct answer today would make "gelernt" (start screen and reminder). The
 * rules come from [Leitner.outcomeOfFirstAttempt] with the same arguments the repository uses
 * when the answer is stored, so a level-3 word counts once it was asked on an earlier day.
 */
object SureToday {
    fun canBecomeSure(word: QuizWord, today: LocalDate, zone: ZoneId): Boolean {
        if (word.leitnerLevel >= LearningProgress.SURE_LEVEL) return false
        val early = !Due.isDue(word, today, zone)
        val earlier = Due.reviewedEarlierDay(word.lastReviewedAt, today, zone)
        val outcome = if (word.verbForms != null) {
            Leitner.outcomeOfFirstAttempt(word.leitnerLevel, correct = true, early = early, reviewedEarlierDay = earlier)
        } else {
            val typeable = word.isTypeable
            Leitner.outcomeOfFirstAttempt(
                word.leitnerLevel, correct = true, early = early, maxLevel = Leitner.choiceMaxLevel(typeable),
                reviewedEarlierDay = earlier,
                form = if (typeable) AnswerForm.TYPED else AnswerForm.MULTIPLE_CHOICE
            )
        }
        return outcome.newLevel >= LearningProgress.SURE_LEVEL
    }

    /** [words] must already be filtered to the selection (see [QuizSelection.filter]). */
    fun count(words: List<QuizWord>, today: LocalDate, zone: ZoneId): Int = words.count { canBecomeSure(it, today, zone) }
}
