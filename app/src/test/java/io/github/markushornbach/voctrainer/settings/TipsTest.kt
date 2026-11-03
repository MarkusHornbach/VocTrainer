package io.github.markushornbach.voctrainer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TipsTest {
    @Test
    fun keysAndIdsAreStable() {
        assertEquals(
            listOf("N1", "N2", "Verben", "Tippen", "Wiederholung", "N3", "Npre4", "N4", "N5"),
            Tip.entries.map { it.id }
        )
        // The tips kept from the earlier catalog reuse their stored keys, so their status carries over.
        assertEquals(
            listOf("selection", "start_button", "verbs", "typing", "repeat", "round_result", "levels", "avatar_card", "all_known"),
            Tip.entries.map { it.key }
        )
        for (tip in Tip.entries) assertEquals(tip, Tip.fromKey(tip.key))
        assertNull(Tip.fromKey("unknown"))
    }

    @Test
    fun keysOfRemovedTipsAreIgnored() {
        val removed = listOf(
            "progress_card", "streak", "verbs_tile", "direction_de_en", "almost_correct", "learned_word",
            "points", "badges", "avatar_grown", "manage_vocabulary"
        )
        for (key in removed) assertNull(key, Tip.fromKey(key))
    }

    @Test
    fun anOpenTipIsDueOnlyWhenItsTriggerHolds() {
        val state = TipState()
        assertFalse(state.isDue(Tip.AVATAR_CARD, triggered = false))
        assertTrue(state.isDue(Tip.AVATAR_CARD, triggered = true))
    }

    @Test
    fun understoodIsDoneForGood() {
        val state = TipState().understood(Tip.SELECTION)
        assertEquals(setOf(Tip.SELECTION), state.done)
        assertFalse(state.isDue(Tip.SELECTION, triggered = true))
        assertTrue(state.isDue(Tip.START_BUTTON, triggered = true))
    }

    @Test
    fun resetOpensEveryTip() {
        val reset = TipState()
        assertTrue(Tip.entries.all { reset.isDue(it, triggered = true) })
    }

    /** "Tipp sofort zeigen": due without its trigger and although done, until "Verstanden". */
    @Test
    fun aForcedTipComesOnce() {
        val done = TipState().understood(Tip.SELECTION)
        val forced = done.forcedNow(Tip.SELECTION)
        assertTrue(forced.isDue(Tip.SELECTION, triggered = false))
        val understood = forced.understood(Tip.SELECTION)
        assertFalse(understood.isDue(Tip.SELECTION, triggered = true))
        assertTrue(understood.forced.isEmpty())
    }

    @Test
    fun theFirstDueTipByOrderIsShown() {
        val all = mapOf(Tip.AVATAR_CARD to true, Tip.START_BUTTON to true, Tip.SELECTION to true)
        assertEquals(Tip.SELECTION, TipState.next(TipState(), all))
        assertEquals(Tip.START_BUTTON, TipState.next(TipState().understood(Tip.SELECTION), all))
        // Without a trigger, a later tip whose trigger holds comes first.
        assertEquals(Tip.AVATAR_CARD, TipState.next(TipState(), all + (Tip.SELECTION to false) + (Tip.START_BUTTON to false)))
        // A forced tip needs no trigger, but still its target.
        assertEquals(Tip.START_BUTTON, TipState.next(TipState(forced = setOf(Tip.START_BUTTON)), mapOf(Tip.START_BUTTON to false)))
        assertNull(TipState.next(TipState(forced = setOf(Tip.START_BUTTON)), mapOf(Tip.AVATAR_CARD to false)))
    }

    @Test
    fun blockedTipsWait() {
        val all = mapOf(Tip.SELECTION to true, Tip.AVATAR_CARD to true)
        assertNull(TipState.next(TipState(), all) { true })
        assertEquals(Tip.AVATAR_CARD, TipState.next(TipState(), all) { it == Tip.SELECTION })
    }

    @Test
    fun theStartScreenAndQuestionTips() {
        // "Verben üben" answers the learner's tap: not limited by the visit.
        assertEquals(setOf(Tip.SELECTION, Tip.START_BUTTON, Tip.LEVELS, Tip.AVATAR_CARD, Tip.ALL_KNOWN), Tip.ON_START_SCREEN)
        assertEquals(setOf(Tip.TYPING, Tip.REPEAT), Tip.IN_QUESTION)
        assertEquals(mapOf(Tip.LEVELS to Tip.AVATAR_CARD), Tip.CHAIN)
    }

    /** "Tipp sofort zeigen" of "Dein Lernstand": "Verstanden" forces "Dein Avatar" next. */
    @Test
    fun aForcedChainTipForcesItsSuccessor() {
        val forced = TipState().forcedNow(Tip.LEVELS).understood(Tip.LEVELS)
        assertEquals(setOf(Tip.AVATAR_CARD), forced.forced)
        assertTrue(forced.isDue(Tip.AVATAR_CARD, triggered = false))
        // Not forced: only done, nothing else is forced.
        assertTrue(TipState().understood(Tip.LEVELS).forced.isEmpty())
    }

    /** A fresh installation keeps all tips open (see AppSettings). */
    @Test
    fun aFreshInstallationStartsWithAllTipsOpen() {
        val fresh = TipState()
        assertTrue(Tip.entries.all { fresh.isDue(it, triggered = true) })
        assertTrue(Tip.entries.none { fresh.isDue(it, triggered = false) })
    }
}
