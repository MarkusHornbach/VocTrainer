package io.github.markushornbach.voctrainer.learning

import io.github.markushornbach.voctrainer.learning.AnswerChecker.TypedResult
import io.github.markushornbach.voctrainer.learning.AnswerChecker.WrongReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Fast richtig": typos, American spelling and the learning-material words. */
class FairCheckTest {
    private fun typed(core: String, english: String = core, vararg german: String = arrayOf(core)) =
        word(core, english, *german).copy(coreWord = core)

    private val colour = typed("colour", "colour", "Farbe")
    private val cat = typed("cat", "cat", "Katze")
    private val cut = typed("cut", "(to) cut", "schneiden")
    private val house = typed("house", "house", "Haus")
    private val mouse = typed("mouse", "mouse", "Maus")
    private val big = typed("big", "big", "groß")
    private val large = typed("large", "large", "groß")
    private val programme = typed("programme", "programme", "Programm")
    private val vocabulary = listOf(colour, cat, cut, house, mouse, big, large, programme)
    private val index = AlsoCorrectIndex(vocabulary)
    private val material = LearningMaterial(vocabulary)

    private fun check(answer: String, word: QuizWord) = AnswerChecker.checkTyped(answer, word, index, material)

    private fun almost(answer: String, word: QuizWord) = check(answer, word) as TypedResult.AlmostCorrect

    @Test
    fun everyKindOfTypoIsAlmostCorrectWithTheDifferenceMarked() {
        // Missing letter (the second "o"): marked in the correct spelling only.
        assertEquals(
            TypedResult.AlmostCorrect(AlmostKind.TYPO, "colour", "colur", SpellingDiff(IntRange.EMPTY, 3..3)),
            check("colur", colour)
        )
        // Extra letter: marked in the input only.
        assertEquals(SpellingDiff(6..6, IntRange.EMPTY), almost("colourr", colour).diff)
        // Substitution: both.
        assertEquals(SpellingDiff(1..1, 1..1), almost("calour", colour).diff)
        // Two adjacent letters swapped: both letters on both sides.
        assertEquals(SpellingDiff(3..4, 3..4), almost("coluor", colour).diff)
        assertEquals(AlmostKind.TYPO, almost("Hosue", house).kind)
        // More than one edit, or letters swapped that are not adjacent: wrong.
        assertEquals(TypedResult.Wrong, check("colr", colour))
        assertEquals(TypedResult.Wrong, check("cuolor", colour))
        assertEquals(TypedResult.Wrong, check("hesuo", house))
    }

    @Test
    fun aTypoNeedsAtLeastFourLetters() {
        val dog = typed("dog", "dog", "Hund")
        assertEquals(TypedResult.Wrong, AnswerChecker.checkTyped("dgo", dog, index, material))
        assertEquals(TypedResult.Wrong, AnswerChecker.checkTyped("dogs", dog, index, material))
        assertEquals(WrongReason.TooShort, AnswerChecker.whyWrong("dgo", dog, index, material))
        assertTrue(Typo.longEnough("door"))
        assertFalse(Typo.longEnough("do'r"))
    }

    @Test
    fun aTypoThatIsAWordOfTheLearningMaterialIsWrong() {
        assertEquals(TypedResult.Wrong, check("cut", cat))
        assertEquals(WrongReason.MaterialWord("cut"), AnswerChecker.whyWrong("cut", cat, index, material))
        // "mouse" for "house" is one letter off, but a word of its own.
        assertEquals(TypedResult.Wrong, check("mouse", house))
        assertEquals(WrongReason.MaterialWord("mouse"), AnswerChecker.whyWrong("mouse", house, index, material))
        assertEquals(WrongReason.TooManyErrors, AnswerChecker.whyWrong("hxxse", house, index, material))
        // Only verbs are not typeable now.
        val verb = house.copy(verbForms = VerbForms("x", null))
        assertEquals(WrongReason.NotTypeable, AnswerChecker.whyWrong("x", verb, index, material))
    }

    @Test
    fun theLearningMaterialHoldsSingleWordsOfAllEntriesAndAllVerbForms() {
        val entries = LearningMaterial(
            listOf(word("a", "(to) care for sb./sth.", "x"), typed("don't", "I don’t know", "x")),
            listOf(word("v", "go", "gehen").copy(verbForms = VerbForms("went", "gone")), word("b", "be", "sein")
                .copy(verbForms = VerbForms("was / were", "been")))
        )
        listOf("care", "for", "don't", "I", "know", "go", "went", "gone", "was", "were", "been").forEach {
            assertTrue(it, it in entries)
        }
        listOf("sb", "sth", "to", "(to)", "somebody").forEach { assertFalse(it, it in entries) }
    }

