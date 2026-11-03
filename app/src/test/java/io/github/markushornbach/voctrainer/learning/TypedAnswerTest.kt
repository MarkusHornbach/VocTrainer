package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedAnswerTest {
    private val promise = word("promise", "(to) promise sb. sth.", "jm. etwas versprechen").copy(coreWord = "promise")
    private val dont = word("dont", "don't", "nicht (Verneinung)").copy(coreWord = "don't")

    @Test
    fun coreWordIsCorrectIgnoringCaseWhitespaceAndApostrophes() {
        assertTrue(AnswerChecker.isCorrectTyped("promise", promise))
        assertTrue(AnswerChecker.isCorrectTyped("  PROMISE ", promise))
        assertTrue(AnswerChecker.isCorrectTyped("don’t", dont))
        assertTrue(AnswerChecker.isCorrectTyped("don't", dont.copy(coreWord = "don’t")))
        assertFalse(AnswerChecker.isCorrectTyped("promises", promise))
        assertFalse(AnswerChecker.isCorrectTyped("pormise", promise))
        assertFalse(AnswerChecker.isCorrectTyped("   ", promise))
    }

    @Test
    fun coreWordWithFinalPeriodDoesNotCrashAndIsCheckedAsTyped() {
        // Without a stored type the heuristic may call "e.g." a phrase (not typeable); the import file gives the type.
        println("e.g. heuristic type: " + word("eg0", "e.g.", "zum Beispiel").effectiveType)
        val eg = word("eg", "e.g.", "zum Beispiel", type = WordType.OTHER).copy(coreWord = "e.g.")
        val index = AlsoCorrectIndex(listOf(eg))
        assertTrue(eg.isTypeable)
        assertTrue(AnswerChecker.isCorrectTyped("e.g.", eg))
        assertTrue(AnswerChecker.isCorrectTyped(" E.G. ", eg))
        println("e.g. typed as 'eg': " + AnswerChecker.checkTyped("eg", eg, index))
        println("e.g. typed as 'e.g': " + AnswerChecker.checkTyped("e.g", eg, index))
        AnswerChecker.whyWrong("xyz", eg, index, LearningMaterial(listOf(eg)))
    }

    @Test
    fun fullEntryIsCorrectWithOrWithoutTo() {
        assertTrue(AnswerChecker.isCorrectTyped("(to) promise sb. sth.", promise))
        assertTrue(AnswerChecker.isCorrectTyped("promise sb. sth.", promise))
        assertTrue(AnswerChecker.isCorrectTyped("To promise sb. sth.", promise))
        assertFalse(AnswerChecker.isCorrectTyped("promise sth.", promise))
    }

    @Test
    fun alsoCorrectNeedsASharedMeaningAndSearchesAllVocabulary() {
        val big = word("big", "big", "groß").copy(coreWord = "big")
        val large = word("large", "large", "groß (Größe)", "weit").copy(coreWord = "large")
        val huge = word("huge", "huge", "groß (sehr groß)").copy(coreWord = "huge")
        val small = word("small", "small", "klein").copy(coreWord = "small")
        val index = AlsoCorrectIndex(listOf(big, large, huge, small))
        // Meanings compared without brackets.
        assertEquals(AnswerChecker.TypedResult.AlsoCorrect(large), AnswerChecker.checkTyped("Large", big, index))
        assertEquals(AnswerChecker.TypedResult.AlsoCorrect(huge), AnswerChecker.checkTyped("huge", large, index))
        assertEquals(AnswerChecker.TypedResult.Wrong, AnswerChecker.checkTyped("small", big, index))
        assertEquals(AnswerChecker.TypedResult.Correct, AnswerChecker.checkTyped("big", big, index))
        // A typo of another entry is "fast richtig" (see FairCheckTest), two are wrong.
        assertTrue(AnswerChecker.checkTyped("larg", big, index) is AnswerChecker.TypedResult.AlmostCorrect)
        assertEquals(AnswerChecker.TypedResult.Wrong, AnswerChecker.checkTyped("lrg", big, index))
    }

    @Test
    fun placeholdersAndGenderEndingsAreIgnoredInMeanings() {
        val vow = word("vow", "(to) vow sth.", "jemandem etwas versprechen").copy(coreWord = "vow")
        val index = AlsoCorrectIndex(listOf(promise, vow))
        assertEquals(AnswerChecker.TypedResult.AlsoCorrect(vow), AnswerChecker.checkTyped("vow", promise, index))
        assertEquals("versprechen", AlsoCorrectIndex.comparableMeaning("jm. etwas versprechen"))
        assertEquals("versprechen", AlsoCorrectIndex.comparableMeaning("etw. (fest) Versprechen"))
        assertEquals("lehrer", AlsoCorrectIndex.comparableMeaning("Lehrer/in"))
    }

    @Test
    fun anotherEntryCountsByItsCoreWord() {
        // Every entry has a core word, so typing another entry means typing its core word.
        val pupil = word("pupil", "pupil", "Schüler/in").copy(coreWord = "pupil")
        val student = word("student", "student (AE)", "Schüler/in").copy(coreWord = "student")
        val schoolKid = word("kid", "school kid", "Schüler").copy(coreWord = "kid")
        val index = AlsoCorrectIndex(listOf(pupil, student, schoolKid))
        assertEquals(AnswerChecker.TypedResult.AlsoCorrect(student), AnswerChecker.checkTyped("student", pupil, index))
        assertEquals(AnswerChecker.TypedResult.AlsoCorrect(schoolKid), AnswerChecker.checkTyped("kid", pupil, index))
        assertEquals(AnswerChecker.TypedResult.Wrong, AnswerChecker.checkTyped("school kid", pupil, index))
        assertEquals("look", AlsoCorrectIndex.typedTarget(word("x", "(to) look at sth.", "x").copy(coreWord = "look")))
        // Without a core word (only verbs) there is nothing to type.
        assertNull(AlsoCorrectIndex.typedTarget(word("x", "(to) ask sb.", "x")))
    }

    @Test
    fun frameReplacesTheCoreWordByAGapThatFillsWhileTyping() {
        val task = TypingTask.of(promise)!!
        assertEquals("(to) ____ sb. sth.", task.frame(""))
        assertEquals("(to) pr__ sb. sth.", task.frame("pr"))
        assertEquals("(to) promises! sb. sth.", task.frame("promises!"))
        // A single word is just the gap; the core word is found as a whole word only.
        assertEquals("____", TypingTask.of(word("a", "art", "Kunst").copy(coreWord = "art"))!!.frame(""))
        assertEquals("(to) start ____", TypingTask.of(word("s", "(to) start art", "x").copy(coreWord = "art"))!!.frame(""))
        // Core word missing from the entry: only the gap.
        assertEquals("____", TypingTask.of(word("m", "(to) be late", "x").copy(coreWord = "later"))!!.frame(""))
        assertNull(TypingTask.of(word("n", "dog", "Hund")))
    }

    @Test
    fun typingQuestionsFromLevelThreeForTypeableWordsOnly() {
        val pool = (1..8).map { word("w$it", "word$it", "Wort$it").copy(coreWord = "word$it") }
        val generator = MultipleChoiceGenerator(kotlin.random.Random(1))
        val levels = (0..5).map { generator.generate(pool[0].copy(leitnerLevel = it), pool)!!.typing != null }
        assertEquals(listOf(false, false, false, true, true, true), levels)
        val question = generator.generate(pool[0].copy(leitnerLevel = 4), pool)!!
        // "Mit Vorschlägen" uses the German → English options.
        assertEquals(4, question.options.size)
        assertEquals("word1", question.options[question.correctIndex])
        assertNull(question.speakOnShow)
        assertEquals("word1", question.speakAfterAnswer)
        // Not typeable (no core word; never the case for an active entry): multiple choice.
        assertNull(generator.generate(pool[0].copy(leitnerLevel = 4, coreWord = null), pool)!!.typing)
    }
}
