package io.github.markushornbach.voctrainer.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FigureWiggleTest {
    @Test
    fun wigglesOnlyAfterARise() {
        assertTrue(FigureWiggle.shouldWiggle(lastShown = 2, stage = 3))
        assertTrue(FigureWiggle.shouldWiggle(lastShown = 0, stage = 2))
        assertFalse(FigureWiggle.shouldWiggle(lastShown = 3, stage = 3))
        assertFalse(FigureWiggle.shouldWiggle(lastShown = 4, stage = 2))
    }

    @Test
    fun aFreshFigureAtStageZeroDoesNotWiggle() {
        assertFalse(FigureWiggle.shouldWiggle(lastShown = 0, stage = 0))
    }
}
