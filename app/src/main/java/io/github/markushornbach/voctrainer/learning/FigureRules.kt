package io.github.markushornbach.voctrainer.learning

/** The figures a learner can grow. [key] is stored in the database and the backup file. */
enum class FigureKind(
    val key: String,
    /** "Drache" */
    val displayName: String,
    /** "Dein Drache" */
    val withArticle: String,
    /** "Den Drachen nehmen?" */
    val choiceQuestion: String,
    /** Personal pronoun for "… kommt in deine Sammlung." */
    val pronoun: String,
    /** Short line on the choice card. */
    val startLine: String
) {
    DRAGON("dragon", "Drache", "Dein Drache", "Den Drachen nehmen?", "Er", "Startet als Ei"),
    MONSTER("monster", "Monster", "Dein Monster", "Das Monster nehmen?", "Es", "Startet als Ei"),
    FIGHTER("fighter", "Kämpfer", "Dein Kämpfer", "Den Kämpfer nehmen?", "Er", "Startet als Rekrut");

    companion object {
        /** Null for an unknown key, e.g. a kind added by a newer app version. */
        fun fromKey(key: String): FigureKind? = entries.firstOrNull { it.key == key }
    }
}

/** What a stage needs: points since the figure was started and sure words (whole book). */
data class StageRequirement(val points: Int, val sureWords: Int)

/** How far the next stage is; 0 means that part is already fulfilled. */
data class NextStage(val stage: Int, val missingPoints: Int, val missingSureWords: Int) {
    /** The points are reached (full bar), only sure words are missing: the bar shows a lock. */
    val onlySureWordsMissing: Boolean get() = missingPoints == 0 && missingSureWords > 0
}

/** Sure and askable vocabulary entries over all vocabulary (see [FigureRules.countWords]). */
data class WordCounts(val sure: Int, val askable: Int)

/**
 * The requirements of avatar [number] (1-based, see [FigureRules.avatarNumber]) in a book with
 * [askable] entries. Avatar k starts where avatar k−1 ended: its sure-word requirements start at
 * `(k − 1) × 300` (the whole book counts). The cap (80 % of the askable entries) shrinks words and
 * points of the avatar in the same ratio [scale], so small books and the end of the imported
 * vocabulary stay reachable.
 */
class AvatarPlan(val number: Int, val askable: Int) {
    val cap: Int = FigureRules.sureWordCap(askable)

    /** Sure words the finished avatars already used up. */
    val base: Int = (number - 1) * FigureRules.FULL_AVATAR_WORDS

    /** New sure words this avatar can still ask for; negative if the finished ones used more than the cap. */
    val available: Int = cap - base

    /** [available] clamped to the smallest and largest share, in 300ths of a full avatar (integer maths, no rounding noise). */
    private val scaleNumerator: Int =
        available.coerceIn(Math.round(FigureRules.MIN_SCALE * FigureRules.FULL_AVATAR_WORDS).toInt(), FigureRules.FULL_AVATAR_WORDS)

    /** Share of a full avatar's requirements, [FigureRules.MIN_SCALE]..1. */
    val scale: Double = scaleNumerator.toDouble() / FigureRules.FULL_AVATAR_WORDS

    /** The first avatar can always be chosen; a later one needs enough new words. */
    val canStart: Boolean = number <= 1 || available >= FigureRules.MIN_NEXT_AVATAR_WORDS

    private val requirements: List<StageRequirement> = run {
        var previousPoints = 0
        FigureRules.STAGES.map { full ->
            val points = maxOf(Math.round(full.points.toDouble() * scaleNumerator / FigureRules.FULL_AVATAR_WORDS).toInt(), previousPoints + 1)
            previousPoints = points
            val words = if (full.sureWords == 0) 0 else minOf(base + ceilDiv(full.sureWords * scaleNumerator, FigureRules.FULL_AVATAR_WORDS), cap)
            StageRequirement(points, words)
        }
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

    fun requirement(stage: Int): StageRequirement = requirements[stage.coerceIn(1, FigureRules.MAX_STAGE) - 1]
}

/**
 * Growing a figure: each stage needs points since the figure was started and, from stage 3 on,
 * sure words. A word needs about a week to reach level 4, so a figure cannot grow in one day.
 * Every avatar needs its own words (see [AvatarPlan]).
 */
object FigureRules {
    const val MAX_STAGE = 10

