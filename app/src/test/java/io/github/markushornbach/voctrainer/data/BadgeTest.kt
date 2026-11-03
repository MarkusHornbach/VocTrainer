package io.github.markushornbach.voctrainer.data

import io.github.markushornbach.voctrainer.learning.BadgeKey
import io.github.markushornbach.voctrainer.learning.BadgeRules
import io.github.markushornbach.voctrainer.learning.BadgeSeries
import io.github.markushornbach.voctrainer.learning.BadgeValues
import io.github.markushornbach.voctrainer.learning.WordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeTest {
    private fun word(
        english: String, level: Int = 0, typed: Long? = null, deleted: Boolean = false, type: WordType? = WordType.NOUN
    ) = Vocabulary(
        id = english, english = english, germanMeanings = listOf("x"), wordType = type,
        leitnerLevel = level, highestLevel = level, updatedAt = 1L, isDeleted = deleted, firstTypedCorrectAt = typed
    )

    @Test
    fun keysSkipUnknownSeries() {
        assertEquals(
            setOf(BadgeKey(BadgeSeries.STREAK, 2)),
            listOf(Badge("streak", 2, 1L), Badge("rounds", 1, 1L)).keys()
        )
    }

    @Test
    fun sureWordsIncludePhrases() {
        val words = listOf(
            word("stop", level = 4), word("go", level = 5), word("run", level = 3),
            word("by the way", level = 4, type = WordType.PHRASE), word("gone", level = 5, deleted = true)
        )
        assertEquals(3, badgeValues(words, longestStreak = 0).sureWords)
    }

    @Test
    fun leftoverSelfTypedRowsAreNotCountedOrShown() {
        val rows = listOf(Badge("streak", 1, 1L), Badge("self_typed", 1, 2L), Badge("self_typed", 4, 3L))

        // Counting (Sammelmeldung) and display read the table through keys().
        assertEquals(1, rows.keys().size)
        val shown = BadgeRules.progress(BadgeValues(3, 0), rows.keys())
        assertEquals(listOf(BadgeSeries.STREAK, BadgeSeries.SURE_WORDS), shown.map { it.series })
        assertEquals(listOf(1, 0), shown.map { it.tier })
        // Awarding does not touch them and never announces them.
        assertTrue(BadgeRules.newlyReached(BadgeValues(3, 0), rows.keys()).isEmpty())
        // The backup only writes known series.
        val written = VocTrainerJson.parseSync(encodeTestSync(badges = rows.map { it.inTestBook() }))
        assertEquals(listOf(Badge("streak", 1, 1L).inTestBook()), written.badges)
    }

    @Test
    fun valuesIgnoreTheFirstTypedDate() {
        val words = listOf(word("stop", typed = 10L, level = 4), word("go", typed = 20L))
        assertEquals(BadgeValues(longestStreak = 7, sureWords = 1), badgeValues(words, 7))
    }

    private val badgeFile = """{$TEST_SYNC_HEAD, "entries": [], "badges": [%s]}"""

    @Test
    fun syncRoundTripKeepsBadges() {
        val badges = listOf(Badge("sure_words", 1, 30L), Badge("streak", 1, 10L), Badge("streak", 2, 20L)).map { it.inTestBook() }
        val parsed = VocTrainerJson.parseSync(encodeTestSync(badges = badges))
        assertEquals(badges.toSet(), parsed.badges.toSet())
    }

    @Test
    fun syncFileWithoutBadgesParsesAndUnknownSeriesAreSkipped() {
        assertTrue(VocTrainerJson.parseSync("""{$TEST_SYNC_HEAD, "entries": []}""").badges.isEmpty())

        val parsed = VocTrainerJson.parseSync(
            badgeFile.format(
                """{"series": "self_typed", "tier": 2, "reachedAt": 1, "bookId": "b"}, {"series": "rounds", "tier": 7, "reachedAt": 1, "bookId": "b"}, {"series": "sure_words", "tier": 1, "reachedAt": 5, "bookId": "b"}"""
            )
        )
        assertEquals(listOf(Badge("sure_words", 1, 5L).inTestBook()), parsed.badges)
    }

    @Test
    fun rejectsInvalidBadgesWithAGermanMessage() {
        mapOf(
            """{"series": "streak", "tier": 0, "reachedAt": 1, "bookId": "b"}""" to "Abzeichen 1: „tier“ muss zwischen 1 und 4 liegen",
            """{"series": "streak", "tier": 5, "reachedAt": 1, "bookId": "b"}""" to "Abzeichen 1: „tier“ muss zwischen 1 und 4 liegen",
            """{"series": "streak", "tier": 1, "reachedAt": -1, "bookId": "b"}""" to "Abzeichen 1: „reachedAt“ ist ungültig",
            """{"series": "streak", "tier": 1, "reachedAt": 1}""" to "Abzeichen 1: „bookId“ fehlt"
        ).forEach { (badge, message) ->
            val error = assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseSync(badgeFile.format(badge)) }
            assertEquals(message, error.message)
        }
        val badge = """{"series": "streak", "tier": 1, "reachedAt": 1, "bookId": "b"}"""
        assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseSync(badgeFile.format("$badge, $badge")) }
    }
}
