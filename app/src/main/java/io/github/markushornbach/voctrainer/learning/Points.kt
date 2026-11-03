package io.github.markushornbach.voctrainer.learning

/** One stored row of points (see `PointsDay`), independent of Room. */
data class PointsRecord(
    val date: String,
    val deviceId: String,
    val risePoints: Int,
    val dailyBonus: Int,
    val startPoints: Int
)

/**
 * Points of the reward system. A word earns points only for levels it reaches for the first
 * time (its highest level), due or not, so dropping and rising again can never be farmed; points
 * are never lost.
 */
object Points {
    const val DAILY_BONUS_BASE = 10
    const val DAILY_BONUS_MAX = 20

    /**
     * First attempt of a word in a round. If the level rises (same rule as
     * [Leitner.afterFirstAttempt], up to [maxLevel]; [early] and [reviewedEarlierDay] as there),
     * the word earns [forNewLevels] above its [highestLevel]; a level it already had earns
     * nothing, due or not. A due word on level 5 that stays on 5 by a typed answer still gets 5 points. A typeable
     * word held at its multiple-choice cap gets nothing, and so do multiple-choice answers on level 5
     * (no hold points without typing), and so do "Mit Vorschlägen" instead of typing and
     * "Auch richtig" or "Fast richtig" ([kept]).
     */
    fun forFirstAttempt(
        oldLevel: Int,
        highestLevel: Int,
        correct: Boolean,
        early: Boolean,
        maxLevel: Int = Leitner.MAX_LEVEL,
        reviewedEarlierDay: Boolean = true,
        form: AnswerForm = AnswerForm.MULTIPLE_CHOICE,
        kept: KeptAnswer? = null
    ): Int {
        if (!correct || kept != null || form == AnswerForm.CHOICE_INSTEAD_OF_TYPING) return 0
        val newLevel = Leitner.afterFirstAttempt(
            oldLevel, correct = true, early = early, maxLevel = maxLevel,
            reviewedEarlierDay = reviewedEarlierDay, form = form
        )
        return when {
            newLevel > oldLevel -> forNewLevels(highestLevel, newLevel)
            form == AnswerForm.TYPED && !early && newLevel == Leitner.MAX_LEVEL && oldLevel == Leitner.MAX_LEVEL -> newLevel
            else -> 0
        }
    }

    /** Sum of all levels above [highestLevel] up to [newLevel] (several at once if a word jumps). */
    fun forNewLevels(highestLevel: Int, newLevel: Int): Int = ((highestLevel + 1)..newLevel).sum()

    /** Once per day when the daily goal is reached: 10 + streak days (including today), at most 20. */
    fun dailyBonus(streakDays: Int): Int = (DAILY_BONUS_BASE + streakDays).coerceIn(DAILY_BONUS_BASE, DAILY_BONUS_MAX)

    /**
     * Total over all devices: every rise point counts; the daily bonus counts once per date
     * (the highest of all devices), the start points once per learner (the highest row). Start
     * points are never awarded; new rows carry 0, a backup may bring rows with start points.
     */
    fun total(rows: List<PointsRecord>): Int {
        val rise = rows.sumOf { it.risePoints }
        val bonus = rows.groupBy { it.date }.values.sumOf { day -> day.maxOf { it.dailyBonus } }
        val start = rows.maxOfOrNull { it.startPoints } ?: 0
        return rise + bonus + start
    }

    /**
     * Test app only ("Punkte setzen"): the rise points the row of [date] and [deviceId] needs so
     * that [total] becomes [target]. Only the rise points change, so bonus and start points
     * count as before; the result may be negative.
     */
    fun risePointsForTotal(rows: List<PointsRecord>, date: String, deviceId: String, target: Int): Int {
        val current = rows.firstOrNull { it.date == date && it.deviceId == deviceId }?.risePoints ?: 0
        return current + target - total(rows)
    }
}
