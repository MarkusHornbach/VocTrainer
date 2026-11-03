package io.github.markushornbach.voctrainer.data

import io.github.markushornbach.voctrainer.learning.Streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PracticeDayTest {
    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun syncFileCarriesReviewDatesAndPracticeDays() {
        val vocabulary = listOf(
            Vocabulary(id = "v1", english = "bag", germanMeanings = listOf("Tasche"), unit = 1, leitnerLevel = 2,
                updatedAt = 10, lastReviewedAt = 1_759_000_000_000, highestLevel = 2),
            Vocabulary(id = "v2", english = "bus", germanMeanings = listOf("Bus"), unit = 1, updatedAt = 11)
        ).map { it.copy(bookId = testBook.id) }
        val verbs = listOf(
            IrregularVerb(id = "i1", infinitive = "(to) go", simplePast = "went", germanMeanings = listOf("gehen"),
                unit = 1, leitnerLevel = 3, updatedAt = 12, lastReviewedAt = 1_759_100_000_000)
        ).map { it.copy(bookId = testBook.id) }
        val days = listOf(
            PracticeDay("2026-10-07", "phone", 2, 2, 500),
            PracticeDay("2026-10-08", "tablet", 1, 1, 600)
        ).map { it.inTestBook() }

        val json = encodeTestSync(vocabulary, verbs, days)
        assertTrue(json.contains("\"version\": 5"))
        val parsed = VocTrainerJson.parseSync(json)

        assertEquals(vocabulary, parsed.vocabulary)
        assertNull(parsed.vocabulary[1].lastReviewedAt)
        assertEquals(verbs, parsed.irregularVerbs)
        assertEquals(days, parsed.practiceDays)
    }

    @Test
    fun invalidPracticeDaysAreRejected() {
        fun file(day: String) =
            """{$TEST_SYNC_HEAD, "entries": [], "practiceDays": [$day]}"""
        listOf(
            """{"bookId": "b", "date": "8.10.2026", "deviceId": "a", "roundsCompleted": 1, "dailyGoal": 1, "updatedAt": 1}""",
            """{"bookId": "b", "date": "2026-10-08", "deviceId": " ", "roundsCompleted": 1, "dailyGoal": 1, "updatedAt": 1}""",
            """{"bookId": "b", "date": "2026-10-08", "deviceId": "a", "roundsCompleted": -1, "dailyGoal": 1, "updatedAt": 1}""",
            """{"bookId": "b", "date": "2026-10-08", "deviceId": "a", "roundsCompleted": 1, "dailyGoal": 0, "updatedAt": 1}"""
        ).forEach { day ->
            assertThrows(day, InvalidFileException::class.java) { VocTrainerJson.parseSync(file(day)) }
        }
        val twice = """{"bookId": "b", "date": "2026-10-08", "deviceId": "a", "roundsCompleted": 1, "dailyGoal": 1, "updatedAt": 1}"""
        assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseSync(file("$twice, $twice")) }
    }

    @Test
    fun bookRestartClearsTheReviewDate() {
        val word = Vocabulary(id = "a", english = "a", germanMeanings = listOf("a"), leitnerLevel = 3, lastReviewedAt = 5)
        assertNull(BookRestart.vocabulary(listOf(word), 9).single().lastReviewedAt)
        val verb = IrregularVerb(id = "v", infinitive = "v", simplePast = "p", germanMeanings = listOf("x"), unit = 1,
            leitnerLevel = 2, lastReviewedAt = 5)
        assertNull(BookRestart.verbs(listOf(verb), 9).single().lastReviewedAt)
    }
}
