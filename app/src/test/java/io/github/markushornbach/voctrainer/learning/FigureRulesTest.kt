package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class FigureRulesTest {
    @Test
    fun onlySureWordsMissingMeansAFullBarWithALock() {
        assertTrue(NextStage(4, missingPoints = 0, missingSureWords = 3).onlySureWordsMissing)
        assertFalse(NextStage(4, missingPoints = 10, missingSureWords = 3).onlySureWordsMissing)
        assertFalse(NextStage(4, missingPoints = 0, missingSureWords = 0).onlySureWordsMissing)
        assertFalse(NextStage(4, missingPoints = 10, missingSureWords = 0).onlySureWordsMissing)
    }

    private val plenty = WordCounts(sure = 1_000, askable = 2_000)
    private val full = AvatarPlan(1, 2_000)

    @Test
    fun eachPointThresholdIsAnEdge() {
        val points = listOf(30, 120, 300, 550, 900, 1_300, 1_800, 2_500, 3_300, 4_300)
        points.forEachIndexed { index, needed ->
            val stage = index + 1
            assertEquals("stage $stage reached", stage, FigureRules.computedStage(needed, plenty, 1))
            assertEquals("stage $stage missed by 1 point", stage - 1, FigureRules.computedStage(needed - 1, plenty, 1))
        }
        assertEquals(0, FigureRules.computedStage(0, plenty, 1))
        assertEquals(10, FigureRules.computedStage(100_000, plenty, 1))
    }

    @Test
    fun eachSureWordThresholdIsAnEdge() {
        val sure = listOf(0, 0, 10, 25, 50, 80, 120, 170, 230, 300)
        sure.forEachIndexed { index, needed ->
            val stage = index + 1
            if (needed == 0) return@forEachIndexed
            assertEquals(stage, FigureRules.computedStage(100_000, WordCounts(needed, 2_000), 1))
            assertEquals(stage - 1, FigureRules.computedStage(100_000, WordCounts(needed - 1, 2_000), 1))
        }
    }

    @Test
    fun stagesOneAndTwoNeedNoSureWords() {
        assertEquals(2, FigureRules.computedStage(299, WordCounts(0, 500), 1))
        assertEquals(2, FigureRules.computedStage(10_000, WordCounts(9, 500), 1))
    }

    @Test
    fun sureWordsAreCappedAtEightyPercentOfASmallVocabulary() {
        // 20 askable words: cap 16, scale 0.1 (minimum): stage 3 needs ceil(1) = 1, stage 10 min(30, 16) = 16.
        assertEquals(1, AvatarPlan(1, 20).requirement(3).sureWords)
        assertEquals(16, AvatarPlan(1, 20).requirement(10).sureWords)
        assertEquals(10, FigureRules.computedStage(100_000, WordCounts(16, 20), 1))
        assertEquals(7, FigureRules.computedStage(100_000, WordCounts(15, 20), 1))
        // floor(0.8 × 7) = 5
        assertEquals(5, FigureRules.sureWordCap(7))
        assertEquals(0, AvatarPlan(1, 0).requirement(3).sureWords)
    }

    @Test
    fun aReachedStageIsNeverLowered() {
        assertEquals(6, FigureRules.newStage(6, pointsSinceStart = 0, counts = WordCounts(0, 10), number = 1))
        assertEquals(7, FigureRules.newStage(6, pointsSinceStart = 1_800, counts = plenty, number = 1))
        assertEquals(10, FigureRules.newStage(10, pointsSinceStart = 0, counts = plenty, number = 1))
    }

    @Test
    fun pointsSinceStartCountFromTheFiguresStart() {
        assertEquals(1_234, FigureRules.pointsSinceStart(1_234, 0))
        assertEquals(200, FigureRules.pointsSinceStart(4_700, 4_500))
        assertEquals(0, FigureRules.pointsSinceStart(100, 300))
    }

    @Test
    fun nextStageLeavesOutWhatIsFulfilled() {
        assertEquals(NextStage(4, missingPoints = 120, missingSureWords = 5), FigureRules.nextStage(3, 430, WordCounts(20, 2_000), 1))
        assertEquals(NextStage(4, missingPoints = 0, missingSureWords = 5), FigureRules.nextStage(3, 600, WordCounts(20, 2_000), 1))
        assertEquals(NextStage(1, missingPoints = 30, missingSureWords = 0), FigureRules.nextStage(0, 0, WordCounts(0, 2_000), 1))
        assertNull(FigureRules.nextStage(10, 0, plenty, 1))
    }

    @Test
    fun pointsProgressCountsFromTheCurrentStage() {
        // Stage 0 -> 1: 15 of 30 points.
        assertEquals(0.5f, FigureRules.pointsProgress(0, 15, full), 0.0001f)
        // Stage 2 -> 3: points 120..300, 200 = 80 of 180 (sure words do not matter).
        assertEquals(80f / 180f, FigureRules.pointsProgress(2, 200, full), 0.0001f)
        // Stage 3 -> 4: points 300..550, 425 = 0.5.
        assertEquals(0.5f, FigureRules.pointsProgress(3, 425, full), 0.0001f)
    }

    @Test
    fun pointsProgressClamps() {
        // Fewer points than the current stage (stage kept although points were not reached).
        assertEquals(0f, FigureRules.pointsProgress(3, 100, full), 0.0001f)
        // Points complete while sure words lag behind: full.
        assertEquals(1f, FigureRules.pointsProgress(3, 600, full), 0.0001f)
    }

    @Test
    fun progressIsFullAtTheLastStage() {
        assertEquals(1f, FigureRules.pointsProgress(10, 0, full), 0.0001f)
    }

    @Test
    fun countsSureAskableVocabularyIncludingPhrases() {
        val words = listOf(
            word("1", "bus", "Bus", level = 4),
            word("2", "car", "Auto", level = 5),
            word("3", "train", "Zug", level = 3),
            word("4", "Nice to meet you.", "Freut mich.", level = 5, type = WordType.PHRASE),
            word("5", "boat", "Boot", level = 0)
        )
        assertEquals(WordCounts(sure = 3, askable = 5), FigureRules.countWords(words))
    }

    @Test
    fun kindsHaveStableKeysAndTexts() {
        assertEquals(listOf("dragon", "monster", "fighter"), FigureKind.entries.map { it.key })
        assertEquals(FigureKind.MONSTER, FigureKind.fromKey("monster"))
        assertNull(FigureKind.fromKey("unicorn"))
        assertEquals("Den Drachen nehmen?", FigureKind.DRAGON.choiceQuestion)
    }

    // --- Avatar model: every avatar needs its own words ---

    @Test
    fun aFullFirstAvatarEqualsTheStageTable() {
        val plan = AvatarPlan(1, 375)
        assertEquals(300, plan.cap)
        assertEquals(1.0, plan.scale, 0.0)
        FigureRules.STAGES.forEachIndexed { index, full -> assertEquals(full, plan.requirement(index + 1)) }
    }

    @Test
    fun laterAvatarsStartWhereTheEarlierOnesEnded() {
        val plan = AvatarPlan(2, 800)
        assertEquals(640, plan.cap)
        assertEquals(300, plan.base)
        assertEquals(1.0, plan.scale, 0.0)
        assertEquals(StageRequirement(300, 310), plan.requirement(3))
        assertEquals(StageRequirement(900, 350), plan.requirement(5))
        assertEquals(StageRequirement(4_300, 600), plan.requirement(10))
        // Stages 1 and 2 stay free, also for a later avatar.
        assertEquals(StageRequirement(30, 0), plan.requirement(1))
        assertEquals(StageRequirement(120, 0), plan.requirement(2))
        assertEquals(2, FigureRules.computedStage(10_000, WordCounts(0, 800), 2))
        assertEquals(2, FigureRules.computedStage(10_000, WordCounts(309, 800), 2))
        assertEquals(3, FigureRules.computedStage(10_000, WordCounts(310, 800), 2))
    }

    @Test
    fun theThirdAvatarOf800EntriesGetsTheRest() {
        // cap 640, base 600: 40 words are left, scale 40/300.
        val plan = AvatarPlan(3, 800)
        assertEquals(40, plan.available)
        assertEquals(40.0 / 300, plan.scale, 1e-9)
        assertEquals(StageRequirement(573, 640), plan.requirement(10))
        assertEquals(40, plan.requirement(3).points)
        assertEquals(602, plan.requirement(3).sureWords)
        assertFalse(plan.canStart)
    }

    @Test
    fun aSmallBookShrinksWordsAndPointsInTheSameRatio() {
        val plan = AvatarPlan(1, 150)
        assertEquals(120, plan.cap)
        assertEquals(0.4, plan.scale, 1e-9)
        assertEquals(StageRequirement(1_720, 120), plan.requirement(10))
        assertEquals(12, plan.requirement(1).points)
        assertEquals(4, plan.requirement(3).sureWords)
        assertEquals(0, plan.requirement(2).sureWords)
    }

    @Test
    fun aPartialLastAvatarScalesWithWhatIsLeft() {
        // 700 entries: cap 560; avatar 2 has 260 words left.
        val plan = AvatarPlan(2, 700)
        assertEquals(260, plan.available)
        assertEquals(StageRequirement(3_727, 560), plan.requirement(10))
        assertEquals(StageRequirement(260, 309), plan.requirement(3))
        assertTrue(plan.canStart)
    }

    @Test
    fun theScaleNeverFallsBelowTheMinimum() {
        val plan = AvatarPlan(4, 100)
        assertEquals(FigureRules.MIN_SCALE, plan.scale, 1e-9)
        assertEquals(430, plan.requirement(10).points)
        assertEquals(80, plan.cap)
    }

    @Test
    fun pointsGrowStrictlyAndWordsNeverShrinkOrExceedTheCap() {
        for (number in 1..4) for (askable in listOf(0, 1, 5, 20, 150, 563, 800, 2_000)) {
            val plan = AvatarPlan(number, askable)
            val rows = (1..10).map { plan.requirement(it) }
            rows.zipWithNext().forEach { (a, b) ->
                assertTrue("points k=$number n=$askable", b.points > a.points)
                assertTrue("words k=$number n=$askable", b.sureWords >= a.sureWords)
            }
            rows.forEach { assertTrue("cap k=$number n=$askable", it.sureWords <= plan.cap) }
        }
    }

    @Test
    fun theStoredStageIsNotLoweredWhenTheRequirementsMoveAway() {
        // Stage 6 needs 80 words in a big book; the learner has 60 (and reached stage 6 earlier in a smaller book).
                assertTrue(FigureRules.computedStage(1_300, WordCounts(60, 2_000), 1) < 6)
        assertEquals(6, FigureRules.newStage(6, 1_300, WordCounts(60, 2_000), 1))
    }

    @Test
    fun aNewAvatarNeedsEnoughNewWords() {
        assertTrue(AvatarPlan(1, 0).canStart)
        assertFalse(AvatarPlan(2, 562).canStart)
        assertTrue(AvatarPlan(2, 563).canStart)
        assertFalse(AvatarPlan(3, 937).canStart)
        assertTrue(AvatarPlan(3, 938).canStart)
    }

    @Test
    fun avatarNumberIsFinishedFiguresPlusOne() {
        assertEquals(1, FigureRules.avatarNumber(0))
        assertEquals(3, FigureRules.avatarNumber(2))
    }
}
