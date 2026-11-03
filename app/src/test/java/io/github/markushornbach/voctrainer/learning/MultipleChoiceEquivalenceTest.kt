package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The precomputed comparison keys of [QuizWord] must give exactly the same results as the
 * original implementation, which normalized every meaning again for every pair of words.
 */
class MultipleChoiceEquivalenceTest {
    /** The implementation before the speed-up, kept verbatim as a reference. */
    private class ReferenceGenerator(private val random: Random) {
        fun generate(target: QuizWord, candidates: List<QuizWord>): MultipleChoiceQuestion? {
            val valid = validCandidates(target, candidates)
            val type = target.effectiveType
            val tiers = listOf(
                valid.filter { it.unit == target.unit && it.part == target.part && it.effectiveType == type },
                valid.filter { it.unit == target.unit && it.effectiveType == type },
                valid.filter { it.effectiveType == type },
                valid
            )
            val chosen = mutableListOf<QuizWord>()
            val chosenIds = HashSet<String>()
            val chosenMainMeanings = HashSet<String>()
            for (tier in tiers) {
                for (candidate in tier.shuffled(random)) {
                    if (chosen.size == 3) break
                    if (candidate.id in chosenIds) continue
                    if (!chosenMainMeanings.add(oldMeaningKey(candidate.mainMeaning))) continue
                    chosenIds += candidate.id
                    chosen += candidate
                }
                if (chosen.size == 3) break
            }
            if (chosen.size < 3) return null
            val options = (chosen + target).shuffled(random)
            return MultipleChoiceQuestion(
                word = target,
                prompt = target.english,
                options = options.map { GermanText.withoutGenderSuffix(it.mainMeaning) },
                correctIndex = options.indexOf(target),
                solution = target.germanMeanings.joinToString(", ") { GermanText.withoutGenderSuffix(it) },
                speakOnShow = target.english
            )
        }

        fun canGenerate(target: QuizWord, candidates: List<QuizWord>): Boolean =
            validCandidates(target, candidates).mapTo(HashSet()) { oldMeaningKey(it.mainMeaning) }.size >= 3

        private fun validCandidates(target: QuizWord, candidates: List<QuizWord>): List<QuizWord> {
            val targetEnglish = normalizeText(target.english)
            val targetMeanings = target.germanMeanings.mapTo(HashSet()) { oldMeaningKey(it) }
            return candidates.filter { candidate ->
                candidate.id != target.id &&
                    normalizeText(candidate.english) != targetEnglish &&
                    candidate.germanMeanings.none { oldMeaningKey(it) in targetMeanings }
            }
        }

        private fun oldMeaningKey(meaning: String): String = normalizeText(GermanText.withoutGenderSuffix(meaning))
    }

    /** Gender suffixes, shared meanings, English variants, phrases and verbs. */
    private val edgeCases = listOf(
        word("t1", "dancer", "Tänzer/in", unit = 1, part = "A"),
        word("t2", "dancer", "Tänzer", unit = 2, part = "A"),
        word("t3", "teacher", "Lehrer:innen", "Lehrer", unit = 1, part = "A"),
        word("t4", "Teacher ", "Lehrer(in)", unit = 3, part = null),
        word("t5", "student", "Schüler*in", unit = 1, part = "B"),
        word("t6", "pupil", "Schüler", "Schülerin", unit = 1, part = "B"),
        word("t7", "Get  off", "aussteigen", unit = 1, part = "C"),
        word("t8", "get off", "absteigen", unit = 2, part = "C"),
        word("t9", "bench", "Bank (zum Sitzen)", unit = 1, part = "C"),
        word("t10", "bank", "Bank", unit = 1, part = "C"),
        word("t11", "Nice to meet you.", "Freut mich.", unit = 1, part = "A"),
        word("t12", "Pleased to meet you.", "Freut mich.", "Sehr erfreut.", unit = 1, part = "A"),
        word("t13", "(to) run", "laufen", "rennen", unit = 1, part = "A"),
        word("t14", "(to) walk", "laufen", "gehen", unit = 1, part = "A"),
        word("t15", "(to) go", "gehen", unit = 2, part = null, type = WordType.VERB),
        word("t16", "car", "Auto", unit = 1, part = "A", type = WordType.NOUN),
        word("t17", "automobile", "AUTO", unit = 1, part = "A", type = WordType.NOUN),
        word("t18", "bus", "Bus", unit = 1, part = "A"),
        word("t19", "coach", "Bus", "Trainer/in", unit = 2, part = "B")
    )

    private fun pools(): Map<String, List<QuizWord>> {
        val example = exampleWords(EXAMPLE_BOOK)
        // About 800 words like a full school year: the example copied into further units.
        val large = example + (1..3).flatMap { shift ->
            example.map { it.copy(id = "${it.id}-$shift", unit = it.unit!! + 2 * shift) }
        }
        return mapOf(
            "edge cases" to edgeCases,
            "example file" to example,
            "example + edge cases" to example + edgeCases,
            "old example file" to exampleWords(),
            "large pool" to large,
            "regular verbs" to RegularVerbs.detect(vocabularyVerbFixture(), irregularFixtureVerbs())
        )
    }

    @Test
    fun canGenerateGivesTheSameResultsAsBefore() {
        pools().forEach { (name, pool) ->
            val generator = MultipleChoiceGenerator(Random(1))
            val reference = ReferenceGenerator(Random(1))
            pool.forEach { word ->
                assertEquals("$name: ${word.english}", reference.canGenerate(word, pool), generator.canGenerate(word, pool))
            }
        }
    }

    @Test
    fun generatedQuestionsAreIdenticalForTheSameRandomSeed() {
        pools().forEach { (name, pool) ->
            listOf(1, 7, 42).forEach { seed ->
                val generator = MultipleChoiceGenerator(Random(seed))
                val reference = ReferenceGenerator(Random(seed))
                pool.forEach { word ->
                    assertEquals("$name/$seed: ${word.english}", reference.generate(word, pool), generator.generate(word, pool))
                }
            }
        }
    }

    @Test
    fun edgeCasesBehaveAsIntended() {
        val generator = MultipleChoiceGenerator(Random(1))
        // "Tänzer" counts as the same meaning as "Tänzer/in" and is never a distractor for it.
        repeat(20) { seed ->
            val question = MultipleChoiceGenerator(Random(seed)).generate(edgeCases[0], edgeCases)!!
            assertEquals(1, question.options.count { it == "Tänzer" })
        }
        // A pool with only two other main meanings is not enough.
        val tooSmall = listOf(edgeCases[15], edgeCases[16], edgeCases[17], edgeCases[0])
        assertFalse(generator.canGenerate(edgeCases[15], tooSmall))
        assertTrue(generator.canGenerate(edgeCases[15], edgeCases))
    }

    @Test
    fun printTimingForReview() {
        val pool = pools().getValue("large pool")
        val generator = MultipleChoiceGenerator(Random(1))
        val reference = ReferenceGenerator(Random(1))
        val selection = pool.take(660)
        fun millis(block: () -> Unit): Long {
            val start = System.nanoTime()
            block()
            return (System.nanoTime() - start) / 1_000_000
        }
        val before = millis { selection.count { reference.canGenerate(it, pool) } }
        val after = millis { selection.count { generator.canGenerate(it, pool) } }
        println("canGenerate for ${selection.size} words, pool ${pool.size}: before $before ms, after $after ms (JVM)")
    }
}
