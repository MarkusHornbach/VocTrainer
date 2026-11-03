package io.github.markushornbach.voctrainer.learning

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

data class RoundQuestion(
    val question: MultipleChoiceQuestion,
    /** 1-based position of the word in the round. */
    val number: Int,
    val isRepeat: Boolean,
    /** The word was not due yet (fill-up); a correct first answer then raises it only up to level 3 (see [Leitner.afterFirstAttempt]). */
    val pickedEarly: Boolean = false
)

/** Words of a round in pick order; [earlyIds] were picked before they were due. */
data class RoundPick(val words: List<QuizWord>, val earlyIds: Set<String>)

/** What to show after a word was edited during the round ([QuizRound.reaskAfterEdit]). */
sealed interface ReaskResult {
    /** The question of the edited word, rebuilt from its current data; nothing answered yet. */
    data class Question(val question: RoundQuestion) : ReaskResult

    /** Nothing to ask again for the edited word (done, deleted or not askable): go on with the next question. */
    data object Next : ReaskResult
}

/**
 * One quiz round. A word answered wrongly is appended to the end and asked again (with new
 * distractors) until it is answered correctly once; only first attempts count for the score.
 */
class QuizRound(
    words: List<QuizWord>,
    pool: List<QuizWord>,
    private val generator: QuestionGenerator,
    private val earlyIds: Set<String> = emptySet()
) {
    private data class Entry(val word: QuizWord, val number: Int, val isRepeat: Boolean)

    /** Where the question asked last stands. */
    private enum class Shown { OPEN, ANSWERED_RIGHT, ANSWERED_WRONG }

    /** All words distractors are taken from; edited words are replaced ([reaskAfterEdit]). */
    var pool: List<QuizWord> = pool
        private set

    private val queue = ArrayDeque(words.mapIndexed { index, word -> Entry(word, index + 1, false) })
    private var current: Entry? = null
    private var shown: Entry? = null
    private var shownState = Shown.OPEN
    private val firstAttempts = LinkedHashMap<String, Pair<QuizWord, Boolean>>()
    private val credits = HashMap<String, Int>()

    val size: Int = words.size

    /** Returns the next question, or null when the round is over. */
    fun nextQuestion(): RoundQuestion? {
        while (true) {
            val entry = queue.removeFirstOrNull() ?: run { current = null; shown = null; return null }
            val question = generator.generate(entry.word, pool) ?: continue
            return open(entry, question)
        }
    }

    private fun open(entry: Entry, question: MultipleChoiceQuestion): RoundQuestion {
        current = entry
        shown = entry
        shownState = Shown.OPEN
        return RoundQuestion(question, entry.number, entry.isRepeat, entry.word.id in earlyIds)
    }

    /** Records the answer to the current question; returns true if it was the word's first attempt. */
    fun recordAnswer(correct: Boolean, credit: Int = if (correct) RoundStars.FULL_CREDIT else 0): Boolean {
        val entry = checkNotNull(current) { "No question is open" }
        current = null
        shownState = if (correct) Shown.ANSWERED_RIGHT else Shown.ANSWERED_WRONG
        val firstAttempt = !entry.isRepeat
        if (firstAttempt) {
            firstAttempts[entry.word.id] = entry.word to correct
            credits[entry.word.id] = credit
        }
        if (!correct) queue.addLast(entry.copy(isRepeat = true))
        return firstAttempt
    }

    /**
     * The word of the question shown last was edited ([updated]; null = it was deleted). Rebuilds
     * that question from the current data of the word, unanswered, so the form, options and frame
     * follow the new data. The word keeps its first-attempt record: if it was already answered
     * wrongly, its pending repetition is asked now (it is taken out of the queue, so it comes only
     * once); if it was answered correctly there is nothing to ask again. A deleted word is removed
     * from the round with its repetition. The pool is updated either way.
     */
    fun reaskAfterEdit(updated: QuizWord?): ReaskResult {
        val entry = shown ?: return ReaskResult.Next
        val id = entry.word.id
        pool = if (updated == null) pool.filter { it.id != id } else pool.map { if (it.id == id) updated else it }
        if (updated == null) {
            queue.removeAll { it.word.id == id }
            current = null
            shown = null
            return ReaskResult.Next
        }
        val reopened = when (shownState) {
            Shown.OPEN -> entry
            Shown.ANSWERED_WRONG -> queue.lastOrNull { it.word.id == id && it.isRepeat }?.also { queue.remove(it) }
            Shown.ANSWERED_RIGHT -> null
        } ?: return ReaskResult.Next
        val entryNow = reopened.copy(word = updated)
        val question = generator.generate(updated, pool) ?: run { current = null; shown = null; return ReaskResult.Next }
        return ReaskResult.Question(open(entryNow, question))
    }

    val correctOnFirstAttempt: Int get() = firstAttempts.values.count { it.second }

    /** Sum of the credits (in thirds) of all first attempts; see [RoundStars]. */
    val starCredit: Int get() = credits.values.sum()

    /** First attempts that earned only one third (typing was possible but "Mit Vorschlägen" or "Fast richtig" was used). */
    val reducedCredits: Int get() = credits.values.count { it == RoundStars.REDUCED_CREDIT }

    val wrongOnFirstAttempt: List<QuizWord> get() = firstAttempts.values.filter { !it.second }.map { it.first }

    companion object {
        const val ROUND_SIZE = 10

        /** Places per round reserved for words that can become "gelernt" today (see [SureToday]). */
        const val SURE_SLOTS = 3

        /**
         * Picks the words of a round from [selection]:
         * 1. up to [SURE_SLOTS] words that can become "gelernt" today ([SureToday.canBecomeSure]),
         *    the oldest review day first (random among equal days);
         * 2. the remaining places from all other words: due reviews (level ≥ 1), lowest level first,
         *    random within a level; then new words (level 0), random; then the fill-up with words
         *    that are not due yet, lowest level first, within a level the oldest review day first
         *    (random among equal level and day).
         * Every picked word that is not due is marked as picked early.
         */
        fun pickWords(
            selection: List<QuizWord>,
            random: Random,
            today: LocalDate,
            zone: ZoneId,
            size: Int = ROUND_SIZE
        ): RoundPick {
            val slots = selection.filter { SureToday.canBecomeSure(it, today, zone) }
                .groupBy { reviewDay(it, zone) }.toSortedMap().values
                .flatMap { it.shuffled(random) }
                .take(minOf(SURE_SLOTS, size))
            val slotIds = slots.mapTo(HashSet()) { it.id }
            val (due, notDue) = selection.filter { it.id !in slotIds }.partition { Due.isDue(it, today, zone) }
            val reviews = due.filter { it.leitnerLevel >= 1 }
                .groupBy { it.leitnerLevel }.toSortedMap().values
                .flatMap { it.shuffled(random) }
            val new = due.filter { it.leitnerLevel <= 0 }.shuffled(random)
            val early = notDue.groupBy { it.leitnerLevel to reviewDay(it, zone) }
                .toSortedMap(compareBy<Pair<Int, LocalDate>> { it.first }.thenBy { it.second })
                .values
                .flatMap { it.shuffled(random) }
            val picked = slots + (reviews + new + early).take(size - slots.size)
            return RoundPick(picked, picked.filter { !Due.isDue(it, today, zone) }.mapTo(HashSet()) { it.id })
        }

        private fun reviewDay(word: QuizWord, zone: ZoneId): LocalDate =
            word.lastReviewedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.MIN
    }
}
