package io.github.markushornbach.voctrainer.ui

import io.github.markushornbach.voctrainer.learning.QuizRound
import io.github.markushornbach.voctrainer.settings.QuizMode
import io.github.markushornbach.voctrainer.settings.Tip
import io.github.markushornbach.voctrainer.settings.TipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TipTextsTest {
    private fun card(tip: Tip) = TipTexts.content(tip)

    /** The exact texts of the catalog. */
    @Test
    fun everyTipHasItsExactCard() {
        assertEquals("📖", card(Tip.SELECTION).emoji)
        assertEquals("Welche Wörter möchtest du lernen?", card(Tip.SELECTION).title)
        assertEquals(
            listOf("Auswählen, welche Vokabeln du als nächstes lernen möchtest", "Unit, Part, Seiten aus deinem Buch"),
            card(Tip.SELECTION).lines
        )
        assertEquals("🚀", card(Tip.START_BUTTON).emoji)
        assertEquals("Los geht's", card(Tip.START_BUTTON).title)
        assertEquals(
            listOf("${QuizRound.ROUND_SIZE} Wörter pro Runde", "Erst mit Vorschlägen, später selbst tippen"),
            card(Tip.START_BUTTON).lines
        )
        assertEquals("⌨️", card(Tip.TYPING).emoji)
        assertEquals("Selbst tippen", card(Tip.TYPING).title)
        assertEquals(listOf("Zu schwer? Tippe auf „Mit Vorschlägen“ (das Wort fällt zurück)"), card(Tip.TYPING).lines)
        assertEquals("⭐", card(Tip.ROUND_RESULT).emoji)
        assertEquals("Das hast du geschafft", card(Tip.ROUND_RESULT).title)
        assertEquals(
            listOf("Aufgestiegen: Wörter, die du jetzt besser kannst", "Dafür gibt es Punkte", "Punkte lassen deinen Avatar wachsen"),
            card(Tip.ROUND_RESULT).lines
        )
        assertEquals("🐉", card(Tip.AVATAR_CARD).emoji)
        assertEquals("Dein Avatar", card(Tip.AVATAR_CARD).title)
        assertEquals(listOf("Punkte + gelernte Wörter → nächste Stufe", "Tippe auf ihn, um mehr zu erfahren."), card(Tip.AVATAR_CARD).lines)
        assertEquals("🎉", card(Tip.ALL_KNOWN).emoji)
        assertEquals("Jetzt kennst du alles Wichtige", card(Tip.ALL_KNOWN).title)
        assertEquals(listOf("Alle Erklärungen siehst du hier unter ?", "Tippe auf den Screens auf ⓘ, um mehr zu erfahren."), card(Tip.ALL_KNOWN).lines)
    }

    /** The exact texts of the three tips of decision 29. */
    @Test
    fun theAddedTipsHaveTheirExactCards() {
        val levels = card(Tip.LEVELS)
        assertEquals("📈", levels.emoji)
        assertEquals("Dein Lernstand", levels.title)
        assertEquals("Hier siehst du, auf welchem Level deine Wörter sind.", levels.lead)
        assertFalse(levels.bullets)
        assertEquals(listOf("✔ Antwort richtig: ein Level höher", "✘ Antwort falsch: ein Level zurück", "Aufklappen für mehr Details."), levels.lines)

        val verbs = card(Tip.VERBS)
        assertEquals("🔀", verbs.emoji)
        assertEquals("Verben üben", verbs.title)
        assertEquals(
            listOf(
                "Hier wechselst du zwischen Vokabeln und Verben.",
                "Bei Verben übst du die Vergangenheit (Simple Past).",
                "10 unregelmäßige Verben + 2 regelmäßige Verben, damit du beides lernst. Deshalb 12 Fragen.",
                "Lernst du schon drei Formen (go – went – gone)? Stell es um unter Einstellungen → Üben."
            ),
            verbs.lines
        )
        assertEquals(
            listOf(
                "Hier wechselst du zwischen Vokabeln und Verben.",
                "Bei Verben übst du Simple Past und Past Participle.",
                "10 unregelmäßige Verben + 2 regelmäßige Verben, damit du beides lernst. Deshalb 12 Fragen.",
                "Nur zwei Formen? Stell es um unter Einstellungen → Üben."
            ),
            TipTexts.content(Tip.VERBS, threeVerbForms = true).lines
        )
        // Only "Verben üben" depends on the verb forms.
        for (tip in Tip.entries - Tip.VERBS) assertEquals(card(tip), TipTexts.content(tip, threeVerbForms = true))

        val repeat = card(Tip.REPEAT)
        assertEquals("🔁", repeat.emoji)
        assertEquals("Wiederholung", repeat.title)
        assertEquals(
            listOf(
                "Zum Schluss übst du alle falschen Wörter noch einmal.",
                "So merkst du sie dir besser.",
                "Bringt keine Punkte und kein Level."
            ),
            repeat.lines
        )
        assertEquals(
            "Zum Schluss übst du alle falschen Verben noch einmal.",
            TipTexts.content(Tip.REPEAT, verbs = true).lines.first()
        )
        // Only "Wiederholung" depends on the mode.
        for (tip in Tip.entries - Tip.REPEAT) assertEquals(card(tip), TipTexts.content(tip, verbs = true))
        for (tip in Tip.entries - Tip.LEVELS) assertNull(card(tip).lead)
    }

    @Test
    fun noTipHasASecondButton() {
        assertEquals(emptySet<Tip>(), Tip.entries.filter { card(it).secondary != null }.toSet())
    }

    @Test
    fun tipCardsFollowTheTextRules() {
        for (tip in Tip.entries) for (threeVerbForms in listOf(false, true)) {
            val content = TipTexts.content(tip, threeVerbForms)
            // "Verben üben" has one more line: where the verb forms are switched.
            assertTrue(content.lines.size <= if (tip == Tip.VERBS) 4 else 3)
            val text = (content.lines + content.title + listOfNotNull(content.lead)).joinToString(" ")
            // Neutral: no "Neu: …", no review intervals, no "sicher" for learned words, no parents' role,
            // nothing that stays "forever".
            for (word in listOf("Neu", "fällig", "Pause", "sicher", "Figur", "Multiple Choice", "Auswahl", "Eltern", "Lehrkr", "immer", "nie verloren")) {
                assertFalse("$tip: $word", text.contains(word))
            }
        }
    }

    @Test
    fun selectionTipComesAfterTheAvatarChoiceAndBeforeTheStartButton() {
        val choosing = FigureState(loaded = true, hasBook = true)
        assertTrue(choosing.needsChoice)
        // No target while the avatar is still to be chosen (the screen leaves it out).
        assertFalse(startTipTriggers(choosing).getValue(Tip.SELECTION))

        val chosen = FigureState(loaded = true, hasBook = true, canStartNext = false)
        var state = TipState()
        assertEquals(Tip.SELECTION, TipState.next(state, startTipTriggers(chosen, selectionTipDone = false)))
        // N1 only dismissed (not "Verstanden"): N1 again, never N2.
        assertEquals(Tip.SELECTION, TipState.next(state, startTipTriggers(chosen, selectionTipDone = false)))
        state = state.understood(Tip.SELECTION)
        // "Los geht's" as soon as N1 is done (the visit rule delays it to the next visit).
        assertEquals(Tip.START_BUTTON, TipState.next(state, startTipTriggers(chosen, selectionTipDone = true)))
        // Without N1 done there is no trigger.
        assertFalse(startTipTriggers(chosen, selectionTipDone = false).getValue(Tip.START_BUTTON))
    }

    @Test
    fun startingARoundBeforeTheStartTipMarksItDone() {
        val chosen = FigureState(loaded = true, hasBook = true, canStartNext = false)
        val state = TipState().understood(Tip.SELECTION).roundStarted()
        assertTrue(Tip.START_BUTTON in state.done)
        assertNull(TipState.next(state, startTipTriggers(chosen, selectionTipDone = true)))
        // Already done: unchanged.
        assertSame(state, state.roundStarted())
    }

    @Test
    fun theAvatarTipComesAfterTheFirstFinishedRound() {
        val figure = FigureState(loaded = true, hasBook = true, canStartNext = false)
        // "Dein Lernstand" comes first (it chains to the avatar tip, see StartVisit).
        val state = TipState(done = setOf(Tip.SELECTION, Tip.START_BUTTON, Tip.LEVELS))
        assertNull(TipState.next(TipState(done = setOf(Tip.SELECTION, Tip.START_BUTTON)), startTipTriggers(figure, roundResultDone = false, levelsDone = false)))
        assertEquals(Tip.AVATAR_CARD, TipState.next(state, startTipTriggers(figure, roundResultDone = true, levelsDone = true)))
        assertNull(TipState.next(state.understood(Tip.AVATAR_CARD), startTipTriggers(figure, roundResultDone = true, levelsDone = true)))
    }

    @Test
    fun theLevelsTipComesBeforeTheAvatarTipInTheVocabularyModeOnly() {
        val figure = FigureState(loaded = true, hasBook = true, canStartNext = false)
        val state = TipState(done = setOf(Tip.SELECTION, Tip.START_BUTTON))
        assertNull(TipState.next(state, startTipTriggers(figure, roundResultDone = false)))
        assertEquals(Tip.LEVELS, TipState.next(state, startTipTriggers(figure, roundResultDone = true)))
        assertFalse(startTipTriggers(figure, roundResultDone = true, mode = QuizMode.VERBS).getValue(Tip.LEVELS))
        // Book progress alone triggers nothing: the avatar tip waits for "Dein Lernstand".
        assertNull(TipState.next(state, startTipTriggers(figure, roundResultDone = false, levelsDone = false)))
    }

    /** N2 names the round size of words: it waits for the vocabulary mode. */
    @Test
    fun theStartTipWaitsForTheVocabularyMode() {
        val chosen = FigureState(loaded = true, hasBook = true, canStartNext = false)
        assertFalse(startTipTriggers(chosen, selectionTipDone = true, mode = QuizMode.VERBS).getValue(Tip.START_BUTTON))
        assertTrue(startTipTriggers(chosen, selectionTipDone = true, mode = QuizMode.VOCABULARY).getValue(Tip.START_BUTTON))
    }

    @Test
    fun theVerbTipNeedsTheVerbModeWithVerbs() {
        val chosen = FigureState(loaded = true, hasBook = true, canStartNext = false)
        assertTrue(startTipTriggers(chosen, mode = QuizMode.VERBS, verbsShown = true).getValue(Tip.VERBS))
        // "Noch keine Verben da.": it stays open for later.
        assertFalse(startTipTriggers(chosen, mode = QuizMode.VERBS, verbsShown = false).getValue(Tip.VERBS))
        assertFalse(startTipTriggers(chosen, mode = QuizMode.VOCABULARY, verbsShown = true).getValue(Tip.VERBS))
    }

    @Test
    fun theClosingTipFollowsTheAvatarTip() {
        assertFalse(allKnownTriggered(emptySet()))
        assertFalse(allKnownTriggered(setOf(Tip.SELECTION, Tip.START_BUTTON, Tip.TYPING, Tip.ROUND_RESULT)))
        // Done by "Verstanden" or the update; it does not wait for the typing tip.
        assertTrue(allKnownTriggered(setOf(Tip.AVATAR_CARD)))
    }

    @Test
    fun testToolsNameTheSituationOfEveryTip() {
        for (tip in Tip.entries) assertTrue(tip.id, TestToolsTexts.tipSituation(tip).isNotBlank())
    }
}
