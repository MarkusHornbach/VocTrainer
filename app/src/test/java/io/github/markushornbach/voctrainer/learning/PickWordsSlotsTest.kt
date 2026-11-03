package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/** The places of a round reserved for words that can become "gelernt" today ([QuizRound.SURE_SLOTS]). */
class PickWordsSlotsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 10, 6)

    private fun reviewed(daysAgo: Long) = today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun typeable(id: String, level: Int, daysAgo: Long?) = QuizWord(
        id, id, listOf("Wort $id"), 1, null, level,
        lastReviewedAt = daysAgo?.let(::reviewed), coreWord = id
    )

    private fun pick(words: List<QuizWord>, size: Int = QuizRound.ROUND_SIZE) =
        QuizRound.pickWords(words, Random(11), today, zone, size)

    @Test
    fun thirtyLevelOneAndThirtyLevelThreeWordsGiveThreeSlotWords() {
        val words = (1..30).map { typeable("a$it", 1, 3) } + (1..30).map { typeable("b$it", 3, 5) }

        val picked = pick(words).words

        assertEquals(10, picked.size)
        assertEquals(listOf(3, 3, 3), picked.take(3).map { it.leitnerLevel })
        assertEquals(7, picked.drop(3).count { it.leitnerLevel == 1 })
        assertEquals(7, picked.drop(3).size)
    }

    @Test
    fun fewerThanThreeCandidatesTakeOnlyTheirOwnSlots() {
        val words = (1..2).map { typeable("b$it", 3, 5) } + (1..20).map { typeable("a$it", 1, 3) }

        val picked = pick(words).words

        assertEquals(10, picked.size)
        assertEquals(2, picked.count { it.leitnerLevel == 3 })
        assertEquals(8, picked.count { it.leitnerLevel == 1 })
    }

    @Test
    fun withoutCandidatesTheOrderIsTheOldOne() {
        val words = (1..4).flatMap { level -> (1..4).map { typeable("l$level-$it", level.coerceAtMost(2), 4) } } +
            (1..4).map { typeable("n$it", 0, null) }

        val picked = pick(words).words

        assertEquals(10, picked.size)
        assertTrue(picked.none { SureToday.canBecomeSure(it, today, zone) })
        // Due reviews first, lowest level first; new words only after them.
        val levels = picked.map { it.leitnerLevel }
        assertEquals(levels.sortedBy { if (it == 0) 99 else it }, levels)
    }

    @Test
    fun candidatesAreTakenOldestReviewDayFirst() {
        val words = (1..5).map { typeable("c$it", 3, 4L + it) } + (1..10).map { typeable("a$it", 1, 3) }

        val picked = pick(words).words

        // Reviewed 9, 8, 7, 6 and 5 days ago: the three oldest are c5, c4, c3.
        assertEquals(setOf("c5", "c4", "c3"), picked.take(3).map { it.id }.toSet())
        assertEquals(listOf("c5", "c4", "c3"), picked.take(3).map { it.id })
    }

    @Test
    fun picksThatAreNotDueAreMarkedEarlyWhereverTheyComeFrom() {
        // Level 3 asked 2 days ago rests longer (4 days) but can still become "gelernt" today.
        val slotWord = typeable("s", 3, 2)
        val dueWord = typeable("d", 1, 3)
        val fillUp = typeable("f", 5, 0)

        val pick = pick(listOf(slotWord, dueWord, fillUp))

        assertFalse(Due.isDue(slotWord, today, zone))
        assertEquals(listOf("s", "d", "f"), pick.words.map { it.id })
        assertEquals(setOf("s", "f"), pick.earlyIds)
    }

    @Test
    fun smallSelectionsGiveSmallerRounds() {
        val pick = pick(listOf(typeable("x", 3, 5), typeable("y", 3, 6)))
        assertEquals(listOf("y", "x"), pick.words.map { it.id })

        assertEquals(1, pick(listOf(typeable("only", 2, 5))).words.size)
        assertTrue(pick(emptyList()).words.isEmpty())
    }
}
