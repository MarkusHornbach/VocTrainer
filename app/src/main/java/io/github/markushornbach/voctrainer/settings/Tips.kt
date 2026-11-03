package io.github.markushornbach.voctrainer.settings

/**
 * The one-time spotlight hints ("Tipp"). [key] is stored (settings, backup) and never changes;
 * the order of the entries is the priority when several tips are due at the same time. [id] is the
 * short catalogue id, [shortName] is for the test tools only.
 */
enum class Tip(val key: String, val id: String, val shortName: String) {
    SELECTION("selection", "N1", "Welche Wörter?"),
    START_BUTTON("start_button", "N2", "Los geht's"),
    VERBS("verbs", "Verben", "Verben üben"),
    TYPING("typing", "Tippen", "Selbst tippen"),
    REPEAT("repeat", "Wiederholung", "Wiederholung"),
    ROUND_RESULT("round_result", "N3", "Das hast du geschafft"),
    LEVELS("levels", "Npre4", "Dein Lernstand"),
    AVATAR_CARD("avatar_card", "N4", "Dein Avatar"),
    ALL_KNOWN("all_known", "N5", "Jetzt kennst du alles Wichtige");

    companion object {
        /** Unknown keys (e.g. from a newer version) give null and are ignored. */
        fun fromKey(key: String?): Tip? = entries.firstOrNull { it.key == key }

        /**
         * The tips whose target is on the start screen: at most one of them per visit. [VERBS] is not
         * one of them: it answers the learner's own tap and neither uses up nor waits for the visit's tip.
         */
        val ON_START_SCREEN = setOf(SELECTION, START_BUTTON, LEVELS, AVATAR_CARD, ALL_KNOWN)

        /** The tips placed in a question: they must not take the focus from the input field. */
        val IN_QUESTION = setOf(TYPING, REPEAT)

        /**
         * The one exception to "one tip per visit": once the key tip is closed with "Verstanden", its
         * value comes at once in the same visit.
         */
        val CHAIN = mapOf(LEVELS to AVATAR_CARD)
    }
}

/**
 * Which tips are done. A tip is due if it is not [done] and its trigger holds (decided by the
 * screen that shows its target), or if it is [forced] ("Tipp sofort zeigen" of the test app; not
 * stored).
 */
data class TipState(val done: Set<Tip> = emptySet(), val forced: Set<Tip> = emptySet()) {
    fun isDue(tip: Tip, triggered: Boolean): Boolean = tip in forced || (tip !in done && triggered)

    /**
     * "Verstanden": done, and a forced showing is used up. A forced tip with a [Tip.CHAIN] successor
     * forces that one, so "Tipp sofort zeigen" shows the chain as well.
     */
    fun understood(tip: Tip): TipState {
        val chained = Tip.CHAIN[tip]?.takeIf { tip in forced }
        return copy(done = done + tip, forced = forced - tip + listOfNotNull(chained))
    }

    /** "Tipp sofort zeigen": due at the next occasion, also when done. */
    fun forcedNow(tip: Tip): TipState = copy(forced = forced + tip)

    /**
     * A round was started with "Los geht's": the tip on that button is no longer needed, so it counts
     * as done if it has not been shown yet.
     */
    fun roundStarted(): TipState = if (Tip.START_BUTTON in done) this else understood(Tip.START_BUTTON)

    companion object {
        /**
         * The tip to show among [targets] (tip → its trigger holds), by priority; null = none.
         * [blocked] tips wait (dialog open, round running, …).
         */
        fun next(state: TipState, targets: Map<Tip, Boolean>, blocked: (Tip) -> Boolean = { false }): Tip? =
            Tip.entries.firstOrNull { tip ->
                val triggered = targets[tip] ?: return@firstOrNull false
                state.isDue(tip, triggered) && !blocked(tip)
            }
    }
}

/** The first start (welcome, book choice, avatar choice) of a new installation. */
object FirstStart {
    /** "Ohne Tipps starten": every tip done. */
    fun withoutTips(): TipState = TipState(done = Tip.entries.toSet())
}
