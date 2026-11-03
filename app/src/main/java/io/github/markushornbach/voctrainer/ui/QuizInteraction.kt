package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.AlsoCorrectIndex
import io.github.markushornbach.voctrainer.learning.AnswerChecker
import io.github.markushornbach.voctrainer.learning.AnswerForm
import io.github.markushornbach.voctrainer.learning.KeptAnswer
import io.github.markushornbach.voctrainer.learning.LearningMaterial
import io.github.markushornbach.voctrainer.learning.QuizRound
import io.github.markushornbach.voctrainer.learning.QuizWord
import io.github.markushornbach.voctrainer.learning.RoundStars
import kotlin.random.Random

/** Where the learner is within one question. */
enum class AnswerPhase {
    /** Options neutral and tappable; the first tap is the real answer. */
    ANSWERING,

    /** Answered correctly (first try or retry); all options disabled, moves on by itself. */
    CORRECT,

    /** Answered wrongly: tapped option red, correct one green, solution and "Nochmal versuchen". */
    SOLUTION,

    /** After "Nochmal versuchen": the same options reshuffled and neutral, solution hidden. */
    RETRY
}

/** Where the learner is within a typing question ([QuizRoundState.Asking.typing]). */
enum class TypingPhase {
    /** Input field open; "Mit Vorschlägen" may appear while it is empty. */
    TYPING,

    /** "Mit Vorschlägen" was tapped: the options are shown and [AnswerPhase] applies; no way back. */
    CHOICE,

    /** Typed correctly (gap turns green); moves on by itself. */
    CORRECT,

    /** "Auch richtig! Gesucht war: …"; moves on by itself. */
    ALSO_CORRECT,

    /**
     * Typed wrongly or "Fast richtig" ([TypingState.almost]): the solution stays visible and the
     * core word has to be typed once.
     */
    COPYING,

    /** The core word was typed after a mistake; moves on by itself. */
    COPIED
}

/** State of a typing question; see [QuizInteraction] for the transitions. */
data class TypingState(
    val phase: TypingPhase = TypingPhase.TYPING,
    val input: String = "",
    /** The "Mit Vorschlägen" delay ([auswahlDelayMs]) has passed since the question appeared (once per question). */
    val choiceDelayPassed: Boolean = false,
    /** "Erst ein Wort eintippen": submitted with an empty field. */
    val emptyHint: Boolean = false,
    /** The first typed answer once submitted ("Auch richtig" shows it in green). */
    val typedAnswer: String? = null,
    /** The other entry that was typed ("Auch richtig"). */
    val alsoCorrect: QuizWord? = null,
    /** "Fast richtig": the copying shows what was off instead of the wrong-answer feedback. */
    val almost: AnswerChecker.TypedResult.AlmostCorrect? = null,
    /** The core word was copied wrongly; the learner tries again. */
    val copyMismatch: Boolean = false
) {
    /** "Mit Vorschlägen" is offered only after the delay and only while the field is empty. */
    val showsChoiceButton: Boolean
        get() = phase == TypingPhase.TYPING && choiceDelayPassed && input.isEmpty()

    val inputOpen: Boolean get() = phase == TypingPhase.TYPING || phase == TypingPhase.COPYING

    /** The first answer was given by typing (not "Mit Vorschlägen"). */
    val answeredByTyping: Boolean
        get() = phase == TypingPhase.CORRECT || phase == TypingPhase.ALSO_CORRECT ||
            phase == TypingPhase.COPYING || phase == TypingPhase.COPIED
}

/** Result of tapping an answer option or submitting a typed answer. */
sealed interface TapOutcome {
    /** The tap changes nothing (e.g. while the solution is shown). */
    data object Ignored : TapOutcome

    /**
     * An answer, either the real one or a retry. [countsForLevel] is true only for the first
     * answer of a word in the round; retries never change score, level or repetition.
     */
    data class Answered(
        val state: QuizRoundState.Asking,
        val correct: Boolean,
        val countsForLevel: Boolean,
        val form: AnswerForm = AnswerForm.MULTIPLE_CHOICE,
        /** "Auch richtig" or "Fast richtig": level unchanged, no points, not repeated. */
        val kept: KeptAnswer? = null,
        /** The English entry is spoken after this answer (not again after copying the core word). */
        val speaks: Boolean = true,
        /**
         * What is recorded for the level: a two-step question is right only if both first answers
         * were right, so a right second step can still count as wrong.
         */
        val countsAsCorrect: Boolean = correct
    ) : TapOutcome {
        /** "Fast richtig" goes on to copying the word, like a wrong answer. */
        val advancesAutomatically: Boolean get() = correct || kept == KeptAnswer.ALSO_CORRECT
    }

