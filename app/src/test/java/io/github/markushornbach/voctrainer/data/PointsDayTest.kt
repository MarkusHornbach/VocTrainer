package io.github.markushornbach.voctrainer.data

import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.AvatarPlan
import io.github.markushornbach.voctrainer.learning.FigureRules
import io.github.markushornbach.voctrainer.learning.Points
import io.github.markushornbach.voctrainer.learning.WordCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PointsDayTest {
    /** Writes [changed] over [rows] like the repository does (by date and device). */
    private fun write(rows: List<PointsDay>, changed: List<PointsDay>): List<PointsDay> {
        val all = rows.associateBy { it.date to it.deviceId }.toMutableMap()
        changed.forEach { all[it.date to it.deviceId] = it }
        return all.values.sortedWith(compareBy({ it.date }, { it.deviceId }))
    }


    private fun total(rows: List<PointsDay>) = Points.total(rows.map { it.toRecord() })

    @Test
    fun syncRoundTripKeepsPointsDays() {
        val days = listOf(
            PointsDay("2026-10-08", "phone", risePoints = 0, dailyBonus = 0, startPoints = 120, updatedAt = 50),
            PointsDay("2026-10-09", "phone", risePoints = 9, dailyBonus = 12, startPoints = 0, updatedAt = 100)
        ).map { it.inTestBook() }
        val parsed = VocTrainerJson.parseSync(encodeTestSync(pointsDays = days))
        assertEquals(days, parsed.pointsDays)
    }

    @Test
    fun syncFileWithoutPointsDaysIsStillValid() {
        val parsed = VocTrainerJson.parseSync(
            """{$TEST_SYNC_HEAD, "entries": [],
                "practiceDays": [{"bookId": "b", "date": "2026-10-08", "deviceId": "p", "roundsCompleted": 1, "dailyGoal": 2, "updatedAt": 5}]}"""
        )
        assertTrue(parsed.pointsDays.isEmpty())
        assertEquals(1, parsed.practiceDays.size)
    }

    @Test
    fun rejectsInvalidPointsDays() {
        listOf(
            """{"bookId": "b", "date": "9.10.2026", "deviceId": "p", "risePoints": 1, "updatedAt": 5}""",
            """{"bookId": "b", "date": "2026-10-09", "deviceId": "", "risePoints": 1, "updatedAt": 5}""",
            """{"bookId": "b", "date": "2026-10-09", "deviceId": "p", "risePoints": -1, "updatedAt": 5}""",
            """{"bookId": "b", "date": "2026-10-09", "deviceId": "p", "risePoints": 1, "updatedAt": -5}"""
        ).forEach { day ->
            assertThrows(InvalidFileException::class.java) {
                VocTrainerJson.parseSync("""{$TEST_SYNC_HEAD, "entries": [], "pointsDays": [$day]}""")
            }
        }
        val day = """{"bookId": "b", "date": "2026-10-09", "deviceId": "p", "risePoints": 1, "updatedAt": 5}"""
        assertThrows(InvalidFileException::class.java) {
            VocTrainerJson.parseSync("""{$TEST_SYNC_HEAD, "entries": [], "pointsDays": [$day, $day]}""")
        }
    }

    @Test
    fun theTestAppReadsBackNegativeRisePoints() {
        val day = """{"bookId": "b", "date": "2026-10-09", "deviceId": "p", "risePoints": -40, "updatedAt": 5}"""
        val json = """{$TEST_SYNC_HEAD, "entries": [], "pointsDays": [$day]}"""
        assertEquals(-40, VocTrainerJson.parseSync(json, allowNegativeRisePoints = true).pointsDays.single().risePoints)
        assertThrows(InvalidFileException::class.java) { VocTrainerJson.parseSync(json) }
    }

    @Test
    fun bookRestartClearsAllPointsAndTheNewAvatarStartsAtStageZero() {
        val rows = listOf(
            PointsDay("2026-10-08", "phone", startPoints = 120, updatedAt = 50),
            PointsDay("2026-10-09", "phone", risePoints = 9, dailyBonus = 12, updatedAt = 100),
            PointsDay("2026-10-09", "tablet", risePoints = 4, dailyBonus = 12, updatedAt = 200),
            PointsDay("2026-10-10", "phone", updatedAt = 300)
        )
        val cleared = PointsDayReset.cleared(rows, now = 1_000)
        // Rows without points are left alone; the others are rewritten with a newer updatedAt.
        assertEquals(3, cleared.size)
        assertTrue(cleared.all { it.updatedAt == 1_000L })
        val after = write(rows, cleared)
        assertEquals(0, total(after))

        val old = listOf(Figure(0, "dragon", pointsAtStart = 0, startedAt = 1, updatedAt = 1, stage = 5))
        val figure = old.startNext(FigureKind.MONSTER, total(after), now = 1_000, askable = 1_000, slotStart = BookRestart.figureSlotStart(old))!!
        assertEquals(0, figure.stage)
        assertEquals(0, figure.pointsAtStart)
        // Derived value: the progress to the next stage starts from 0 points.
        val next = FigureRules.nextStage(figure.stage, FigureRules.pointsSinceStart(total(after), figure.pointsAtStart), WordCounts(0, 1_000), 1)!!
        assertEquals(AvatarPlan(1, 1_000).requirement(1).points, next.missingPoints)
    }

    @Test
    fun resettingTwiceWritesNothingTheSecondTime() {
        val rows = listOf(PointsDay("2026-10-09", "phone", risePoints = 9, updatedAt = 100))
        assertTrue(PointsDayReset.cleared(write(rows, PointsDayReset.cleared(rows, 500)), 600).isEmpty())
    }
}
