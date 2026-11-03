package io.github.markushornbach.voctrainer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstStartTest {
    @Test
    fun withoutTipsEveryTipIsDone() {
        val state = FirstStart.withoutTips()
        assertEquals(Tip.entries.toSet(), state.done)
        assertEquals(9, state.done.size)
        assertTrue(Tip.entries.none { state.isDue(it, triggered = true) })
    }
}
