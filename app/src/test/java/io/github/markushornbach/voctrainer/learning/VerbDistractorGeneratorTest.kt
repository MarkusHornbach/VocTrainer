package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VerbDistractorGeneratorTest {
    private val verbs: List<QuizWord> = irregularFixtureVerbs()

    private fun verb(infinitive: String) = verbs.first { it.english == infinitive }

    private fun distractors(infinitive: String, seed: Int = 1) =
        VerbDistractorGenerator(Random(seed)).distractors(verb(infinitive), verbs)

    @Test
    fun expectedResultsFromTheSpecification() {
        val expected = mapOf(
            "(to) take" to listOf("taked", "toke", "tought"),
            "(to) think" to listOf("thinked", "thank", "thaught"),
            "(to) bring" to listOf("bringed", "brang", "braught"),
            "(to) swim" to listOf("swimmed", "swum", "swought"),
            "(to) bite" to listOf("bited", "bote", "baught"),
            "(to) win" to listOf("winned", "wan", "wint"),
            "(to) mean" to listOf("meaned", "men", "ment"),
            "(to) ride a bike" to listOf("rided a bike", "rid a bike", "rought a bike"),
            "(to) wake up" to listOf("waked up", "wook up", "wought up")
        )
        expected.forEach { (infinitive, distractors) ->
            assertEquals(infinitive, distractors, distractors(infinitive))
        }
    }

    /** Case F3: the special cases tested here are all in the example book. */
    @Test
    fun theSpecialVerbsAreInTheExampleBook() {
        val special = listOf(
            "be", "come", "hang", "choose", "hit", "put", "read", "ride a bike", "wake up",
            "take", "think", "bring", "swim", "bite", "win", "mean"
        ).map { "(to) $it" }
        assertEquals(special, special.filter { infinitive -> verbs.any { it.english == infinitive } })
        assertEquals("was / were", verb("(to) be").verbForms!!.simplePast)
    }

    @Test
    fun speaksTheInfinitiveFirstAndTheSimplePastAfterTheAnswer() {
        val question = VerbDistractorGenerator(Random(1)).generate(verb("(to) take"), verbs)!!
        assertEquals("(to) take", question.speakOnShow)
        assertEquals(question.options[question.correctIndex], question.speakAfterAnswer)
        assertEquals("took", question.speakAfterAnswer)
    }

    @Test
    fun comeNeverGetsAForbiddenWord() {
        repeat(30) { seed -> assertFalse("cum" in distractors("(to) come", seed)) }
    }

    @Test
    fun hangNeverGetsTheRealFormHanged() {
        repeat(30) { seed -> assertFalse("hanged" in distractors("(to) hang", seed)) }
    }

    @Test
    fun beGetsThreeDistractorsButNeverItsOwnForms() {
        repeat(30) { seed ->
            val result = distractors("(to) be", seed)
            assertEquals(3, result.size)
            assertTrue(result.none { it == "was" || it == "were" || it == "be" })
        }
    }

    @Test
    fun chooseGetsAtMostOneOughtOrAughtForm() {
        repeat(30) { seed ->
            assertTrue(distractors("(to) choose", seed).count { it == "chought" || it == "chaught" } <= 1)
        }
    }

    @Test
    fun verbsWithIdenticalFormsWork() {
        listOf("(to) hit", "(to) put", "(to) read").forEach { infinitive ->
            val result = distractors(infinitive)
            assertEquals(infinitive, 3, result.size)
            val base = infinitive.removePrefix("(to) ")
            assertFalse(infinitive, base in result)
        }
    }

    @Test
    fun forbiddenWordsAreNeverShown() {
        assertTrue("coke" in IrregularVerbList.forbidden)
        verbs.forEach { verb ->
            assertTrue(distractors(verb.english).none { it in IrregularVerbList.forbidden })
        }
    }

    @Test
    fun everyFixtureVerbGetsAQuestionWithFourDistinctOptions() {
        val generator = VerbDistractorGenerator(Random(3))
        verbs.forEach { verb ->
            assertTrue(verb.english, generator.canGenerate(verb, verbs))
            val question = generator.generate(verb, verbs)
            assertNotNull(verb.english, question)
            question!!
            assertEquals(4, question.options.toSet().size)
            assertEquals(verb.verbForms!!.simplePast, question.options[question.correctIndex])
            assertEquals("Wie lautet das simple past?", question.instruction)
            assertEquals(verb.english, question.prompt)
        }
    }

    @Test
    fun distractorsAreDeterministicExceptForTheFallback() {
        val withoutFallback = verbs.filter { verb ->
            VerbDistractorGenerator(Random(1)).distractors(verb, verbs) == VerbDistractorGenerator(Random(99)).distractors(verb, verbs)
        }
        assertTrue(withoutFallback.size > verbs.size / 2)
    }

    @Test
    fun printDistractorTableForReview() {
        val generator = VerbDistractorGenerator(Random(7))
        println("| Infinitive | Simple past | Distractors |")
        println("|---|---|---|")
        verbs.forEach { verb ->
            println("| ${verb.english} | ${verb.verbForms!!.simplePast} | ${generator.distractors(verb, verbs).joinToString(", ")} |")
        }
    }
}
