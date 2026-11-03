package io.github.markushornbach.voctrainer.settings

/**
 * UI tests that are not about tips or the first start: every tip done and the first start over, so
 * neither a spotlight nor the welcome covers the screen.
 */
fun AppSettings.markAllTipsDone() {
    setTipsDone(Tip.entries.toSet())
    finishFirstStart()
}

/** Exactly the tips [done] are done, all others are open (through "Tipps wieder zeigen" and "Verstanden"). */
fun AppSettings.setTipsDone(done: Set<Tip>) {
    resetTips()
    done.forEach(::tipUnderstood)
}
