package io.github.markushornbach.voctrainer.data

import io.github.markushornbach.voctrainer.learning.WordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelResetTest {
    private val now = 9_000L

    private fun vocabulary(id: String, level: Int, deleted: Boolean = false) = Vocabulary(
        id = id, english = id, germanMeanings = listOf("x"), unit = 1, leitnerLevel = level,
        updatedAt = 100L, isDeleted = deleted, highestLevel = level
    )

    private fun verb(id: String, level: Int) = IrregularVerb(
        id = id, infinitive = id, simplePast = "past", germanMeanings = listOf("x"), unit = 1,
        leitnerLevel = level, updatedAt = 100L
    )

    @Test
    fun setsSelectedVocabularyToALevelUpOrDownAndMakesItDue() {
        val entries = listOf(
            vocabulary("up", 0), vocabulary("down", 5).copy(lastReviewedAt = 50L),
            vocabulary("same", 2).copy(lastReviewedAt = 60L), vocabulary("done", 2),
            vocabulary("other", 0), vocabulary("gone", 0, deleted = true)
        )

        val changed = LevelReset.vocabularyTo(entries, setOf("up", "down", "same", "done", "gone"), 2, now)

        assertEquals(listOf("up", "down", "same"), changed.map { it.id })
        assertTrue(changed.all { it.leitnerLevel == 2 && it.lastReviewedAt == null && it.updatedAt == now })
        // Test app: the highest level follows, so the points for new levels can be tried again.
        assertTrue(changed.all { it.highestLevel == 2 })
    }

    @Test
    fun settingALevelAlsoChangesAnOnlyHigherHighestLevel() {
        val entry = vocabulary("a", 2).copy(highestLevel = 5)
        assertEquals(listOf(entry.copy(highestLevel = 2, updatedAt = now)), LevelReset.vocabularyTo(listOf(entry), setOf("a"), 2, now))
    }

    @Test
    fun withLevelNeverLowersTheHighestLevel() {
        val entry = vocabulary("a", 3).copy(highestLevel = 4)
        assertEquals(4, entry.withLevel(1).highestLevel)
        assertEquals(1, entry.withLevel(1).leitnerLevel)
        assertEquals(5, entry.withLevel(5).highestLevel)
    }

    @Test
    fun setsSelectedVerbsToALevelAndClearsTheirReviewDate() {
        val verbs = listOf(verb("take", 2).copy(lastReviewedAt = 50L), verb("go", 4), verb("be", 0).copy(isDeleted = true))

        val changed = LevelReset.verbsTo(verbs, setOf("take", "go", "be"), level = 4, now = now)

        assertEquals(listOf("take"), changed.map { it.id })
        assertEquals(verbs[0].copy(leitnerLevel = 4, lastReviewedAt = null, updatedAt = now), changed[0])
    }

    @Test
    fun dueNowClearsOnlyTheReviewDateAndKeepsTheLevel() {
        val entries = listOf(
            vocabulary("a", 3).copy(lastReviewedAt = 50L), vocabulary("b", 2),
            vocabulary("other", 4).copy(lastReviewedAt = 50L), vocabulary("gone", 2, deleted = true).copy(lastReviewedAt = 50L)
        )

        val changed = LevelReset.vocabularyDueNow(entries, setOf("a", "b", "gone"), now)

        assertEquals(listOf(entries[0].copy(lastReviewedAt = null, updatedAt = now)), changed)
    }

    @Test
    fun dueNowForVerbs() {
        val verbs = listOf(verb("take", 5).copy(lastReviewedAt = 50L), verb("go", 1))

        val changed = LevelReset.verbsDueNow(verbs, setOf("take", "go"), now)

        assertEquals(listOf(verbs[0].copy(lastReviewedAt = null, updatedAt = now)), changed)
    }
}
