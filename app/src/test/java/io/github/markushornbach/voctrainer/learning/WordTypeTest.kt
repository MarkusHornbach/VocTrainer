package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WordTypeTest {
    @Test
    fun typicalEntriesOfEachType() {
        assertEquals(WordType.VERB, WordType.of("(to) rain", "regnen"))
        assertEquals(WordType.PHRASE, WordType.of("Don't worry.", "Mach dir keine Sorgen."))
        assertEquals(WordType.NOUN, WordType.of("pond", "(Garten-)Teich"))
        assertEquals(WordType.NOUN, WordType.of("sports hall", "(große) Sporthalle"))
        assertEquals(WordType.OTHER, WordType.of("sunny", "sonnig"))
    }

    @Test
    fun checksInOrderVerbPhraseNounOther() {
        assertEquals(WordType.VERB, WordType.of("(To) take care of sb./sth. …", "sich um jn./etwas kümmern"))
        assertEquals(WordType.PHRASE, WordType.of("…, isn't it?", "…, oder?"))
        assertEquals(WordType.PHRASE, WordType.of("the next morning", "am nächsten Morgen"))
        assertEquals(WordType.PHRASE, WordType.of("Thanks", "Danke!"))
        assertEquals(WordType.NOUN, WordType.of("the cat", "die Katze"))
        assertEquals(WordType.OTHER, WordType.of("early", "früh"))
    }

    @Test
    fun exampleFileDistribution() {
        val counts = exampleWords().groupingBy { WordType.of(it.english, it.mainMeaning) }.eachCount()
        assertEquals(mapOf(WordType.NOUN to 57, WordType.VERB to 39, WordType.OTHER to 29, WordType.PHRASE to 25), counts)
    }

    /** In the example book the stored type differs from the heuristic for these entries. */
    @Test
    fun exampleBookStoresTypesTheHeuristicGetsWrong() {
        val words = exampleWords(EXAMPLE_BOOK).associateBy { it.english }
        val expected = mapOf(
            "the whole family" to WordType.NOUN, "at the weekend" to WordType.OTHER,
            "o'clock" to WordType.OTHER, "e.g." to WordType.OTHER
        )
        expected.forEach { (english, stored) ->
            val word = words.getValue(english)
            assertEquals(english, stored, word.effectiveType)
            assertNotEquals(english, stored, WordType.of(word.english, word.mainMeaning))
        }
    }

    @Test
    fun storedTypeWinsOverHeuristic() {
        assertEquals(WordType.OTHER, WordType.effective(WordType.OTHER, "the next morning", "am nächsten Morgen"))
        assertEquals(WordType.PHRASE, WordType.effective(null, "the next morning", "am nächsten Morgen"))
        assertEquals(WordType.NOUN, word("t", "tea", "leichte Mahlzeit", type = WordType.NOUN).effectiveType)
        assertEquals(WordType.OTHER, word("t", "tea", "leichte Mahlzeit").effectiveType)
    }

    @Test
    fun parsesJsonValuesAndIgnoresUnknownOnes() {
        assertEquals(WordType.VERB, WordType.fromJsonValue("verb"))
        assertEquals(WordType.PHRASE, WordType.fromJsonValue("Phrase"))
        assertEquals(null, WordType.fromJsonValue("adjective"))
        assertEquals(null, WordType.fromJsonValue(null))
    }

    @Test
    fun storedTypesOfTheNewExampleFile() {
        val counts = exampleWords(EXAMPLE_BOOK).groupingBy { it.effectiveType }.eachCount()
        assertEquals(mapOf(WordType.NOUN to 58, WordType.VERB to 39, WordType.OTHER to 35, WordType.PHRASE to 18), counts)
    }
}
