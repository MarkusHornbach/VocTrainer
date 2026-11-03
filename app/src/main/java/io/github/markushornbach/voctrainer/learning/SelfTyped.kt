package io.github.markushornbach.voctrainer.learning

/**
 * "Selbst getippt": a word counts once the learner typed it correctly on the first attempt of a
 * round (see [io.github.markushornbach.voctrainer.data.Vocabulary.firstTypedCorrectAt]). Nothing is shown yet;
 * a later step builds a badge on it.
 */
object SelfTyped {
    /**
     * True for a first attempt ([form], [correct], [kept] as recorded by the round) that typed
     * the target word exactly: not "Auch richtig" (another entry), not "Fast richtig" (typo or
     * American spelling), not "Mit Vorschlägen", not a multiple choice question. The copy after a mistake
     * and the repetition at the end of the round are no first attempts and never reach this
     * check; verb training never records it.
     */
    fun counts(form: AnswerForm, correct: Boolean, kept: KeptAnswer?): Boolean =
        form == AnswerForm.TYPED && correct && kept == null
}
