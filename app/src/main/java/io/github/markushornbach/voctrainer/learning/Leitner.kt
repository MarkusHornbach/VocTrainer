package io.github.markushornbach.voctrainer.learning

/** How the first answer of a word in a round was given. */
enum class AnswerForm {
    /** A plain multiple-choice question (vocabulary on levels 0–2, verbs). */
    MULTIPLE_CHOICE,

    /** Typing question, answered by typing. */
    TYPED,

    /** Typing question, answered with "Mit Vorschlägen" (multiple choice instead of typing). */
    CHOICE_INSTEAD_OF_TYPING
}

/** A typed first answer that is neither plainly right nor wrong: level kept, no points, not repeated. */
enum class KeptAnswer {
    /** "Auch richtig": another entry with a shared German meaning was typed. */
    ALSO_CORRECT,

    /** "Fast richtig": a small typo or the American spelling (the word is copied once). */
    ALMOST_CORRECT
}

object Leitner {
    const val MAX_LEVEL = 5

    /** Days a word rests after a review, by level (index); level 0 is always due. */
    private val INTERVAL_DAYS = listOf(0, 1, 2, 4, 7, 14)

    fun intervalDays(level: Int): Int = INTERVAL_DAYS[level.coerceIn(0, MAX_LEVEL)]

    /**
     * Highest level multiple choice ("Mit Vorschlägen") gives a typeable word ([QuizWord.isTypeable]);
     * levels 4 and 5 are reached by typing it. "Mit Vorschlägen" instead of typing sets a typeable word
     * on level 4 or 5 back to this level.
     */
    const val CHOICE_MAX_TYPEABLE = 3

    /** Highest level multiple choice can give: [CHOICE_MAX_TYPEABLE] for typeable words, else (verbs) [MAX_LEVEL]. */
    fun choiceMaxLevel(typeable: Boolean): Int = if (typeable) CHOICE_MAX_TYPEABLE else MAX_LEVEL

    /** Any correct first attempt can raise a word up to this level, due or not (practice). */
    const val PRACTICE_MAX_LEVEL = 3

    /**
     * Rising to this level needs a previous review on an earlier calendar day (due or not);
     * above it only due words rise.
     */
    const val NEXT_DAY_MAX_LEVEL = 4

    /**
     * Only the first attempt of a word in a round changes its level. A wrong answer lowers it by
     * one (down to 0). A correct answer raises it by one, up to [maxLevel]: to [PRACTICE_MAX_LEVEL]
     * always, to [NEXT_DAY_MAX_LEVEL] only if [reviewedEarlierDay] (the previous review was on an
     * earlier day than today), above that only if the word was due (not [early]). A level at or
     * above [maxLevel] (e.g. from a backup) becomes [maxLevel] on a correct answer.
     *
     * [form]: typed correctly rises the same way up to [MAX_LEVEL] ([maxLevel] is ignored);
     * "Mit Vorschlägen" correct gives min(level, [CHOICE_MAX_TYPEABLE]). [kept] ("Auch richtig" or
     * "Fast richtig", see [KeptAnswer]) keeps the level, whatever [correct] says.
     */
    fun afterFirstAttempt(
        level: Int,
        correct: Boolean,
        early: Boolean = false,
        maxLevel: Int = MAX_LEVEL,
        reviewedEarlierDay: Boolean = true,
        form: AnswerForm = AnswerForm.MULTIPLE_CHOICE,
        kept: KeptAnswer? = null
    ): Int = outcomeOfFirstAttempt(level, correct, early, maxLevel, reviewedEarlierDay, form, kept).newLevel

    /** Like [afterFirstAttempt], with the reason for the result (the round hints are built from it). */
    fun outcomeOfFirstAttempt(
        level: Int,
        correct: Boolean,
        early: Boolean = false,
        maxLevel: Int = MAX_LEVEL,
        reviewedEarlierDay: Boolean = true,
        form: AnswerForm = AnswerForm.MULTIPLE_CHOICE,
        kept: KeptAnswer? = null
    ): LevelOutcome {
        when (kept) {
            KeptAnswer.ALSO_CORRECT -> return LevelOutcome(level, level, LevelReason.ALSO_CORRECT)
            KeptAnswer.ALMOST_CORRECT -> return LevelOutcome(level, level, LevelReason.ALMOST_CORRECT)
            null -> Unit
        }
        if (!correct) {
            val dropped = (level - 1).coerceAtLeast(0)
            return LevelOutcome(level, dropped, if (dropped < level) LevelReason.WRONG_DROP else LevelReason.WRONG_BOTTOM)
        }
        val limit = when (form) {
            AnswerForm.MULTIPLE_CHOICE -> maxLevel
            AnswerForm.TYPED -> MAX_LEVEL
            AnswerForm.CHOICE_INSTEAD_OF_TYPING -> return choiceCapped(level, CHOICE_MAX_TYPEABLE)
        }
        return rise(level, early, limit, reviewedEarlierDay)
    }

    private fun choiceCapped(level: Int, cap: Int): LevelOutcome {
        val new = level.coerceAtMost(cap)
        return LevelOutcome(level, new, if (new < level) LevelReason.CHOICE_DROPS else LevelReason.CHOICE_KEEPS)
    }

    private fun rise(level: Int, early: Boolean, maxLevel: Int, reviewedEarlierDay: Boolean): LevelOutcome {
        if (level >= maxLevel) {
            return when {
                maxLevel == MAX_LEVEL -> LevelOutcome(level, maxLevel, LevelReason.STAYS_TOP)
                else -> choiceCapped(level, maxLevel)
            }
        }
        val target = level + 1
        val blocked = when {
            target <= PRACTICE_MAX_LEVEL -> null
            target <= NEXT_DAY_MAX_LEVEL -> if (reviewedEarlierDay) null else LevelReason.STAYS_NEXT_DAY
            else -> if (early) LevelReason.STAYS_NOT_DUE else null
        }
        return if (blocked == null) LevelOutcome(level, target, LevelReason.RISE) else LevelOutcome(level, level, blocked)
    }
}

/** Why the first attempt of a word gave its new level (see [Leitner.outcomeOfFirstAttempt]). */
enum class LevelReason {
    /** Correct, one level up. */
    RISE,

    /** Correct, but level 4 needs an earlier calendar day. */
    STAYS_NEXT_DAY,

    /** Correct, but level 5 needs the word to be due. */
    STAYS_NOT_DUE,

    /** Correct on level 5. */
    STAYS_TOP,

    /** "Mit Vorschlägen" (or the multiple-choice cap) correct, level kept at the cap. */
    CHOICE_KEEPS,

    /** "Mit Vorschlägen" (or the multiple-choice cap) correct on a higher level: back to the cap. */
    CHOICE_DROPS,

    /** "Auch richtig": another entry was typed; level kept. */
    ALSO_CORRECT,

    /** "Fast richtig": small typo or American spelling; level kept. */
    ALMOST_CORRECT,

    /** Wrong, one level down. */
    WRONG_DROP,

    /** Wrong on level 0: cannot drop further. */
    WRONG_BOTTOM
}

data class LevelOutcome(val oldLevel: Int, val newLevel: Int, val reason: LevelReason) {
    val rose: Boolean get() = newLevel > oldLevel
    val dropped: Boolean get() = newLevel < oldLevel
}
