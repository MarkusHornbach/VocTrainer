package io.github.markushornbach.voctrainer.learning

/** The figure on the start screen wiggles once after its stage has risen. */
object FigureWiggle {
    /**
     * True if the start screen shows [stage] for the first time after a rise: higher than the
     * stage last shown there ([lastShown], 0 if never shown). A fresh figure at stage 0, the same
     * stage and a lowered stage (test tools) never wiggle.
     */
    fun shouldWiggle(lastShown: Int, stage: Int): Boolean = stage > lastShown
}
