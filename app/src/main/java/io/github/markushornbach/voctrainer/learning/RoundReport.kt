package io.github.markushornbach.voctrainer.learning

import java.time.LocalDate

/** What a first answer did to the level (no line during the round; the result screen explains it in "Details"). */
enum class HintKind {
    RISE,
    RISE_ALREADY_REACHED,
    SURE,
    SURE_ALREADY_REACHED,
    MASTERED,
    MASTERED_ALREADY_REACHED,
    STAYS_NEXT_DAY,
    STAYS_NOT_DUE,
    CHOICE_KEEPS,
    CHOICE_DROPS,
    ALSO_CORRECT,
    ALMOST_CORRECT,
    WRONG_DROP,
    WRONG_BOTTOM
}

/**
 * The effect of one first attempt: [level] is the level after the answer. [targetLevel] is the
 * level the word would rise to ([STAYS_NEXT_DAY][HintKind.STAYS_NEXT_DAY]); [availableFrom] is the day
 * level 5 becomes possible ([STAYS_NOT_DUE][HintKind.STAYS_NOT_DUE] only, null = unknown).
 */
data class RoundHint(
    val kind: HintKind,
    val level: Int,
    val availableFrom: LocalDate? = null,
    val targetLevel: Int = level + 1
) {
    /** Rose without points: "Details" says "Level bereits vorher erreicht". */
    val alreadyReached: Boolean
        get() = kind == HintKind.RISE_ALREADY_REACHED || kind == HintKind.SURE_ALREADY_REACHED ||
            kind == HintKind.MASTERED_ALREADY_REACHED

    /** A rise, also to a level the word had before. */
    val isRise: Boolean
        get() = kind == HintKind.RISE || kind == HintKind.SURE || kind == HintKind.MASTERED || alreadyReached
}

/** One recorded first attempt of a round. [pointsApply] is false in verb training (no points at all). */
data class AttemptRecord(
    val english: String,
    val outcome: LevelOutcome,
    val points: Int,
    val pointsApply: Boolean = true,
    /** The day level 5 becomes possible (only for [LevelReason.STAYS_NOT_DUE]). */
    val availableFrom: LocalDate? = null,
    /** The kind of "Fast richtig" (only for [LevelReason.ALMOST_CORRECT]). */
    val almost: AlmostKind? = null
) {
    /** Rose without earning points: the word had this level before (see [Points.forFirstAttempt]). */
    val risenToReachedLevel: Boolean get() = outcome.rose && pointsApply && points == 0
}

/** What happened to the level of a word at its first attempt (arrow in "Details"). */
enum class DetailStatus { UP, DOWN, SAME }

/** Why a word did not rise or dropped (second line of "Details"; texts: `RoundDetailTexts`). */
enum class DetailReason {
    NEXT_DAY, NOT_DUE, CHOICE, WRONG, ALSO_CORRECT,

    /** "Fast richtig" by a small typo (of the asked word or of an "Auch richtig" entry). */
    ALMOST_TYPO,

    /** "Fast richtig" by the American spelling. */
    ALMOST_AMERICAN,

    /** Correct on level 5, which stays. */
    TOP
}

/**
 * One word of "Details" on the result screen: [level] is the level after the answer, [points] those
 * of the first attempt (null in verb training, which gives none). [reason] explains why the level
 * did not rise (null if it rose); [reachedBefore] = rose to a level the word had before (0 points);
 * [notDue] = correct on level 5 but not due (0 points).
 */
data class RoundDetail(
    val english: String,
    val status: DetailStatus,
    val level: Int,
    val points: Int?,
    val reason: DetailReason? = null,
    val reachedBefore: Boolean = false,
    val notDue: Boolean = false,
    val availableFrom: LocalDate? = null
)

object RoundReport {
    /** The hint for the first attempt [record]; null = no line (correct on level 5). */
    fun hint(record: AttemptRecord): RoundHint? =
        hint(record.outcome, record.risenToReachedLevel, record.availableFrom)

