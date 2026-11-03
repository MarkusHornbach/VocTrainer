package io.github.markushornbach.voctrainer.learning

/** A four-option question as shown on the quiz screen. */
data class MultipleChoiceQuestion(
    val word: QuizWord,
    /** Large text at the top, e.g. the English word or the infinitive. */
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    /** Optional smaller line below the prompt (verbs: the German meaning). */
    val subtitle: String? = null,
    /** Optional instruction line, e.g. "Wie lautet das simple past?". */
    val instruction: String? = null,
    /** Shown after a wrong answer. */
    val solution: String,
    /** Spoken when the question appears (if auto-speak is on) and by the speaker button before answering. */
    val speakOnShow: String? = null,
    /** Spoken after the answer (if auto-speak is on); the speaker button speaks it once answered. */
    val speakAfterAnswer: String? = null,
    /**
     * Set for a typing question (typeable word from level 3): the learner types the core word;
     * [options] are then the "Mit Vorschlägen" alternative.
     */
    val typing: TypingTask? = null,
    /**
     * Set for a verb with three forms: after the simple past ([options]) the past participle is
     * chosen in this second step. The word counts as right only if both first answers were right.
     */
    val secondStep: AnswerStep? = null
)

/** The second step of a [MultipleChoiceQuestion]: its own options, solution and speech. */
data class AnswerStep(
    val options: List<String>,
    val correctIndex: Int,
    /** Shown after a wrong answer in this step. */
    val solution: String,
    /** Spoken after the answer of this step (if auto-speak is on) and by the speaker button once answered. */
    val speakAfterAnswer: String?
)

/**
 * The English entry around the core word: `(to) give sb. a ____` is [before] = "(to) give sb. a ",
 * [after] = "". If the core word is not found in the entry, the frame is just the gap.
 */
data class TypingTask(val coreWord: String, val before: String, val after: String) {
    /** The frame with the gap filled by [input] as typed so far. */
    fun frame(input: String): String = before + gap(input) + after

    companion object {
        const val GAP = "____"

        /** What stands in the gap: the input, padded with underscores to the width of [GAP]. */
        fun gap(input: String): String = input + "_".repeat((GAP.length - input.length).coerceAtLeast(0))

        fun of(word: QuizWord): TypingTask? {
            val core = word.coreWord ?: return null
            val match = Regex("""(?<!\p{L})""" + Regex.escape(core) + """(?!\p{L})""", RegexOption.IGNORE_CASE)
                .find(word.english)
                ?: return TypingTask(core, "", "")
            return TypingTask(core, word.english.substring(0, match.range.first), word.english.substring(match.range.last + 1))
        }
    }
}

/** Creates questions for a quiz round; one implementation per question type. */
interface QuestionGenerator {
    /** Returns null if the word cannot get enough distractors. */
    fun generate(target: QuizWord, candidates: List<QuizWord>): MultipleChoiceQuestion?

    fun canGenerate(target: QuizWord, candidates: List<QuizWord>): Boolean

    companion object {
        const val DISTRACTOR_COUNT = 3
    }
}
