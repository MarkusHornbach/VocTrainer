package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.data.BookRestart
import io.github.markushornbach.voctrainer.data.RestartItem
import io.github.markushornbach.voctrainer.learning.AvatarPlan
import io.github.markushornbach.voctrainer.learning.BadgeRules
import io.github.markushornbach.voctrainer.learning.BadgeSeries
import io.github.markushornbach.voctrainer.learning.DetailStatus
import io.github.markushornbach.voctrainer.learning.FigureRules
import io.github.markushornbach.voctrainer.learning.LearningProgress
import io.github.markushornbach.voctrainer.learning.AnswerForm
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.learning.Points
import io.github.markushornbach.voctrainer.learning.QuestionDirection
import io.github.markushornbach.voctrainer.learning.Reach
import io.github.markushornbach.voctrainer.learning.Reachability
import io.github.markushornbach.voctrainer.learning.VerbQuestion
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode

/** One row of the stage table in "So wächst dein Avatar"; [sureWords] 0 is shown as "–". */
data class StageRow(
    val stage: Int,
    val points: Int,
    val sureWords: Int,
    val reached: Boolean,
    /** The stage the avatar grows to next ([InfoTexts.NEXT_STAGE_MARKER]). */
    val isNext: Boolean,
    /** Cannot be reached today (see [Reach.stageLocked]). */
    val locked: Boolean
)

/** One short line of an info page (half a sentence, no full stop); [note] is a small muted second line. */
data class InfoLine(val text: String, val note: String? = null)

/** A block of an info page: a small bold [title] (null = none) and its lines. */
data class InfoBlock(val title: String?, val lines: List<InfoLine>)

/** One row of a two-column table of an info page; [note] is a small muted line below [value]. */
data class InfoRow(
    val label: String,
    val value: String,
    val note: String? = null,
    /** Shown in a muted colour (e.g. answers that give nothing). */
    val muted: Boolean = false
)

/** How a word is asked on a level (the colour groups of the level list); [label] is the text beside the colour. */
enum class LevelQuestionKind(val label: String) {
    EN_TO_DE_CHOOSE("EN → DE · mit Vorschlägen"),
    DE_TO_EN_CHOOSE("DE → EN · mit Vorschlägen"),
    DE_TO_EN_TYPE("DE → EN · tippen"),

    /** Irregular verbs: the simple past is chosen on every level ([VerbQuestion.SIMPLE_PAST]). */
    VERB_SIMPLE_PAST_CHOOSE("Simple Past · mit Vorschlägen"),

    /** Irregular verbs with "3 Formen": simple past, then past participle ([VerbQuestion.THREE_FORMS]). */
    VERB_THREE_FORMS_CHOOSE("Simple Past + Past Participle · mit Vorschlägen");

    companion object {
        /** Vocabulary: from the direction and typing constants, never from fixed level numbers. */
        fun forVocabulary(level: Int): LevelQuestionKind = when {
            level >= QuestionDirection.TYPING_FROM_LEVEL -> DE_TO_EN_TYPE
            QuestionDirection.forLevel(level) == QuestionDirection.GERMAN_TO_ENGLISH -> DE_TO_EN_CHOOSE
            else -> EN_TO_DE_CHOOSE
        }

        fun forVerb(question: VerbQuestion): LevelQuestionKind = when (question) {
            VerbQuestion.SIMPLE_PAST -> VERB_SIMPLE_PAST_CHOOSE
            VerbQuestion.THREE_FORMS -> VERB_THREE_FORMS_CHOOSE
        }
    }
}

/** Consecutive level rows that are asked in the same way (one coloured block in the level list). */
data class LevelGroup(val kind: LevelQuestionKind, val rows: List<LevelRow>)

/** One row of the level list in "Was bedeuten die Level?". */
data class LevelRow(
    val level: Int,
    val name: String,
    val locked: Boolean,
    val kind: LevelQuestionKind
)

/** One step of the staircase of "Was bedeuten die Level?": [direction], [form] and its [levels]. */
data class AskStep(val kind: LevelQuestionKind, val direction: String, val form: String, val levels: String)

/** One series of the badge table: [label] and the values of tier 1..[BadgeRules.MAX_TIER]. */
data class BadgeTableRow(val label: String, val thresholds: List<Int>)

/** The groups of the help page, in this order. */
enum class InfoGroup(val label: String) {
    PRACTICE("ÜBEN"),
    REWARDS("BELOHNUNGEN"),
    SETTINGS("EINSTELLUNGEN")
}