    fun hint(
        outcome: LevelOutcome,
        alreadyReached: Boolean,
        availableFrom: LocalDate? = null
    ): RoundHint? {
        val level = outcome.newLevel
        val kind = when (outcome.reason) {
            LevelReason.RISE -> when {
                level == Leitner.MAX_LEVEL ->
                    if (alreadyReached) HintKind.MASTERED_ALREADY_REACHED else HintKind.MASTERED
                level == Leitner.NEXT_DAY_MAX_LEVEL ->
                    if (alreadyReached) HintKind.SURE_ALREADY_REACHED else HintKind.SURE
                else -> if (alreadyReached) HintKind.RISE_ALREADY_REACHED else HintKind.RISE
            }
            LevelReason.STAYS_NEXT_DAY -> HintKind.STAYS_NEXT_DAY
            LevelReason.STAYS_NOT_DUE -> HintKind.STAYS_NOT_DUE
            LevelReason.STAYS_TOP -> return null
            LevelReason.CHOICE_KEEPS -> HintKind.CHOICE_KEEPS
            LevelReason.CHOICE_DROPS -> HintKind.CHOICE_DROPS
            LevelReason.ALSO_CORRECT -> HintKind.ALSO_CORRECT
            LevelReason.ALMOST_CORRECT -> HintKind.ALMOST_CORRECT
            LevelReason.WRONG_DROP -> HintKind.WRONG_DROP
            LevelReason.WRONG_BOTTOM -> HintKind.WRONG_BOTTOM
        }
        return RoundHint(
            kind, level,
            availableFrom = availableFrom.takeIf { kind == HintKind.STAYS_NOT_DUE }
        )
    }

    /**
     * Words whose level went up in the round. Counted from [details], so the number on the result
     * screen and the list in "Details" always match (the first attempt is the only level change of a round).
     */
    fun risen(details: List<RoundDetail>): Int = details.count { it.status == DetailStatus.UP }

    /** Words whose level actually decreased (wrong answers and "Mit Vorschlägen" drops together). */
    fun dropped(details: List<RoundDetail>): Int = details.count { it.status == DetailStatus.DOWN }

    /** Verb training: rises first, then unchanged, then drops; round order within each group. */
    fun risesFirst(details: List<RoundDetail>): List<RoundDetail> = details.sortedBy {
        when (it.status) {
            DetailStatus.UP -> 0
            DetailStatus.SAME -> 1
            DetailStatus.DOWN -> 2
        }
    }

    /** "Details": one entry per first attempt of the round, in round order. */
    fun details(records: List<AttemptRecord>): List<RoundDetail> = records.map { record ->
        val outcome = record.outcome
        val reason = when (outcome.reason) {
            LevelReason.RISE -> null
            LevelReason.STAYS_NEXT_DAY -> DetailReason.NEXT_DAY
            LevelReason.STAYS_NOT_DUE -> DetailReason.NOT_DUE
            LevelReason.STAYS_TOP -> DetailReason.TOP
            LevelReason.CHOICE_KEEPS, LevelReason.CHOICE_DROPS -> DetailReason.CHOICE
            LevelReason.ALSO_CORRECT -> DetailReason.ALSO_CORRECT
            LevelReason.ALMOST_CORRECT ->
                if (record.almost == AlmostKind.AMERICAN) DetailReason.ALMOST_AMERICAN else DetailReason.ALMOST_TYPO
            LevelReason.WRONG_DROP, LevelReason.WRONG_BOTTOM -> DetailReason.WRONG
        }
        RoundDetail(
            english = record.english,
            status = when {
                outcome.rose -> DetailStatus.UP
                outcome.dropped -> DetailStatus.DOWN
                else -> DetailStatus.SAME
            },
            level = outcome.newLevel,
            points = record.points.takeIf { record.pointsApply },
            reason = reason,
            reachedBefore = record.risenToReachedLevel,
            notDue = reason == DetailReason.TOP && record.pointsApply && record.points == 0,
            availableFrom = record.availableFrom.takeIf { reason == DetailReason.NOT_DUE }
        )
    }
}
