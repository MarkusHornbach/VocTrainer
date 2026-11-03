package io.github.markushornbach.voctrainer.data

import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.WordCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FigureTest {
    private val plenty = WordCounts(sure = 1_000, askable = 2_000)

    private fun figure(slot: Int, stage: Int, updatedAt: Long, kind: FigureKind = FigureKind.DRAGON, finishedAt: Long? = null) =
        Figure(slot, kind.key, pointsAtStart = 0, stage = stage, startedAt = 1L, finishedAt = finishedAt, updatedAt = updatedAt)

    @Test
    fun theFirstFigureStartsWithZeroPointsTheNextWithTheTotal() {
        val first = emptyList<Figure>().startNext(FigureKind.MONSTER, totalPoints = 245, now = 10L, askable = 2_000, slotStart = 0)!!
        assertEquals(0, first.slot)
        assertEquals(0, first.pointsAtStart)
        assertEquals("monster", first.kind)
        assertNull(listOf(first).startNext(FigureKind.DRAGON, 245, 11L, 2_000, slotStart = 0))

        val finished = first.copy(stage = 10, finishedAt = 20L)
        val second = listOf(finished).startNext(FigureKind.MONSTER, totalPoints = 4_700, now = 30L, askable = 2_000, slotStart = 0)!!
        assertEquals(1, second.slot)
        assertEquals(4_700, second.pointsAtStart)
        assertEquals(second, listOf(finished, second).current(0))
        assertEquals(listOf(finished), listOf(finished, second).collection())
    }

    @Test
    fun theAvatarNumberCountsFinishedFiguresOnly() {
        val finished = figure(0, stage = 10, updatedAt = 5L, finishedAt = 5L)
        val givenUp = figure(1, stage = 4, updatedAt = 6L)
        val growing = figure(2, stage = 1, updatedAt = 7L)
        assertEquals(1, emptyList<Figure>().avatarPlan(100, 0).number)
        assertEquals(2, listOf(finished).avatarPlan(100, 0).number)
        assertEquals(2, listOf(finished, givenUp, growing).avatarPlan(100, 0).number)
        assertEquals(3, listOf(finished, figure(1, 10, 6L, finishedAt = 6L), givenUp).avatarPlan(100, 0).number)
    }

    @Test
    fun aNewAvatarIsRefusedWhileThereAreNotEnoughNewWords() {
        val finished = listOf(figure(0, stage = 10, updatedAt = 5L, finishedAt = 5L))
        // cap 449, base 300: only 149 new words are left.
        assertNull(finished.startNext(FigureKind.DRAGON, 5_000, 9L, askable = 562, slotStart = 0))
        // cap 450: 150 new words are enough.
        assertEquals(1, finished.startNext(FigureKind.DRAGON, 5_000, 9L, askable = 563, slotStart = 0)!!.slot)
        // The first avatar is always allowed, also in a tiny book.
        assertEquals(0, emptyList<Figure>().startNext(FigureKind.DRAGON, 0, 9L, askable = 3, slotStart = 0)!!.slot)
    }

    /** "Buch neu beginnen": the figures below the slot start belong to an earlier start of the book. */
    @Test
    fun figuresOfAnEarlierStartAreNeverCurrentAndDoNotCount() {
        val finished = figure(0, stage = 10, updatedAt = 5L, finishedAt = 5L)
        val growing = figure(1, stage = 6, updatedAt = 7L)
        val figures = listOf(finished, growing)
        val slotStart = BookRestart.figureSlotStart(figures)
        assertEquals(2, slotStart)

        // The old unfinished figure is not current; the learner chooses again.
        assertNull(figures.current(slotStart))
        // The old finished figure stays in the collection but does not count: avatar 1 with base 0 again.
        assertEquals(listOf(finished), figures.collection())
        val plan = figures.avatarPlan(2_000, slotStart)
        assertEquals(1, plan.number)
        assertEquals(0, plan.base)

        // The new avatar takes the slot after the highest one and is the first of this start (0 points at start).
        val next = figures.startNext(FigureKind.FIGHTER, totalPoints = 0, now = 9L, askable = 2_000, slotStart = slotStart)!!
        assertEquals(2, next.slot)
        assertEquals(0, next.pointsAtStart)
        val after = figures + next
        assertEquals(next, after.current(slotStart))
        // Finished later: it counts, the old one still does not.
        val nextFinished = next.copy(stage = 10, finishedAt = 20L)
        assertEquals(2, (figures + nextFinished).avatarPlan(2_000, slotStart).number)
        assertEquals(listOf(finished, nextFinished), (figures + nextFinished).collection())
        // The second avatar of this start begins with the total points.
        val second = (figures + nextFinished).startNext(FigureKind.DRAGON, totalPoints = 4_400, now = 30L, askable = 2_000, slotStart = slotStart)!!
        assertEquals(3, second.slot)
        assertEquals(4_400, second.pointsAtStart)
    }

    @Test
    fun aRestartedBookWithoutFiguresStartsAtSlotZeroAndTheNextSlotNeverGoesBelowTheStart() {
        assertEquals(0, BookRestart.figureSlotStart(emptyList()))
        // Test app "Avatar zurücksetzen" deletes the figures, the slot start stays: the next figure is still current.
        val next = emptyList<Figure>().startNext(FigureKind.DRAGON, 0, 1L, askable = 100, slotStart = 3)!!
        assertEquals(3, next.slot)
        assertEquals(next, listOf(next).current(3))
    }

    /** Given up: a lower unfinished slot (possible in a backup). */
    @Test
    fun aGivenUpFigureNeverComesBack() {
        val givenUp = figure(0, stage = 4, updatedAt = 10L)
        val finished = figure(1, stage = 10, updatedAt = 90L, kind = FigureKind.MONSTER, finishedAt = 90L)
        assertNull(listOf(givenUp, finished).current(0))
        assertEquals(listOf(finished), listOf(givenUp, finished).collection())
        // The next figure takes the slot after both.
        assertEquals(2, listOf(givenUp, finished).startNext(FigureKind.DRAGON, 5_000, 100L, 2_000, slotStart = 0)!!.slot)
    }

    @Test
    fun reachingStageTenFinishesTheFigure() {
        val growing = figure(0, stage = 9, updatedAt = 5L)
        assertNull(growing.grown(3_300, plenty, 1, now = 50L))
        val finished = growing.grown(4_300, plenty, 1, now = 60L)!!
        assertEquals(10, finished.stage)
        assertEquals(60L, finished.finishedAt)
        assertTrue(finished.isFinished)
        assertNull(listOf(finished).current(0))
    }

    @Test
    fun aStageIsNeverLoweredByGrowing() {
        assertNull(figure(0, stage = 5, updatedAt = 5L).grown(0, WordCounts(0, 0), 1, now = 9L))
    }

    @Test
    fun syncRoundTripKeepsFigures() {
        val figures = listOf(
            figure(0, stage = 10, updatedAt = 50L, finishedAt = 40L),
            figure(1, stage = 2, updatedAt = 60L, kind = FigureKind.FIGHTER).copy(pointsAtStart = 4_400)
        ).map { it.inTestBook() }
        val parsed = VocTrainerJson.parseSync(encodeTestSync(figures = figures))
        assertEquals(figures, parsed.figures)
    }

    @Test
    fun syncFileWithoutFiguresIsValidAndUnknownKindsAreSkipped() {
        val old = VocTrainerJson.parseSync("""{$TEST_SYNC_HEAD, "entries": []}""")
        assertTrue(old.figures.isEmpty())

        val withUnknown = VocTrainerJson.parseSync(
            """{$TEST_SYNC_HEAD, "entries": [], "figures": [
                {"bookId": "b", "slot": 0, "kind": "unicorn", "pointsAtStart": 0, "stage": 1, "startedAt": 1, "updatedAt": 2},
                {"bookId": "b", "slot": 1, "kind": "dragon", "pointsAtStart": 5, "stage": 0, "startedAt": 1, "updatedAt": 2}]}"""
        )
        assertEquals(listOf(1), withUnknown.figures.map { it.slot })
    }

    @Test
    fun rejectsInvalidFigures() {
        listOf(
            """{"bookId": "b", "slot": -1, "kind": "dragon", "pointsAtStart": 0, "stage": 0, "startedAt": 1, "updatedAt": 2}""",
            """{"bookId": "b", "slot": 0, "kind": "dragon", "pointsAtStart": 0, "stage": 11, "startedAt": 1, "updatedAt": 2}""",
            """{"bookId": "b", "slot": 0, "kind": "dragon", "pointsAtStart": -3, "stage": 0, "startedAt": 1, "updatedAt": 2}"""
        ).forEach { figure ->
            assertThrowsInvalid("""{$TEST_SYNC_HEAD, "entries": [], "figures": [$figure]}""")
        }
        val figure = """{"bookId": "b", "slot": 0, "kind": "dragon", "pointsAtStart": 0, "stage": 0, "startedAt": 1, "updatedAt": 2}"""
        assertThrowsInvalid("""{$TEST_SYNC_HEAD, "entries": [], "figures": [$figure, $figure]}""")
    }

    private fun assertThrowsInvalid(json: String) {
        org.junit.Assert.assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseSync(json) }
    }

    @Test
    fun withStageSetsTheStageAndFinishesAtTheLastStage() {
        val growing = figure(slot = 0, stage = 3, updatedAt = 5L)
        assertEquals(figure(0, stage = 7, updatedAt = 50L), growing.withStage(7, now = 50L))
        assertEquals(figure(0, stage = 1, updatedAt = 50L), growing.withStage(1, now = 50L))

        val finished = growing.withStage(10, now = 50L)
        assertEquals(10, finished.stage)
        assertEquals(50L, finished.finishedAt)
        assertNull(listOf(finished).current(0))
        assertEquals(listOf(finished), listOf(finished).collection())
    }
}
