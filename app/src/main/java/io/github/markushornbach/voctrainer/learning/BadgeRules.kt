package io.github.markushornbach.voctrainer.learning

/**
 * A badge series. [key] is stored in the database and the backup file; [thresholds] are the values
 * of tier 1..4 (Bronze, Silber, Gold, Platin).
 */
enum class BadgeSeries(val key: String, val displayName: String, val thresholds: List<Int>) {
    /** Longest streak in days ([StreakInfo.longest]). */
    STREAK("streak", "Serie", listOf(3, 7, 30, 100)),

    /** Sure words now, as the figure counts them ([FigureRules.countWords]). */
    SURE_WORDS("sure_words", "Gelernte Wörter", listOf(10, 50, 150, 500));

    companion object {
        /** Null for an unknown key, e.g. a series added by a newer app version. */
        fun fromKey(key: String): BadgeSeries? = entries.firstOrNull { it.key == key }
    }
}

/** One earned (or newly reached) badge: [tier] 1..[BadgeRules.MAX_TIER]. */
data class BadgeKey(val series: BadgeSeries, val tier: Int)

/** The two values the badges are measured by. */
data class BadgeValues(val longestStreak: Int, val sureWords: Int) {
    fun of(series: BadgeSeries): Int = when (series) {
        BadgeSeries.STREAK -> longestStreak
        BadgeSeries.SURE_WORDS -> sureWords
    }
}

/** One row of "Meine Abzeichen": the highest earned [tier] (0 = none yet) and the current [value]. */
data class BadgeProgress(val series: BadgeSeries, val tier: Int, val value: Int) {
    /** Value of the next tier; null when all tiers are earned. */
    val nextThreshold: Int? get() = BadgeRules.nextThreshold(series, tier)
}

/**
 * Badges: three series with four tiers each. They give no points and are never lost: a tier is
 * earned once the value reaches its threshold and stays even if the value drops later.
 */
object BadgeRules {
    const val MAX_TIER = 4

    private val TIER_NAMES = listOf("Bronze", "Silber", "Gold", "Platin")

    fun tierName(tier: Int): String = TIER_NAMES[tier.coerceIn(1, MAX_TIER) - 1]

    fun threshold(series: BadgeSeries, tier: Int): Int = series.thresholds[tier.coerceIn(1, MAX_TIER) - 1]

    /** Highest tier whose threshold [value] reaches; 0 if none. */
    fun tierFor(series: BadgeSeries, value: Int): Int = series.thresholds.count { value >= it }

    /** Threshold of the tier after [tier]; null after the last tier. */
    fun nextThreshold(series: BadgeSeries, tier: Int): Int? =
        if (tier >= MAX_TIER) null else threshold(series, tier + 1)

    /** All tiers [values] reach that are not in [earned] yet, in series and tier order. */
    fun newlyReached(values: BadgeValues, earned: Set<BadgeKey>): List<BadgeKey> =
        BadgeSeries.entries.flatMap { series ->
            (1..tierFor(series, values.of(series))).map { BadgeKey(series, it) }.filterNot { it in earned }
        }

    /** The rows of "Meine Abzeichen" in the order Serie, Gelernte Wörter. */
    fun progress(values: BadgeValues, earned: Set<BadgeKey>): List<BadgeProgress> =
        BadgeSeries.entries.map { series ->
            val tier = earned.filter { it.series == series }.maxOfOrNull { it.tier } ?: 0
            BadgeProgress(series, tier, values.of(series))
        }
}