    @Test
    fun everyAmericanRuleIsAlmostCorrect() {
        val cases = mapOf(
            "colour" to "color", "favourite" to "favorite", "neighbours" to "neighbors", "honourable" to "honorable",
            "centre" to "center", "theatres" to "theaters", "metre" to "meter", "fibre" to "fiber",
            "organise" to "organize", "realised" to "realized", "organisation" to "organization", "analyse" to "analyze",
            "travelled" to "traveled", "cancelling" to "canceling", "traveller" to "traveler",
            "dialogue" to "dialog", "catalogue" to "catalog",
            "programme" to "program", "defence" to "defense", "licence" to "license", "defenceless" to "defenseless"
        )
        for ((british, american) in cases) {
            assertTrue("$british → $american", american in AmericanSpelling.variants(british))
        }
        // Rules combine: "favourite" + nothing else; "centre" + "s".
        assertEquals(listOf("our → or"), AmericanSpelling.variants("colour")["color"])
        assertEquals(listOf("tre → ter"), AmericanSpelling.variants("centres")["centers"])
    }

    @Test
    fun americanRulesLeaveOtherWordsAlone() {
        listOf(
            "journey", "course", "four", "hour", "tourist", "filled", "called", "spelling", "smaller", "seller",
            "wise", "otherwise", "rise", "surprise", "exercised", "promise", "advertise", "noise", "programmed", "vogue"
        ).forEach { assertTrue(it, AmericanSpelling.variants(it).isEmpty()) }
    }

    @Test
    fun americanSpellingInTheQuiz() {
        val color = almost("color", colour)
        assertEquals(AlmostKind.AMERICAN, color.kind)
        assertEquals("colour", color.expected)
        assertEquals(SpellingDiff(IntRange.EMPTY, 4..4), color.diff)
        assertEquals(listOf("our → or"), color.rules)
        // "program" is the American spelling, "programm" one typo.
        assertEquals(AlmostKind.AMERICAN, almost("program", programme).kind)
        assertEquals(AlmostKind.TYPO, almost("programm", programme).kind)
        // An excluded -ise word is no American spelling ("surprize" is still one typo).
        val surprise = typed("surprise", "surprise", "Überraschung")
        val result = AnswerChecker.checkTyped("surprize", surprise, index, material) as TypedResult.AlmostCorrect
        assertEquals(AlmostKind.TYPO, result.kind)
        val organise = typed("organise", "(to) organise", "organisieren")
        assertEquals(AlmostKind.AMERICAN, (AnswerChecker.checkTyped("organize", organise, index, material) as TypedResult.AlmostCorrect).kind)
    }

    @Test
    fun anAmericanVariantThatIsAWordOfTheMaterialIsWrong() {
        val meter = typed("meter", "meter", "Zähler")
        val metre = typed("metre", "metre", "Meter")
        val words = listOf(meter, metre)
        assertEquals(
            TypedResult.Wrong,
            AnswerChecker.checkTyped("meter", metre, AlsoCorrectIndex(words), LearningMaterial(words))
        )
    }

    @Test
    fun aTypoOfAnAlsoCorrectEntryIsAlmostCorrect() {
        val result = almost("larg", big)
        assertEquals(AlmostKind.TYPO_OF_OTHER, result.kind)
        assertEquals(large, result.other)
        assertEquals("large", result.expected)
        // The exact other entry is still "Auch richtig".
        assertEquals(TypedResult.AlsoCorrect(large), check("large", big))
    }

    @Test
    fun aLeadingToIsIgnored() {
        val organise = typed("organise", "(to) organise", "organisieren")
        assertEquals(AlmostKind.AMERICAN, (AnswerChecker.checkTyped("to organize", organise, index, material) as TypedResult.AlmostCorrect).kind)
        assertEquals(AlmostKind.TYPO, (AnswerChecker.checkTyped("(to) organsie", organise, index, material) as TypedResult.AlmostCorrect).kind)
        assertEquals("organsie", (AnswerChecker.checkTyped("To organsie", organise, index, material) as TypedResult.AlmostCorrect).given)
    }

    @Test
    fun theChecksRunInOrder() {
        // Correct before everything.
        assertEquals(TypedResult.Correct, check("colour", colour))
        // "Auch richtig" before a typo of the asked word: an entry one letter off with a shared meaning.
        val house2 = typed("houses", "houses", "Haus")
        val words = listOf(house, house2)
        assertEquals(TypedResult.AlsoCorrect(house2), AnswerChecker.checkTyped("houses", house, AlsoCorrectIndex(words), LearningMaterial(words)))
        // American before typo: "color" is both one letter off and the American spelling.
        assertEquals(AlmostKind.AMERICAN, almost("color", colour).kind)
        // Typo of the asked word before a typo of another entry.
        val bigs = typed("bigs", "bigs", "groß")
        val large2 = typed("bigss", "bigss", "groß")
        val pair = listOf(bigs, large2)
        val asked = AnswerChecker.checkTyped("bigsx", bigs, AlsoCorrectIndex(pair), LearningMaterial(pair)) as TypedResult.AlmostCorrect
        assertEquals(AlmostKind.TYPO, asked.kind)
        assertNull(asked.other)
    }

    @Test
    fun spellingDiffAroundMarksTheMiddle() {
        assertEquals(SpellingDiff(IntRange.EMPTY, 4..4), SpellingDiff.around("color", "colour"))
        assertEquals(SpellingDiff(4..5, 4..5), SpellingDiff.around("center", "centre"))
        assertEquals(SpellingDiff(IntRange.EMPTY, 7..8), SpellingDiff.around("program", "programme"))
    }
}