    /** The state changed without an answer (e.g. the hint for an empty field). */
    data class Updated(val state: QuizRoundState.Asking) : TapOutcome
}

/** Tap handling of a quiz question, independent of the ViewModel. */
object QuizInteraction {
    /** [optionIndex] is the position on screen ([QuizRoundState.Asking.options]). */
    fun tap(state: QuizRoundState.Asking, optionIndex: Int, round: QuizRound): TapOutcome {
        // A typing question shows its options only after "Mit Vorschlägen".
        if (state.typing != null && state.typing.phase != TypingPhase.CHOICE) return TapOutcome.Ignored
        val correct = optionIndex == state.correctIndex
        // Two steps (verbs with three forms): only both first answers together are the real answer.
        val countsAsCorrect = correct && state.firstStepCorrect != false
        val firstAnswer = state.phase == AnswerPhase.ANSWERING
        val countsForLevel = when (state.phase) {
            // The first step of two is recorded with the second one.
            AnswerPhase.ANSWERING -> !state.isFirstOfTwoSteps && round.recordAnswer(
                countsAsCorrect,
                // "Mit Vorschlägen" instead of typing is worth only a third.
                if (correct && state.typing != null) RoundStars.REDUCED_CREDIT else RoundStars.creditFor(countsAsCorrect)
            )
            AnswerPhase.RETRY -> false
            AnswerPhase.CORRECT, AnswerPhase.SOLUTION -> return TapOutcome.Ignored
        }
        val phase = if (correct) AnswerPhase.CORRECT else AnswerPhase.SOLUTION
        val form = if (state.typing != null) AnswerForm.CHOICE_INSTEAD_OF_TYPING else AnswerForm.MULTIPLE_CHOICE
        val answered = state.copy(
            selectedIndex = optionIndex,
            phase = phase,
            firstStepCorrect = if (state.isFirstOfTwoSteps && firstAnswer) correct else state.firstStepCorrect,
            firstStepMissed = state.inSecondStep && firstAnswer && correct && state.firstStepCorrect == false
        )
        return TapOutcome.Answered(answered, correct, countsForLevel, form, countsAsCorrect = countsAsCorrect)
    }

    /**
     * After the first step of a two-step question was answered correctly (first try or retry): the
     * second step with its own options; null if there is no further step.
     */
    fun nextStep(state: QuizRoundState.Asking): QuizRoundState.Asking? {
        if (!state.isFirstOfTwoSteps || state.phase != AnswerPhase.CORRECT) return null
        val second = state.question.question.secondStep ?: return null
        return state.copy(
            inSecondStep = true,
            phase = AnswerPhase.ANSWERING,
            selectedIndex = null,
            order = second.options.indices.toList()
        )
    }

    /** The input field changed; hints of the last submit disappear. */
    fun typingInput(state: QuizRoundState.Asking, text: String): QuizRoundState.Asking {
        val typing = state.typing?.takeIf { it.inputOpen } ?: return state
        // A line break (pasted text, some keyboards) never belongs to the answer.
        val input = text.replace("\n", "")
        return state.copy(typing = typing.copy(input = input, emptyHint = false, copyMismatch = false))
    }

    /** After [auswahlDelayMs]: "Mit Vorschlägen" may show from now on (while the field is empty). */
    fun choiceDelayPassed(state: QuizRoundState.Asking): QuizRoundState.Asking {
        val typing = state.typing ?: return state
        return state.copy(typing = typing.copy(choiceDelayPassed = true))
    }

    /** "Mit Vorschlägen": the options instead of the input field, for the rest of this question. */
    fun chooseInstead(state: QuizRoundState.Asking): QuizRoundState.Asking {
        val typing = state.typing?.takeIf { it.showsChoiceButton } ?: return state
        return state.copy(typing = typing.copy(phase = TypingPhase.CHOICE))
    }