/**
 * The info pages (ⓘ), in the order of the help page; [number] is the short catalogue id ("I7").
 * "Verben üben" opens the verb levels page ([QuizMode.VERBS]).
 */
enum class InfoPage(val number: Int, val title: String, val group: InfoGroup) {
    SELECTION(7, "Vokabeln auswählen", InfoGroup.PRACTICE),
    LEVELS(1, "Was bedeuten die Level?", InfoGroup.PRACTICE),
    VERBS(9, "Verben üben", InfoGroup.PRACTICE),
    POINTS(4, "So bekommst du Punkte", InfoGroup.REWARDS),
    FIGURE(3, "So wächst dein Avatar", InfoGroup.REWARDS),
    BADGES(6, "Abzeichen", InfoGroup.REWARDS),
    STREAK(5, "Deine Serie", InfoGroup.REWARDS),
    DETAILS(8, "Zeichen in „Details“", InfoGroup.REWARDS),
    BOOKS(10, "Bücher und Sicherung", InfoGroup.SETTINGS),
    RESTART(11, "Buch neu beginnen", InfoGroup.SETTINGS),
    PRACTICE_SETTINGS(12, "Tagesziel & Erinnerung", InfoGroup.SETTINGS)
}

/**
 * German texts of the info pages (ⓘ) as data: short lines and table rows, the dialogs only render
 * them. All numbers come from the rule constants, so the pages stay correct when the rules are tuned.
 */
object InfoTexts {
    val POINTS_TITLE = InfoPage.POINTS.title
    val FIGURE_TITLE = InfoPage.FIGURE.title
    val LEVELS_TITLE = InfoPage.LEVELS.title

    /** Marker of the next stage in the stage table of [FIGURE_TITLE]. */
    const val NEXT_STAGE_MARKER = "nächste Stufe"

    // ---- I1 / I9 "Was bedeuten die Level?" ----

    /** Block title of the level rules. */
    const val RULES_TITLE = "So steigen Wörter auf"

    /** How a level changes (vocabulary and verbs). */
    fun levelRules(): InfoBlock = InfoBlock(
        RULES_TITLE,
        listOf(
            InfoLine("✔ Antwort richtig: +1 Level"),
            InfoLine("✘ Antwort falsch: −1 Level"),
            InfoLine("Es zählt dein erster Versuch"),
            InfoLine("Die Wiederholung am Rundenende zählt nicht"),
            InfoLine("Level ${Leitner.NEXT_DAY_MAX_LEVEL}: frühestens am nächsten Tag"),
            InfoLine("Level ${Leitner.MAX_LEVEL}: nach längerer Zeit noch gewusst")
        )
    )

    /** Where "Verbformen" is switched. */
    private const val SWITCH_PLACE = "Einstellungen → Üben"

    /** First block of the verb levels page ("Verben üben"); [threeForms]: "Verbformen" is "3 Formen". */
    fun verbIntro(threeForms: Boolean = false): InfoBlock = InfoBlock(
        InfoPage.VERBS.title,
        if (threeForms) {
            listOf(
                InfoLine("Unregelmäßige Verben: Simple Past und Past Participle"),
                InfoLine("Erst Simple Past, dann Past Participle"),
                InfoLine("Richtig nur, wenn beide stimmen")
            )
        } else {
            listOf(InfoLine("Unregelmäßige Verben: Simple Past"))
        } + listOf(
            InfoLine("Immer mit Vorschlägen"),
            InfoLine("Keine Punkte, keine Serie, keine Abzeichen"),
            // RegularVerbs mixes a few of them in, so an "-ed" answer can be right.
            InfoLine("Manchmal auch regelmäßige Verben (-ed)"),
            if (threeForms) InfoLine("Nur zwei Formen: $SWITCH_PLACE")
            else InfoLine("Drei Formen (go – went – gone): $SWITCH_PLACE")
        )
    )

    /** All blocks of the levels page in [mode], without the colour groups (they come after the verb intro). */
    fun levelBlocks(mode: QuizMode, threeVerbForms: Boolean = false): List<InfoBlock> = when (mode) {
        QuizMode.VOCABULARY -> listOf(levelRules())
        QuizMode.VERBS -> listOf(verbIntro(threeVerbForms))
    }

