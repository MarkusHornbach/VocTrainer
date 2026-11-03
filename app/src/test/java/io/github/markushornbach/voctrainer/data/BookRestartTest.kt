package io.github.markushornbach.voctrainer.data

import io.github.markushornbach.voctrainer.learning.Points
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Buch neu beginnen": what is reset, what stays, and when there is nothing to reset. */
class BookRestartTest {
    private val now = 9_000L

    private fun vocabulary(id: String, level: Int, highest: Int = level, deleted: Boolean = false) = Vocabulary(
        id = id, english = id, germanMeanings = listOf("x"), unit = 1, leitnerLevel = level, highestLevel = highest,
        lastReviewedAt = if (level > 0) 50L else null, updatedAt = 100L, isDeleted = deleted
    )

    private fun verb(id: String, level: Int, deleted: Boolean = false) = IrregularVerb(
        id = id, infinitive = id, simplePast = "past", germanMeanings = listOf("x"), unit = 1,
        leitnerLevel = level, lastReviewedAt = if (level > 0) 50L else null, updatedAt = 100L, isDeleted = deleted
    )

    @Test
    fun vocabularyIsNewAgainIncludingIgnoredEntries() {
        val typed = vocabulary("typed", 5).copy(firstTypedCorrectAt = 40L)
        // Ignored entries are stored as isDeleted; they are reset too.
        val entries = listOf(
            typed, vocabulary("highOnly", 0, highest = 3), vocabulary("new", 0),
            vocabulary("reviewedAtZero", 0).copy(lastReviewedAt = 70L), vocabulary("ignored", 4, deleted = true)
        )

        val changed = BookRestart.vocabulary(entries, now)

        assertEquals(listOf("typed", "highOnly", "reviewedAtZero", "ignored"), changed.map { it.id })
        assertTrue(changed.last().isDeleted)
        assertEquals(
            typed.copy(leitnerLevel = 0, highestLevel = 0, lastReviewedAt = null, firstTypedCorrectAt = null, updatedAt = now),
            changed[0]
        )
        assertTrue(changed.all { it.leitnerLevel == 0 && it.highestLevel == 0 && it.lastReviewedAt == null && it.updatedAt == now })
    }

    @Test
    fun deletedVerbsStayUntouched() {
        assertTrue(BookRestart.verbs(listOf(verb("gone", 3, deleted = true)), now).isEmpty())
    }

    @Test
    fun verbsAreNewAgain() {
        val verbs = listOf(verb("take", 4), verb("go", 0), verb("be", 0).copy(lastReviewedAt = 70L))

        val changed = BookRestart.verbs(verbs, now)

        assertEquals(listOf(verbs[0].copy(leitnerLevel = 0, lastReviewedAt = null, updatedAt = now), verbs[2].copy(lastReviewedAt = null, updatedAt = now)), changed)
    }

    @Test
    fun practiceDaysKeepTheirRowsWithoutRounds() {
        val days = listOf(PracticeDay("2026-10-20", "phone", 3, 2, 100L), PracticeDay("2026-10-21", "tablet", 0, 2, 100L))

        val changed = BookRestart.practiceDays(days, now)

        assertEquals(listOf(PracticeDay("2026-10-20", "phone", 0, 2, now)), changed)
    }

    @Test
    fun pointsOfTheBookBecomeZero() {
        val rows = listOf(
            PointsDay("2026-10-20", "phone", risePoints = 30, dailyBonus = 10, updatedAt = 100L),
            PointsDay("2026-10-20", "tablet", startPoints = 120, updatedAt = 100L)
        )
        val written = PointsDayReset.cleared(rows, now)
        assertEquals(0, Points.total(written.map { it.toRecord() }))
        assertTrue(written.all { it.updatedAt == now })
    }

    @Test
    fun theFiguresSoFarMoveToAnEarlierStart() {
        val figures = listOf(
            Figure(0, "dragon", pointsAtStart = 0, stage = 10, startedAt = 1L, finishedAt = 5L, updatedAt = 5L),
            Figure(1, "monster", pointsAtStart = 4_300, stage = 3, startedAt = 6L, updatedAt = 7L)
        )
        assertEquals(2, BookRestart.figureSlotStart(figures))
        assertEquals(0, BookRestart.figureSlotStart(emptyList()))
    }

