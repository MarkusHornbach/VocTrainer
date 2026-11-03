package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MultipleChoiceGeneratorTest {
    private fun distractorsOf(question: MultipleChoiceQuestion) =
        question.options.filterIndexed { index, _ -> index != question.correctIndex }

    @Test
    fun everyExampleWordGetsFourDistinctOptionsWithExactlyOneCorrect() {
        val words = exampleWords()
        val generator = MultipleChoiceGenerator(Random(42))

        for (target in words) {
            val question = generator.generate(target, words)
            assertNotNull("No question for ${target.english}", question)
            question!!
            assertEquals(target.english, question.prompt)
            assertEquals(4, question.options.size)
            assertEquals(4, question.options.map { normalizeText(it) }.toSet().size)
            assertEquals(GermanText.withoutGenderSuffix(target.mainMeaning), question.options[question.correctIndex])
            assertTrue(question.options.none { it.endsWith("/in") })
            val targetMeanings = target.germanMeanings.map { normalizeText(GermanText.withoutGenderSuffix(it)) }.toSet()
            val correctOptions = question.options.count { normalizeText(it) in targetMeanings }
            assertEquals("Exactly one correct option for ${target.english}", 1, correctOptions)
        }
    }

    /** Case D3: pairs of the example book with a shared meaning are never distractors for each other. */
    @Test
    fun exampleBookPairsWithASharedMeaningAreNeverDistractorsForEachOther() {
        val pairs = listOf(
            Triple("large", "big", "groß"), Triple("kind", "friendly", "freundlich"), Triple("quiet", "calm", "ruhig"),
            Triple("(to) taste", "(to) try", "probieren"), Triple("present", "gift", "Geschenk")
        )
        val example = exampleWords(EXAMPLE_BOOK)
        for (level in listOf(0, 2)) {
            val words = example.map { it.copy(leitnerLevel = level) }
            val byEnglish = words.associateBy { it.english }
            for ((first, second, shared) in pairs) {
                for ((target, other) in listOf(first to second, second to first)) {
                    val word = byEnglish.getValue(target)
                    assertTrue(shared in word.germanMeanings && shared in byEnglish.getValue(other).germanMeanings)
                    repeat(30) { seed ->
                        val question = MultipleChoiceGenerator(Random(seed)).generate(word, words)!!
                        // English → German shows the shared meaning at most once; German → English never the other word.
                        assertTrue("$target/$level", question.options.count { it == shared } <= 1)
                        assertFalse("$target/$level", other in question.options)
                    }
                }
            }
        }
    }

    /** Case D4: "light" is two entries (unit 2 "leicht", unit 3 "Licht"); each is asked with its own meaning. */
    @Test
    fun theSameEnglishWordInTwoUnitsIsTwoWords() {
        val words = exampleWords(EXAMPLE_BOOK)
        val lights = words.filter { it.english == "light" }
        assertEquals(listOf(2 to listOf("leicht"), 3 to listOf("Licht")), lights.map { it.unit to it.germanMeanings })
        lights.forEach { light ->
            repeat(20) { seed ->
                val question = MultipleChoiceGenerator(Random(seed)).generate(light, words)!!
                assertEquals(light.mainMeaning, question.options[question.correctIndex])
                assertEquals(1, question.options.count { it == "leicht" || it == "Licht" })
            }
        }
    }

    @Test
    fun wordsSharingAMeaningAreNeverDistractorsForEachOther() {
        val big = word("big", "big", "groß", part = "A")
        val tall = word("tall", "tall", "hoch", "groß", part = "A")
        val others = (1..6).map { word("o$it", "word$it", "wort$it", part = "A") }
        val words = listOf(big, tall) + others

        repeat(200) { seed ->
            val generator = MultipleChoiceGenerator(Random(seed))
            val forBig = generator.generate(big, words)!!
            assertFalse(forBig.options.any { it == "hoch" })
            assertEquals(1, forBig.options.count { it == "groß" })
            val forTall = generator.generate(tall, words)!!
            assertEquals(listOf("hoch"), forTall.options.filter { it == "hoch" || it == "groß" })
        }
    }

    @Test
    fun meaningsThatOnlyDifferByGenderSuffixCountAsTheSame() {
        val target = word("t", "dancer", "Tänzer/in")
        val sameAsDisplayed = word("s", "dance teacher", "Tänzer")
        val others = (1..3).map { word("o$it", "other$it", "Ander$it") }

        repeat(50) { seed ->
            val question = MultipleChoiceGenerator(Random(seed)).generate(target, listOf(target, sameAsDisplayed) + others)!!
            assertEquals(1, question.options.count { it == "Tänzer" })
            assertEquals("Tänzer", question.options[question.correctIndex])
        }
    }

    @Test
    fun sameEnglishInAnotherUnitIsNeverADistractor() {
        val target = word("t", "change", "Wechselgeld", unit = 1)
        val sameEnglish = word("s", " Change ", "Kleingeld", unit = 2)
        val others = (1..3).map { word("o$it", "other$it", "Ander$it") }

        val question = MultipleChoiceGenerator(Random(1)).generate(target, listOf(target, sameEnglish) + others)!!

        assertFalse(question.options.contains("Kleingeld"))
    }

    @Test
    fun distractorsNeverRepeatTheSameMainMeaning() {
        val target = word("t", "house", "Haus")
        val candidates = listOf(
            word("a", "home", "Heim"), word("b", "home2", "heim"),
            word("c", "flat", "Wohnung"), word("d", "room", "Zimmer"), word("e", "garden", "Garten")
        )
        repeat(100) { seed ->
            val question = MultipleChoiceGenerator(Random(seed)).generate(target, listOf(target) + candidates)!!
            assertEquals(4, question.options.map { normalizeText(it) }.toSet().size)
        }
    }

    @Test
    fun prefersSameUnitPartAndWordType() {
        val target = word("t", "(to) go", "gehen", part = "A")
        val verbs1A = (1..4).map { word("v$it", "(to) verb$it", "verb$it", part = "A") }
        val nouns1A = (1..4).map { word("n$it", "noun$it", "Nomen$it", part = "A") }
        val verbs1B = (1..4).map { word("b$it", "(to) other$it", "anders$it", part = "B") }
        val words = listOf(target) + verbs1A + nouns1A + verbs1B
        val allowed = verbs1A.map { it.mainMeaning }.toSet()

        repeat(100) { seed ->
            val question = MultipleChoiceGenerator(Random(seed)).generate(target, words)!!
            assertTrue(distractorsOf(question).all { it in allowed })
        }
    }

    @Test
    fun fallsBackTierByTierWhenAPartIsSmall() {
        val target = word("t", "(to) go", "gehen", unit = 1, part = "A")
        val verb1A = word("a", "(to) run", "rennen", unit = 1, part = "A")
        val verb1B = word("b", "(to) swim", "schwimmen", unit = 1, part = "B")
        val verb2 = word("c", "(to) fly", "fliegen", unit = 2, part = null)
        val verb3 = word("d", "(to) sing", "singen", unit = 3, part = null)
        val nouns1A = (1..5).map { word("n$it", "noun$it", "Nomen$it", unit = 1, part = "A") }
        val words = listOf(target, verb1A, verb1B, verb2, verb3) + nouns1A

        repeat(100) { seed ->
            val distractors = distractorsOf(MultipleChoiceGenerator(Random(seed)).generate(target, words)!!).toSet()
            // Tier 1 (rennen) and tier 2 (schwimmen) are used up first, the third comes from tier 3.
            assertTrue("rennen" in distractors)
            assertTrue("schwimmen" in distractors)
            assertTrue(distractors.none { it.startsWith("Nomen") })
        }
    }

    @Test
    fun fallsBackToOtherWordTypesWhenNecessary() {
        val target = word("t", "(to) go", "gehen")
        val words = listOf(target, word("a", "(to) run", "rennen"), word("n1", "dog", "Hund"), word("n2", "cat", "Katze"))

        val distractors = distractorsOf(MultipleChoiceGenerator(Random(3)).generate(target, words)!!).toSet()

        assertEquals(setOf("rennen", "Hund", "Katze"), distractors)
    }

    @Test
    fun returnsNullWithFewerThanFourUsableWords() {
        val target = word("t", "big", "groß")
        val words = listOf(target, word("a", "small", "klein"), word("b", "tall", "groß"), word("c", "old", "alt"))
        val generator = MultipleChoiceGenerator(Random(0))

        assertNull(generator.generate(target, words.take(3)))
        assertFalse(generator.canGenerate(target, words.take(3)))
        // Four words, but "tall" shares "groß" with the target: still only two valid distractors.
        assertNull(generator.generate(target, words))
        assertFalse(generator.canGenerate(target, words))
        assertTrue(generator.canGenerate(target, words + word("d", "new", "neu")))
    }
}
