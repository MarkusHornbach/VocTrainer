package io.github.markushornbach.voctrainer.learning

import io.github.markushornbach.voctrainer.data.CoreWords
import io.github.markushornbach.voctrainer.data.VocTrainerJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Phrases with a core word are typed like words. */
class PhraseTypingTest {
    private fun phrase(english: String, core: String?) =
        word(english, english, "Bedeutung", type = WordType.PHRASE).copy(coreWord = core)

    private fun correct(answer: String, word: QuizWord) =
        AnswerChecker.checkTyped(answer, word, AlsoCorrectIndex(listOf(word)))

    @Test
    fun typeabilityNoLongerDependsOnTheWordType() {
        assertTrue(phrase("Have you ever …?", "ever").isTypeable)
        assertFalse(phrase("Have you ever …?", null).isTypeable)
        assertFalse(word("go", "go", "gehen").copy(coreWord = "go", verbForms = VerbForms("went", "gone")).isTypeable)
        assertTrue(word("dog", "dog", "Hund").copy(coreWord = "dog").isTypeable)
    }

    @Test
    fun coreWordWithAFinalPeriodAlsoCountsWithoutIt() {
        val eg = phrase("e.g.", "e.g.")
        for (answer in listOf("e.g.", "e.g", "E.G.", "E.G", " e.g ")) {
            assertEquals(answer, AnswerChecker.TypedResult.Correct, correct(answer, eg))
        }
        assertNotEquals(AnswerChecker.TypedResult.Correct, correct("eg", eg))
        assertNotEquals(AnswerChecker.TypedResult.Correct, correct("e.g..", eg))

        val etc = word("etc", "etc.", "usw.", type = WordType.OTHER).copy(coreWord = "etc.")
        assertEquals(AnswerChecker.TypedResult.Correct, correct("etc.", etc))
        assertEquals(AnswerChecker.TypedResult.Correct, correct("ETC", etc))
        assertNotEquals(AnswerChecker.TypedResult.Correct, correct("et", etc))
    }

    @Test
    fun coreWordsWithoutAFinalPeriodKeepTheirRule() {
        val dog = word("dog", "dog", "Hund").copy(coreWord = "dog")
        assertEquals(AnswerChecker.TypedResult.Correct, correct("dog", dog))
        // Only a missing final period of the core word is new; an extra one is not correct.
        assertNotEquals(AnswerChecker.TypedResult.Correct, correct("dog.", dog))
        assertNotEquals(AnswerChecker.TypedResult.Correct, correct("do", dog))
    }

    @Test
    fun apostropheVariantsOfTheCoreWord() {
        val cant = phrase("I can't wait.", "can't")
        assertEquals(TypingTask("can't", "I ", " wait."), TypingTask.of(cant))
        for (answer in listOf("can’t", "can't", "CAN'T", "can‘t", "canʼt", "can`t", "can´t")) {
            assertEquals(answer, AnswerChecker.TypedResult.Correct, correct(answer, cant))
        }
    }

    /** As in the repository. */
    private fun correctOnALaterDay(word: QuizWord, form: AnswerForm): Int = Leitner.outcomeOfFirstAttempt(
        word.leitnerLevel, correct = true, early = false, maxLevel = Leitner.choiceMaxLevel(word.isTypeable),
        reviewedEarlierDay = true, form = form
    ).newLevel

    @Test
    fun typeablePhrasesFollowTheNormalLadder() {
        val typeable = phrase("Have you ever …?", "ever").copy(leitnerLevel = 3)
        assertEquals(4, correctOnALaterDay(typeable, AnswerForm.TYPED))
        // There is no short ladder for phrases; choosing never passes level 3.
        assertEquals(3, correctOnALaterDay(typeable, AnswerForm.CHOICE_INSTEAD_OF_TYPING))
    }
}

/**
 * Phrases of every shape with their core word: the gap at the start, in the middle or at the end,
 * next to punctuation, apostrophes, "/" and "…", and long phrases.
 */
@RunWith(Parameterized::class)
class PhraseShapesTest(private val english: String, private val core: String) {
    private val phrase = word(english, english, "Bedeutung", type = WordType.PHRASE).copy(coreWord = core)

    @Test
    fun isTypeable() {
        assertTrue(phrase.isTypeable)
    }

    @Test
    fun gapIsFound() {
        val task = TypingTask.of(phrase)
        assertNotNull(task)
        task!!
        assertEquals(core, task.coreWord)
        val matched = english.substring(task.before.length, english.length - task.after.length)
        assertTrue("$english: gap '$matched'", matched.equals(core, ignoreCase = true))
        assertEquals(english, task.before + matched + task.after)
        // The whole entry is the gap only for "e.g.".
        if (core != english) assertTrue(english, task.before.isNotEmpty() || task.after.isNotEmpty())
    }

    /** The chips of hand entry offer each of these core words, and the import accepts it. */
    @Test
    fun coreWordIsAChipCandidate() {
        assertEquals(core, CoreWords.candidateFor(core, english))
        assertTrue(VocTrainerJson.isValidCoreWord(core, english))
    }

    @Test
    fun coreWordIsCorrect() {
        val index = AlsoCorrectIndex(listOf(phrase))
        assertEquals(AnswerChecker.TypedResult.Correct, AnswerChecker.checkTyped(core, phrase, index))
        assertEquals(AnswerChecker.TypedResult.Correct, AnswerChecker.checkTyped(core.uppercase(), phrase, index))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): List<Array<String>> = listOf(
            "I want to …" to "want",
            "We laughed a lot." to "lot",
            "Say thank you to … from me." to "thank",
            "…, okay?" to "okay",
            "What was the film like?" to "like",
            "Bye, …" to "Bye",
            "…, you know." to "know",
            "I'm afraid of …" to "afraid",
            "There you go." to "There",
            "Wait a second." to "Wait",
            "The car didn't start." to "start",
            "Here's your change." to "change",
            "every Monday/Tuesday/…" to "every",
            "across the road/the river" to "across",
            "(to) talk/sing/… along" to "along",
            "And the best part?" to "best",
            "I'm going to paint a picture." to "going",
            "What could be nicer?" to "nicer",
            "(not) at all" to "all",
            "Sorry, …" to "Sorry",
            "Do you need …?" to "need",
            "Who did you play with?" to "with",
            "Run!" to "Run",
            "here and there" to "there",
            "between the old school and the new sports hall" to "between",
            "Oh no!" to "no",
            "Have you ever …?" to "ever",
            "What about a …?" to "about",
            "I'd like to …" to "like",
            "one week/month" to "one",
            "Can we keep it?" to "keep",
            "What's the matter with Tom/you?" to "matter",
            "I'll take the red/blue/… one" to "take",
            "e.g." to "e.g.",
            "somebody?" to "somebody",
            "everyone?" to "everyone",
            "three times a day" to "times",
            "twice a month" to "twice"
        ).map { (english, core) -> arrayOf(english, core) }
    }
}
