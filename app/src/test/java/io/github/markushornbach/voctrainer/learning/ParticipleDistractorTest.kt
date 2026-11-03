package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The second step of "3 Formen": past participle distractors and the two-step question. */
class ParticipleDistractorTest {
    private val verbs: List<QuizWord> = irregularFixtureVerbs()

    private fun verb(infinitive: String) = verbs.first { it.english == infinitive }

    private fun generator(seed: Int = 1) = VerbDistractorGenerator(Random(seed), VerbQuestion.THREE_FORMS)

    private fun distractors(infinitive: String) = generator().participleDistractors(verb(infinitive), verbs)

    @Test
    fun differentFormsOfferTheOwnSimplePast() {
        assertEquals(listOf("went", "goed", "wenten"), distractors("(to) go"))
        assertEquals(listOf("took", "taked", "tooken"), distractors("(to) take"))
        assertEquals(listOf("woke up", "waked up", "waken up"), distractors("(to) wake up"))
    }

    @Test
    fun equalSimplePastAndParticipleAppearOnlyOnce() {
        for (infinitive in listOf("(to) make", "(to) bring", "(to) get", "(to) hang", "(to) find")) {
            val forms = verb(infinitive).verbForms!!
            assertEquals(infinitive, forms.simplePast, forms.pastParticiple)
            val options = distractors(infinitive)
            assertEquals(infinitive, 3, options.size)
            assertFalse(infinitive, forms.simplePast in options)
        }
    }

    @Test
    fun allThreeFormsEqualGiveThreeOtherOptions() {
        for (infinitive in listOf("(to) put", "(to) hit", "(to) read")) {
            val question = generator().generate(verb(infinitive), verbs)!!
            val second = question.secondStep!!
            val head = infinitive.removePrefix("(to) ")
            assertEquals(infinitive, head, second.options[second.correctIndex])
            assertEquals(infinitive, 1, second.options.count { it == head })
            assertEquals(infinitive, 4, second.options.toSet().size)
        }
    }

    @Test
    fun doubleSimplePastIsOneForm() {
        val be = distractors("(to) be")
        assertEquals("was / were", be.first())
        assertFalse(be.any { it == "was" || it == "were" })
        assertFalse("been" in be)
        assertEquals(3, be.size)
    }

    @Test
    fun neverAFormOfAnotherVerbAndNeverTheAnswer() {
        val otherForms = HashSet(IrregularVerbList.allForms)
        verbs.forEach { verb ->
            otherForms += VerbDistractorGenerator.splitHead(verb.english).first
            verb.verbForms!!.let { forms ->
                otherForms += VerbDistractorGenerator.correctHeads(forms.simplePast)
                forms.pastParticiple?.let { otherForms += VerbDistractorGenerator.correctHeads(it) }
            }
        }
        for (verb in verbs.filter { it.verbForms!!.pastParticiple != null }) {
            val forms = verb.verbForms!!
            val options = generator().participleDistractors(verb, verbs)
            // The only real form allowed is the verb's own simple past.
            val rules = options.filter { it != forms.simplePast }
            for (option in rules) {
                assertFalse("${verb.english}: $option", VerbDistractorGenerator.splitHead(option).first in otherForms)
            }
            assertFalse(verb.english, forms.pastParticiple in options)
            assertEquals(verb.english, options.size, options.toSet().size)
            assertTrue(verb.english, options.none { it in IrregularVerbList.forbidden })
        }
    }

    @Test
    fun everyExampleVerbWithParticipleGetsBothSteps() {
        for (verb in verbs.filter { it.verbForms!!.pastParticiple != null }) {
            repeat(5) { seed ->
                val question = generator(seed).generate(verb, verbs)
                assertNotNull(verb.english, question)
                val second = question!!.secondStep!!
                assertEquals(4, question.options.toSet().size)
                assertEquals(4, second.options.toSet().size)
                assertEquals(verb.verbForms!!.simplePast, question.options[question.correctIndex])
                assertEquals(verb.verbForms!!.pastParticiple, second.options[second.correctIndex])
            }
        }
    }

    @Test
    fun verbsWithoutParticipleAreNotAskedWithThreeForms() {
        val ride = verb("(to) ride a bike")
        assertNull(ride.verbForms!!.pastParticiple)
        assertFalse(generator().canGenerate(ride, verbs))
        assertNull(generator().generate(ride, verbs))
        // With two forms the verb is asked as before.
        assertTrue(VerbDistractorGenerator(Random(1)).canGenerate(ride, verbs))
    }

    @Test
    fun speechAndTextsOfTheTwoSteps() {
        val question = generator().generate(verb("(to) go"), verbs)!!
        assertEquals("Wie lauten die Formen?", question.instruction)
        assertEquals("(to) go", question.speakOnShow)
        // Nothing after the simple past, all three forms after the participle.
        assertNull(question.speakAfterAnswer)
        assertEquals("(to) go – went", question.solution)
        assertEquals("(to) go – went – gone", question.secondStep!!.speakAfterAnswer)
        assertEquals("(to) go – went – gone", question.secondStep!!.solution)
        // Two forms: unchanged, no second step.
        val two = VerbDistractorGenerator(Random(1)).generate(verb("(to) go"), verbs)!!
        assertNull(two.secondStep)
        assertEquals("went", two.speakAfterAnswer)
    }

    @Test
    fun regularVerbsAreAskedInTwoStepsWithTheSameForm() {
        val regular = RegularVerbs.detect(vocabularyVerbFixture(), verbs)
        assertTrue(regular.isNotEmpty())
        for (word in regular) {
            val forms = word.verbForms!!
            assertEquals(forms.simplePast, forms.pastParticiple)
            val question = generator().generate(word, verbs) ?: continue
            val second = question.secondStep!!
            assertEquals(forms.simplePast, second.options[second.correctIndex])
            assertEquals(4, second.options.toSet().size)
            assertFalse(word.countsForProgress)
        }
        assertTrue(regular.any { generator().canGenerate(it, verbs) })
    }

    @Test
    fun aRoundWithThreeFormsRepeatsWrongVerbsWithBothSteps() {
        val askable = verbs.filter { generator().canGenerate(it, verbs) }
        assertEquals(verbs.count { it.verbForms!!.pastParticiple != null }, askable.size)
        val round = QuizRound(askable.take(2), verbs, generator())
        val first = round.nextQuestion()!!
        assertNotNull(first.question.secondStep)
        round.recordAnswer(correct = false)
        round.nextQuestion()!!
        round.recordAnswer(correct = true)
        val repeat = round.nextQuestion()!!
        assertTrue(repeat.isRepeat)
        assertEquals(first.question.word, repeat.question.word)
        assertNotNull(repeat.question.secondStep)
    }
}
