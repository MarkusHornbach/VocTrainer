package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class RegularVerbsTest {
    private val irregular = irregularFixtureVerbs()
    private val regular = RegularVerbs.detect(vocabularyVerbFixture(), irregular)

    private fun regular(english: String) = regular.first { it.english == english }

    private fun distractors(english: String, seed: Int = 1) =
        VerbDistractorGenerator(Random(seed)).distractors(regular(english), irregular)

    @Test
    fun detectsExactlyTheRegularVerbsOfTheFixture() {
        val expected = listOf(
            "promise sb. sth.", "ask sb. for sth.", "walk", "bark", "wash", "carry", "help", "play", "jump", "taste",
            "try", "shop", "stop", "dance", "listen (to)", "answer the phone", "invite sb. (to)", "camp", "rain", "stay",
            "turn sth. off", "turn sth. on", "work", "climb", "land", "look up", "cook", "pack"
        ).map { "(to) $it" }
        assertEquals(expected, regular.map { it.english })
        // Irregular (in the verb list) or not ending like a plain verb: never asked as regular verbs.
        val notDetected = listOf(
            "take care of sb./sth.", "take sb. for a walk", "be allowed to", "feed", "fall asleep", "go shopping",
            "buy", "sell", "pay (for sth.)", "put up a tent", "sing"
        ).map { "(to) $it" }
        val verbEntries = vocabularyVerbFixture().map { it.english }
        assertTrue(verbEntries.containsAll(notDetected))
        assertTrue(regular.none { it.english in notDetected })
        assertTrue(regular.all { it.verbForms!!.isRegular })
    }

    @Test
    fun correctAnswerIsTheRegularFormPlusTheTail() {
        assertEquals("invited sb. (to)", regular("(to) invite sb. (to)").verbForms!!.simplePast)
        assertEquals("answered the phone", regular("(to) answer the phone").verbForms!!.simplePast)
        assertEquals("shopped", regular("(to) shop").verbForms!!.simplePast)
        assertEquals("stayed", regular("(to) stay").verbForms!!.simplePast)
    }

    @Test
    fun expectedDistractorsFromTheSpecification() {
        val expected = mapOf(
            "(to) shop" to setOf("shep", "shought", "shoped"),
            "(to) dance" to setOf("donce", "dought", "danceed"),
            "(to) stay" to setOf("stew", "stought", "staied"),
            "(to) help" to setOf("holp", "hought", "helpt"),
            "(to) listen (to)" to setOf("liston (to)", "listent (to)", "listenned (to)"),
            "(to) walk" to setOf("wulk", "wought", "walkt"),
            "(to) cook" to setOf("cok", "cought", "cookt"),
            "(to) carry" to setOf("carrought", "carryed", "carryt"),
            "(to) try" to setOf("trought", "tryed", "tryt"),
            "(to) rain" to setOf("raint", "rought", "rainned"),
            "(to) land" to setOf("lund", "lant", "lought"),
            "(to) work" to setOf("werk", "wought", "workt"),
            "(to) answer the phone" to setOf("answor the phone", "answought the phone", "answerred the phone"),
            "(to) invite sb. (to)" to setOf("invote sb. (to)", "invought sb. (to)", "inviteed sb. (to)")
        )
        expected.forEach { (english, distractors) ->
            assertEquals(english, distractors, distractors(english).toSet())
        }
    }

    @Test
    fun turnOffGetsOnlyTwoDistractorsAndIsNeverMixedIn() {
        val turnOff = regular("(to) turn sth. off")
        val turnOn = regular("(to) turn sth. on")
        val generator = VerbDistractorGenerator(Random(1))
        assertEquals(listOf("tarn sth. off", "tought sth. off"), generator.distractors(turnOff, irregular))
        assertEquals(listOf("tarn sth. on", "tought sth. on"), generator.distractors(turnOn, irregular))
        // Exactly these verbs get fewer than three distractors.
        assertEquals(
            listOf("(to) ask sb. for sth.", "(to) turn sth. off", "(to) turn sth. on"),
            regular.filterNot { generator.canGenerate(it, irregular) }.map { it.english }
        )
        val tooFew = regular.filterNot { generator.canGenerate(it, irregular) }
        assertTrue(tooFew.all { generator.distractors(it, irregular).size == 2 })
        repeat(20) { seed ->
            val picked = RegularVerbs.pick(regular, irregular, VerbDistractorGenerator(Random(seed)), Random(seed), 30)
            assertTrue(picked.none { it in tooFew })
        }
    }

    @Test
    fun cokeNeverAppears() {
        repeat(20) { seed ->
            regular.forEach { word ->
                assertFalse(VerbDistractorGenerator(Random(seed)).distractors(word, irregular).any { "coke" in it })
            }
        }
    }

    @Test
    fun staidNeverAppears() {
        repeat(20) { seed ->
            assertFalse(distractors("(to) stay", seed).any { it.startsWith("staid") })
            regular.forEach { word ->
                assertFalse(VerbDistractorGenerator(Random(seed)).distractors(word, irregular).any { "staid" in it })
            }
        }
    }

    @Test
    fun regularQuestionsLookLikeIrregularOnes() {
        val generator = VerbDistractorGenerator(Random(4))
        val question = generator.generate(regular("(to) shop"), irregular)!!
        assertEquals("(to) shop", question.prompt)
        assertEquals("Wie lautet das simple past?", question.instruction)
        assertEquals("shopped", question.options[question.correctIndex])
        assertEquals(4, question.options.toSet().size)
        assertEquals("(to) shop – shopped", question.solution)
    }

    @Test
    fun roundsGetUpToTwoRegularVerbsThatNeverCountForProgress() {
        val random = Random(9)
        val generator = VerbDistractorGenerator(random)
        val irregularWords = QuizRound.pickWords(irregular, random, LocalDate.of(2026, 10, 6), ZoneOffset.UTC).words
        assertEquals(10, irregularWords.size)

        val twoOrMore = RegularVerbs.pick(regular, irregular, generator, random)
        val mixed = RegularVerbs.mixIn(irregularWords, twoOrMore, random)
        assertEquals(12, QuizRound(mixed, irregular, generator).size)
        assertEquals(irregularWords, mixed.filter { it.countsForProgress })

        val oneOnly = RegularVerbs.pick(listOf(regular("(to) shop")), irregular, generator, random)
        assertEquals(11, RegularVerbs.mixIn(irregularWords, oneOnly, random).size)
        assertEquals(10, RegularVerbs.mixIn(irregularWords, emptyList(), random).size)

        assertTrue(mixed.filterNot { it.countsForProgress }.all { it.verbForms!!.isRegular })
        assertEquals(2, mixed.count { !it.countsForProgress })
    }

    @Test
    fun wrongRegularVerbsAreRepeatedInTheRound() {
        val generator = VerbDistractorGenerator(Random(3))
        val words = listOf(regular("(to) shop"), irregular.first { it.english == "(to) take" })
        val round = QuizRound(words, irregular, generator)

        val first = round.nextQuestion()!!
        round.recordAnswer(correct = false)
        round.nextQuestion()!!
        round.recordAnswer(correct = true)
        val repeat = round.nextQuestion()!!
        assertTrue(repeat.isRepeat)
        assertEquals(first.question.word, repeat.question.word)
        round.recordAnswer(correct = true)
        assertEquals(listOf(regular("(to) shop")), round.wrongOnFirstAttempt)
    }

    @Test
    fun printRegularVerbTableForReview() {
        val generator = VerbDistractorGenerator(Random(1))
        println("| Vocabulary | Correct | Distractors |")
        println("|---|---|---|")
        regular.forEach { word ->
            val distractors = generator.distractors(word, irregular)
            val note = if (distractors.size < 3) " *(only ${distractors.size} – not mixed in)*" else ""
            println("| ${word.english} | ${word.verbForms!!.simplePast} | ${distractors.joinToString(", ")}$note |")
        }
    }
}
