package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.QuizRound
import io.github.markushornbach.voctrainer.learning.RegularVerbs
import io.github.markushornbach.voctrainer.settings.Tip

/** What a tip card shows: emoji, title, a few short lines and an optional second button. */
data class TipContent(
    val emoji: String,
    val title: String,
    val lines: List<String>,
    /** Label of the second button next to "Verstanden" (it also marks the tip done); null = none. */
    val secondary: String? = null,
    /** One line above the [lines], without a bullet; null = none. */
    val lead: String? = null,
    /** The [lines] start with their own sign (✔, ✘), so no bullet. */
    val bullets: Boolean = true
)

/** German texts of the tips ("Tipp") and of "Tipps wieder zeigen". */
object TipTexts {
    const val UNDERSTOOD = "Verstanden"
    const val SHOW_AGAIN = "Tipps wieder zeigen"
    const val SHOWN_AGAIN = "Tipps kommen wieder."

    /** Where "Verbformen" is switched. */
    private const val SWITCH_PLACE = "Einstellungen → Üben"

    /** The card of [tip]; [threeVerbForms]: "Verbformen" is "3 Formen" ("Verben üben"). */
    fun content(tip: Tip, threeVerbForms: Boolean = false): TipContent = when (tip) {
        Tip.SELECTION -> TipContent(
            emoji = "📖",
            title = "Welche Wörter möchtest du lernen?",
            lines = listOf(
                "Auswählen, welche Vokabeln du als nächstes lernen möchtest",
                "Unit, Part, Seiten aus deinem Buch"
            )
        )
        Tip.START_BUTTON -> TipContent(
            emoji = "🚀",
            title = "Los geht's",
            lines = listOf(
                "${QuizRound.ROUND_SIZE} Wörter pro Runde",
                "Erst mit Vorschlägen, später selbst tippen"
            )
        )
        Tip.VERBS -> TipContent(
            emoji = "🔀",
            title = "Verben üben",
            lines = listOf(
                "Hier wechselst du zwischen Vokabeln und Verben.",
                if (threeVerbForms) "Bei Verben übst du Simple Past und Past Participle."
                else "Bei Verben übst du die Vergangenheit (Simple Past).",
                "${QuizRound.ROUND_SIZE} unregelmäßige Verben + ${RegularVerbs.PER_ROUND} regelmäßige Verben, " +
                    "damit du beides lernst. Deshalb ${QuizRound.ROUND_SIZE + RegularVerbs.PER_ROUND} Fragen.",
                if (threeVerbForms) "Nur zwei Formen? Stell es um unter $SWITCH_PLACE."
                else "Lernst du schon drei Formen (go – went – gone)? Stell es um unter $SWITCH_PLACE."
            )
        )
        Tip.TYPING -> TipContent(
            emoji = "⌨️",
            title = "Selbst tippen",
            lines = listOf("Zu schwer? Tippe auf „$CHOICE_BUTTON“ (das Wort fällt zurück)")
        )
        Tip.REPEAT -> repeat(verbs = false)
        Tip.LEVELS -> TipContent(
            emoji = "📈",
            title = "Dein Lernstand",
            lead = "Hier siehst du, auf welchem Level deine Wörter sind.",
            lines = listOf("✔ Antwort richtig: ein Level höher", "✘ Antwort falsch: ein Level zurück", "Aufklappen für mehr Details."),
            bullets = false
        )
        Tip.ROUND_RESULT -> TipContent(
            emoji = "⭐",
            title = "Das hast du geschafft",
            lines = listOf(
                "Aufgestiegen: Wörter, die du jetzt besser kannst",
                "Dafür gibt es Punkte",
                "Punkte lassen deinen Avatar wachsen"
            )
        )
        Tip.AVATAR_CARD -> TipContent(
            emoji = "🐉",
            title = "Dein Avatar",
            lines = listOf("Punkte + gelernte Wörter → nächste Stufe", "Tippe auf ihn, um mehr zu erfahren.")
        )
        Tip.ALL_KNOWN -> TipContent(
            emoji = "🎉",
            title = "Jetzt kennst du alles Wichtige",
            lines = listOf("Alle Erklärungen siehst du hier unter ?", "Tippe auf den Screens auf ⓘ, um mehr zu erfahren.")
        )
    }

    /** The card of [tip] in the current round: "Wiederholung" names verbs in the verb mode. */
    fun content(tip: Tip, verbs: Boolean, threeVerbForms: Boolean = false): TipContent =
        if (tip == Tip.REPEAT) repeat(verbs) else content(tip, threeVerbForms)

    private fun repeat(verbs: Boolean) = TipContent(
        emoji = "🔁",
        title = "Wiederholung",
        lines = listOf(
            "Zum Schluss übst du alle falschen ${if (verbs) "Verben" else "Wörter"} noch einmal.",
            "So merkst du sie dir besser.",
            "Bringt keine Punkte und kein Level."
        )
    )
}
