package io.github.markushornbach.voctrainer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactHeightTest {
    @Test
    fun smallPhonesAreCompact() {
        assertTrue(isCompactHeight(683f)) // Nexus 5X without the system bars
        assertTrue(isCompactHeight(731f)) // Nexus 5X
        assertTrue(isCompactHeight(COMPACT_HEIGHT_THRESHOLD_DP - 0.5f))
    }

    @Test
    fun usualPhonesAreNotCompact() {
        assertFalse(isCompactHeight(COMPACT_HEIGHT_THRESHOLD_DP.toFloat()))
        assertFalse(isCompactHeight(915f)) // Pixel 8a
        assertFalse(isCompactHeight(923f)) // Pixel 10
    }

    @Test
    fun scrollsJustFarEnoughForTheFeedbackLines() {
        // The lines end 30 px below the upper part.
        assertEquals(30, compactCopyScrollTarget(current = 0, linesBottom = 530f, viewportBottom = 500f, max = 100))
        // Everything is in view: no scrolling, and back up if it was scrolled further than needed.
        assertEquals(0, compactCopyScrollTarget(current = 0, linesBottom = 400f, viewportBottom = 500f, max = 100))
        assertEquals(10, compactCopyScrollTarget(current = 40, linesBottom = 470f, viewportBottom = 500f, max = 100))
    }

    @Test
    fun scrollTargetStaysInsideTheScrollRange() {
        assertEquals(100, compactCopyScrollTarget(current = 90, linesBottom = 600f, viewportBottom = 500f, max = 100))
        assertEquals(0, compactCopyScrollTarget(current = 10, linesBottom = 300f, viewportBottom = 500f, max = 100))
    }

    @Test
    fun theLabelNamesTheAskedVerbForm() {
        assertEquals(VerbStepTexts.SIMPLE_PAST_LABEL, verbFormLabel("Wie lautet das simple past?", inSecondStep = false))
        assertEquals(VerbStepTexts.SIMPLE_PAST_LABEL, verbFormLabel("Wie lauten die Formen?", inSecondStep = false))
        assertEquals(VerbStepTexts.PAST_PARTICIPLE_LABEL, verbFormLabel("Wie lauten die Formen?", inSecondStep = true))
    }

    @Test
    fun vocabularyQuestionsHaveNoLabel() {
        assertNull(verbFormLabel(null, inSecondStep = false))
    }
}
