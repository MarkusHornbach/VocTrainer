package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.data.RestartItem
import io.github.markushornbach.voctrainer.learning.AnswerForm
import io.github.markushornbach.voctrainer.learning.BadgeRules
import io.github.markushornbach.voctrainer.learning.BadgeSeries
import io.github.markushornbach.voctrainer.learning.DetailReason
import io.github.markushornbach.voctrainer.learning.FigureRules
import io.github.markushornbach.voctrainer.learning.FirstRound
import io.github.markushornbach.voctrainer.learning.LearningProgress
import io.github.markushornbach.voctrainer.learning.Leitner
import io.github.markushornbach.voctrainer.learning.MultipleChoiceGenerator
import io.github.markushornbach.voctrainer.learning.Points
import io.github.markushornbach.voctrainer.learning.QuestionDirection
import io.github.markushornbach.voctrainer.learning.Reach
import io.github.markushornbach.voctrainer.learning.StreakInfo
import io.github.markushornbach.voctrainer.learning.Typo
import io.github.markushornbach.voctrainer.learning.word
import io.github.markushornbach.voctrainer.settings.AppSettings
import io.github.markushornbach.voctrainer.settings.QuizMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoTextsTest {
    /** Every line and table cell of every info page (with the data the help page may have). */
    private fun allPageTexts(): List<String> {
        val lines = QuizMode.entries.flatMap(InfoTexts::levelBlocks).flatMap { it.lines + listOfNotNull(it.title?.let(::InfoLine)) } +
            InfoTexts.figureLines() + InfoTexts.pointsLines(restartNote = true) +
            InfoTexts.streakLines() + InfoTexts.badgeLines() + InfoTexts.selectionLines() +
            InfoTexts.bookLines() + InfoTexts.restartLines() +
            InfoTexts.practiceSettingsLines()
        val rows = InfoTexts.pointsRows() + InfoTexts.streakRows(3, jokerUsed = true, longest = 9) +
            InfoTexts.selectionRows() + InfoTexts.detailSymbolRows() + InfoTexts.detailReasonRows() + InfoTexts.bookRows()
        val steps = InfoTexts.askSteps().flatMap { listOf(it.direction, it.form, it.levels) }
        return lines.map { it.text } + rows.flatMap { listOf(it.label, it.value) } + steps +
            InfoTexts.badgeRows().map { it.label } + InfoTexts.restartResetLines() + InfoTexts.restartKeepLines() +
            InfoPage.entries.map { it.title }
    }

    /** The small notes, which may be a little longer. */
    private fun allNotes(): List<String> =
        (QuizMode.entries.flatMap(InfoTexts::levelBlocks).flatMap { it.lines } + InfoTexts.figureLines()).mapNotNull { it.note } +
            (InfoTexts.pointsRows() + InfoTexts.streakRows(3, jokerUsed = false, longest = 9)).mapNotNull { it.note }

    @Test
    fun linesAreShortAndHaveNoFullStop() {
        for (text in allPageTexts()) {
            assertTrue(text, text.length <= MAX_LINE)
            assertFalse(text, text.endsWith("."))
        }
        for (note in allNotes()) assertFalse(note, note.endsWith("."))
    }

    @Test
    fun forbiddenTermsAreAbsent() {
        val forbidden = listOf(
            Regex("fällig", RegexOption.IGNORE_CASE),
            Regex("Multiple Choice", RegexOption.IGNORE_CASE),
            Regex("""\bFigur\b"""),
            // "sicher" in the sense of level 4 ("Sicherung" is the backup and allowed).
            Regex("""\bsicher\b""", RegexOption.IGNORE_CASE),
            Regex("Deckel"),
            Regex("Kernwort"),
            // No review intervals in days.
            Regex("""[0-9]+ Tage(n)? (Pause|warten|später)"""),
            // No second role and nothing that stays "forever".
            Regex("Eltern|Lehrkr"),
            Regex("für immer|nie verloren", RegexOption.IGNORE_CASE)
        )
        for (text in allPageTexts() + allNotes()) for (term in forbidden) assertFalse("$term in $text", term.containsMatchIn(text))
    }

    @Test
    fun helpPageGroupsAndOrder() {
        assertEquals(
            listOf("Vokabeln auswählen", "Was bedeuten die Level?", "Verben üben"),
            InfoPage.entries.filter { it.group == InfoGroup.PRACTICE }.map { it.title }
        )
        assertEquals(
            listOf("So bekommst du Punkte", "So wächst dein Avatar", "Abzeichen", "Deine Serie", "Zeichen in „Details“"),
            InfoPage.entries.filter { it.group == InfoGroup.REWARDS }.map { it.title }
        )
        assertEquals(
            listOf("Bücher und Sicherung", "Buch neu beginnen", "Tagesziel & Erinnerung"),
            InfoPage.entries.filter { it.group == InfoGroup.SETTINGS }.map { it.title }
        )
        assertEquals(((1..12) - 2).toList(), InfoPage.entries.map { it.number }.sorted())
        assertEquals(PracticeTexts.STREAK_TITLE, InfoPage.STREAK.title)
        assertEquals(RestartBookTexts.TITLE, InfoPage.RESTART.title)
    }

    @Test
    fun levelRulesComeFromTheConstants() {
        assertEquals(
            listOf(
                "✔ Antwort richtig: +1 Level", "✘ Antwort falsch: −1 Level", "Es zählt dein erster Versuch",
                "Die Wiederholung am Rundenende zählt nicht",
                "Level ${Leitner.NEXT_DAY_MAX_LEVEL}: frühestens am nächsten Tag",
                "Level ${Leitner.MAX_LEVEL}: nach längerer Zeit noch gewusst"
            ),
            InfoTexts.levelRules().lines.map { it.text }
        )
        assertEquals(listOf("So steigen Wörter auf"), InfoTexts.levelBlocks(QuizMode.VOCABULARY).map { it.title })
    }

    @Test
    fun verbPageStartsWithVerbenUebenAndHasNoRulesBlock() {
        val blocks = InfoTexts.levelBlocks(QuizMode.VERBS)
        assertEquals(listOf("Verben üben"), blocks.map { it.title })
        assertEquals(
            listOf(
                "Unregelmäßige Verben: Simple Past", "Immer mit Vorschlägen",
                "Keine Punkte, keine Serie, keine Abzeichen", "Manchmal auch regelmäßige Verben (-ed)",
                "Drei Formen (go – went – gone): Einstellungen → Üben"
            ),
            blocks[0].lines.map { it.text }
        )
        assertFalse(blocks.flatMap { it.lines }.any { it.text.contains("Tippen") })
    }

    @Test
    fun verbPageWithThreeFormsNamesBothForms() {
        val blocks = InfoTexts.levelBlocks(QuizMode.VERBS, threeVerbForms = true)
        assertEquals(listOf("Verben üben"), blocks.map { it.title })
        assertEquals(
            listOf(
                "Unregelmäßige Verben: Simple Past und Past Participle", "Erst Simple Past, dann Past Participle",
                "Richtig nur, wenn beide stimmen", "Immer mit Vorschlägen",
                "Keine Punkte, keine Serie, keine Abzeichen", "Manchmal auch regelmäßige Verben (-ed)",
                "Nur zwei Formen: Einstellungen → Üben"
            ),
            blocks[0].lines.map { it.text }
        )
        // The vocabulary page does not depend on the verb forms.
        assertEquals(InfoTexts.levelBlocks(QuizMode.VOCABULARY), InfoTexts.levelBlocks(QuizMode.VOCABULARY, threeVerbForms = true))
    }

    @Test
    fun askStepsFollowTheLevelGroups() {
        val steps = InfoTexts.askSteps()
        assertEquals(
            listOf(LevelQuestionKind.EN_TO_DE_CHOOSE, LevelQuestionKind.DE_TO_EN_CHOOSE, LevelQuestionKind.DE_TO_EN_TYPE),
            steps.map { it.kind }
        )
        val de = QuestionDirection.GERMAN_TO_ENGLISH_FROM_LEVEL
        val typing = QuestionDirection.TYPING_FROM_LEVEL
        val first = if (de - 1 == 0) "Level 0" else "Level 0–${de - 1}"
        val second = if (typing - 1 == de) "Level $de" else "Level $de–${typing - 1}"
        assertEquals(listOf(first, second, "ab Level $typing"), steps.map { it.levels })
        assertEquals(listOf("Mit Vorschlägen", "Mit Vorschlägen", "Tippen"), steps.map { it.form })
        assertEquals(listOf("🇬🇧 → 🇩🇪", "🇩🇪 → 🇬🇧", "🇩🇪 → 🇬🇧"), steps.map { it.direction })
    }

    @Test
    fun figureLinesUseTheConstants() {
        assertEquals(
            listOf(
                "Jede Stufe braucht Punkte", "Ab Stufe ${FigureRules.firstSureWordStage} auch gelernte Wörter"
            ),
            InfoTexts.figureLines().map { it.text }
        )
    }

    @Test
    fun pointsTableComesFromTheRules() {
        val rows = InfoTexts.pointsRows()
        val example = Leitner.PRACTICE_MAX_LEVEL
        val max = Leitner.MAX_LEVEL
        val hold = Points.forFirstAttempt(max, max, correct = true, early = false, form = AnswerForm.TYPED)
        assertEquals(
            listOf(
                "Wort erreicht zum ersten Mal ein Level", "Level $max nach längerer Zeit wieder getippt",
                "Tagesziel geschafft", "Level schon einmal erreicht", "Falsch · Nochmal versuchen · Fast richtig"
            ),
            rows.map { it.label }
        )
        assertEquals(listOf("so viel wie das Level", "$hold", "${Points.DAILY_BONUS_BASE} + Serie", "0", "0"), rows.map { it.value })
        assertEquals("Level $example = ${Points.forNewLevels(example - 1, example)}", rows[0].note)
        assertEquals("1 pro Serientag, höchstens ${Points.DAILY_BONUS_MAX}", rows[2].note)
        assertEquals(listOf(3, 4), rows.indices.filter { rows[it].muted })
        assertEquals(
            listOf("Verben: keine Punkte"),
            InfoTexts.pointsLines(restartNote = false).map { it.text }
        )
        assertEquals("Nur „Buch neu beginnen“ setzt Punkte auf 0", InfoTexts.pointsLines(restartNote = true).last().text)
    }

    @Test
    fun streakTableWithAndWithoutValues() {
        val known = InfoTexts.streakRows(3, jokerUsed = true, longest = 9)
        assertEquals(listOf("Tagesziel", "Serie", "Joker", "Längste Serie"), known.map { it.label })
        assertEquals("3 Runden pro Tag", known[0].value)
        assertEquals("Ändern: Einstellungen", known[0].note)
        assertEquals("Diese Woche: eingesetzt", known[2].note)
        assertEquals("9 Tage", known[3].value)
        assertEquals("Diese Woche: frei", InfoTexts.streakRows(2, jokerUsed = false, longest = 1)[2].note)
        val unknown = InfoTexts.streakRows(2, jokerUsed = null, longest = null)
        assertEquals(listOf("Tagesziel", "Serie", "Joker"), unknown.map { it.label })
        assertEquals(null, unknown[2].note)
        assertEquals(listOf("Nur Vokabelrunden zählen", "Heute zählt bis Mitternacht"), InfoTexts.streakLines().map { it.text })
    }

    @Test
    fun badgeTableEqualsTheSeries() {
        assertEquals((1..BadgeRules.MAX_TIER).map(BadgeRules::tierName), InfoTexts.badgeTiers())
        assertEquals(listOf("Bronze", "Silber", "Gold", "Platin"), InfoTexts.badgeTiers())
        val rows = InfoTexts.badgeRows()
        assertEquals(BadgeSeries.entries.map { it.thresholds }, rows.map { it.thresholds })
        assertEquals(listOf("Serie (Tage)", "Gelernte Wörter"), rows.map { it.label })
    }

    @Test
    fun selectionPageNamesTheRealLabels() {
        assertEquals(listOf("Unit", "Part", "Seiten"), InfoTexts.selectionRows().map { it.label })
        assertEquals(listOf("Kombinieren geht"), InfoTexts.selectionLines().map { it.text })
    }

    @Test
    fun detailsPageCoversEverySymbolAndReason() {
        val symbols = InfoTexts.detailSymbolRows().map { it.label }
        for (symbol in listOf(RoundDetailTexts.SYMBOL_UP, RoundDetailTexts.SYMBOL_SAME, RoundDetailTexts.SYMBOL_DOWN)) {
            assertTrue(symbol, symbols.any { it.startsWith(symbol) })
        }
        assertTrue(symbols.contains(RoundDetailTexts.pointsText(LearningProgress.SURE_LEVEL)))
        // Every text a reason can show is on the page with a meaning of 2 to 5 words.
        val reasons = DetailReason.entries.flatMap(RoundDetailTexts::reasonTexts) +
            RoundDetailTexts.REACHED_BEFORE + RoundDetailTexts.LATER_POINTS
        val rows = InfoTexts.detailReasonRows()
        for (reason in reasons) {
            val meaning = InfoTexts.detailReasonMeaning(reason)
            assertNotNull("no meaning for „$reason“", meaning)
            assertTrue(meaning!!, meaning.split(" ").size in 2..5)
            assertTrue(rows.any { it.label == reason && it.value == meaning })
        }
        assertEquals(rows.size, rows.map { it.label }.distinct().size)
        assertTrue(rows.any { it.label == "mit Vorschlägen nur bis Level ${Leitner.CHOICE_MAX_TYPEABLE}" })
    }

    @Test
    fun bookPageNamesTheButtonsAsOnThePage() {
        assertEquals(
            listOf(
                ManageVocabularyTexts.IMPORT, ManageVocabularyTexts.EXPORT, ManageVocabularyTexts.RESTORE,
                BookTexts.LIST_TITLE
            ),
            InfoTexts.bookRows().map { it.label }
        )
        assertEquals("Vokabeln importieren", ManageVocabularyTexts.IMPORT)
        assertEquals(
            "Lernstand deiner Bücher in eine Datei (ohne Beispielbuch)",
            InfoTexts.bookRows().single { it.label == ManageVocabularyTexts.EXPORT }.value
        )
        assertEquals(
            listOf("Immer nur ein Buch aktiv", "Lernstand jedes Buchs bleibt erhalten"),
            InfoTexts.bookLines().map { it.text }
        )
        // No device sync any more: nothing on the page speaks of it.
        val page = InfoTexts.bookRows().flatMap { listOf(it.label, it.value) } + InfoTexts.bookLines().map { it.text }
        assertTrue(page.none { it.contains("abgleich", ignoreCase = true) })
    }

    @Test
    fun restartPageListsWhatBookRestartResets() {
        assertEquals(RestartItem.entries.map(InfoTexts::restartResetLine), InfoTexts.restartResetLines())
        assertEquals(
            listOf("Wörter und Verben auf „neu“", "Punkte", "Abzeichen", "Serie", "wachsender Avatar"),
            InfoTexts.restartResetLines()
        )
        assertEquals(
            listOf("fertige Avatare", "Vokabeln des Buchs", "Auswahl", "andere Bücher", "Einstellungen"),
            InfoTexts.restartKeepLines()
        )
        // The confirmation dialog names the same things.
        val ask = RestartBookTexts.askText("X")
        for (kept in listOf("fertigen Avatare", "Auswahl", "andere Bücher", "Einstellungen")) assertTrue(kept, ask.contains(kept))
    }

    @Test
    fun restartQuestionOfARealBookAnnouncesTheBackup() {
        val ask = RestartBookTexts.askText("Testbuch 6")
        assertTrue(ask.contains(RestartBookTexts.BACKUP_FIRST))
        assertFalse(ask.contains(RestartBookTexts.EXAMPLE_BOOK_LOST))
        assertTrue(ask.endsWith("\n\nGilt nur für dieses Gerät."))
        assertFalse(ask.contains("Abgleich"))
    }

    @Test
    fun restartQuestionOfTheExampleBookMentionsNoBackup() {
        val ask = RestartBookTexts.askText("Beispielbuch Englisch", isExampleBook = true)
        assertFalse(ask.contains("Sicherung"))
        assertTrue(ask.contains("Der alte Stand im Beispielbuch lässt sich danach nicht zurückholen."))
        assertTrue(ask.startsWith("„Beispielbuch Englisch“ beginnt noch einmal von vorn."))
    }

    @Test
    fun practiceSettingsPageUsesTheGoalRange() {
        assertEquals(
            "Tagesziel: ${AppSettings.DAILY_GOAL_RANGE.first}–${AppSettings.DAILY_GOAL_RANGE.last} Runden pro Tag",
            InfoTexts.practiceSettingsLines().first().text
        )
    }

    @Test
    fun startScreenTexts() {
        assertEquals("Dein Buch braucht mindestens ${MultipleChoiceGenerator.DISTRACTOR_COUNT + 1} Wörter.", StartTexts.TOO_FEW_WORDS)
        assertEquals("Noch keine Verben da.", StartTexts.NO_VERBS)
        assertEquals("Starte deine erste Runde!", StartTexts.FIRST_ROUND)
    }

    @Test
    fun firstRoundIsDoneAfterAStoredRoundOrALevelAboveZero() {
        val fresh = listOf(word("a", "cat", "Katze"), word("b", "dog", "Hund"))
        assertFalse(FirstRound.done(fresh, emptyList()))
        assertFalse(FirstRound.done(fresh, listOf(0, 0)))
        assertTrue(FirstRound.done(fresh, listOf(0, 1)))
        assertTrue(FirstRound.done(fresh + word("c", "bird", "Vogel", level = 1), emptyList()))
    }

    @Test
    fun streakDialogDataFromTheStreak() {
        // Without loaded values the help page uses this device's goal (see StreakInfoDialog).
        val streak = StreakInfo(current = 2, longest = 5, roundsToday = 1, goalToday = 4, jokerUsedThisWeek = false)
        assertEquals("4 Runden pro Tag", InfoTexts.streakRows(streak.goalToday, streak.jokerUsedThisWeek, streak.longest)[0].value)
    }

    @Test
    fun stageRowsMarkReachedNextAndLockedStages() {
        val rows = InfoTexts.stageRows(currentStage = 3, reach = Reach(askable = 2_000, possibleSureWords = 30))
        assertEquals((1..FigureRules.MAX_STAGE).toList(), rows.map { it.stage })
        assertEquals(listOf(1, 2, 3), rows.filter { it.reached }.map { it.stage })
        assertEquals(listOf(4), rows.filter { it.isNext }.map { it.stage })
        // Stage 4 needs 25 (reachable), stage 5 needs 50.
        assertEquals((5..FigureRules.MAX_STAGE).toList(), rows.filter { it.locked }.map { it.stage })
        assertEquals(StageRow(3, 300, 10, reached = true, isNext = false, locked = false), rows[2])
        assertEquals(0, rows[0].sureWords)
    }

    @Test
    fun stageRowsUseTheCappedSureWords() {
        // 20 askable → at most 16 sure words.
        val rows = InfoTexts.stageRows(currentStage = 0, reach = Reach(askable = 20, possibleSureWords = 20))
        assertEquals(listOf(0, 0, 1, 3, 5, 8, 12, 16, 16, 16), rows.map { it.sureWords })
        assertFalse(rows.any { it.locked })
        assertEquals(1, rows.single { it.isNext }.stage)
    }

    @Test
    fun stageRowsOfALaterAvatarStartAtItsOffset() {
        val rows = InfoTexts.stageRows(currentStage = 0, reach = Reach(askable = 800, possibleSureWords = 800, avatarNumber = 2))
        assertEquals(listOf(0, 0, 310, 325, 350, 380, 420, 470, 530, 600), rows.map { it.sureWords })
        assertEquals(StageRow(10, 4_300, 600, reached = false, isNext = false, locked = false), rows.last())
    }

    @Test
    fun reachedStagesAreNeverLockedAndFinishedHasNoNextRow() {
        val rows = InfoTexts.stageRows(FigureRules.MAX_STAGE, Reach(askable = 200, possibleSureWords = 0))
        assertTrue(rows.all { it.reached && !it.locked && !it.isNext })
    }

    @Test
    fun stageTableScrollsToOneReachedRowAboveTheNextStage() {
        assertEquals(0, InfoTexts.stageScrollIndex(0))
        assertEquals(0, InfoTexts.stageScrollIndex(1))
        assertEquals(2, InfoTexts.stageScrollIndex(3))
        assertEquals(FigureRules.MAX_STAGE - 1, InfoTexts.stageScrollIndex(FigureRules.MAX_STAGE))
    }

    @Test
    fun lockedNoteSingularAndPlural() {
        assertEquals(
            "Noch nicht erreichbar: Im Moment können nur 12 deiner Wörter gelernt werden.",
            InfoTexts.lockedStagesNote(12)
        )
        assertTrue(InfoTexts.lockedStagesNote(1).contains("Im Moment kann nur 1 deiner Wörter gelernt werden."))
        assertTrue(InfoTexts.lockedStagesNote(0).contains("Im Moment kann noch keines deiner Wörter gelernt werden."))
    }

    @Test
    fun levelRowsWithNamesAndLocksOnlyForVocabulary() {
        val vocabulary = InfoTexts.levelRows(QuizMode.VOCABULARY)
        assertEquals((0..5).toList(), vocabulary.map { it.level })
        assertEquals(listOf("neu", "am Üben", "am Üben", "am Üben", "gelernt", "gemeistert"), vocabulary.map { it.name })
        assertFalse(vocabulary.any { it.locked })
        assertFalse(InfoTexts.levelRows(QuizMode.VERBS).any { it.locked })
    }

    private companion object {
        /** Longest line or table cell of an info page (notes may be longer). */
        const val MAX_LINE = 70
    }
}
