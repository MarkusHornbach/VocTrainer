package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundStarsTest {
    /** Full credit (3) for every correct word, like the old count of correct first attempts. */
    private fun stars(correct: Int, total: Int) = RoundStars.forCredit(correct * RoundStars.FULL_CREDIT, total)

    @Test
    fun tenWordRound() {
        val expected = listOf(0, 0, 0, 0, 1, 1, 1, 2, 2, 3, 3)
        (0..10).forEach { correct ->
            assertEquals("$correct of 10", expected[correct], stars(correct, 10))
        }
    }

    @Test
    fun smallerRoundsUseTheShare() {
        assertEquals(3, stars(3, 3))
        assertEquals(1, stars(2, 3)) // 67 %
        assertEquals(0, stars(1, 3)) // 33 %
        assertEquals(2, stars(5, 7)) // 71 %
        assertEquals(1, stars(2, 5)) // exactly 40 %
        assertEquals(3, stars(1, 1))
        assertEquals(0, stars(0, 1))
    }

    @Test
    fun reducedAnswersWeighOnlyAThird() {
        // 10 words: 9 typed + 1 "Mit Vorschlägen" = 28 of 30 (93 %) still 3 stars; 2 "Mit Vorschlägen" = 26 (87 %) → 2 stars.
        assertEquals(3, RoundStars.forCredit(9 * 3 + 1, 10))
        assertEquals(2, RoundStars.forCredit(8 * 3 + 2, 10))
        // Everything answered with "Mit Vorschlägen" on typeable words: 10 of 30 = 33 % → 0 stars (typed: 3).
        assertEquals(0, RoundStars.forCredit(10 * RoundStars.REDUCED_CREDIT, 10))
        assertEquals(3, RoundStars.forCredit(10 * RoundStars.FULL_CREDIT, 10))
        // 3 words: one third of credit is exactly 40 % → 1 star.
        assertEquals(1, RoundStars.forCredit(3 * 3 * 4 / 10 + 1, 3))
    }

    @Test
    fun emptyRoundHasNoStars() {
        assertEquals(0, RoundStars.forCredit(0, 0))
    }

    @Test
    fun hintShowsOnlyWithReducedAnswersAndLessThanThreeStars() {
        assertTrue(RoundStars.showsReducedHint(stars = 2, reducedCredits = 1))
        assertTrue(RoundStars.showsReducedHint(stars = 0, reducedCredits = 4))
        assertFalse(RoundStars.showsReducedHint(stars = 3, reducedCredits = 1))
        assertFalse(RoundStars.showsReducedHint(stars = 2, reducedCredits = 0))
    }
}
