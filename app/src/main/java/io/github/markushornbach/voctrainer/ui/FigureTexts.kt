package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.FigureKind
import io.github.markushornbach.voctrainer.learning.FigureRules
import io.github.markushornbach.voctrainer.learning.NextStage
import io.github.markushornbach.voctrainer.learning.Reach

/** German texts of the growing avatar (code name: figure). */
object FigureTexts {
    fun choiceTitle(next: Boolean): String = if (next) "Wähle deinen nächsten Avatar" else "Wähle deinen Avatar"

    /** Below the title of every avatar choice. */
    val CHOICE_LINES = listOf(
        "Wächst mit Punkten und gelernten Wörtern",
        "Bleibt, bis er ausgewachsen ist – dann wählst du den nächsten"
    )

    /** "Dein Drache · Stufe 3 von 10". */
    fun stageLine(kind: FigureKind, stage: Int): String =
        "${kind.withArticle} · Stufe $stage von ${FigureRules.MAX_STAGE}"

    /** "Punkte: 1.234 · Gelernte Wörter: 35" (learned words of the whole book). */
    fun pointsAndLearned(total: Int, learned: Int): String =
        "Punkte: ${PracticeTexts.formatNumber(total)} · Gelernte Wörter: ${PracticeTexts.formatNumber(learned)}"

    /**
     * "Noch 120 Punkte und 5 gelernte Wörter bis Stufe 4"; parts that are fulfilled are left
     * out; [next] null = "Fertig ausgewachsen!".
     */
    fun nextStage(next: NextStage?): String {
        if (next == null) return FINISHED
        val parts = listOfNotNull(
            next.missingPoints.takeIf { it > 0 }?.let {
                PracticeTexts.formatNumber(it) + if (it == 1) " Punkt" else " Punkte"
            },
            next.missingSureWords.takeIf { it > 0 }?.let {
                if (it == 1) "1 gelerntes Wort" else "$it gelernte Wörter"
            }
        )
        return if (parts.isEmpty()) {
            "Stufe ${next.stage} ist erreicht – dein Avatar wächst gleich."
        } else {
            "Noch ${parts.joinToString(" und ")} bis Stufe ${next.stage}"
        }
    }

    const val FINISHED = "Fertig ausgewachsen!"

    /**
     * One short line below the avatar on the start screen: only what is still missing, e.g.
     * "Stufe 4: noch 120 Punkte, 5 gelernte Wörter"; null if nothing is missing, at the last stage
     * or without an avatar ([next] null).
     */
    fun homeNextStage(next: NextStage?): String? {
        if (next == null) return null
        val parts = listOfNotNull(
            next.missingPoints.takeIf { it > 0 }?.let {
                PracticeTexts.formatNumber(it) + if (it == 1) " Punkt" else " Punkte"
            },
            next.missingSureWords.takeIf { it > 0 }?.let { if (it == 1) "1 gelerntes Wort" else "$it gelernte Wörter" }
        )
        return if (parts.isEmpty()) null else "Stufe ${next.stage}: noch ${parts.joinToString(", ")}"
    }

    /**
     * Shown with a lock when the next stage is locked (see [Reach.stageLocked]); not shown at the
     * moment, since typing exists every word can become sure.
     */
    const val NEXT_STAGE_LOCKED = "Diese Stufe ist im Moment noch nicht erreichbar."

    const val EMPTY_COLLECTION = "Noch leer – hier landen deine fertigen Avatare."

    /** "Mein Avatar" while no new avatar can be chosen (see [FigureState.paused]). */
    const val PAUSED = "Super, du hast fast alle Wörter in diesem Buch gelernt! " +
        "Sobald neue Wörter dazukommen, kannst du einen neuen Avatar wählen."

    /** Result screen: "Dein Drache ist gewachsen! Stufe 3 von 10" or the finished text. */
    fun grown(kind: FigureKind, stage: Int, finished: Boolean): String =
        if (finished) {
            "${kind.withArticle} ist fertig ausgewachsen! ${kind.pronoun} kommt in deine Sammlung."
        } else {
            "${kind.withArticle} ist gewachsen! Stufe $stage von ${FigureRules.MAX_STAGE}"
        }
}
