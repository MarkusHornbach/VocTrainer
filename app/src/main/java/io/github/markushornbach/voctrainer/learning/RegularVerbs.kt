package io.github.markushornbach.voctrainer.learning

import kotlin.random.Random

/**
 * Regular verbs from the vocabulary, mixed into irregular verb rounds so that an "-ed" answer is
 * not automatically wrong. They never change any learning progress.
 */
object RegularVerbs {
    const val PER_ROUND = 2

    /**
     * Vocabulary entries usable as regular verbs: effective type VERB, a plain head word (no "/",
     * no "…"), and the head word is no form of the built-in list or of an imported irregular verb.
     * The correct answer is the regular form of the head word plus the unchanged tail, for the simple
     * past and the past participle alike.
     */
    fun detect(vocabulary: List<QuizWord>, irregularVerbs: List<QuizWord>): List<QuizWord> {
        val irregularForms = HashSet(IrregularVerbList.allForms)
        irregularVerbs.forEach { verb ->
            irregularForms += VerbDistractorGenerator.splitHead(verb.english).first
            verb.verbForms?.let { irregularForms += VerbDistractorGenerator.correctHeads(it.simplePast) }
        }
        return vocabulary.mapNotNull { word ->
            if (word.effectiveType != WordType.VERB || "…" in word.english) return@mapNotNull null
            val (head, tail) = VerbDistractorGenerator.splitHead(word.english)
            if (head.isEmpty() || '/' in head || head in irregularForms) return@mapNotNull null
            // The past participle of a regular verb is the same "-ed" form: play → played → played.
            val past = VerbDistractorGenerator.regularForm(head) + tail
            word.copy(storedType = WordType.VERB, verbForms = VerbForms(past, past, isRegular = true))
        }
    }

    /** Up to [count] random regular verbs that can get three distractors. */
    fun pick(
        regularVerbs: List<QuizWord>,
        pool: List<QuizWord>,
        generator: QuestionGenerator,
        random: Random,
        count: Int = PER_ROUND
    ): List<QuizWord> = regularVerbs.filter { generator.canGenerate(it, pool) }.shuffled(random).take(count)

    /** Inserts [extra] at random positions into [words], keeping the order of [words]. */
    fun mixIn(words: List<QuizWord>, extra: List<QuizWord>, random: Random): List<QuizWord> {
        val result = words.toMutableList()
        extra.forEach { result.add(random.nextInt(result.size + 1), it) }
        return result
    }
}