    /** Sure words of a full avatar: the last row of [STAGES]. */
    const val FULL_AVATAR_WORDS = 300

    /** Share of the askable vocabulary that all avatars together may require as sure words, in percent. */
    private const val SURE_WORD_CAP_PERCENT = 80

    /** A new avatar can be chosen only if at least this many new words are still available. */
    const val MIN_NEXT_AVATAR_WORDS = 150

    /** Smallest share of a full avatar's requirements. */
    const val MIN_SCALE = 0.1

    /** Requirements of a full avatar for stage 1..10 (index 0 = stage 1); tune here after testing. */
    val STAGES: List<StageRequirement> = listOf(
        StageRequirement(30, 0),
        StageRequirement(120, 0),
        StageRequirement(300, 10),
        StageRequirement(550, 25),
        StageRequirement(900, 50),
        StageRequirement(1_300, 80),
        StageRequirement(1_800, 120),
        StageRequirement(2_500, 170),
        StageRequirement(3_300, 230),
        StageRequirement(4_300, 300)
    )

    /** Most sure words all avatars of a book together may require: 80 % of [askable], rounded down. */
    fun sureWordCap(askable: Int): Int = askable * SURE_WORD_CAP_PERCENT / 100

    /** Avatar number of a book: finished figures + 1. Given-up figures do not count. */
    fun avatarNumber(finishedFigures: Int): Int = finishedFigures + 1

    /** Highest stage whose requirements and those of all stages below are met. */
    fun computedStage(pointsSinceStart: Int, counts: WordCounts, number: Int): Int {
        val plan = AvatarPlan(number, counts.askable)
        var stage = 0
        while (stage < MAX_STAGE) {
            val next = stage + 1
            val required = plan.requirement(next)
            val met = pointsSinceStart >= required.points && counts.sure >= required.sureWords
            if (!met) break
            stage = next
        }
        return stage
    }

    /** Points that count for a figure: the total since it was started (never negative). */
    fun pointsSinceStart(totalPoints: Int, pointsAtStart: Int): Int = (totalPoints - pointsAtStart).coerceAtLeast(0)

    /** A reached stage is never lowered; the computed stage can only raise it. */
    fun newStage(stored: Int, pointsSinceStart: Int, counts: WordCounts, number: Int): Int =
        maxOf(stored, computedStage(pointsSinceStart, counts, number)).coerceAtMost(MAX_STAGE)

    /** What is missing for the stage after [stage]; null at the last stage. */
    fun nextStage(stage: Int, pointsSinceStart: Int, counts: WordCounts, number: Int): NextStage? {
        if (stage >= MAX_STAGE) return null
        val next = stage + 1
        val required = AvatarPlan(number, counts.askable).requirement(next)
        return NextStage(
            stage = next,
            missingPoints = (required.points - pointsSinceStart).coerceAtLeast(0),
            missingSureWords = (required.sureWords - counts.sure).coerceAtLeast(0)
        )
    }

    /**
     * Points progress towards the stage after [stage], 0..1, from the current stage's points
     * (stage 0 = 0); the bar on the start screen and on "Mein Avatar". Sure words are not part of
     * it, the text on "Mein Avatar" names them. Full at [MAX_STAGE].
     */
    fun pointsProgress(stage: Int, pointsSinceStart: Int, plan: AvatarPlan): Float {
        if (stage >= MAX_STAGE) return 1f
        val from = if (stage <= 0) 0 else plan.requirement(stage).points
        val to = plan.requirement(stage + 1).points
        return ((pointsSinceStart - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }

    /**
     * Sure words = active vocabulary entries on level ≥ [LearningProgress.SURE_LEVEL]; askable =
     * active entries, phrases included. [words] are the active (non-ignored) vocabulary entries;
     * irregular verbs do not count.
     */
    fun countWords(words: List<QuizWord>): WordCounts =
        WordCounts(words.count { it.leitnerLevel >= LearningProgress.SURE_LEVEL }, words.size)

    /** First stage that needs sure words (3). */
    val firstSureWordStage: Int get() = STAGES.indexOfFirst { it.sureWords > 0 } + 1
}
