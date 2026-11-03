package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.settings.QuizMode

/** The expandable groups of the page "Testwerkzeuge", in display order. */
enum class TestToolGroup(val title: String) {
    LEVEL("Lernlevel"),
    AVATAR("Avatar, Punkte und Abzeichen"),
    HELP("Hilfesystem"),
    BOOKS("Bücher und Vokabeln"),
    CHECK("Prüfung testen"),
    RESET("Zurücksetzen");

    /** Test tag of the header row. */
    val tag: String get() = "testGroup_${name}"
}

/** Which groups are open. All are closed at first. */
object TestToolGroups {
    val none: Set<TestToolGroup> = emptySet()
    val all: Set<TestToolGroup> = TestToolGroup.entries.toSet()

    fun toggle(open: Set<TestToolGroup>, group: TestToolGroup): Set<TestToolGroup> =
        if (group in open) open - group else open + group

    /** The open groups as saved by `rememberSaveable` (names). */
    fun encode(open: Set<TestToolGroup>): List<String> = open.map { it.name }

    fun decode(names: List<String>): Set<TestToolGroup> =
        names.mapNotNull { name -> TestToolGroup.entries.firstOrNull { it.name == name } }.toSet()
}

/** The level that was last set, for [mode]; shown as the selected chip. */
data class LevelHighlight(val mode: QuizMode, val level: Int)

/**
 * "Lernlevel der Auswahl setzen": the toggle (Verben / Vokabeln) and the chip that was last pressed.
 * Session only. [chosenMode] null = follow the current quiz mode.
 */
data class LevelPick(val chosenMode: QuizMode? = null, val highlight: LevelHighlight? = null) {
    fun mode(quizMode: QuizMode): QuizMode = chosenMode ?: quizMode

    /** The toggle was changed: the highlight no longer fits. */
    fun withMode(mode: QuizMode): LevelPick = if (mode == chosenMode) this else LevelPick(chosenMode = mode)

    /** A level was set for [mode]; pressing the same level again keeps it. */
    fun afterSet(mode: QuizMode, level: Int): LevelPick = copy(highlight = LevelHighlight(mode, level))

    /** The quiz selection was changed. */
    fun afterSelectionChange(): LevelPick = copy(highlight = null)

    fun isSelected(mode: QuizMode, level: Int): Boolean = highlight == LevelHighlight(mode, level)
}
