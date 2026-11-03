package io.github.markushornbach.voctrainer.learning

import kotlin.random.Random

enum class QuestionDirection {
    ENGLISH_TO_GERMAN,
    GERMAN_TO_ENGLISH;

    companion object {
        /** "Erst verstehen, dann selbst schreiben": from this level on, the English word is recalled. */
        const val GERMAN_TO_ENGLISH_FROM_LEVEL = 2

        /** Typeable words ([QuizWord.isTypeable]) are typed from this level on, with "Mit Vorschlägen" as alternative. */
        const val TYPING_FROM_LEVEL = 3

        /** A typing question for [word] at its (snapshot) level. */
        fun isTyping(word: QuizWord): Boolean = word.isTypeable && word.leitnerLevel >= TYPING_FROM_LEVEL

        fun forLevel(level: Int): QuestionDirection =
            if (level >= GERMAN_TO_ENGLISH_FROM_LEVEL) GERMAN_TO_ENGLISH else ENGLISH_TO_GERMAN
    }
}

/**
 * Builds a four-option question from the vocabulary itself (no network). The direction follows
 * the level of [QuizWord] as passed in; a round passes the snapshot taken at its start, so a
 * repeated word keeps the direction (and the typing form) of its first question. A typing
 * question carries the options of German → English as its "Mit Vorschlägen" alternative.
 * Distractors come from the first tier that still has valid candidates:
 * same unit + part + word type → same unit + word type → same word type → anything.
 * A candidate is never valid if it could also be a correct answer, and no two options show the
 * same text (German main meaning, or English entry for German → English).
 */
class MultipleChoiceGenerator(private val random: Random) : QuestionGenerator {

    /** Returns null if fewer than [DISTRACTOR_COUNT] valid distractors exist. */
    override fun generate(target: QuizWord, candidates: List<QuizWord>): MultipleChoiceQuestion? {
        val direction = QuestionDirection.forLevel(target.leitnerLevel)
        val valid = validCandidates(target, candidates)
        val type = target.effectiveType
        val tiers = listOf(
            valid.filter { it.unit == target.unit && it.part == target.part && it.effectiveType == type },
            valid.filter { it.unit == target.unit && it.effectiveType == type },
            valid.filter { it.effectiveType == type },
            valid
        )
        val chosen = mutableListOf<QuizWord>()
        val chosenIds = HashSet<String>()
        val chosenOptionKeys = HashSet<String>()
        for (tier in tiers) {
            for (candidate in tier.shuffled(random)) {
                if (chosen.size == DISTRACTOR_COUNT) break
                if (candidate.id in chosenIds) continue
                if (!chosenOptionKeys.add(optionKey(candidate, direction))) continue
                chosenIds += candidate.id
                chosen += candidate
            }
            if (chosen.size == DISTRACTOR_COUNT) break
        }
        if (chosen.size < DISTRACTOR_COUNT) return null

        val options = (chosen + target).shuffled(random)
        return when (direction) {
            QuestionDirection.ENGLISH_TO_GERMAN -> MultipleChoiceQuestion(
                word = target,
                prompt = target.english,
                options = options.map { GermanText.withoutGenderSuffix(it.mainMeaning) },
                correctIndex = options.indexOf(target),
                solution = target.germanMeanings.joinToString(", ") { GermanText.withoutGenderSuffix(it) },
                speakOnShow = target.english
            )
            // The voice would give the answer away, so the English word is spoken only after answering.
            // No instruction line: the task is clear from the screen (only verb training has one).
            QuestionDirection.GERMAN_TO_ENGLISH -> MultipleChoiceQuestion(
                word = target,
                prompt = AnswerChecker.germanPrompt(target.germanMeanings),
                options = options.map { it.english },
                correctIndex = options.indexOf(target),
                solution = target.english,
                speakAfterAnswer = target.english,
                typing = if (QuestionDirection.isTyping(target)) TypingTask.of(target) else null
            )
        }
    }

    /**
     * True if at least [DISTRACTOR_COUNT] valid candidates with different option texts exist
     * for the direction [target] will be asked in.
     */
    override fun canGenerate(target: QuizWord, candidates: List<QuizWord>): Boolean {
        val direction = QuestionDirection.forLevel(target.leitnerLevel)
        val optionKeys = HashSet<String>()
        for (candidate in candidates) {
            if (isValid(target, candidate) && optionKeys.add(optionKey(candidate, direction)) &&
                optionKeys.size >= DISTRACTOR_COUNT
            ) {
                return true
            }
        }
        return false
    }

    /** What must differ between options: the German main meaning or the English entry, as displayed. */
    private fun optionKey(word: QuizWord, direction: QuestionDirection): String = when (direction) {
        QuestionDirection.ENGLISH_TO_GERMAN -> word.mainMeaningKey
        QuestionDirection.GERMAN_TO_ENGLISH -> word.englishKey
    }

    private fun validCandidates(target: QuizWord, candidates: List<QuizWord>): List<QuizWord> =
        candidates.filter { isValid(target, it) }

    // Meanings are compared as displayed, so "Tänzer/in" and "Tänzer" can never appear as two options.
    private fun isValid(target: QuizWord, candidate: QuizWord): Boolean {
        if (candidate.id == target.id || candidate.englishKey == target.englishKey) return false
        val targetMeanings = target.meaningKeys
        return candidate.meaningKeys.none { it in targetMeanings }
    }

    companion object {
        const val DISTRACTOR_COUNT = QuestionGenerator.DISTRACTOR_COUNT
    }
}
