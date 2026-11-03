package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.settings.Tip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartVisitTest {
    @Test
    fun onlyOneStartScreenTipPerVisit() {
        val visit = StartVisit()
        assertTrue(visit.allows(Tip.AVATAR_CARD))
        visit.record(Tip.AVATAR_CARD)
        // The tip on screen stays allowed (rotation, fade out); every other start screen tip waits.
        assertTrue(visit.allows(Tip.AVATAR_CARD))
        for (other in Tip.ON_START_SCREEN - Tip.AVATAR_CARD) assertFalse(other.id, visit.allows(other))
    }

    @Test
    fun aSecondTipCannotTakeOverTheVisit() {
        val visit = StartVisit()
        visit.record(Tip.AVATAR_CARD)
        visit.record(Tip.ALL_KNOWN)
        assertEquals(Tip.AVATAR_CARD, visit.shown)
    }

    /** N4 and N5: the closing tip waits for the next visit after the avatar tip. */
    @Test
    fun theNextVisitShowsTheNextTipByPriority() {
        val visit = StartVisit()
        var due = setOf(Tip.AVATAR_CARD, Tip.ALL_KNOWN)
        val shownPerVisit = mutableListOf<Tip>()
        repeat(2) {
            val tip = Tip.entries.first { it in due && visit.allows(it) }
            visit.record(tip)
            assertTrue((due - tip).none { visit.allows(it) })
            shownPerVisit += tip
            due -= tip
            visit.end()
        }
        assertEquals(listOf(Tip.AVATAR_CARD, Tip.ALL_KNOWN), shownPerVisit)
    }

    @Test
    fun endingTheVisitStartsOver() {
        val visit = StartVisit()
        visit.record(Tip.SELECTION)
        assertFalse(visit.allows(Tip.START_BUTTON))
        // The selection screen is opened or closed: "Auswahl" → pick words → apply → "Los geht's".
        visit.end()
        assertNull(visit.shown)
        assertTrue(visit.allows(Tip.START_BUTTON))
    }

    @Test
    fun tipsOffTheStartScreenAreNotLimited() {
        val visit = StartVisit()
        visit.record(Tip.SELECTION)
        for (tip in Tip.entries - Tip.ON_START_SCREEN) assertTrue(tip.id, visit.allows(tip))
        // Recording one of them does not use up the visit.
        val fresh = StartVisit()
        fresh.record(Tip.TYPING)
        fresh.record(Tip.ROUND_RESULT)
        assertNull(fresh.shown)
        assertTrue(fresh.allows(Tip.AVATAR_CARD))
    }

    /** "Dein Lernstand" → "Verstanden" → "Dein Avatar" in the same visit; N5 still waits. */
    @Test
    fun theChainShowsTheAvatarTipAfterTheLevelsTipInTheSameVisit() {
        val visit = StartVisit()
        visit.record(Tip.LEVELS)
        // Only dismissed: the avatar tip waits.
        assertFalse(visit.allows(Tip.AVATAR_CARD, done = emptySet()))
        val done = setOf(Tip.LEVELS)
        assertTrue(visit.allows(Tip.AVATAR_CARD, done = done))
        visit.record(Tip.AVATAR_CARD, done)
        assertEquals(Tip.AVATAR_CARD, visit.shown)
        assertTrue(visit.allows(Tip.AVATAR_CARD, done = done + Tip.AVATAR_CARD))
        assertFalse(visit.allows(Tip.ALL_KNOWN, done = done + Tip.AVATAR_CARD))
        // No chain from another tip.
        val other = StartVisit()
        other.record(Tip.SELECTION)
        assertFalse(other.allows(Tip.AVATAR_CARD, done = setOf(Tip.SELECTION, Tip.LEVELS)))
    }

    @Test
    fun theVerbTipNeitherUsesNorWaitsForTheVisit() {
        val visit = StartVisit()
        visit.record(Tip.SELECTION)
        assertTrue(visit.allows(Tip.VERBS))
        val fresh = StartVisit()
        fresh.record(Tip.VERBS)
        assertNull(fresh.shown)
    }

    @Test
    fun theSwitchOfTheTestToolsTurnsTheLimitOff() {
        val visit = StartVisit()
        visit.record(Tip.SELECTION)
        assertFalse(visit.allows(Tip.AVATAR_CARD, limit = true))
        assertTrue(visit.allows(Tip.AVATAR_CARD, limit = false))
    }

    @Test
    fun aTipShownByTheTestToolsIgnoresTheLimitUntilTheVisitEnds() {
        val visit = StartVisit()
        visit.record(Tip.SELECTION)
        visit.force(Tip.ALL_KNOWN)
        assertTrue(visit.allows(Tip.ALL_KNOWN))
        // Showing it does not use up the visit, and the other tips stay limited.
        visit.record(Tip.ALL_KNOWN)
        assertEquals(Tip.SELECTION, visit.shown)
        assertFalse(visit.allows(Tip.START_BUTTON))
        visit.end()
        assertFalse("forced only for this visit", visit.run { record(Tip.AVATAR_CARD); allows(Tip.ALL_KNOWN) })
    }
}