    /** Levels 0..5 with name (no pauses: they are internal); locked above the multiple-choice cap in vocabulary mode. */
    fun levelRows(mode: QuizMode, threeVerbForms: Boolean = false): List<LevelRow> = (0..Leitner.MAX_LEVEL).map { level ->
        LevelRow(
            level = level,
            name = levelName(level),
            locked = mode == QuizMode.VOCABULARY && Reachability.levelLockedForTypeable(level),
            kind = if (mode == QuizMode.VERBS) {
                LevelQuestionKind.forVerb(if (threeVerbForms) VerbQuestion.THREE_FORMS else VerbQuestion.SIMPLE_PAST)
            } else {
                LevelQuestionKind.forVocabulary(level)
            }
        )
    }

    /** Splits [rows] into blocks of consecutive levels with the same question kind. */
    fun levelGroups(rows: List<LevelRow>): List<LevelGroup> {
        val groups = mutableListOf<LevelGroup>()
        for (row in rows) {
            val last = groups.lastOrNull()
            if (last != null && last.kind == row.kind) groups[groups.lastIndex] = last.copy(rows = last.rows + row)
            else groups += LevelGroup(row.kind, listOf(row))
        }
        return groups
    }

    /** The three steps, from the level groups of the vocabulary (lowest first). */
    fun askSteps(): List<AskStep> {
        val groups = levelGroups(levelRows(QuizMode.VOCABULARY))
        return groups.mapIndexed { index, group ->
            val first = group.rows.first().level
            val last = group.rows.last().level
            val levels = when {
                index == groups.lastIndex && last == Leitner.MAX_LEVEL -> "ab Level $first"
                first == last -> "Level $first"
                else -> "Level $first–$last"
            }
            when (group.kind) {
                LevelQuestionKind.EN_TO_DE_CHOOSE -> AskStep(group.kind, "🇬🇧 → 🇩🇪", CHOOSE, levels)
                LevelQuestionKind.DE_TO_EN_CHOOSE -> AskStep(group.kind, "🇩🇪 → 🇬🇧", CHOOSE, levels)
                LevelQuestionKind.DE_TO_EN_TYPE -> AskStep(group.kind, "🇩🇪 → 🇬🇧", "Tippen", levels)
                LevelQuestionKind.VERB_SIMPLE_PAST_CHOOSE -> AskStep(group.kind, "Simple Past", CHOOSE, levels)
                LevelQuestionKind.VERB_THREE_FORMS_CHOOSE -> AskStep(group.kind, "Simple Past + Past Participle", CHOOSE, levels)
            }
        }
    }

    private const val CHOOSE = "Mit Vorschlägen"

    // ---- I3 "So wächst dein Avatar" ----

    /** Above the stage table; every line is true for the rules in [FigureRules] and [AvatarPlan]. */
    fun figureLines(): List<InfoLine> = listOf(
        InfoLine("Jede Stufe braucht Punkte"),
        InfoLine("Ab Stufe ${FigureRules.firstSureWordStage} auch gelernte Wörter")
    )

    /** All stages 1..[FigureRules.MAX_STAGE] with the requirements of the current avatar (see [AvatarPlan]). */
    fun stageRows(currentStage: Int, reach: Reach): List<StageRow> {
        val plan = AvatarPlan(reach.avatarNumber, reach.askable)
        return (1..FigureRules.MAX_STAGE).map { stage ->
            val reached = stage <= currentStage
            val required = plan.requirement(stage)
            StageRow(
                stage = stage,
                points = required.points,
                sureWords = required.sureWords,
                reached = reached,
                isNext = stage == currentStage + 1,
                locked = !reached && reach.stageLocked(stage)
            )
        }
    }

    /** Index of the first visible row: one reached row above the next stage (the last rows when finished). */
    fun stageScrollIndex(currentStage: Int): Int = (currentStage - 1).coerceIn(0, FigureRules.MAX_STAGE - 1)

    /** Below the table if any stage is locked; [possible] = [Reach.possibleSureWords]. */
    fun lockedStagesNote(possible: Int): String {
        val words = when (possible) {
            0 -> "kann noch keines deiner Wörter"
            1 -> "kann nur 1 deiner Wörter"
            else -> "können nur $possible deiner Wörter"
        }
        return "Noch nicht erreichbar: Im Moment $words gelernt werden."
    }

    // ---- I4 "So bekommst du Punkte" ----

    val POINTS_HEADER = InfoRow("Wofür", "Punkte")

