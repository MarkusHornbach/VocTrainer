package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.QuizFilter
import io.github.markushornbach.voctrainer.learning.QuizRound
import io.github.markushornbach.voctrainer.learning.QuizWord
import io.github.markushornbach.voctrainer.settings.QuizMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

class NextRoundPreviewTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 10, 6)

    private fun reviewed(daysAgo: Long) = today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun word(id: String, level: Int, daysAgo: Long?) = QuizWord(
        id, id, listOf("Wort $id"), 1, null, level, lastReviewedAt = daysAgo?.let(::reviewed), coreWord = id
    )

    private val words = listOf(
        word("slot", 3, 5), // can become "gelernt"
        word("review", 1, 3), // due
        word("fresh", 0, null), // never asked
        word("dropped", 0, 4), // fell back to level 0: due, but not "neu"
        word("early", 5, 0) // not due: fill-up
    )

    private fun preview(source: List<QuizWord> = words, day: LocalDate = today): NextRoundPreview {
        val pick = QuizRound.pickWords(source, Random(1), day, zone)
        return NextRoundPreview(QuizMode.VOCABULARY, source, QuizFilter(), day, pick, zone)
    }

    @Test
    fun countsAndReasonsFollowThePick() {
        val preview = preview()

        assertEquals(1, preview.learnable)
        assertEquals(1, preview.newCount)
        assertEquals(
            mapOf(
                "slot" to PickReason.LEARNABLE,
                "review" to PickReason.REVIEW,
                "fresh" to PickReason.NEW,
                "dropped" to PickReason.NEW,
                "early" to PickReason.EARLY
            ),
            preview.reasons.associate { (word, reason) -> word.id to reason }
        )
        assertEquals("slot", preview.reasons.first().first.id)
    }

    @Test
    fun aPreviewIsOnlyUsableForTheSameInputs() {
        val preview = preview()

        assertTrue(preview.isFor(QuizMode.VOCABULARY, words, QuizFilter(), today))
        assertFalse(preview.isFor(QuizMode.VERBS, words, QuizFilter(), today))
        assertFalse(preview.isFor(QuizMode.VOCABULARY, words.toList(), QuizFilter(), today))
        assertFalse(preview.isFor(QuizMode.VOCABULARY, words, QuizFilter(), today.plusDays(1)))
        assertFalse(preview.isFor(QuizMode.VOCABULARY, words, QuizFilter(parts = setOf("A")), today))
    }
}