    /**
     * "Prüfen" or the keyboard action. An empty field only shows the hint. The first typed
     * answer is recorded in [round]: correct, "Auch richtig" (via [index]), "Fast richtig" (typo or
     * American spelling, checked against [material]) or wrong; the last two open the copying of the
     * core word. While copying, only the core word (or the entry) moves on.
     */
    fun submitTyped(
        state: QuizRoundState.Asking,
        round: QuizRound,
        index: AlsoCorrectIndex,
        material: LearningMaterial = LearningMaterial.EMPTY
    ): TapOutcome {
        val typing = state.typing ?: return TapOutcome.Ignored
        val word = state.question.question.word
        if (typing.inputOpen && typing.input.isBlank()) {
            return TapOutcome.Updated(state.copy(typing = typing.copy(emptyHint = true)))
        }
        return when (typing.phase) {
            TypingPhase.TYPING -> {
                val answer = typing.input.trim()
                val result = AnswerChecker.checkTyped(answer, word, index, material)
                // "Auch richtig" and "Fast richtig" count as right for the round: not repeated.
                val wrong = result == AnswerChecker.TypedResult.Wrong
                val countsForLevel = round.recordAnswer(
                    correct = !wrong,
                    credit = if (result is AnswerChecker.TypedResult.AlmostCorrect) RoundStars.REDUCED_CREDIT
                    else RoundStars.creditFor(!wrong)
                )
                val next = when (result) {
                    AnswerChecker.TypedResult.Correct -> typing.copy(phase = TypingPhase.CORRECT, typedAnswer = answer)
                    is AnswerChecker.TypedResult.AlsoCorrect ->
                        typing.copy(phase = TypingPhase.ALSO_CORRECT, typedAnswer = answer, alsoCorrect = result.other)
                    is AnswerChecker.TypedResult.AlmostCorrect ->
                        typing.copy(phase = TypingPhase.COPYING, typedAnswer = answer, input = "", almost = result)
                    AnswerChecker.TypedResult.Wrong ->
                        typing.copy(phase = TypingPhase.COPYING, typedAnswer = answer, input = "")
                }
                TapOutcome.Answered(
                    state.copy(typing = next),
                    correct = result == AnswerChecker.TypedResult.Correct,
                    countsForLevel = countsForLevel,
                    form = AnswerForm.TYPED,
                    kept = when (result) {
                        is AnswerChecker.TypedResult.AlsoCorrect -> KeptAnswer.ALSO_CORRECT
                        is AnswerChecker.TypedResult.AlmostCorrect -> KeptAnswer.ALMOST_CORRECT
                        else -> null
                    }
                )
            }
            TypingPhase.COPYING -> if (AnswerChecker.isCorrectTyped(typing.input, word)) {
                TapOutcome.Answered(
                    state.copy(typing = typing.copy(phase = TypingPhase.COPIED)),
                    correct = true, countsForLevel = false, form = AnswerForm.TYPED, speaks = false
                )
            } else {
                TapOutcome.Updated(state.copy(typing = typing.copy(copyMismatch = true)))
            }
            else -> TapOutcome.Ignored
        }
    }

    /** What the speaker button speaks: the solution text once answered, otherwise the question. */
    fun speakerText(state: QuizRoundState.Asking): String? {
        val question = state.question.question
        val answered = state.phase == AnswerPhase.CORRECT || state.phase == AnswerPhase.SOLUTION ||
            state.typing?.answeredByTyping == true
        return if (answered) state.speakAfterAnswer ?: question.speakOnShow else question.speakOnShow
    }

    /**
     * Spoken automatically after a tap that answers: the real answer and answers in the retry
     * phase alike (correct, or the solution after a wrong one).
     */
    fun autoSpeechAfterTap(before: QuizRoundState.Asking, autoSpeak: Boolean): String? {
        val answers = before.phase == AnswerPhase.ANSWERING || before.phase == AnswerPhase.RETRY
        return if (autoSpeak && answers) before.speakAfterAnswer else null
    }

    /** Spoken automatically after "Nochmal versuchen": the question again, as if it had just appeared. */
    fun autoSpeechOnRetry(state: QuizRoundState.Asking, autoSpeak: Boolean): String? =
        if (autoSpeak && state.phase == AnswerPhase.RETRY) state.question.question.speakOnShow else null

    /**
     * "Nochmal versuchen": the same options in a new order, the correct one at another position
     * whenever possible. Returns [state] unchanged outside the solution phase.
     */
    fun retry(state: QuizRoundState.Asking, random: Random): QuizRoundState.Asking {
        if (state.phase != AnswerPhase.SOLUTION) return state
        val correctOption = state.stepCorrectIndex
        val oldPosition = state.order.indexOf(correctOption)
        val order = state.order.shuffled(random).toMutableList()
        if (order.size > 1 && order.indexOf(correctOption) == oldPosition) {
            val other = (order.indices - oldPosition).random(random)
            order[oldPosition] = order[other]
            order[other] = correctOption
        }
        return state.copy(order = order, selectedIndex = null, phase = AnswerPhase.RETRY)
    }
}