    /** The table "Wofür → Punkte" (see [Points.forFirstAttempt] and [Points.dailyBonus]). */
    fun pointsRows(): List<InfoRow> {
        val example = Leitner.PRACTICE_MAX_LEVEL
        val max = Leitner.MAX_LEVEL
        val hold = Points.forFirstAttempt(max, highestLevel = max, correct = true, early = false, form = AnswerForm.TYPED)
        return listOf(
            InfoRow(
                "Wort erreicht zum ersten Mal ein Level",
                "so viel wie das Level",
                note = "Level $example = ${Points.forNewLevels(example - 1, example)}"
            ),
            InfoRow("Level $max nach längerer Zeit wieder getippt", hold.toString()),
            InfoRow(
                "Tagesziel geschafft",
                "${Points.DAILY_BONUS_BASE} + Serie",
                note = "1 pro Serientag, höchstens ${Points.DAILY_BONUS_MAX}"
            ),
            InfoRow("Level schon einmal erreicht", "0", muted = true),
            InfoRow("Falsch · Nochmal versuchen · Fast richtig", "0", muted = true)
        )
    }

    /** Below the table; [restartNote]: on the help page also the one action that resets points. */
    fun pointsLines(restartNote: Boolean): List<InfoLine> = listOfNotNull(
        InfoLine("Verben: keine Punkte"),
        InfoLine("Nur „${RestartBookTexts.TITLE}“ setzt Punkte auf 0").takeIf { restartNote }
    )

    // ---- I5 "Deine Serie" ----

    /** [jokerUsed] and [longest] null = not known (the part is left out). */
    fun streakRows(goal: Int, jokerUsed: Boolean?, longest: Int?): List<InfoRow> = listOfNotNull(
        InfoRow("Tagesziel", "$goal Runden pro Tag", note = "Ändern: Einstellungen"),
        InfoRow("Serie", "Tage in Folge mit Tagesziel"),
        InfoRow(
            "Joker",
            "1 Tag pro Woche auslassen",
            note = jokerUsed?.let { if (it) "Diese Woche: eingesetzt" else "Diese Woche: frei" }
        ),
        longest?.let { InfoRow("Längste Serie", PracticeTexts.days(it)) }
    )

    fun streakLines(): List<InfoLine> = listOf(
        InfoLine("Nur Vokabelrunden zählen"),
        InfoLine("Heute zählt bis Mitternacht")
    )

    // ---- I6 "Abzeichen" ----

    /** Column titles: the tier names of [BadgeRules]. */
    fun badgeTiers(): List<String> = (1..BadgeRules.MAX_TIER).map(BadgeRules::tierName)

    fun badgeRows(): List<BadgeTableRow> = BadgeSeries.entries.map { series ->
        val label = when (series) {
            BadgeSeries.STREAK -> "${series.displayName} (Tage)"
            BadgeSeries.SURE_WORDS -> series.displayName
        }
        BadgeTableRow(label, (1..BadgeRules.MAX_TIER).map { BadgeRules.threshold(series, it) })
    }

    fun badgeLines(): List<InfoLine> = listOf(
        InfoLine("Bringen keine Punkte"),
        InfoLine("Nur Vokabelrunden")
    )

    // ---- I7 "Vokabeln auswählen" / "Verben auswählen" ----

    fun selectionTitle(mode: QuizMode): String =
        if (mode == QuizMode.VERBS) "Verben auswählen" else InfoPage.SELECTION.title

    fun selectionRows(): List<InfoRow> = listOf(
        InfoRow("Unit", "Kapitel im Buch"),
        InfoRow("Part", "Teil der Unit (erst Unit wählen)"),
        InfoRow("Seiten", "von … bis")
    )

    /** The count line is the one below the filters ([countLabel] with " ausgewählt"). */
    fun selectionLines(): List<InfoLine> = listOf(
        InfoLine("Kombinieren geht")
    )

    // ---- I8 "Zeichen in „Details“" ----

    val DETAILS_SYMBOL_HEADER = InfoRow("Zeichen", "Bedeutung")
    val DETAILS_REASON_HEADER = InfoRow("Kurzer Grund", "Bedeutung")

    /** The symbols of a row in "Details", built by [RoundDetailTexts] itself (example levels around "gelernt"). */
    fun detailSymbolRows(): List<InfoRow> {
        val level = LearningProgress.SURE_LEVEL
        return listOf(
            InfoRow(RoundDetailTexts.statusText(DetailStatus.UP, level), "aufgestiegen auf Level $level"),
            InfoRow(RoundDetailTexts.statusText(DetailStatus.SAME, level - 1), "bleibt auf Level ${level - 1}"),
            InfoRow(RoundDetailTexts.statusText(DetailStatus.DOWN, level - 2), "zurück auf Level ${level - 2}"),
            InfoRow(RoundDetailTexts.pointsText(level), "Punkte für dieses Wort")
        )
    }

