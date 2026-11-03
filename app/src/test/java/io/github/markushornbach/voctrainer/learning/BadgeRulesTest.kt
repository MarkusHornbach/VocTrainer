package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeRulesTest {
    @Test
    fun streakTierAtAndAroundEveryThreshold() {
        val expected = mapOf(0 to 0, 2 to 0, 3 to 1, 6 to 1, 7 to 2, 29 to 2, 30 to 3, 99 to 3, 100 to 4, 365 to 4)
        expected.forEach { (value, tier) -> assertEquals("value $value", tier, BadgeRules.tierFor(BadgeSeries.STREAK, value)) }
    }

    @Test
    fun sureWordsTierAtAndAroundEveryThreshold() {
        val expected = mapOf(9 to 0, 10 to 1, 49 to 1, 50 to 2, 149 to 2, 150 to 3, 499 to 3, 500 to 4)
        expected.forEach { (value, tier) -> assertEquals("value $value", tier, BadgeRules.tierFor(BadgeSeries.SURE_WORDS, value)) }
    }

    @Test
    fun nextThresholdAndAllTiersReached() {
        assertEquals(3, BadgeRules.nextThreshold(BadgeSeries.STREAK, 0))
        assertEquals(30, BadgeRules.nextThreshold(BadgeSeries.STREAK, 2))
        assertEquals(500, BadgeRules.nextThreshold(BadgeSeries.SURE_WORDS, 3))
        assertNull(BadgeRules.nextThreshold(BadgeSeries.SURE_WORDS, 4))
        assertEquals(listOf("Bronze", "Silber", "Gold", "Platin"), (1..4).map { BadgeRules.tierName(it) })
    }

    @Test
    fun severalTiersAreReachedAtOnce() {
        val newly = BadgeRules.newlyReached(BadgeValues(longestStreak = 31, sureWords = 9), emptySet())
        assertEquals(
            listOf(
                BadgeKey(BadgeSeries.STREAK, 1), BadgeKey(BadgeSeries.STREAK, 2), BadgeKey(BadgeSeries.STREAK, 3)
            ),
            newly
        )
    }

    @Test
    fun onlyMissingTiersAreAwarded() {
        val earned = setOf(BadgeKey(BadgeSeries.STREAK, 1), BadgeKey(BadgeSeries.STREAK, 2))
        assertEquals(
            listOf(BadgeKey(BadgeSeries.STREAK, 3)),
            BadgeRules.newlyReached(BadgeValues(30, 0), earned)
        )
        assertTrue(BadgeRules.newlyReached(BadgeValues(7, 0), earned).isEmpty())
    }

    @Test
    fun earnedTiersStayWhenTheValueDrops() {
        // 60 sure words earned Silber; now only 5 words are sure (and the longest streak was reset by a restore).
        val earned = setOf(BadgeKey(BadgeSeries.SURE_WORDS, 1), BadgeKey(BadgeSeries.SURE_WORDS, 2))
        val values = BadgeValues(longestStreak = 0, sureWords = 5)

        assertTrue(BadgeRules.newlyReached(values, earned).isEmpty())
        val row = BadgeRules.progress(values, earned).single { it.series == BadgeSeries.SURE_WORDS }
        assertEquals(2, row.tier)
        assertEquals(5, row.value)
        assertEquals(150, row.nextThreshold)
    }

    @Test
    fun progressRowsAreInSeriesOrderWithTheHighestEarnedTier() {
        val earned = setOf(BadgeKey(BadgeSeries.SURE_WORDS, 1), BadgeKey(BadgeSeries.SURE_WORDS, 3))
        val rows = BadgeRules.progress(BadgeValues(2, 120), earned)
        assertEquals(listOf(BadgeSeries.STREAK, BadgeSeries.SURE_WORDS), rows.map { it.series })
        assertEquals(listOf(0, 3), rows.map { it.tier })
        assertEquals(listOf(2, 120), rows.map { it.value })
    }

    @Test
    fun seriesKeysAreStable() {
        assertEquals(listOf("streak", "sure_words"), BadgeSeries.entries.map { it.key })
        assertEquals(BadgeSeries.SURE_WORDS, BadgeSeries.fromKey("sure_words"))
        assertNull(BadgeSeries.fromKey("rounds"))
    }
}