    /** Every item named on the info page "Buch neu beginnen" is one part that the restart resets. */
    @Test
    fun everyRestartItemIsReset() {
        val growing = Figure(0, "dragon", pointsAtStart = 0, stage = 3, startedAt = 1L, updatedAt = 1L)
        for (item in RestartItem.entries) {
            when (item) {
                RestartItem.LEVELS -> {
                    assertTrue(BookRestart.vocabulary(listOf(vocabulary("a", 3)), 5L).single().leitnerLevel == 0)
                    assertTrue(BookRestart.verbs(listOf(verb("v", 2)), 5L).single().leitnerLevel == 0)
                }
                RestartItem.POINTS -> assertEquals(
                    0,
                    Points.total(PointsDayReset.cleared(listOf(PointsDay("2026-10-20", "phone", risePoints = 5, updatedAt = 1L)), 5L).map { it.toRecord() })
                )
                // The repository deletes all badges of the book (`deleteAllOfActiveBook`); here: they count as progress.
                RestartItem.BADGES -> assertTrue(
                    BookRestart.hasSomethingToReset(emptyList(), emptyList(), emptyList(), emptyList(), listOf(Badge("streak", 1, 1L)), emptyList(), 0)
                )
                RestartItem.STREAK -> assertEquals(
                    0, BookRestart.practiceDays(listOf(PracticeDay("2026-10-20", "phone", 3, 2, 1L)), 5L).single().roundsCompleted
                )
                RestartItem.GROWING_AVATAR -> assertEquals(1, BookRestart.figureSlotStart(listOf(growing)))
            }
        }
    }

    @Test
    fun detectsWhetherThereIsAnythingToReset() {
        fun has(
            vocabulary: List<Vocabulary> = listOf(vocabulary("a", 0)),
            verbs: List<IrregularVerb> = listOf(verb("v", 0)),
            points: List<PointsDay> = emptyList(),
            practice: List<PracticeDay> = emptyList(),
            badges: List<Badge> = emptyList(),
            figures: List<Figure> = emptyList(),
            slotStart: Int = 0
        ) = BookRestart.hasSomethingToReset(vocabulary, verbs, points, practice, badges, figures, slotStart)

        assertFalse(has())
        assertTrue(has(vocabulary = listOf(vocabulary("a", 1))))
        assertTrue(has(vocabulary = listOf(vocabulary("a", 0, highest = 2))))
        // An ignored word would be reset too; a deleted verb would not.
        assertTrue(has(vocabulary = listOf(vocabulary("a", 3, deleted = true))))
        assertFalse(has(verbs = listOf(verb("v", 2, deleted = true))))
        assertTrue(has(verbs = listOf(verb("v", 2))))
        assertTrue(has(points = listOf(PointsDay("2026-10-20", "phone", risePoints = 5, updatedAt = 1L))))
        // Rows with 0 points (e.g. after a restart) are nothing to reset.
        assertFalse(has(points = listOf(PointsDay("2026-10-20", "phone", updatedAt = 1L))))
        assertTrue(has(badges = listOf(Badge("sure_words", 1, 1L))))
        assertTrue(has(practice = listOf(PracticeDay("2026-10-20", "phone", 1, 2, 1L))))
        assertFalse(has(practice = listOf(PracticeDay("2026-10-20", "phone", 0, 2, 1L))))
        val growing = Figure(0, "dragon", pointsAtStart = 0, stage = 0, startedAt = 1L, updatedAt = 1L)
        assertTrue(has(figures = listOf(growing)))
        // An old figure of an earlier start, or only finished figures, are nothing to reset.
        assertFalse(has(figures = listOf(growing), slotStart = 1))
        assertFalse(has(figures = listOf(growing.copy(stage = 10, finishedAt = 2L))))
    }

    @Test
    fun theSlotStartIsWrittenAndReadAndMissingMeansZero() {
        val book = testBook.copy(figureSlotStart = 3)
        val parsed = VocTrainerJson.parseSync(VocTrainerJson.encodeSync(listOf(book), emptyList(), emptyList()))
        assertEquals(listOf(book), parsed.books)
        assertTrue(VocTrainerJson.encodeSync(listOf(book), emptyList(), emptyList()).contains("\"figureSlotStart\": 3"))

        // Files without the value: 0.
        assertEquals(0, VocTrainerJson.parseSync("""{$TEST_SYNC_HEAD, "entries": []}""").books.single().figureSlotStart)

        val negative = """{"format": "voctrainer-sync", "version": 5, "books": [{"id": "b", "title": "Buch", "figureSlotStart": -1}], "entries": []}"""
        assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseSync(negative) }
    }
}