    /** The meaning of every short reason [RoundDetailTexts] can show; null = no meaning yet. */
    fun detailReasonMeaning(reason: String): String? = when (reason) {
        RoundDetailTexts.NO_RISE_TODAY -> "richtig, Level bleibt heute"
        RoundDetailTexts.RISE_TOMORROW -> "ab morgen geht es höher"
        RoundDetailTexts.REACHED_BEFORE -> "keine neuen Punkte"
        RoundDetailTexts.LATER_POINTS -> "Punkte gibt es später wieder"
        RoundDetailTexts.CHOICE_CAP -> "für mehr: selbst tippen"
        RoundDetailTexts.ALSO_CORRECT -> "auch richtig, Level bleibt"
        RoundDetailTexts.ALMOST_TYPO, RoundDetailTexts.ALMOST_AMERICAN -> "Level bleibt, keine Punkte"
        RoundDetailTexts.TOP -> "höher geht es nicht"
        RoundDetailTexts.WRONG -> "ein Level zurück"
        else -> null
    }

    fun detailReasonRows(): List<InfoRow> = RoundDetailTexts.allReasons().map { reason ->
        InfoRow(reason, detailReasonMeaning(reason) ?: "")
    }

    // ---- I10 "Bücher und Sicherung" ----

    val BOOKS_HEADER = InfoRow("Knopf", "Was passiert")

    /** One row per button of "Vokabeln verwalten", labels as on the page. */
    fun bookRows(): List<InfoRow> = listOf(
        InfoRow(ManageVocabularyTexts.IMPORT, "Buchdatei laden: neues Buch oder Buch ergänzen"),
        InfoRow(ManageVocabularyTexts.EXPORT, "Lernstand deiner Bücher in eine Datei (ohne Beispielbuch)"),
        InfoRow(ManageVocabularyTexts.RESTORE, "ersetzt alles auf diesem Gerät"),
        InfoRow(BookTexts.LIST_TITLE, "Antippen: mit diesem Buch lernen")
    )

    fun bookLines(): List<InfoLine> = listOf(
        InfoLine("Immer nur ein Buch aktiv"),
        InfoLine("Lernstand jedes Buchs bleibt erhalten")
    )

    // ---- I11 "Buch neu beginnen" ----

    const val RESTART_RESET_TITLE = "Wird zurückgesetzt"
    const val RESTART_KEEP_TITLE = "Bleibt"

    /** What [BookRestart] resets, one line per [RestartItem]. */
    fun restartResetLine(item: RestartItem): String = when (item) {
        RestartItem.LEVELS -> "Wörter und Verben auf „neu“"
        RestartItem.POINTS -> "Punkte"
        RestartItem.BADGES -> "Abzeichen"
        RestartItem.STREAK -> "Serie"
        RestartItem.GROWING_AVATAR -> "wachsender Avatar"
    }

    fun restartResetLines(): List<String> = RestartItem.entries.map(::restartResetLine)

    /** What [BookRestart] leaves untouched (the same list as [RestartBookTexts.askText]). */
    fun restartKeepLines(): List<String> =
        listOf("fertige Avatare", "Vokabeln des Buchs", "Auswahl", "andere Bücher", "Einstellungen")

    fun restartLines(): List<InfoLine> = listOf(
        InfoLine("Vorher wird eine Sicherung gespeichert (nicht beim Beispielbuch)"),
        InfoLine("Nur auf diesem Gerät")
    )

    // ---- I12 "Tagesziel & Erinnerung" ----

    /** The reminder comes only while today's goal is missing (see `ReminderWorker`). */
    fun practiceSettingsLines(): List<InfoLine> = listOf(
        InfoLine("Tagesziel: ${AppSettings.DAILY_GOAL_RANGE.first}–${AppSettings.DAILY_GOAL_RANGE.last} Runden pro Tag"),
        InfoLine("Geschafft: Bonuspunkte und Serie"),
        InfoLine("Erinnerung: jeden Tag zur gewählten Uhrzeit"),
        InfoLine("Nur wenn das Tagesziel noch fehlt")
    )
}
