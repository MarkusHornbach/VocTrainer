package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.FigureRules
import io.github.markushornbach.voctrainer.learning.NextStage
import io.github.markushornbach.voctrainer.learning.WordCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FigureTextsTest {
    @Test
    fun pageTexts() {
        assertEquals("Wähle deinen Avatar", FigureTexts.choiceTitle(next = false))
        assertEquals("Wähle deinen nächsten Avatar", FigureTexts.choiceTitle(next = true))
        assertEquals("Dein Drache · Stufe 3 von 10", FigureTexts.stageLine(FigureKind.DRAGON, 3))
        assertEquals("Punkte: 0 · Gelernte Wörter: 0", FigureTexts.pointsAndLearned(0, 0))
        assertEquals("Punkte: 1.234 · Gelernte Wörter: 35", FigureTexts.pointsAndLearned(1_234, 35))
        assertEquals("Punkte: 12.345 · Gelernte Wörter: 1", FigureTexts.pointsAndLearned(12_345, 1))
        assertEquals("Noch 120 Punkte und 5 gelernte Wörter bis Stufe 4", FigureTexts.nextStage(NextStage(4, 120, 5)))
        assertEquals("Noch 120 Punkte bis Stufe 4", FigureTexts.nextStage(NextStage(4, 120, 0)))
        assertEquals("Noch 5 gelernte Wörter bis Stufe 4", FigureTexts.nextStage(NextStage(4, 0, 5)))
        assertEquals("Noch 1 Punkt und 1 gelerntes Wort bis Stufe 2", FigureTexts.nextStage(NextStage(2, 1, 1)))
        assertEquals("Noch 1.050 Punkte bis Stufe 7", FigureTexts.nextStage(NextStage(7, 1_050, 0)))
        assertEquals("Fertig ausgewachsen!", FigureTexts.nextStage(null))
    }

    @Test
    fun homeLineShowsOnlyWhatIsMissing() {
        // Stage 3 needs 300 points and 10 sure words (a big book: full avatar).
        val line = { points: Int, sure: Int, askable: Int ->
            FigureTexts.homeNextStage(FigureRules.nextStage(2, points, WordCounts(sure, askable), 1))
        }
        assertEquals("Stufe 3: noch 100 Punkte", line(200, 10, 1_000))
        assertEquals("Stufe 3: noch 4 gelernte Wörter", line(300, 6, 1_000))
        assertEquals("Stufe 3: noch 1 Punkt, 1 gelerntes Wort", line(299, 9, 1_000))
        // Small vocabulary: 80 % of 5 words = 4 sure words are enough.
        assertNull(line(300, 4, 5))
        assertNull(line(300, 10, 1_000))
        assertEquals("Stufe 7: noch 1.050 Punkte", FigureTexts.homeNextStage(NextStage(7, 1_050, 0)))
        // Last stage reached, or no avatar.
        assertNull(FigureTexts.homeNextStage(FigureRules.nextStage(FigureRules.MAX_STAGE, 9_999, WordCounts(500, 500), 1)))
        assertNull(FigureTexts.homeNextStage(null))
        // The longest realistic line stays short enough for one line below the avatar.
        assertEquals(
            "Stufe 10: noch 9.999 Punkte, 999 gelernte Wörter",
            FigureTexts.homeNextStage(NextStage(10, 9_999, 999))
        )
    }

    @Test
    fun resultScreenTexts() {
        assertEquals("Dein Drache ist gewachsen! Stufe 3 von 10", FigureTexts.grown(FigureKind.DRAGON, 3, finished = false))
        assertEquals(
            "Dein Monster ist fertig ausgewachsen! Es kommt in deine Sammlung.",
            FigureTexts.grown(FigureKind.MONSTER, 10, finished = true)
        )
        assertEquals(
            "Dein Kämpfer ist fertig ausgewachsen! Er kommt in deine Sammlung.",
            FigureTexts.grown(FigureKind.FIGHTER, 10, finished = true)
        )
    }

    @Test
    fun everyFigureHasItsOwnImageForEveryStage() {
        FigureKind.entries.forEach { kind -> assertEquals(11, FigureImages.availableStages(kind)) }
        assertEquals(R.drawable.figure_dragon_0, FigureImages.forStage(FigureKind.DRAGON, 0))
        assertEquals(R.drawable.figure_dragon_5, FigureImages.forStage(FigureKind.DRAGON, 5))
        assertEquals(R.drawable.figure_dragon_10, FigureImages.forStage(FigureKind.DRAGON, 10))
        assertEquals(R.drawable.figure_monster_3, FigureImages.forStage(FigureKind.MONSTER, 3))
        assertEquals(R.drawable.figure_monster_7, FigureImages.forStage(FigureKind.MONSTER, 7))
        assertEquals(R.drawable.figure_fighter_4, FigureImages.forStage(FigureKind.FIGHTER, 4))
        assertEquals(R.drawable.figure_fighter_9, FigureImages.forStage(FigureKind.FIGHTER, 9))
        assertEquals(R.drawable.figure_fighter_10, FigureImages.forStage(FigureKind.FIGHTER, 10))
    }

    @Test
    fun stagesOutsideTheRangeFallBackToTheNearestImage() {
        assertEquals(R.drawable.figure_dragon_0, FigureImages.forStage(FigureKind.DRAGON, -1))
        assertEquals(R.drawable.figure_monster_10, FigureImages.forStage(FigureKind.MONSTER, 11))
    }
}
