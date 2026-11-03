package io.github.markushornbach.voctrainer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.markushornbach.voctrainer.settings.Tip

/**
 * The rule "at most one tip per visit of the start screen": the tip of [Tip.ON_START_SCREEN] shown
 * in this visit, if any. A visit ends with [end] (the start screen is left, or the selection screen
 * is opened or closed). Dialogs and an activity recreation do not end it, so the holder lives in
 * the [QuizViewModel], not in the composition.
 */
class StartVisit {
    /** The start screen tip shown in this visit; null = none yet. Observable by the tip host. */
    var shown by mutableStateOf<Tip?>(null)
        private set

    /** Tips asked for by "Tipp sofort zeigen" (test app): they ignore the limit until the visit ends. */
    private var forced by mutableStateOf(emptySet<Tip>())

    /**
     * Whether [tip] may be shown now. Tips off the start screen are never limited; the tip that
     * was shown in this visit stays allowed (it is still open, or comes back after a rotation), and
     * so does its [Tip.CHAIN] successor once it is in [done] ("Verstanden"). [limit] false switches
     * the rule off (test app).
     */
    fun allows(tip: Tip, done: Set<Tip> = emptySet(), limit: Boolean = true): Boolean =
        !limit || tip !in Tip.ON_START_SCREEN || tip in forced || shown == null || shown == tip ||
            chainsTo(tip, done)

    private fun chainsTo(tip: Tip, done: Set<Tip>): Boolean {
        val first = shown ?: return false
        return Tip.CHAIN[first] == tip && first in done
    }

    /** [tip] is on screen now; a chained tip takes over the visit. */
    fun record(tip: Tip, done: Set<Tip> = emptySet()) {
        if (tip in Tip.ON_START_SCREEN && tip !in forced && (shown == null || chainsTo(tip, done))) shown = tip
    }

    fun force(tip: Tip) {
        forced = forced + tip
    }

    /** The visit is over: the next start screen may show one tip again. */
    fun end() {
        if (shown != null) shown = null
        if (forced.isNotEmpty()) forced = emptySet()
    }
}
